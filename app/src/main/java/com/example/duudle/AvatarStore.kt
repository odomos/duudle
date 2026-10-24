package com.example.duudle

import android.content.Context

/**
 * Keeps the profile character in the same SharedPreferences file MainActivity uses for
 * "username", one entry per uid.
 *
 * Local only on purpose: the account is anonymous and lives on this phone
 * (allowBackup=false), so the character goes when the account does. Syncing it to
 * Firestore would be a deliberate data-format change (new field + rules), not a tweak.
 */
object AvatarStore {
    private const val PREFS = "duudle_prefs"
    private const val KEY_PREFIX = "avatar_v1_"

    // load and save share one lock (this object), so a save's read-previous -> commit ->
    // rollback runs as one step: two saves can't interleave (one rolling back over the
    // other's success, e.g. around a rotation), and load never sees a failed write before
    // it's been rolled back. A load may briefly wait for a save's disk write.
    @Synchronized
    fun load(context: Context, uid: String): AvatarConfig {
        if (uid.isBlank()) return AvatarConfig.DEFAULT
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PREFIX + uid, null)
        return decodeAvatarConfig(raw)
    }

    /**
     * Writes synchronously (commit) and returns whether it reached disk, so call it off the
     * main thread. Without a uid it writes nothing and returns false, so a signed-out
     * character never lands under a shared key.
     */
    @Synchronized
    fun save(context: Context, uid: String, config: AvatarConfig): Boolean {
        if (uid.isBlank()) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = KEY_PREFIX + uid
        val previous = prefs.getString(key, null)
        if (prefs.edit().putString(key, config.encode()).commit()) return true
        // commit() updates the in-memory map before writing to disk and doesn't undo that
        // when the write fails, so load() would hand back the unsaved look until the app
        // restarts. Put the previous value back (this edit fixes the in-memory map even if
        // its own disk write fails too).
        val rollback = prefs.edit()
        if (previous == null) rollback.remove(key) else rollback.putString(key, previous)
        rollback.commit()
        return false
    }
}
