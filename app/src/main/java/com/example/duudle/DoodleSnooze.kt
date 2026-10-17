package com.example.duudle

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Snooze for the doodle overlay. The doodle is saved to a local file and an
 * alarm re-shows it later through the normal overlay path, so it works with
 * the app killed and needs no network when it fires.
 */
object DoodleSnooze {
    private const val TAG = "DuudleSnooze"
    internal const val EXTRA_DOODLE_ID = "doodle_id"

    private fun fileFor(context: Context, doodleId: String): File =
        File(File(context.filesDir, "snoozed").apply { mkdirs() }, "$doodleId.json")

    /** Snoozing the same doodle again replaces its earlier alarm and payload. */
    fun snooze(
        context: Context,
        doodleId: String,
        senderUid: String,
        senderUsername: String,
        strokes: List<DoodleStroke>,
        delayMinutes: Int
    ) {
        val payload = JSONObject()
            .put("senderUid", senderUid)
            .put("senderUsername", senderUsername)
            .put("strokes", strokesToJson(strokes))
        fileFor(context, doodleId).writeText(payload.toString())

        val triggerAt = System.currentTimeMillis() + delayMinutes * 60_000L
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val alarm = pendingIntent(context, doodleId)
        // Exact alarms need a special permission on Android 12+. Without it,
        // setAndAllowWhileIdle still fires during Doze, just less precisely.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, alarm)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, alarm)
        }
        Log.d(TAG, "snoozed doodle=$doodleId for ${delayMinutes}m")
    }

    /** Drops a pending snooze, e.g. when the doodle is removed or replied to. */
    fun cancel(context: Context, doodleId: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, doodleId))
        if (fileFor(context, doodleId).delete()) Log.d(TAG, "cancelled snooze doodle=$doodleId")
    }

    /** Runs when the alarm fires: re-show the overlay once, then forget the doodle. */
    internal fun fire(context: Context, doodleId: String) {
        val file = fileFor(context, doodleId)
        if (!file.exists()) {
            Log.d(TAG, "snooze fired but doodle=$doodleId was already removed")
            return
        }
        try {
            if (!Settings.canDrawOverlays(context)) {
                Log.w(TAG, "snooze fired but overlay permission is gone, dropping doodle=$doodleId")
                return
            }
            val payload = JSONObject(file.readText())
            showRealDoodleOverlay(
                context = context,
                doodleId = doodleId,
                senderUid = payload.getString("senderUid"),
                senderUsername = payload.getString("senderUsername"),
                strokes = strokesFromJson(payload.getString("strokes"))
            )
            Log.d(TAG, "re-showed snoozed doodle=$doodleId")
        } catch (e: Exception) {
            Log.e(TAG, "failed to re-show snoozed doodle=$doodleId: ${e.message}")
        } finally {
            file.delete()
        }
    }

    private fun pendingIntent(context: Context, doodleId: String): PendingIntent {
        val intent = Intent(context, SnoozeAlarmReceiver::class.java)
            .setData(Uri.parse("duudle://snooze/$doodleId")) // one distinct alarm per doodle
            .putExtra(EXTRA_DOODLE_ID, doodleId)
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class SnoozeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // onReceive runs on the main thread, which overlay windows require.
        val doodleId = intent.getStringExtra(DoodleSnooze.EXTRA_DOODLE_ID) ?: return
        DoodleSnooze.fire(context.applicationContext, doodleId)
    }
}
