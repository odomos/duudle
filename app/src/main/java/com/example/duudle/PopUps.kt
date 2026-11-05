package com.example.duudle

import android.content.Context

// The user's own on/off for pop-ups (Profile > "Make sure doodles pop up"). Off means a
// friend's doodle arrives as the normal notification (DoodleNotification) instead of
// floating over other apps, even when "Show over other apps" is granted. On by default.

private const val PREFS = "duudle_prefs"
private const val KEY_POPUPS_ON = "popups_enabled"

object PopUps {
    fun isOn(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_POPUPS_ON, true)

    fun setOn(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_POPUPS_ON, on).apply()
    }
}

/** A doodle push floats over other apps only if the user wants pop-ups and Android allows them. */
fun showsAsPopUp(popUpsOn: Boolean, canDrawOverlays: Boolean): Boolean = popUpsOn && canDrawOverlays
