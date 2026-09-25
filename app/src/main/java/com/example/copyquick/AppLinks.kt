package com.example.copyquick

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Central place for Play Store / support / legal URLs.
 *
 * Legal pages: https://github.com/sharonnaz/easy-backup-legal
 */
object AppLinks {
    const val supportEmail = "sharon.naz@gmail.com"

    /** Same as applicationId — used for Play Store listing / rate. */
    const val playPackageName = "com.example.copyquick"

    /** Public legal pages — sharonnaz/easy-backup-legal on GitHub Pages. */
    const val privacyPolicy =
        "https://sharonnaz.github.io/easy-backup-legal/privacy-policy.html"
    const val termsOfService =
        "https://sharonnaz.github.io/easy-backup-legal/terms-of-service.html"

    fun openUrl(context: Context, url: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: ActivityNotFoundException) {
            // No browser available.
        }
    }

    fun writeFeedback(context: Context) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(supportEmail))
            putExtra(Intent.EXTRA_SUBJECT, "Copy Quick feedback")
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Write Feedback"))
        } catch (_: ActivityNotFoundException) {
            // No mail client.
        }
    }

    fun rateApp(context: Context) {
        val market = Uri.parse("market://details?id=$playPackageName")
        val web = Uri.parse("https://play.google.com/store/apps/details?id=$playPackageName")
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, market).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: ActivityNotFoundException) {
            openUrl(context, web.toString())
        }
    }
}
