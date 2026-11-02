package com.example.duudle

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

// Phase 5b: link the anonymous account to a Google account, so it can be signed back into
// on a new phone, after a reinstall, or after the package name changes. The uid stays the
// same, so every doodle, friend and chat comes along.

private const val TAG = "DuudleDebug"

/**
 * The OAuth web client id the google-services plugin generates from google-services.json,
 * or null while this build's Firebase project has no Google sign-in set up (then the app
 * hides the Google options).
 */
@SuppressLint("DiscouragedApi") // the resource only exists once Google sign-in is configured
fun googleWebClientId(context: Context): String? {
    val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    return if (id == 0) null else context.getString(id).ifBlank { null }
}

/** The Google email this account is linked to, or null if it isn't linked. */
fun linkedGoogleEmail(user: FirebaseUser?): String? =
    user?.providerData?.firstOrNull { it.providerId == GoogleAuthProvider.PROVIDER_ID }?.let { it.email ?: "" }

/** Why getting a Google account from the phone ended. */
sealed class GooglePick {
    data class Token(val idToken: String) : GooglePick()
    data object Cancelled : GooglePick()
    data object NoAccount : GooglePick()
    data object Failed : GooglePick()
}

/** Shows Google's account picker. [activityContext] must be the Activity. */
suspend fun pickGoogleAccount(activityContext: Context, webClientId: String): GooglePick = try {
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
        .build()
    val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        GooglePick.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
    } else {
        GooglePick.Failed
    }
} catch (e: GetCredentialCancellationException) {
    GooglePick.Cancelled
} catch (e: NoCredentialException) {
    GooglePick.NoAccount
} catch (e: GetCredentialException) {
    Log.e(TAG, "Google account picker failed: ${e.type}")
    GooglePick.Failed
}

enum class LinkResult { LINKED, ALREADY_USED, FAILED }

/** Adds Google as a way to sign in to the current (anonymous) account. Same uid afterwards. */
fun linkGoogleAccount(idToken: String, onDone: (LinkResult) -> Unit) {
    val user = FirebaseAuth.getInstance().currentUser ?: return onDone(LinkResult.FAILED)
    user.linkWithCredential(GoogleAuthProvider.getCredential(idToken, null))
        .addOnSuccessListener { onDone(LinkResult.LINKED) }
        .addOnFailureListener { e ->
            Log.e(TAG, "Linking Google failed: ${e.javaClass.simpleName}")
            onDone(if (e is FirebaseAuthUserCollisionException) LinkResult.ALREADY_USED else LinkResult.FAILED)
        }
}

sealed class GoogleSignIn {
    /** Back in an existing account; [termsVersion] is what it accepted (0 if unknown). */
    data class SignedIn(val username: String, val termsVersion: Int) : GoogleSignIn()
    /** No Duudle account is linked to that Google account. Nothing was left behind. */
    data object NoDuudleAccount : GoogleSignIn()
    data object Failed : GoogleSignIn()
}

/**
 * Signs in to the Duudle account linked to this Google account. Signing in with an unlinked
 * Google account would create an empty Firebase account, so that one is deleted again.
 */
fun signInWithGoogle(idToken: String, onDone: (GoogleSignIn) -> Unit) {
    val auth = FirebaseAuth.getInstance()
    auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
        .addOnSuccessListener { result ->
            val user = result.user ?: return@addOnSuccessListener onDone(GoogleSignIn.Failed)
            if (result.additionalUserInfo?.isNewUser == true) {
                user.delete().addOnCompleteListener {
                    auth.signOut()
                    onDone(GoogleSignIn.NoDuudleAccount)
                }
                return@addOnSuccessListener
            }
            val userRef = FirebaseFirestore.getInstance().collection("users").document(user.uid)
            userRef.get()
                .addOnSuccessListener { profile ->
                    val username = profile.getString("username")
                    if (username.isNullOrBlank()) {
                        auth.signOut()
                        onDone(GoogleSignIn.NoDuudleAccount)
                        return@addOnSuccessListener
                    }
                    userRef.collection("private").document("terms").get()
                        .addOnSuccessListener { terms -> onDone(GoogleSignIn.SignedIn(username, (terms.getLong("version") ?: 0L).toInt())) }
                        .addOnFailureListener { onDone(GoogleSignIn.SignedIn(username, 0)) }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Couldn't load the signed-in profile: ${e.message}")
                    auth.signOut()
                    onDone(GoogleSignIn.Failed)
                }
        }
        .addOnFailureListener { e ->
            Log.e(TAG, "Google sign-in failed: ${e.javaClass.simpleName}")
            onDone(GoogleSignIn.Failed)
        }
}

fun linkResultMessage(result: LinkResult): String = when (result) {
    LinkResult.LINKED -> "Linked. You can now sign back in with Google on any phone."
    LinkResult.ALREADY_USED -> "That Google account is already linked to another Duudle account. Pick a different one."
    LinkResult.FAILED -> "Couldn't link Google. Check your connection and try again."
}

fun googlePickMessage(pick: GooglePick): String? = when (pick) {
    is GooglePick.Token, GooglePick.Cancelled -> null
    GooglePick.NoAccount -> "Add a Google account to this phone in Settings first."
    GooglePick.Failed -> "Couldn't open Google sign-in. Try again."
}
