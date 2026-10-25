package com.example.duudle

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File

/**
 * Snooze for the doodle overlay. The doodle is saved to a local file and an
 * alarm re-shows it later through the normal overlay path, so it works with
 * the app killed and needs no network when it fires.
 *
 * Alarms don't survive a reboot or an app update, but the files do: [rescheduleAll]
 * (run by [SnoozeRescheduleReceiver] and on app start) re-arms each file's alarm, or
 * shows it straight away if its time has passed.
 */
object DoodleSnooze {
    private const val TAG = "DuudleSnooze"
    internal const val EXTRA_DOODLE_ID = "doodle_id"

    private val version = MutableStateFlow(0)

    /** Bumped whenever a snooze is added, cancelled or fired, so screens can re-read [list]. */
    val changes: StateFlow<Int> = version.asStateFlow()

    private fun bump() = version.update { it + 1 }

    private fun snoozeDir(context: Context): File = File(context.filesDir, "snoozed").apply { mkdirs() }

    private fun fileFor(context: Context, doodleId: String): File = File(snoozeDir(context), "$doodleId.json")

    /**
     * Snoozing the same doodle again replaces its earlier alarm and payload.
     * Returns when it'll pop up again (epoch ms).
     */
    fun snooze(
        context: Context,
        doodleId: String,
        senderUid: String,
        senderUsername: String,
        strokes: List<DoodleStroke>,
        delayMinutes: Int,
        cardId: String? = null
    ): Long {
        val triggerAt = System.currentTimeMillis() + delayMinutes * 60_000L
        val payload = SnoozePayload(
            doodleId = doodleId,
            senderUid = senderUid,
            senderUsername = senderUsername,
            strokesJson = strokesToJson(strokes),
            triggerAt = triggerAt,
            cardId = cardId
        )
        fileFor(context, doodleId).writeText(JSONObject(payload.toMap()).toString())
        arm(context, doodleId, triggerAt)
        bump()
        Log.d(TAG, "snoozed doodle=$doodleId for ${delayMinutes}m")
        return triggerAt
    }

    private fun arm(context: Context, doodleId: String, triggerAt: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val alarm = pendingIntent(context, doodleId)
        // Exact alarms need a special permission on Android 12+ (off by default on 14+;
        // Profile's setup card links to it). Without it, setAndAllowWhileIdle still fires
        // during Doze, just less precisely.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, alarm)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, alarm)
        }
    }

    /** Drops a pending snooze, e.g. when the doodle is removed or replied to. */
    fun cancel(context: Context, doodleId: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, doodleId))
        if (fileFor(context, doodleId).delete()) Log.d(TAG, "cancelled snooze doodle=$doodleId")
        bump()
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
            val payload = readPayload(file) ?: throw IllegalStateException("unreadable snooze file")
            showRealDoodleOverlay(
                context = context,
                doodleId = doodleId,
                senderUid = payload.senderUid,
                senderUsername = payload.senderUsername,
                strokes = strokesFromJson(payload.strokesJson),
                cardId = payload.cardId
            )
            Log.d(TAG, "re-showed snoozed doodle=$doodleId")
        } catch (e: Exception) {
            Log.e(TAG, "failed to re-show snoozed doodle=$doodleId: ${e.message}")
        } finally {
            file.delete()
            bump()
        }
    }

    /**
     * Re-arms every saved snooze after alarms were wiped (reboot, app update, force-stop),
     * or shows it now if it's due. Safe to run any number of times: an alarm for the same
     * doodle replaces the previous one rather than adding a second.
     */
    fun rescheduleAll(context: Context) {
        val appContext = context.applicationContext
        val files = try {
            snoozeDir(appContext).listFiles { f -> f.name.endsWith(".json") }.orEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "couldn't list snoozes: ${e.message}")
            return
        }
        val now = System.currentTimeMillis()
        val mainHandler = Handler(Looper.getMainLooper())
        for (file in files) {
            val payload = readPayload(file)
            if (payload == null) {
                // It could never be shown, and it would sit in the folder forever.
                Log.w(TAG, "dropping unreadable snooze file ${file.name}")
                file.delete()
                continue
            }
            val doodleId = payload.doodleId
            when (snoozeAction(payload.triggerAt, now)) {
                SnoozeAction.REARM -> {
                    arm(appContext, doodleId, payload.triggerAt!!)
                    Log.d(TAG, "re-armed doodle=$doodleId")
                }
                SnoozeAction.FIRE_NOW -> {
                    appContext.getSystemService(AlarmManager::class.java).cancel(pendingIntent(appContext, doodleId))
                    Log.d(TAG, "snooze for doodle=$doodleId is due, showing now")
                    // Overlay windows must be created on the main thread.
                    mainHandler.post { fire(appContext, doodleId) }
                }
            }
        }
    }

    /** Everything currently snoozed. Unreadable files are skipped; never throws. */
    fun list(context: Context): List<SnoozedDoodle> = try {
        snoozeDir(context).listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull { file ->
            readPayload(file)?.let { SnoozedDoodle(it.doodleId, it.senderUsername, it.triggerAt, it.cardId) }
        }
    } catch (e: Exception) {
        Log.e(TAG, "couldn't list snoozes: ${e.message}")
        emptyList()
    }

    private fun readPayload(file: File): SnoozePayload? = try {
        val json = JSONObject(file.readText())
        val map = json.keys().asSequence().associateWith { json.opt(it) }
        snoozePayloadFromMap(map, fallbackDoodleId = file.name.removeSuffix(".json"))
    } catch (e: Exception) {
        Log.e(TAG, "unreadable snooze file ${file.name}: ${e.message}")
        null
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

// ----- Pure helpers (no Android types) so the file format and timing rules are unit tested. -----

/** A snoozed doodle as shown in the Vault. [triggerAt] is null for files from older builds. */
data class SnoozedDoodle(val doodleId: String, val senderUsername: String, val triggerAt: Long?, val cardId: String?)

/**
 * The snooze file's contents. Files written before this build have no doodleId (it's the
 * file name), no triggerAt and no cardId.
 */
data class SnoozePayload(
    val doodleId: String,
    val senderUid: String,
    val senderUsername: String,
    val strokesJson: String,
    val triggerAt: Long?,
    val cardId: String?
) {
    fun toMap(): Map<String, Any> = buildMap {
        put("doodleId", doodleId)
        put("senderUid", senderUid)
        put("senderUsername", senderUsername)
        put("strokes", strokesJson)
        triggerAt?.let { put("triggerAt", it) }
        cardId?.let { put("cardId", it) }
    }
}

/** Null if a field the overlay needs is missing. */
fun snoozePayloadFromMap(map: Map<String, Any?>, fallbackDoodleId: String): SnoozePayload? {
    val senderUid = map["senderUid"] as? String ?: return null
    val senderUsername = map["senderUsername"] as? String ?: return null
    val strokes = map["strokes"] as? String ?: return null
    val doodleId = (map["doodleId"] as? String)?.takeIf { it.isNotBlank() } ?: fallbackDoodleId
    if (doodleId.isBlank()) return null
    return SnoozePayload(
        doodleId = doodleId,
        senderUid = senderUid,
        senderUsername = senderUsername,
        strokesJson = strokes,
        triggerAt = (map["triggerAt"] as? Number)?.toLong(),
        cardId = cardIdFromPayload(map["cardId"] as? String)
    )
}

enum class SnoozeAction { FIRE_NOW, REARM }

/** Snoozes due within this long are shown at once rather than re-armed. */
const val SNOOZE_FIRE_NOW_WINDOW_MS = 5_000L

/** What [DoodleSnooze.rescheduleAll] does with a file: no triggerAt means an older build's file. */
fun snoozeAction(triggerAt: Long?, now: Long): SnoozeAction =
    if (triggerAt == null || triggerAt - now <= SNOOZE_FIRE_NOW_WINDOW_MS) SnoozeAction.FIRE_NOW else SnoozeAction.REARM

class SnoozeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // onReceive runs on the main thread, which overlay windows require.
        val doodleId = intent.getStringExtra(DoodleSnooze.EXTRA_DOODLE_ID) ?: return
        DoodleSnooze.fire(context.applicationContext, doodleId)
    }
}

/** Alarms are wiped by a reboot or app update; put the snoozes back. */
class SnoozeRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Exported for the system broadcasts below; anything else is ignored.
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                Log.d("DuudleSnooze", "re-arming snoozes after ${intent.action}")
                DoodleSnooze.rescheduleAll(context)
            }
        }
    }
}
