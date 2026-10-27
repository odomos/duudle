package com.example.duudle

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File

/**
 * Snooze = "pause pop-ups". Tapping Snooze on an overlay pauses ALL doodle and flash-card
 * pop-ups until [quiet until][pausedUntil]. The tapped doodle, and every doodle that arrives
 * while paused, is *held*: saved as a small file in the snooze folder instead of popping up.
 * Held doodles show in the Vault's Snoozed tab until the user opens them there (or removes
 * or replies to them from an overlay).
 *
 * When the pause ends (one alarm) nothing pops up. If anything was held during that pause,
 * one ordinary notification says so and opens the Snoozed tab.
 *
 * Pause state lives in duudle_prefs: "pause_quiet_until" (0 = no pause whose end is still
 * to be handled) and "pause_started_at". Alarms are lost on reboot, force-stop and (we
 * assume) app updates, so [rescheduleAll] re-arms the end alarm, or handles a missed end.
 */
object DoodleSnooze {
    private const val TAG = "DuudleSnooze"
    internal const val EXTRA_DOODLE_ID = "doodle_id"
    internal const val ACTION_PAUSE_END = "com.example.duudle.action.PAUSE_END"
    /** MainActivity extra: open the Vault on the Snoozed tab. */
    const val EXTRA_OPEN_VAULT_SNOOZED = "open_vault_snoozed"

    private const val PREFS = "duudle_prefs"
    private const val KEY_QUIET_UNTIL = "pause_quiet_until"
    private const val KEY_STARTED_AT = "pause_started_at"

    private const val WAITING_CHANNEL_ID = "doodles_waiting"
    private const val WAITING_NOTIFICATION_ID = 0x0D00D1E
    private const val WAITING_REQUEST_CODE = 0x0D00D1E

    private val version = MutableStateFlow(0)

    /** Bumped whenever the held list or the pause changes, so screens can re-read them. */
    val changes: StateFlow<Int> = version.asStateFlow()

    private fun bump() = version.update { it + 1 }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun snoozeDir(context: Context): File = File(context.filesDir, "snoozed").apply { mkdirs() }

    private fun fileFor(context: Context, doodleId: String): File = File(snoozeDir(context), "$doodleId.json")

    // ----- Pause -----

    /** When pop-ups come back, or null if they aren't paused right now. */
    fun pausedUntil(context: Context): Long? {
        val quietUntil = prefs(context).getLong(KEY_QUIET_UNTIL, 0L)
        return if (isPaused(System.currentTimeMillis(), quietUntil)) quietUntil else null
    }

    fun isPausedNow(context: Context): Boolean = pausedUntil(context) != null

    /**
     * Overlay "Snooze N min": pause pop-ups (or extend the pause) and hold the tapped doodle.
     * Returns when pop-ups come back (epoch ms).
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
        // Pause first, so the doodle's heldAt is never before the pause started.
        val quietUntil = pause(context, delayMinutes)
        hold(context, doodleId, senderUid, senderUsername, strokesToJson(strokes), cardId)
        Log.d(TAG, "paused pop-ups for ${delayMinutes}m, held doodle=$doodleId")
        return quietUntil
    }

    private fun pause(context: Context, minutes: Int): Long {
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        val existing = prefs.getLong(KEY_QUIET_UNTIL, 0L)
        val quietUntil = extendPause(existing, now, minutes)
        val startedAt = pauseStartFor(existing, prefs.getLong(KEY_STARTED_AT, 0L), now)
        prefs.edit().putLong(KEY_QUIET_UNTIL, quietUntil).putLong(KEY_STARTED_AT, startedAt).commit()
        armPauseEnd(context, quietUntil)
        bump()
        return quietUntil
    }

    /** "Resume pop-ups": end the pause now. No notification: the user is looking at the box. */
    fun resume(context: Context) {
        prefs(context).edit().putLong(KEY_QUIET_UNTIL, 0L).putLong(KEY_STARTED_AT, 0L).commit()
        context.getSystemService(AlarmManager::class.java).cancel(pauseEndIntent(context))
        bump()
        Log.d(TAG, "pop-ups resumed")
    }

    /**
     * A doodle push that arrived while paused: keep it in the held list instead of showing
     * it. [strokesJson] is the push's compact strokes (or null); the Vault shows the doodle
     * from Firestore, so nothing is fetched here and no overlay permission is needed.
     */
    fun hold(
        context: Context,
        doodleId: String,
        senderUid: String,
        senderUsername: String,
        strokesJson: String?,
        cardId: String?
    ) {
        val payload = SnoozePayload(
            doodleId = doodleId,
            senderUid = senderUid,
            senderUsername = senderUsername,
            strokesJson = strokesJson ?: "[]",
            heldAt = System.currentTimeMillis(),
            cardId = cardId
        )
        fileFor(context, doodleId).writeText(JSONObject(payload.toMap()).toString())
        bump()
    }

    private fun armPauseEnd(context: Context, quietUntil: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val alarm = pauseEndIntent(context)
        // Exact alarms need a special permission on Android 12+ (off by default on 14+;
        // Profile's setup card links to it). Without it, setAndAllowWhileIdle still fires
        // during Doze, just less precisely.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quietUntil, alarm)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quietUntil, alarm)
        }
    }

    /**
     * The pause-end alarm fired (or [rescheduleAll] found a missed end). Nothing pops up;
     * if doodles were held during the pause, one notification says so. Handled once:
     * quiet-until is cleared before posting.
     */
    internal fun onPauseEnd(context: Context) {
        val prefs = prefs(context)
        val quietUntil = prefs.getLong(KEY_QUIET_UNTIL, 0L)
        when (pauseEndAction(System.currentTimeMillis(), quietUntil)) {
            PauseEndAction.NONE -> return // resumed early, or already handled
            PauseEndAction.REARM -> {
                // The pause was extended after this alarm was set.
                armPauseEnd(context, quietUntil)
                return
            }
            PauseEndAction.END -> Unit
        }
        val startedAt = prefs.getLong(KEY_STARTED_AT, 0L)
        val heldInPause = heldDuringPause(list(context).map { it.heldAt }, startedAt)
        val notify = shouldNotifyPauseEnd(heldInPause, endHandled = false)
        prefs.edit().putLong(KEY_QUIET_UNTIL, 0L).putLong(KEY_STARTED_AT, 0L).commit()
        bump()
        Log.d(TAG, "pause ended, held during it=$heldInPause")
        if (notify) postWaitingNotification(context, heldInPause)
    }

    private fun postWaitingNotification(context: Context, count: Int) {
        val appContext = context.applicationContext
        val manager = NotificationManagerCompat.from(appContext)
        if (!manager.areNotificationsEnabled() ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED)
        ) {
            // The doodles stay in the Snoozed tab either way.
            Log.w(TAG, "notifications are off, not posting the waiting-doodles notice")
            return
        }
        appContext.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(WAITING_CHANNEL_ID, "Paused doodles", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val openIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_VAULT_SNOOZED, true)
        }
        val contentIntent = PendingIntent.getActivity(
            appContext, WAITING_REQUEST_CODE, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = pausedDoodlesText(count)
        val notification = NotificationCompat.Builder(appContext, WAITING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Duudle")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            // Lock-screen-private, like chat notifications.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(appContext, WAITING_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Doodles waiting")
                    .build()
            )
            .build()
        try {
            manager.notify(WAITING_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "not allowed to post the waiting-doodles notice: ${e.message}")
        }
    }

    /** The user is looking at the Snoozed tab: the "doodles waiting" notice has done its job. */
    fun clearWaitingNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(WAITING_NOTIFICATION_ID)
    }

    // ----- Held list -----

    /**
     * Takes a doodle out of the held list: opened in the Vault, or removed/replied to from
     * an overlay. Also clears any per-doodle alarm left by the old snooze model.
     */
    fun cancel(context: Context, doodleId: String) {
        context.getSystemService(AlarmManager::class.java).cancel(legacyDoodleIntent(context, doodleId))
        if (fileFor(context, doodleId).delete()) Log.d(TAG, "released held doodle=$doodleId")
        bump()
    }

    /** Everything held. Unreadable files are skipped; never throws. */
    fun list(context: Context): List<SnoozedDoodle> = try {
        snoozeDir(context).listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull { file ->
            readPayload(file)?.let { SnoozedDoodle(it.doodleId, it.senderUsername, it.heldAt, it.cardId) }
        }
    } catch (e: Exception) {
        Log.e(TAG, "couldn't list held doodles: ${e.message}")
        emptyList()
    }

    /**
     * Runs on boot, app update, exact-alarm permission change and every app start.
     * Idempotent: re-arms the pause-end alarm if a pause is still on, or handles a pause
     * that ended while its alarm was lost (posting the notice once). Files from the old
     * per-doodle snooze simply become held doodles; their old alarms are cancelled.
     */
    fun rescheduleAll(context: Context) {
        val appContext = context.applicationContext
        try {
            val alarmManager = appContext.getSystemService(AlarmManager::class.java)
            snoozeDir(appContext).listFiles { f -> f.name.endsWith(".json") }.orEmpty().forEach { file ->
                val payload = readPayload(file)
                if (payload == null) {
                    // Can't be listed or shown; it would sit in the folder forever.
                    Log.w(TAG, "dropping unreadable snooze file ${file.name}")
                    file.delete()
                } else {
                    alarmManager.cancel(legacyDoodleIntent(appContext, payload.doodleId))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "couldn't tidy snooze files: ${e.message}")
        }
        val quietUntil = prefs(appContext).getLong(KEY_QUIET_UNTIL, 0L)
        when (pauseEndAction(System.currentTimeMillis(), quietUntil)) {
            PauseEndAction.NONE -> Unit
            PauseEndAction.REARM -> armPauseEnd(appContext, quietUntil)
            PauseEndAction.END -> onPauseEnd(appContext)
        }
        bump()
    }

    private fun readPayload(file: File): SnoozePayload? = try {
        val json = JSONObject(file.readText())
        val map = json.keys().asSequence().associateWith { json.opt(it) }
        val payload = snoozePayloadFromMap(map, fallbackDoodleId = file.name.removeSuffix(".json"))
        // Files from the old per-doodle snooze have no heldAt: use when the file was written.
        if (payload != null && payload.heldAt == null) payload.copy(heldAt = file.lastModified()) else payload
    } catch (e: Exception) {
        Log.e(TAG, "unreadable snooze file ${file.name}: ${e.message}")
        null
    }

    private fun pauseEndIntent(context: Context): PendingIntent {
        val intent = Intent(context, SnoozeAlarmReceiver::class.java)
            .setAction(ACTION_PAUSE_END)
            .setData(Uri.parse("duudle://pause-end"))
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Matches the per-doodle alarm the old snooze set, so it can be cancelled. */
    private fun legacyDoodleIntent(context: Context, doodleId: String): PendingIntent {
        val intent = Intent(context, SnoozeAlarmReceiver::class.java)
            .setData(Uri.parse("duudle://snooze/$doodleId"))
            .putExtra(EXTRA_DOODLE_ID, doodleId)
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

// ----- Pure helpers (no Android types), unit tested in DoodleSnoozeTest. -----

/** A held doodle as the Vault shows it. */
data class SnoozedDoodle(val doodleId: String, val senderUsername: String, val heldAt: Long?, val cardId: String?)

/**
 * A held doodle's file. Files from the old per-doodle snooze have no doodleId (it's the
 * file name) and no heldAt, and may have a triggerAt, which is now ignored.
 */
data class SnoozePayload(
    val doodleId: String,
    val senderUid: String,
    val senderUsername: String,
    val strokesJson: String,
    val heldAt: Long?,
    val cardId: String?
) {
    fun toMap(): Map<String, Any> = buildMap {
        put("doodleId", doodleId)
        put("senderUid", senderUid)
        put("senderUsername", senderUsername)
        put("strokes", strokesJson)
        heldAt?.let { put("heldAt", it) }
        cardId?.let { put("cardId", it) }
    }
}

/** Null if the file is missing a field every format has. */
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
        heldAt = (map["heldAt"] as? Number)?.toLong(),
        cardId = cardIdFromPayload(map["cardId"] as? String)
    )
}

/** [quietUntil] 0 means no pause. */
fun isPaused(now: Long, quietUntil: Long): Boolean = quietUntil > 0L && now < quietUntil

/** Snoozing again while paused keeps the later of the two end times. */
fun extendPause(existing: Long, now: Long, minutes: Int): Long = maxOf(existing, now + minutes * 60_000L)

/**
 * When the pause being set started. A new pause starts now; extending one (or one whose
 * end was missed and not yet handled) keeps the original start, so its doodles still count.
 */
fun pauseStartFor(existingQuietUntil: Long, existingStartedAt: Long, now: Long): Long =
    if (existingQuietUntil == 0L) now else existingStartedAt

/** How many held doodles came in during the pause that started at [pauseStartedAt]. */
fun heldDuringPause(heldAts: List<Long?>, pauseStartedAt: Long): Int =
    heldAts.count { it != null && it >= pauseStartedAt }

fun shouldNotifyPauseEnd(heldInPause: Int, endHandled: Boolean): Boolean = heldInPause > 0 && !endHandled

fun pausedDoodlesText(count: Int): String =
    if (count == 1) "1 doodle arrived while pop-ups were paused"
    else "$count doodles arrived while pop-ups were paused"

enum class PauseEndAction { NONE, REARM, END }

/** An end alarm within this long of quiet-until counts as on time. */
const val PAUSE_END_WINDOW_MS = 5_000L

/** NONE: no pause to end. REARM: still paused (alarm early, or re-arming after a reboot). END: handle the end. */
fun pauseEndAction(now: Long, quietUntil: Long): PauseEndAction = when {
    quietUntil == 0L -> PauseEndAction.NONE
    quietUntil - now > PAUSE_END_WINDOW_MS -> PauseEndAction.REARM
    else -> PauseEndAction.END
}

class SnoozeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DoodleSnooze.ACTION_PAUSE_END) {
            DoodleSnooze.onPauseEnd(context.applicationContext)
        } else {
            // A per-doodle alarm left by the old snooze: those doodles are held now, so it
            // no longer pops anything up.
            Log.d("DuudleSnooze", "ignoring an old per-doodle snooze alarm")
        }
    }
}

/** Alarms are wiped by a reboot (and possibly an app update); put the pause-end alarm back. */
class SnoozeRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Exported for the system broadcasts below; anything else is ignored.
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                Log.d("DuudleSnooze", "re-arming the pause after ${intent.action}")
                DoodleSnooze.rescheduleAll(context)
            }
        }
    }
}
