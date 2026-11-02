package com.example.duudle

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.example.duudle.ui.theme.DuudleColors
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.messaging.FirebaseMessaging
import java.net.HttpURLConnection
import java.net.URL

// Profile > Delete account (Phase 5a). The deleteAccount Cloud Function deletes everything
// stored for the account (functions/accountDeletion.js); this phone then forgets it.

private const val TAG = "DuudleDebug"

/** The deleteAccount function in the app's own Firebase project (live or staging). */
fun deleteAccountUrl(projectId: String): String =
    "https://us-central1-$projectId.cloudfunctions.net/deleteAccount"

/** What the server's answer means. A 401 can mean an earlier try already deleted the account. */
enum class DeleteResponse { DELETED, CHECK_ACCOUNT, FAILED }

fun deleteResponseFor(httpStatus: Int): DeleteResponse = when (httpStatus) {
    200 -> DeleteResponse.DELETED
    401 -> DeleteResponse.CHECK_ACCOUNT
    else -> DeleteResponse.FAILED
}

/** Deletes the signed-in account on the server, then on this phone. [onDone] runs on the main thread. */
fun deleteMyAccount(context: Context, onDone: (Boolean) -> Unit) {
    val app = context.applicationContext
    val main = Handler(Looper.getMainLooper())
    fun finish(ok: Boolean) = main.post {
        if (ok) forgetAccountOnThisPhone(app)
        onDone(ok)
    }
    val user = FirebaseAuth.getInstance().currentUser
    val projectId = FirebaseApp.getInstance().options.projectId
    if (user == null || projectId.isNullOrBlank()) {
        onDone(false)
        return
    }
    user.getIdToken(true)
        .addOnSuccessListener { result ->
            val token = result.token ?: return@addOnSuccessListener onDone(false)
            Thread {
                val status = try {
                    postDelete(deleteAccountUrl(projectId), token)
                } catch (e: Exception) {
                    Log.e(TAG, "Delete account request failed: ${e.message}")
                    -1
                }
                when (deleteResponseFor(status)) {
                    DeleteResponse.DELETED -> finish(true)
                    DeleteResponse.FAILED -> finish(false)
                    // The account is gone if reloading it says the user no longer exists.
                    DeleteResponse.CHECK_ACCOUNT -> main.post {
                        user.reload()
                            .addOnSuccessListener { finish(false) }
                            .addOnFailureListener { e -> finish(e is FirebaseAuthInvalidUserException) }
                    }
                }
            }.start()
        }
        .addOnFailureListener { e ->
            // Refreshing the token of an already deleted account fails the same way.
            if (e is FirebaseAuthInvalidUserException) finish(true) else onDone(false)
        }
}

private fun postDelete(url: String, idToken: String): Int {
    val connection = URL(url).openConnection() as HttpURLConnection
    return try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 120_000
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $idToken")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write("{}".toByteArray()) }
        connection.responseCode
    } finally {
        connection.disconnect()
    }
}

/** Clears everything this phone kept for the account and signs out. */
fun forgetAccountOnThisPhone(context: Context) {
    DoodleSnooze.list(context).forEach { DoodleSnooze.cancel(context, it.doodleId) }
    DoodleSnooze.resume(context)
    DoodleSnooze.clearWaitingNotification(context)
    NotificationManagerCompat.from(context).cancelAll()
    context.getSharedPreferences("duudle_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    Blocks.clear()
    FirebaseMessaging.getInstance().deleteToken()
    FirebaseAuth.getInstance().signOut()
}

/**
 * "Delete your account?" with what goes. While the server works the dialog can't be
 * dismissed; on success [onDeleted] runs after this phone has signed out.
 */
@Composable
fun DeleteAccountDialog(username: String, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    var deleting by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text("Delete your account?") },
        text = {
            Column {
                Text(
                    "This permanently deletes @$username: your profile, friends, friend requests, the doodles " +
                        "and flash cards you sent and received, your chats, saved doodles and settings. Your " +
                        "friends lose your doodles and chats too. It can't be undone.",
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Anything someone has reported is kept until we've reviewed it, then deleted.",
                    fontSize = 13.sp,
                    color = DuudleColors.Muted
                )
                if (deleting) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = DuudleColors.Ink)
                        Spacer(Modifier.width(10.dp))
                        Text("Deleting…", fontSize = 14.sp)
                    }
                } else if (failed) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Couldn't delete your account. Check your connection and try again.",
                        fontSize = 14.sp,
                        color = DuudleColors.ErrorRed
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !deleting,
                onClick = {
                    deleting = true
                    failed = false
                    deleteMyAccount(context) { ok ->
                        deleting = false
                        if (ok) onDeleted() else failed = true
                    }
                }
            ) { Text(if (failed) "Try again" else "Delete account", color = DuudleColors.ErrorRed, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(enabled = !deleting, onClick = onDismiss) { Text("Cancel", color = DuudleColors.Ink) }
        }
    )
}
