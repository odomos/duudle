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

    fun load(context: Context, uid: String): AvatarConfig {
        if (uid.isBlank()) return AvatarConfig.DEFAULT
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PREFIX + uid, null)
        return decodeAvatarConfig(raw)
    }

    /** Does nothing without a uid, so a signed-out character never lands under a shared key. */
    fun save(context: Context, uid: String, config: AvatarConfig) {
        if (uid.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PREFIX + uid, config.encode())
            .apply()
    }
}
