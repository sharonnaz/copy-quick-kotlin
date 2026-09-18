package com.example.easybackup

import android.content.Context
import android.text.format.Formatter

/**
 * Tracks how many bytes have been backed up so far, to enforce the free
 * 1 GB quota before requiring the unlock purchase.
 *
 * Stored in `SharedPreferences` — same-device only. The iOS version of this
 * app uses iCloud's key-value store, which syncs per signed-in Apple ID, so
 * a reinstall on the same account doesn't quietly reset the quota. Android
 * has no equivalent built into the platform for free: the closest options
 * are Google Drive's hidden appDataFolder API or a Firebase project, both of
 * which need real setup (a Google Cloud project, API keys) beyond what's
 * wired up here. This is an honest gap, not an oversight — if you want true
 * per-Google-account persistence across reinstalls, wiring in one of those
 * is the next step; until then, this tracks per-device only, and a
 * reinstall does reset it. The Google Sign-In requirement (see AccountGate)
 * still makes sense on its own — it's what the free-account allowlist and
 * the purchase both rely on to identify the current user.
 */
object UsageTracker {
    /** 1 GiB, matching how Android itself reports storage. */
    const val FREE_QUOTA_BYTES: Long = 1_073_741_824L

    private const val PREFS = "usage_tracker"
    private const val KEY_BYTES = "total_bytes_backed_up_v1"

    fun totalBytesBackedUp(context: Context): Long =
        prefs(context).getLong(KEY_BYTES, 0L)

    fun addBytes(context: Context, n: Long) {
        if (n <= 0) return
        val current = totalBytesBackedUp(context)
        prefs(context).edit().putLong(KEY_BYTES, current + n).apply()
    }

    /** Clears backed-up byte count — debug / testing only. */
    fun resetQuotaForTesting(context: Context) {
        prefs(context).edit().putLong(KEY_BYTES, 0L).apply()
    }

    fun remainingFreeBytes(context: Context): Long =
        (FREE_QUOTA_BYTES - totalBytesBackedUp(context)).coerceAtLeast(0L)

    fun hasFreeQuotaRemaining(context: Context): Boolean =
        remainingFreeBytes(context) > 0

    fun usedFraction(context: Context): Float =
        (totalBytesBackedUp(context).toFloat() / FREE_QUOTA_BYTES.toFloat()).coerceIn(0f, 1f)

    /** e.g. "612 MB of 1 GB free" */
    fun formattedUsage(context: Context): String {
        val used = Formatter.formatShortFileSize(context, totalBytesBackedUp(context))
        val total = Formatter.formatShortFileSize(context, FREE_QUOTA_BYTES)
        return "$used of $total free"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
