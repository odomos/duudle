package com.example.duudle

import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch

// Phase 6: per-account rate limits, enforced by firestore.rules. Every doodle or card, chat
// message, friend request and report goes out in one batch with a step of the sender's
// counter users/{uid}/limits/{kind}: {count, windowStart, lastId}. lastId names the doc the
// step pays for, so one step covers exactly one doc.

private const val HOUR_MILLIS = 60 * 60 * 1000L
private const val DAY_MILLIS = 24 * HOUR_MILLIS

/** Keep in step with limitMax and limitWindow in firestore.rules. */
enum class RateLimit(val id: String, val max: Int, val windowMillis: Long, val noun: String) {
    DOODLES("doodles", 60, HOUR_MILLIS, "doodles"),
    MESSAGES("messages", 300, HOUR_MILLIS, "messages"),
    FRIEND_REQUESTS("friend_requests", 30, DAY_MILLIS, "friend requests"),
    REPORTS("reports", 20, DAY_MILLIS, "reports")
}

/** A counter as this phone last saw it. */
data class LimitCounter(val count: Long, val windowStartMillis: Long)

/** True if the next send starts a new window: there's no counter yet, or its window has passed. */
fun startsNewWindow(limit: RateLimit, counter: LimitCounter?, nowMillis: Long): Boolean =
    counter == null || nowMillis > counter.windowStartMillis + limit.windowMillis

/** How long until another send is allowed; 0 if one is allowed now. */
fun waitBeforeNextSend(limit: RateLimit, counter: LimitCounter?, nowMillis: Long): Long =
    if (counter == null || startsNewWindow(limit, counter, nowMillis) || counter.count < limit.max) 0
    else (counter.windowStartMillis + limit.windowMillis - nowMillis).coerceAtLeast(1)

fun rateLimitMessage(limit: RateLimit, waitMillis: Long): String {
    val minutes = ((waitMillis + 59_999) / 60_000).coerceAtLeast(1)
    val hours = (minutes + 59) / 60
    val wait = when {
        minutes == 1L -> "a minute"
        minutes < 60 -> "$minutes minutes"
        hours == 1L -> "an hour"
        else -> "$hours hours"
    }
    return "You've sent a lot of ${limit.noun} lately. Try again in $wait."
}

/** The limit is reached; the message is meant for the user. */
class RateLimitedException(message: String) : Exception(message)

/** What to tell the user when a send failed: the limit message, or [fallback] for anything else. */
fun rateLimitedOr(e: Exception?, fallback: String): String = (e as? RateLimitedException)?.message ?: fallback

private fun counterStep(newWindow: Boolean, docId: String): Map<String, Any> =
    if (newWindow) mapOf("count" to 1, "windowStart" to FieldValue.serverTimestamp(), "lastId" to docId)
    else mapOf("count" to FieldValue.increment(1), "lastId" to docId)

private fun DocumentSnapshot.toLimitCounter(): LimitCounter? {
    if (!exists()) return null
    val count = getLong("count") ?: return null
    val start = getTimestamp("windowStart", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return null
    return LimitCounter(count, start.toDate().time)
}

/**
 * Writes [data] to [doc] in one batch with a counter step (plus whatever [also] adds).
 * The counter comes from the phone's cache when it can (it's there after the first send),
 * to choose between starting a new window and adding one. If the rules refuse, the counter
 * is re-read from the server: either the limit is reached ([RateLimitedException]) or the
 * cached copy was out of date, so it tries once more. [onDone] gets null on success.
 */
fun sendRateLimited(
    limit: RateLimit,
    uid: String,
    doc: DocumentReference,
    data: Map<String, Any>,
    also: (WriteBatch) -> Unit = {},
    onDone: (Exception?) -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val counterRef = db.collection("users").document(uid).collection("limits").document(limit.id)

    fun attempt(counter: LimitCounter?, isRetry: Boolean) {
        val now = System.currentTimeMillis()
        val wait = waitBeforeNextSend(limit, counter, now)
        if (wait > 0) {
            Log.w("DuudleDebug", "Rate limit reached for ${limit.id}")
            onDone(RateLimitedException(rateLimitMessage(limit, wait)))
            return
        }
        val batch = db.batch()
        batch.set(doc, data)
        also(batch)
        batch.set(counterRef, counterStep(startsNewWindow(limit, counter, now), doc.id), SetOptions.merge())
        batch.commit()
            .addOnSuccessListener { onDone(null) }
            .addOnFailureListener { e ->
                val denied = (e as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                if (!denied || isRetry) {
                    onDone(e)
                    return@addOnFailureListener
                }
                counterRef.get(Source.SERVER)
                    .addOnSuccessListener { attempt(it.toLimitCounter(), isRetry = true) }
                    .addOnFailureListener { onDone(e) }
            }
    }

    counterRef.get(Source.CACHE)
        .addOnSuccessListener { attempt(it.toLimitCounter(), isRetry = false) }
        .addOnFailureListener {
            // Not cached yet (first send on this phone): ask the server; offline, assume a new window.
            counterRef.get().addOnCompleteListener { task ->
                attempt(if (task.isSuccessful) task.result.toLimitCounter() else null, isRetry = false)
            }
        }
}
