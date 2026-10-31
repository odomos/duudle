package com.example.duudle

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.duudle.ui.theme.DuudleColors
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// ----- Report: what can be reported, and why (ids must match firestore.rules) -----

enum class ReportReason(val id: String, val label: String) {
    HARASSMENT("harassment", "Bullying or harassment"),
    HATE("hate", "Hate speech or symbols"),
    SEXUAL("sexual", "Sexual or nude content"),
    VIOLENCE("violence", "Violence, threats or self-harm"),
    SPAM("spam", "Spam or scams"),
    IMPERSONATION("impersonation", "Pretending to be someone else"),
    UNDERAGE("underage", "Might be under 13"),
    OTHER("other", "Something else")
}

/** What a report is about. [contentId] is the doodle id or chat id; null for the person. */
data class ReportSubject(
    val reportedUid: String,
    val reportedUsername: String,
    val contentType: String,
    val contentId: String? = null
) {
    companion object {
        fun user(uid: String, username: String) = ReportSubject(uid, username, "user")
        fun doodle(senderUid: String, senderUsername: String, doodleId: String) =
            ReportSubject(senderUid, senderUsername, "doodle", doodleId)
        fun chat(friendUid: String, friendUsername: String, chatId: String) =
            ReportSubject(friendUid, friendUsername, "chat", chatId)
    }
}

const val MAX_REPORT_DETAILS = 1000

/** The report document, minus the server timestamp (added on submit). Matches the rules. */
fun reportFields(reporterUid: String, subject: ReportSubject, reason: ReportReason, details: String): Map<String, Any> {
    val fields = mutableMapOf<String, Any>(
        "reporterId" to reporterUid,
        "reportedUid" to subject.reportedUid,
        "reason" to reason.id,
        "contentType" to subject.contentType,
        "status" to "open"
    )
    subject.contentId?.let { fields["contentId"] = it }
    details.trim().take(MAX_REPORT_DETAILS).takeIf { it.isNotEmpty() }?.let { fields["details"] = it }
    return fields
}

fun submitReport(reporterUid: String, subject: ReportSubject, reason: ReportReason, details: String, onDone: (Boolean) -> Unit) {
    val data = reportFields(reporterUid, subject, reason, details) + ("createdAt" to FieldValue.serverTimestamp())
    FirebaseFirestore.getInstance().collection("reports").add(data)
        .addOnSuccessListener { onDone(true) }
        .addOnFailureListener { e ->
            Log.e("DuudleDebug", "Report failed: ${e.message}")
            onDone(false)
        }
}

// ----- Block -----

/** Who I've blocked (uid to username), kept live while signed in. Read by every screen. */
object Blocks {
    private val blocked = MutableStateFlow<Map<String, String>>(emptyMap())
    val state: StateFlow<Map<String, String>> = blocked

    fun listen(myUid: String): ListenerRegistration =
        FirebaseFirestore.getInstance().collection("users").document(myUid).collection("blocks")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("DuudleDebug", "Blocks listener failed: ${error.message}")
                    return@addSnapshotListener
                }
                blocked.value = snapshot?.documents?.associate { it.id to (it.getString("username") ?: "someone") } ?: emptyMap()
            }

    fun clear() {
        blocked.value = emptyMap()
    }
}

/**
 * Blocks [otherUid] and, in the same batch, removes the friendship and any pending requests
 * between us. The rules then refuse anything new between us, in either direction.
 */
fun blockUser(myUid: String, otherUid: String, otherUsername: String, onDone: (Boolean) -> Unit) {
    if (myUid.isBlank() || otherUid.isBlank() || myUid == otherUid) {
        onDone(false)
        return
    }
    val db = FirebaseFirestore.getInstance()
    val friendships = db.collection("friendships")
    val requests = db.collection("friend_requests")
    Tasks.whenAllSuccess<QuerySnapshot>(
        friendships.whereEqualTo("userA", myUid).whereEqualTo("userB", otherUid).get(),
        friendships.whereEqualTo("userA", otherUid).whereEqualTo("userB", myUid).get(),
        requests.whereEqualTo("fromUid", myUid).whereEqualTo("toUid", otherUid).get(),
        requests.whereEqualTo("fromUid", otherUid).whereEqualTo("toUid", myUid).get()
    ).addOnSuccessListener { snapshots ->
        val batch = db.batch()
        batch.set(
            db.collection("users").document(myUid).collection("blocks").document(otherUid),
            mapOf("blockedAt" to FieldValue.serverTimestamp(), "username" to otherUsername.take(200))
        )
        snapshots.flatMap { it.documents }.forEach { batch.delete(it.reference) }
        batch.commit()
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { e ->
                Log.e("DuudleDebug", "Block failed: ${e.message}")
                onDone(false)
            }
    }.addOnFailureListener { e ->
        Log.e("DuudleDebug", "Block lookups failed: ${e.message}")
        onDone(false)
    }
}

/** Unblocking doesn't restore the friendship; either of you can send a new request. */
fun unblockUser(myUid: String, otherUid: String, onDone: (Boolean) -> Unit) {
    FirebaseFirestore.getInstance().collection("users").document(myUid).collection("blocks").document(otherUid)
        .delete()
        .addOnSuccessListener { onDone(true) }
        .addOnFailureListener { e ->
            Log.e("DuudleDebug", "Unblock failed: ${e.message}")
            onDone(false)
        }
}

// ----- UI: the "⋮" menu and the two dialogs, shared by Friends, Chat and the Vault -----

/** Which dialog is open. Each screen keeps one and shows [SafetyDialogs] for it. */
class SafetyActions {
    var reportSubject by mutableStateOf<ReportSubject?>(null)
    var blockTarget by mutableStateOf<Pair<String, String>?>(null)

    fun report(subject: ReportSubject) {
        reportSubject = subject
    }

    fun block(uid: String, username: String) {
        blockTarget = uid to username
    }
}

@Composable
fun rememberSafetyActions(): SafetyActions = remember { SafetyActions() }

@Composable
fun SafetyMenuButton(username: String, onReport: () -> Unit, onBlock: () -> Unit, tint: Color = DuudleColors.Ink) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More options for @$username", tint = tint)
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text("Report @$username") }, onClick = {
            open = false
            onReport()
        })
        DropdownMenuItem(text = { Text("Block @$username", color = DuudleColors.ErrorRed) }, onClick = {
            open = false
            onBlock()
        })
    }
}

/** Shows whichever dialog [actions] asks for and does the work. [onBlocked] runs after a block. */
@Composable
fun SafetyDialogs(actions: SafetyActions, currentUid: String, onBlocked: (uid: String) -> Unit = {}) {
    val context = LocalContext.current
    fun doBlock(uid: String, username: String) {
        blockUser(currentUid, uid, username) { ok ->
            if (ok) {
                Toast.makeText(context, "Blocked @$username", Toast.LENGTH_SHORT).show()
                onBlocked(uid)
            } else {
                Toast.makeText(context, "Couldn't block. Check your connection and try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    actions.reportSubject?.let { subject ->
        ReportDialog(
            subject = subject,
            onDismiss = { actions.reportSubject = null },
            onSubmit = { reason, details, alsoBlock ->
                actions.reportSubject = null
                submitReport(currentUid, subject, reason, details) { ok ->
                    Toast.makeText(
                        context,
                        if (ok) "Thanks. We'll review your report." else "Couldn't send the report. Try again.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                if (alsoBlock) doBlock(subject.reportedUid, subject.reportedUsername)
            }
        )
    }

    actions.blockTarget?.let { (uid, username) ->
        AlertDialog(
            onDismissRequest = { actions.blockTarget = null },
            title = { Text("Block @$username?") },
            text = {
                Text(
                    "They'll be removed from your friends and can't send you doodles, flash cards, " +
                        "messages or friend requests. Their doodles and chat are hidden from you. " +
                        "They aren't told. You can unblock them in Profile."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    actions.blockTarget = null
                    doBlock(uid, username)
                }) { Text("Block", color = DuudleColors.ErrorRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { actions.blockTarget = null }) { Text("Cancel", color = DuudleColors.Ink) }
            }
        )
    }
}

@Composable
private fun ReportDialog(
    subject: ReportSubject,
    onDismiss: () -> Unit,
    onSubmit: (ReportReason, String, alsoBlock: Boolean) -> Unit
) {
    var reason by rememberSaveable { mutableStateOf<ReportReason?>(null) }
    var details by rememberSaveable { mutableStateOf("") }
    var alsoBlock by rememberSaveable { mutableStateOf(false) }
    val what = when (subject.contentType) {
        "doodle" -> "this doodle from @${subject.reportedUsername}"
        "chat" -> "your chat with @${subject.reportedUsername}"
        else -> "@${subject.reportedUsername}"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report $what") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text("What's wrong? Reports are private: @${subject.reportedUsername} isn't told who reported them.", fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                ReportReason.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = reason == option, role = Role.RadioButton, onClick = { reason = option }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = reason == option,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = DuudleColors.Ink)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(option.label, fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it.take(MAX_REPORT_DETAILS) },
                    label = { Text("Anything else? (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = alsoBlock, role = Role.Checkbox, onValueChange = { alsoBlock = it }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = alsoBlock,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = DuudleColors.Ink)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Also block @${subject.reportedUsername}", fontSize = 15.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { reason?.let { onSubmit(it, details, alsoBlock) } },
                enabled = reason != null
            ) { Text("Send report", fontWeight = FontWeight.Bold, color = if (reason != null) DuudleColors.Ink else DuudleColors.Muted) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = DuudleColors.Ink) }
        }
    )
}
