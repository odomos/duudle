package com.example.duudle

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.duudle.ui.theme.DuudleColors
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Bump when the Terms or Community Guidelines change in a way users must agree to again:
 * everyone whose accepted version is lower sees the agreement dialog on their next launch.
 */
const val TERMS_VERSION = 1
const val MINIMUM_AGE = 13
private const val KEY_TERMS_ACCEPTED = "terms_version_accepted"

/** DRAFT, to be reviewed by the owner before release. Shown in-app; a web copy comes with Phase 5. */
val TERMS_SECTIONS: List<Pair<String, String>> = listOf(
    "Who can use Duudle" to
        "You must be $MINIMUM_AGE or older. By using Duudle you agree to these Terms and the Community Guidelines below.",
    "Community Guidelines" to
        "Doodles, flash cards and messages must not contain: bullying, harassment or threats; hate speech or " +
        "symbols; sexual or nude content; anything that sexualises or endangers children; violence, gore or " +
        "encouraging self-harm; spam, scams or impersonating someone; anything illegal. Don't send pop-ups to " +
        "people who don't want them. We have zero tolerance for content that exploits children: it's removed, " +
        "the account is banned and it may be reported to the authorities.",
    "Your content" to
        "You're responsible for what you send. Only send drawings and messages you have the right to share. " +
        "Friends you send to can see them, save them and keep them in their Vault.",
    "Report and block" to
        "Report a person, a doodle or a chat from its \"⋮\" menu. Reports are private. Blocking someone removes " +
        "them from your friends and stops them sending you anything. Manage blocked accounts in Profile.",
    "What we do about reports" to
        "We review reports and may remove content, and warn, suspend or ban accounts that break these rules, " +
        "without notice for serious cases.",
    "Pop-ups" to
        "Doodles only float over other apps if you turn on \"Display over other apps\" for Duudle, and only " +
        "from friends you've accepted. You can mute a friend, snooze pop-ups or turn the permission off anytime.",
    "Your account" to
        "Your account lives on this phone. Uninstalling Duudle or clearing its data starts a new account.",
    "The service" to
        "Duudle is provided as is, without guarantees that it's always available or that every doodle arrives. " +
        "We may change these Terms; if the change matters, the app will ask you to agree again.",
    "Contact" to
        "Questions or safety concerns: use the contact email on Duudle's Google Play listing."
)

fun hasAcceptedCurrentTerms(context: Context): Boolean =
    context.getSharedPreferences("duudle_prefs", Context.MODE_PRIVATE).getInt(KEY_TERMS_ACCEPTED, 0) >= TERMS_VERSION

/**
 * Remembers the acceptance on this phone (that's what the app checks) and records it in
 * the owner-only users/{uid}/private/terms document as evidence.
 */
fun recordTermsAccepted(context: Context, uid: String) {
    context.getSharedPreferences("duudle_prefs", Context.MODE_PRIVATE).edit().putInt(KEY_TERMS_ACCEPTED, TERMS_VERSION).apply()
    if (uid.isBlank()) return
    FirebaseFirestore.getInstance().collection("users").document(uid).collection("private").document("terms")
        .set(mapOf("version" to TERMS_VERSION, "acceptedAt" to FieldValue.serverTimestamp()))
        .addOnFailureListener { e -> Log.e("DuudleDebug", "Couldn't record the Terms acceptance: ${e.message}") }
}

/** The full Terms and Community Guidelines, read-only. */
@Composable
fun TermsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Terms & Community Guidelines") },
        text = { TermsText(Modifier.heightIn(max = 480.dp)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = DuudleColors.Ink, fontWeight = FontWeight.Bold) } }
    )
}

/**
 * For accounts made before the Terms existed (or before a new version): shown over the app
 * until accepted. There's deliberately no way to dismiss it without agreeing.
 */
@Composable
fun TermsAgreementDialog(onAgree: () -> Unit) {
    var reading by remember { mutableStateOf(false) }
    if (reading) {
        TermsDialog(onDismiss = { reading = false })
        return
    }
    AlertDialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("Before you keep doodling") },
        text = {
            Text(
                "Duudle now has Terms and Community Guidelines, so everyone can see what's allowed, and you can " +
                    "report or block anyone who breaks them. To keep using Duudle, please confirm you're " +
                    "$MINIMUM_AGE or older and agree to them."
            )
        },
        confirmButton = {
            TextButton(onClick = onAgree) { Text("I agree", color = DuudleColors.Ink, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = { reading = true }) { Text("Read them", color = DuudleColors.Ink) }
        }
    )
}

@Composable
private fun TermsText(modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        TERMS_SECTIONS.forEachIndexed { i, (heading, body) ->
            if (i > 0) Spacer(Modifier.height(12.dp))
            Text(heading, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(2.dp))
            Text(body, fontSize = 14.sp)
        }
    }
}
