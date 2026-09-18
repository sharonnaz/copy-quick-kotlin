package com.example.easybackup

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.android.billingclient.api.ProductDetails
import kotlinx.coroutines.launch

private const val PREFS_APP = "easybackup_app"
private const val KEY_LAST_DEST_URI = "last_dest_uri"
private const val KEY_LAST_RUN_KIND = "last_run_kind"
private const val KEY_LAST_RUN_COUNT = "last_run_count"
private const val KEY_LAST_RUN_TIME = "last_run_time"

class MainActivity : ComponentActivity() {

    private lateinit var engine: CopyEngine
    private lateinit var billing: Billing

    private var pendingKind: MediaKind = MediaKind.PHOTO
    private var onFolderPicked: ((Uri) -> Unit)? = null
    private var onPermsGranted: (() -> Unit)? = null

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            onFolderPicked?.invoke(uri)
        }
    }

    private val requestPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) onPermsGranted?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = CopyEngine(applicationContext)
        billing = Billing(applicationContext)
        billing.start()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Theme.base) {
                    AppRoot(
                        engine = engine,
                        billing = billing,
                        onRequestPermissionsAndPickFolder = { kind, onPicked ->
                            pendingKind = kind
                            onFolderPicked = onPicked
                            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
                            } else {
                                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                            onPermsGranted = { launchPicker(rememberedDestination(applicationContext)) }
                            requestPerms.launch(perms)
                        },
                        onLaunchPurchase = { billing.launchPurchase(this) },
                    )
                }
            }
        }
    }

    private fun launchPicker(initialUri: Uri?) {
        // Best-effort hint at where to open — mirrors the iOS app's remembered
        // destination trick. Not guaranteed to work on every OEM's file picker,
        // same honest caveat as the iOS version's directoryURL approach.
        pickFolder.launch(initialUri)
    }
}

/** Persisted "remembered destination" — Android's SAF tree URI stays valid across
 * launches once persistable permission is taken, so (unlike iOS) no bookmark
 * resolution step is needed — just the URI string itself. */
private fun rememberedDestination(context: android.content.Context): Uri? {
    val s = context.getSharedPreferences(PREFS_APP, android.content.Context.MODE_PRIVATE)
        .getString(KEY_LAST_DEST_URI, null) ?: return null
    return try { Uri.parse(s) } catch (_: Exception) { null }
}

private fun saveRememberedDestination(context: android.content.Context, uri: Uri) {
    context.getSharedPreferences(PREFS_APP, android.content.Context.MODE_PRIVATE)
        .edit().putString(KEY_LAST_DEST_URI, uri.toString()).apply()
}

private data class LastRun(val kindLabel: String, val count: Int, val whenMillis: Long)

private fun loadLastRun(context: android.content.Context): LastRun? {
    val p = context.getSharedPreferences(PREFS_APP, android.content.Context.MODE_PRIVATE)
    val kind = p.getString(KEY_LAST_RUN_KIND, null) ?: return null
    val count = p.getInt(KEY_LAST_RUN_COUNT, 0)
    val time = p.getLong(KEY_LAST_RUN_TIME, 0L)
    if (count <= 0) return null
    return LastRun(kind, count, time)
}

private fun saveLastRun(context: android.content.Context, kindLabel: String, count: Int) {
    context.getSharedPreferences(PREFS_APP, android.content.Context.MODE_PRIVATE).edit()
        .putString(KEY_LAST_RUN_KIND, kindLabel)
        .putInt(KEY_LAST_RUN_COUNT, count)
        .putLong(KEY_LAST_RUN_TIME, System.currentTimeMillis())
        .apply()
}

private enum class Screen { HOME, COPY }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun AppRoot(
    engine: CopyEngine,
    billing: Billing,
    onRequestPermissionsAndPickFolder: (MediaKind, (Uri) -> Unit) -> Unit,
    onLaunchPurchase: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current

    var screen by remember { mutableStateOf(Screen.HOME) }
    var pendingKind by remember { mutableStateOf(MediaKind.PHOTO) }
    var showingStorageIntro by remember { mutableStateOf(false) }
    var showingPaywall by remember { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    var showingAccountId by remember { mutableStateOf(false) }

    var folderUnits by remember { mutableStateOf<List<AlbumUnit>>(emptyList()) }
    var folderStates by remember { mutableStateOf<List<FolderState>>(emptyList()) }
    var activeIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var destUri by remember { mutableStateOf<Uri?>(null) }

    var usageRefreshId by remember { mutableStateOf(0) }

    var photoCount by remember { mutableStateOf(0) }
    var videoCount by remember { mutableStateOf(0) }
    var storageUsedFraction by remember { mutableStateOf(0f) }
    var lastRun by remember { mutableStateOf<LastRun?>(null) }

    val isUnlocked by billing.isUnlocked.collectAsState()
    val productDetails by billing.productDetails.collectAsState()
    val purchaseError by billing.purchaseError.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                billing.refreshEntitlement()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun refreshHomeData() {
        storageUsedFraction = deviceStorageUsedFraction()
        lastRun = loadLastRun(context)
        scope.launch {
            photoCount = quickCount(context, MediaKind.PHOTO)
            videoCount = quickCount(context, MediaKind.VIDEO)
        }
    }

    LaunchedEffect(Unit) { refreshHomeData() }

    fun openFolderList(kind: MediaKind, uri: Uri) {
        scope.launch {
            saveRememberedDestination(context, uri)
            val units = engine.discoverFolders(kind)
            if (units.isEmpty()) {
                Haptics.warning(haptics)
                alertMessage = "No matching media was found in your library."
                return@launch
            }
            destUri = uri
            folderUnits = units
            folderStates = units.map { FolderState(it.bucketId, it.name, it.count) }
            activeIds = emptySet()
            pendingKind = kind
            screen = Screen.COPY
        }
    }

    fun startFolder(id: String) {
        val unit = folderUnits.firstOrNull { it.bucketId == id } ?: return
        val existing = folderStates.firstOrNull { it.bucketId == id } ?: return
        if (!(existing.isWaiting || existing.quotaBlocked)) return
        if (activeIds.contains(id)) return
        val root = destUri ?: return

        if (!isUnlocked && !AllowList.currentUserIsFree(context) && !UsageTracker.hasFreeQuotaRemaining(context)) {
            showingPaywall = true
            return
        }

        activeIds = activeIds + id
        scope.launch {
            engine.runFolder(
                kind = pendingKind,
                unit = unit,
                treeUri = root,
                existing = existing,
                hasQuota = { isUnlocked || AllowList.currentUserIsFree(context) || UsageTracker.hasFreeQuotaRemaining(context) },
                onBytesCopied = { bytes -> UsageTracker.addBytes(context, bytes) },
            ) { progress ->
                folderStates = folderStates.map { if (it.bucketId == id) progress else it }
                if (progress.done) {
                    activeIds = activeIds - id
                } else if (progress.quotaBlocked) {
                    activeIds = activeIds - id
                    Haptics.warning(haptics)
                    showingPaywall = true
                }
            }
        }
    }

    fun finishCopyScreen() {
        engine.cancel()
        val totalCopied = folderStates.sumOf { it.copied }
        if (totalCopied > 0) {
            saveLastRun(context, if (pendingKind == MediaKind.PHOTO) "photos" else "videos", totalCopied)
        }
        destUri = null
        screen = Screen.HOME
        refreshHomeData()
    }

    Box(Modifier.fillMaxSize()) {
        when (screen) {
            Screen.HOME -> HomeScreen(
                photoCount = photoCount,
                videoCount = videoCount,
                storageUsedFraction = storageUsedFraction,
                lastRun = lastRun,
                isUnlocked = isUnlocked || AllowList.currentUserIsFree(context),
                usageFraction = UsageTracker.usedFraction(context),
                usageText = UsageTracker.formattedUsage(context),
                usageRefreshId = usageRefreshId,
                onCopyPhotos = { pendingKind = MediaKind.PHOTO; showingStorageIntro = true },
                onCopyVideos = { pendingKind = MediaKind.VIDEO; showingStorageIntro = true },
                onFreeCardTap = { showingPaywall = true },
                onTitleLongPress = { showingAccountId = true },
            )
            Screen.COPY -> CopyScreen(
                pendingKind = pendingKind,
                folderStates = folderStates,
                onStartSelected = { ids -> ids.forEach { startFolder(it) } },
                onFinish = ::finishCopyScreen,
            )
        }
    }

    if (showingStorageIntro) {
        StorageIntroSheet(
            pendingKind = pendingKind,
            onDismiss = { showingStorageIntro = false },
            onContinue = {
                showingStorageIntro = false
                onRequestPermissionsAndPickFolder(pendingKind) { uri -> openFolderList(pendingKind, uri) }
            },
        )
    }

    if (showingPaywall) {
        PaywallSheet(
            productDetails = productDetails,
            purchaseError = purchaseError,
            showResetQuota = !UsageTracker.hasFreeQuotaRemaining(context),
            onDismiss = { showingPaywall = false },
            onPurchase = onLaunchPurchase,
            onRestore = { billing.restore() },
            onResetQuota = {
                UsageTracker.resetQuotaForTesting(context)
                usageRefreshId += 1
                Haptics.tick(haptics)
                showingPaywall = false
            },
        )
        LaunchedEffect(isUnlocked) { if (isUnlocked) showingPaywall = false }
    }

    if (alertMessage != null) {
        AlertDialog(
            onDismissRequest = { alertMessage = null },
            confirmButton = { TextButton(onClick = { alertMessage = null }) { Text("OK") } },
            title = { Text("Notice") },
            text = { Text(alertMessage ?: "") },
        )
    }

    if (showingAccountId) {
        val email = AccountGate.currentAccount(context)?.email ?: "Not signed in."
        AlertDialog(
            onDismissRequest = { showingAccountId = false },
            title = { Text("Account") },
            text = { Text(email) },
            confirmButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(email)); showingAccountId = false }) { Text("Copy") }
            },
            dismissButton = { TextButton(onClick = { showingAccountId = false }) { Text("OK") } },
        )
    }
}

// MARK: - Home

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeScreen(
    photoCount: Int,
    videoCount: Int,
    storageUsedFraction: Float,
    lastRun: LastRun?,
    isUnlocked: Boolean,
    usageFraction: Float,
    usageText: String,
    usageRefreshId: Int,
    onCopyPhotos: () -> Unit,
    onCopyVideos: () -> Unit,
    onFreeCardTap: () -> Unit,
    onTitleLongPress: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Theme.base)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Header — matches iOS: title + subtitle, tight 2dp gap
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Easy Backup",
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = Theme.textPrimary,
                modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onTitleLongPress),
            )
            Text(
                "Back up your library to an external drive",
                fontSize = 13.sp,
                color = Theme.textSecondary,
            )
        }

        // Device storage card
        NeumorphicSurface(Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StorageGauge(storageUsedFraction)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Device storage",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Theme.textPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (storageUsedFraction > 0.9f) {
                            "Nearly full — a good time to back up"
                        } else {
                            "Plenty of room, but backups are still smart"
                        },
                        fontSize = 12.sp,
                        color = Theme.textSecondary,
                    )
                }
            }
        }

        // Free backup quota card
        if (!isUnlocked) {
            key(usageRefreshId) {
                NeumorphicSurface(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onFreeCardTap() },
                    cornerRadius = 22.dp,
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        AccentIconWell(
                            icon = Icons.Filled.Lock,
                            tint = Theme.accentPurple,
                            size = 44.dp,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Free backup",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Theme.textPrimary,
                            )
                            Spacer(Modifier.height(6.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Theme.shadowDark.copy(alpha = 0.18f)),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(usageFraction.coerceIn(0f, 1f))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Theme.accentPurple),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(usageText, fontSize = 11.sp, color = Theme.textSecondary)
                        }
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = Theme.textSecondary.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        // Action tiles — tighter 14dp gap between them (matches iOS nested VStack)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ActionTile(
                title = "Backup photos",
                subtitle = if (photoCount > 0) "$photoCount photos in your library" else "Tap to scan your library",
                icon = Icons.Filled.PhotoLibrary,
                tint = Theme.accentBlue,
                onClick = onCopyPhotos,
            )
            ActionTile(
                title = "Backup videos",
                subtitle = if (videoCount > 0) "$videoCount videos in your library" else "Tap to scan your library",
                icon = Icons.Filled.VideoLibrary,
                tint = Theme.accentPurple,
                onClick = onCopyVideos,
            )
        }

        if (lastRun != null) {
            NeumorphicSurface(Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AccentIconWell(
                        icon = Icons.Filled.History,
                        tint = Theme.accentGreen,
                        size = 44.dp,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Last backup",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Theme.textPrimary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${lastRun.count} ${lastRun.kindLabel} · ${relativeTime(lastRun.whenMillis)}",
                            fontSize = 12.sp,
                            color = Theme.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Theme.base.copy(alpha = 0.65f))
                .border(1.dp, Theme.hairlineDark, RoundedCornerShape(18.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = Theme.textSecondary,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(16.dp),
            )
            Text(
                "Verify your files on the drive before deleting anything from your device to free up space.",
                fontSize = 12.sp,
                color = Theme.textSecondary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// MARK: - Storage intro (Material bottom sheet — a real system sheet, so no
// custom black-border workaround is needed the way iOS required)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StorageIntroSheet(pendingKind: MediaKind, onDismiss: () -> Unit, onContinue: () -> Unit) {
    val tint = if (pendingKind == MediaKind.PHOTO) Theme.accentBlue else Theme.accentPurple
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(18.dp)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Theme.base) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NeumorphicIconBadge(Icons.Filled.Save, tint, size = 52.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                "Where should we save the backup?",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Theme.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Plug in your drive first. On the next screen, choose the drive and pick a folder where your ${if (pendingKind == MediaKind.PHOTO) "photos" else "videos"} will be saved.",
                fontSize = 14.sp,
                color = Theme.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = Color(0xFF1A1A2E).copy(alpha = 0.07f),
                        spotColor = Color(0xFF1A1A2E).copy(alpha = 0.10f),
                    )
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(listOf(Color.White, Theme.card)),
                    )
                    .border(1.dp, Theme.hairline, shape)
                    .clickable {
                        Haptics.tap(haptics)
                        onContinue()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Continue",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Theme.textPrimary,
                )
            }
        }
    }
}

// MARK: - Paywall

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaywallSheet(
    productDetails: ProductDetails?,
    purchaseError: String?,
    showResetQuota: Boolean,
    onDismiss: () -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onResetQuota: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Theme.base) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NeumorphicIconBadge(Icons.Filled.Lock, Theme.accentPurple, size = 60.dp)
            Spacer(Modifier.height(16.dp))
            Text("You've used your free 1 GB", fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                color = Theme.textPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Backing up is free for your first 1 GB. Unlock unlimited backups to keep going — no size limit, one-time purchase.",
                fontSize = 14.sp, color = Theme.textSecondary, textAlign = TextAlign.Center,
            )
            if (purchaseError != null) {
                Spacer(Modifier.height(8.dp))
                Text(purchaseError, fontSize = 12.sp, color = Color.Red, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
            val price = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice
            Button(
                onClick = onPurchase,
                enabled = productDetails != null,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Theme.base, contentColor = Theme.textPrimary),
            ) {
                Text(if (price != null) "Unlock Unlimited — $price" else "Unlock Unlimited", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onRestore) { Text("Restore Purchases", color = Theme.textSecondary) }
            if (BuildConfig.DEBUG && showResetQuota) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onResetQuota) {
                    Text("Reset Quota (Testing)", color = Theme.textSecondary.copy(alpha = 0.85f))
                }
            }
        }
    }
}

// MARK: - Copy screen (folder list, manual per-folder start)

@Composable
private fun CopyScreen(
    pendingKind: MediaKind,
    folderStates: List<FolderState>,
    onStartSelected: (List<String>) -> Unit,
    onFinish: () -> Unit,
) {
    val kindLabel = if (pendingKind == MediaKind.PHOTO) "photos" else "videos"
    val tint = if (pendingKind == MediaKind.PHOTO) Theme.accentBlue else Theme.accentPurple
    val haptics = LocalHapticFeedback.current

    val selectableIds = remember(folderStates) {
        folderStates.filter { it.isWaiting || it.quotaBlocked }.map { it.bucketId }.toSet()
    }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(selectableIds) {
        selectedIds = selectedIds.intersect(selectableIds)
    }

    val selectedWaiting = selectedIds.filter { id ->
        folderStates.any { it.bucketId == id && (it.isWaiting || it.quotaBlocked) }
    }
    val isBusy = folderStates.any { !it.done && !it.isWaiting && !it.quotaBlocked }
    val doneCount = folderStates.count { it.done }
    val allSelectableSelected =
        selectableIds.isNotEmpty() && selectedIds.containsAll(selectableIds)
    val showConfirm = selectedWaiting.isNotEmpty()

    BackHandler(onBack = onFinish)

    Box(
        Modifier
            .fillMaxSize()
            .background(Theme.base)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(top = 8.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Backup $kindLabel",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Theme.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = Theme.textSecondary,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                Haptics.tap(haptics)
                                onFinish()
                            }
                            .padding(6.dp),
                    )
                }
                Text(
                    when {
                        showConfirm -> "Slide to send selected folders to your drive"
                        isBusy -> "Copying to your drive…"
                        selectableIds.isEmpty() && doneCount > 0 -> "All folders backed up"
                        else -> "Select the folders you want to back up"
                    },
                    fontSize = 14.sp,
                    color = Theme.textSecondary,
                )

                if (selectableIds.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            buildString {
                                append("${folderStates.size} folders")
                                if (doneCount > 0) append(" · $doneCount done")
                                if (selectedWaiting.isNotEmpty()) {
                                    append(" · ${selectedWaiting.size} selected")
                                }
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Theme.textSecondary,
                        )
                        Text(
                            if (allSelectableSelected) "Deselect all" else "Select all",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tint,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    Haptics.tap(haptics)
                                    selectedIds =
                                        if (allSelectableSelected) emptySet() else selectableIds
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 12.dp,
                    bottom = if (showConfirm || isBusy) 110.dp else 24.dp,
                ),
            ) {
                items(
                    items = folderStates,
                    key = { it.bucketId },
                ) { folder ->
                    FolderSelectCard(
                        folder = folder,
                        tint = tint,
                        selected = selectedIds.contains(folder.bucketId),
                        onToggle = {
                            selectedIds = if (selectedIds.contains(folder.bucketId)) {
                                selectedIds - folder.bucketId
                            } else {
                                selectedIds + folder.bucketId
                            }
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showConfirm || isBusy,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            enter = fadeIn(tween(220)) +
                slideInVertically(
                    initialOffsetY = { it / 3 },
                    animationSpec = tween(280),
                ),
            exit = fadeOut(tween(160)) +
                slideOutVertically(
                    targetOffsetY = { it / 4 },
                    animationSpec = tween(200),
                ),
        ) {
            if (showConfirm) {
                SlideToPowerOffControl(
                    accent = tint,
                    label = "slide to back up",
                    onCompleted = {
                        val ids = selectedWaiting
                        selectedIds = selectedIds - ids.toSet()
                        onStartSelected(ids)
                    },
                )
            } else {
                // Busy, nothing left to select — compact progress + cancel
                val active = folderStates.filter { !it.done && !it.isWaiting && !it.quotaBlocked }
                val overall =
                    if (active.isEmpty()) 0f else active.map { it.fraction }.average().toFloat()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .shadow(10.dp, RoundedCornerShape(22.dp))
                        .clip(RoundedCornerShape(22.dp))
                        .background(Theme.card)
                        .border(1.dp, Theme.hairlineDark, RoundedCornerShape(22.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Backing up…",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Theme.textPrimary,
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(100.dp))
                                .background(Theme.shadowDark.copy(alpha = 0.10f)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(overall.coerceIn(0.04f, 1f))
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(tint),
                            )
                        }
                    }
                    Text(
                        "Cancel",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tint,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                Haptics.tap(haptics)
                                onFinish()
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

private fun relativeTime(whenMillis: Long): String {
    val diff = System.currentTimeMillis() - whenMillis
    val minutes = diff / 60000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 1440 -> "${minutes / 60} hr ago"
        else -> "${minutes / 1440} d ago"
    }
}

private fun deviceStorageUsedFraction(): Float {
    return try {
        val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val available = stat.availableBytes
        if (total <= 0L) 0f else ((total - available).toFloat() / total.toFloat()).coerceIn(0f, 1f)
    } catch (_: Exception) { 0f }
}

private suspend fun quickCount(context: android.content.Context, kind: MediaKind): Int {
    return try {
        val collection = if (kind == MediaKind.PHOTO)
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        else
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        context.contentResolver.query(collection, arrayOf(android.provider.MediaStore.MediaColumns._ID), null, null, null)?.use { it.count } ?: 0
    } catch (_: Exception) { 0 }
}
