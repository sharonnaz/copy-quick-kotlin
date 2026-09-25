package com.example.copyquick

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

/**
 * Optional Google account lookup for the long-press home title and the
 * release allowlist. Sign-in is NOT required to back up — same as iOS
 * after the Apple ID gate was removed.
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
 * Grants a full unlock (paid tier, no 1 GB cap, no purchase) to the
 * developer’s own installs. Everyone else still uses the free 1 GB quota.
 *
 * Debug APKs skip the cap entirely (mirrors iOS DEBUG). Release builds
 * can still match a signed-in Google email from [freeEmails].
 */
object AllowList {
    val freeEmails: Set<String> = setOf(
        "sharon.naz@gmail.com",
        "7994238183",
        "+917994238183",
        "917994238183",
    )

    fun currentUserIsFree(context: Context): Boolean {
        if (BuildConfig.DEBUG) return true
        val email = AccountGate.currentAccount(context)?.email ?: return false
        val normalized = email.trim().lowercase()
        if (freeEmails.contains(normalized)) return true
        val digits = normalized.filter { it.isDigit() }
        if (digits.length >= 10) {
            val last10 = digits.takeLast(10)
            return freeEmails.any { candidate ->
                val candidateDigits = candidate.filter { it.isDigit() }
                candidateDigits.length >= 10 && candidateDigits.takeLast(10) == last10
            }
        }
        return false
    }
}
