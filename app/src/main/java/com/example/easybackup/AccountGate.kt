package com.example.easybackup

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

/**
 * Google Sign-In is required to use this app — mirrors the iOS app's
 * "Apple ID Required" gate. Needed because both the free-quota tracking and
 * the free-account allowlist depend on being able to identify the current
 * account.
 *
 * REQUIRED SETUP before this works: register an OAuth client ID for this
 * app in Google Cloud Console (APIs & Services → Credentials), using this
 * app's package name and signing SHA-1 fingerprint. Without that, sign-in
 * will fail with a developer error — this is the Android equivalent of the
 * App Store Connect / In-App Purchase setup the iOS version needs.
 */
object AccountGate {
    fun signInClient(context: Context): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    fun currentAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    fun isSignedIn(context: Context): Boolean = currentAccount(context) != null
}

/**
 * Lets specific Google accounts use the app completely free, bypassing both
 * the 1 GB quota and the purchase requirement entirely.
 *
 * Unlike iOS (which has no API for a human-readable Apple ID), Android's
 * Google Sign-In DOES expose the account email directly via
 * `GoogleSignInAccount.email` — so this allowlist can key on the actual
 * email address, which is simpler than the iOS version's opaque token.
 *
 * How to add someone: have them long-press the "Easy Backup" title on the
 * home screen — that shows their signed-in email in a dialog with a copy
 * option. Add it to `freeEmails` below and ship an update.
 *
 * Alternative worth considering: Google Play Console lets you add license
 * testers or generate promo codes for a one-time in-app product, so
 * specific people can get it for $0 through Google's own system — no
 * custom code needed. This allowlist is for when you want it built into
 * the app itself instead.
 */
object AllowList {
    val freeEmails: Set<String> = setOf(
        // "friend@example.com",
    )

    fun currentUserIsFree(context: Context): Boolean {
        val email = AccountGate.currentAccount(context)?.email ?: return false
        return freeEmails.contains(email.lowercase())
    }
}
