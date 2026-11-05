package com.example.duudle

import android.content.Context

// Firestore triggers are delivered at least once, so the same doodle can be pushed twice.
// The phone remembers the last doodle ids it handled and drops a repeat, so a doodle never
// pops up (or notifies) twice. Snoozed doodles re-show through DoodleSnooze, not this path.

private const val PREFS = "duudle_prefs"
private const val KEY_RECENT = "recent_push_doodle_ids"
private const val MAX_REMEMBERED = 100

/**
 * The remembered ids after handling [id] (newest last, at most [cap]), or null if [id] was
 * already there, i.e. this push is a repeat.
 */
fun rememberPushId(recent: List<String>, id: String, cap: Int = MAX_REMEMBERED): List<String>? =
    if (id in recent) null else (recent + id).takeLast(cap)

object PushDedupe {
    /** True the first time this doodle id arrives on this phone; false for a repeat. */
    @Synchronized
    fun isFirstDelivery(context: Context, doodleId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val recent = prefs.getString(KEY_RECENT, "").orEmpty().split(',').filter { it.isNotEmpty() }
        val updated = rememberPushId(recent, doodleId) ?: return false
        prefs.edit().putString(KEY_RECENT, updated.joinToString(",")).apply()
        return true
    }
}
