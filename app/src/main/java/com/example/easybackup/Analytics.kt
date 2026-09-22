package com.example.easybackup

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Thin Firebase Analytics wrapper. Safe no-op until `google-services.json`
 * is present under `app/` (download from Firebase Console → Project settings).
 */
object AppAnalytics {
    private const val TAG = "AppAnalytics"
    @Volatile private var analytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context) ?: run {
                    Log.i(TAG, "google-services.json missing — analytics disabled")
                    return
                }
            }
            analytics = FirebaseAnalytics.getInstance(context)
        } catch (t: Throwable) {
            Log.i(TAG, "Firebase init skipped: ${t.message}")
            analytics = null
        }
    }

    fun log(name: String, params: Map<String, Any?> = emptyMap()) {
        val fa = analytics ?: return
        val bundle = Bundle()
        params.forEach { (key, value) ->
            when (value) {
                null -> Unit
                is String -> bundle.putString(key, value)
                is Int -> bundle.putLong(key, value.toLong())
                is Long -> bundle.putLong(key, value)
                is Double -> bundle.putDouble(key, value)
                is Float -> bundle.putDouble(key, value.toDouble())
                is Boolean -> bundle.putString(key, if (value) "true" else "false")
                else -> bundle.putString(key, value.toString())
            }
        }
        fa.logEvent(name, bundle)
    }

    fun screen(name: String) {
        log(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            mapOf(
                FirebaseAnalytics.Param.SCREEN_NAME to name,
                FirebaseAnalytics.Param.SCREEN_CLASS to name,
            ),
        )
    }

    fun backupFlowStarted(kind: String) = log("backup_flow_started", mapOf("media_kind" to kind))
    fun backupDestinationPicked(kind: String) = log("backup_destination_picked", mapOf("media_kind" to kind))
    fun backupFolderStarted(kind: String, itemCount: Int) =
        log("backup_folder_started", mapOf("media_kind" to kind, "item_count" to itemCount))
    fun backupFolderCompleted(kind: String, copied: Int) =
        log("backup_folder_completed", mapOf("media_kind" to kind, "copied_count" to copied))
    fun backupQuotaBlocked(kind: String) = log("backup_quota_blocked", mapOf("media_kind" to kind))
    fun paywallShown(source: String) = log("paywall_shown", mapOf("source" to source))
    fun purchaseStarted() = log("purchase_started")
    fun purchaseSuccess() = log("purchase_success")
    fun purchaseCancelled() = log("purchase_cancelled")
    fun purchaseFailed(reason: String) = log("purchase_failed", mapOf("reason" to reason.take(80)))
    fun restoreStarted() = log("restore_started")
    fun restoreResult(success: Boolean) = log("restore_result", mapOf("success" to if (success) "true" else "false"))
    fun settingsOpened() = log("settings_opened")
    fun supportLink(name: String) = log("support_link_opened", mapOf("link" to name))
}
