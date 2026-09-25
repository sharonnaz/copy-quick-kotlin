package com.example.copyquick

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream

enum class MediaKind { PHOTO, VIDEO }

/** One folder (MediaStore bucket) discovered up front, before any copying starts. */
data class AlbumUnit(val bucketId: String, val name: String, val count: Int)

/** Per-folder progress; Compose observes a list of these. Mirrors the iOS app's FolderProgress. */
data class FolderState(
    val bucketId: String,
    val name: String,
    val total: Int,
    val copied: Int = 0,
    val failed: Int = 0,
    val done: Boolean = false,
    val deletedFromDevice: Boolean = false,
    /** True when the free quota ran out mid-folder — distinct from `done`. Tapping again resumes from here. */
    val quotaBlocked: Boolean = false,
) {
    val fraction: Float
        get() = if (total <= 0) 1f else ((copied + failed).toFloat() / total).coerceIn(0f, 1f)
    /** True until the user has tapped this folder to start it for the first time. */
    val isWaiting: Boolean get() = !done && !quotaBlocked && copied == 0 && failed == 0
}

/**
 * Copies one folder (bucket) at a time, only when explicitly started by the
 * user — folders are listed up front but sit idle until tapped, matching
 * the iOS app's manual-start behavior. Memory-safe for very large albums:
 * rows are paged from a MediaStore Cursor, each file streams through a
 * fixed buffer (never whole-file in RAM), and progress updates are
 * throttled.
 */
class CopyEngine(private val context: Context) {

    @Volatile private var cancelRequested = false
    private val bufferSize = 1 shl 20  // 1 MB

    fun cancel() { cancelRequested = true }
    fun resetCancel() { cancelRequested = false }

    /** Lists folders (with counts) for a kind — call once when the user picks a destination. Copies nothing. */
    suspend fun discoverFolders(kind: MediaKind): List<AlbumUnit> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collection = collectionUri(kind)
        val bucketIdCol = MediaStore.MediaColumns.BUCKET_ID
        val bucketNameCol = MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
        val projection = arrayOf(bucketIdCol, bucketNameCol)
        val counts = LinkedHashMap<String, Pair<String, Int>>()

        resolver.query(collection, projection, null, null, "$bucketNameCol ASC")?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(bucketIdCol)
            val nameIdx = c.getColumnIndexOrThrow(bucketNameCol)
            while (c.moveToNext()) {
                val id = c.getString(idIdx) ?: continue
                val name = c.getString(nameIdx) ?: "Unknown"
                val e = counts[id]
                counts[id] = if (e == null) name to 1 else e.first to (e.second + 1)
            }
        }
        counts.map { (id, p) -> AlbumUnit(id, sanitize(p.first), p.second) }
    }

    /**
     * Copies one folder's worth of media, starting fresh or resuming from
     * `existing` (used when a previous attempt stopped at the free-quota
     * limit). Call this only when the user taps that specific folder —
     * nothing else starts automatically.
     *
     * `hasQuota` is checked before every file; if it returns false the loop
     * stops immediately and the folder is marked `quotaBlocked` rather than
     * `done`. Because a file's size is only known after it's copied, the
     * quota can be crossed by at most one file's worth before the next
     * check stops it — an accepted, disclosed approximation, not a
     * byte-exact cutoff.
     */
    suspend fun runFolder(
        kind: MediaKind,
        unit: AlbumUnit,
        treeUri: Uri,
        existing: FolderState?,
        hasQuota: () -> Boolean,
        onBytesCopied: (Long) -> Unit,
        onUpdate: suspend (FolderState) -> Unit,
    ) = withContext(Dispatchers.IO) {
        cancelRequested = false
        var state = (existing ?: FolderState(unit.bucketId, unit.name, unit.count)).copy(quotaBlocked = false)
        withContext(Dispatchers.Main) { onUpdate(state) }

        val destRoot = DocumentFile.fromTreeUri(context, treeUri)
        if (destRoot == null) {
            state = state.copy(done = true)
            withContext(Dispatchers.Main) { onUpdate(state) }
            return@withContext
        }
        val destFolder = destRoot.findFile(unit.name) ?: destRoot.createDirectory(unit.name)
        if (destFolder == null) {
            state = state.copy(failed = unit.count - state.copied, done = true)
            withContext(Dispatchers.Main) { onUpdate(state) }
            return@withContext
        }

        val resolver = context.contentResolver
        val collection = collectionUri(kind)
        val idCol = MediaStore.MediaColumns._ID
        val nameCol = MediaStore.MediaColumns.DISPLAY_NAME
        val mimeCol = MediaStore.MediaColumns.MIME_TYPE
        val projection = arrayOf(idCol, nameCol, mimeCol)
        val selection = "${MediaStore.MediaColumns.BUCKET_ID} = ?"
        val args = arrayOf(unit.bucketId)

        var lastEmit = 0L
        suspend fun publish(force: Boolean) {
            val now = System.currentTimeMillis()
            if (force || now - lastEmit > 100) {
                lastEmit = now
                withContext(Dispatchers.Main) { onUpdate(state) }
            }
        }

        val startIndex = state.copied + state.failed
        var rowIndex = 0

        resolver.query(collection, projection, selection, args, "$nameCol ASC")?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(idCol)
            val nameIdx = c.getColumnIndexOrThrow(nameCol)
            val mimeIdx = c.getColumnIndexOrThrow(mimeCol)

            while (c.moveToNext()) {
                if (rowIndex < startIndex) { rowIndex++; continue }   // skip already-copied rows when resuming
                if (cancelRequested) break

                if (!hasQuota()) {
                    state = state.copy(quotaBlocked = true)
                    publish(force = true)
                    return@withContext
                }

                val id = c.getLong(idIdx)
                val displayName = c.getString(nameIdx) ?: "file_$id"
                val mime = c.getString(mimeIdx) ?: "application/octet-stream"
                val srcUri = Uri.withAppendedPath(collection, id.toString())

                val bytes = copyOne(resolver, srcUri, destFolder, displayName, mime)
                state = if (bytes != null) {
                    onBytesCopied(bytes)
                    state.copy(copied = state.copied + 1)
                } else {
                    state.copy(failed = state.failed + 1)
                }
                rowIndex++
                publish(force = false)
            }
        }

        if (!cancelRequested && !state.quotaBlocked) state = state.copy(done = true)
        publish(force = true)
    }

    /** Streams one file; returns bytes written on success, null on failure. */
    private fun copyOne(
        resolver: ContentResolver,
        srcUri: Uri,
        destFolder: DocumentFile,
        displayName: String,
        mime: String,
    ): Long? {
        val finalName = nonClashingName(destFolder, displayName)
        val target = destFolder.createFile(mime, finalName) ?: return null
        return try {
            var total = 0L
            resolver.openInputStream(srcUri)?.use { rawIn ->
                resolver.openOutputStream(target.uri)?.use { rawOut ->
                    val input = BufferedInputStream(rawIn, bufferSize)
                    val output = BufferedOutputStream(rawOut, bufferSize)
                    val buf = ByteArray(bufferSize)
                    while (true) {
                        if (cancelRequested) { output.flush(); return null }
                        val read = input.read(buf)
                        if (read < 0) break
                        output.write(buf, 0, read)
                        total += read
                    }
                    output.flush()
                } ?: return null
            } ?: return null
            total
        } catch (t: Throwable) {
            try { target.delete() } catch (_: Throwable) {}
            null
        }
    }

    private fun collectionUri(kind: MediaKind): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (kind) {
                MediaKind.PHOTO -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                MediaKind.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            }
        } else {
            when (kind) {
                MediaKind.PHOTO -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                MediaKind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        }

    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[/\\\\:*?\"<>|]"), "-").trim()
        return cleaned.ifEmpty { "Folder" }
    }

    private fun nonClashingName(folder: DocumentFile, name: String): String {
        if (folder.findFile(name) == null) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while (folder.findFile("${base}_$n$ext") != null) n++
        return "${base}_$n$ext"
    }

    /**
     * Deletes every asset in this folder from the device's media library —
     * used for the "Delete from this device" option once a folder's backup
     * is done. On Android 11+, `MediaStore.createDeleteRequest` shows the
     * system's own confirmation dialog before anything is actually deleted
     * — the direct equivalent of iOS's `PHAssetChangeRequest.deleteAssets`
     * confirmation, and just as unskippable. On Android 10 and below, each
     * asset is deleted directly, which can throw a `RecoverableSecurityException`
     * per-item requiring its own consent prompt (handled by the caller via
     * the returned `IntentSender`, if any).
     *
     * Returns a `PendingIntentSender` when the caller needs to launch a
     * system confirmation UI, or `null` if deletion is already complete
     * (or failed outright with nothing to confirm).
     */
    suspend fun requestDelete(kind: MediaKind, unit: AlbumUnit): IntentSender? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collection = collectionUri(kind)
        val idCol = MediaStore.MediaColumns._ID
        val selection = "${MediaStore.MediaColumns.BUCKET_ID} = ?"
        val args = arrayOf(unit.bucketId)
        val uris = mutableListOf<Uri>()

        resolver.query(collection, arrayOf(idCol), selection, args, null)?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(idCol)
            while (c.moveToNext()) {
                uris.add(Uri.withAppendedPath(collection, c.getLong(idIdx).toString()))
            }
        }
        if (uris.isEmpty()) return@withContext null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pendingIntent = MediaStore.createDeleteRequest(resolver, uris)
            pendingIntent.intentSender
        } else {
            try {
                uris.forEach { resolver.delete(it, null, null) }
                null
            } catch (e: RecoverableSecurityException) {
                e.userAction.actionIntent.intentSender
            }
        }
    }
}
