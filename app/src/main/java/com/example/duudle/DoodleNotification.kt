package com.example.duudle

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Fallback for when "Display over other apps" is off: without it the overlay can't be
 * shown, so the doodle would otherwise arrive silently and only sit in the Vault.
 */
object DoodleNotification {
    private const val TAG = "DuudleFCM"
    private const val CHANNEL_ID = "doodles"
    /** MainActivity extra: open the Vault. */
    const val EXTRA_OPEN_VAULT = "open_vault"

    fun show(context: Context, senderUid: String, senderUsername: String, isFlashCard: Boolean) {
        val appContext = context.applicationContext
        val manager = NotificationManagerCompat.from(appContext)
        if (!manager.areNotificationsEnabled() ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED)
        ) {
            // Nothing else can be shown; the doodle is still in the Vault.
            Log.w(TAG, "No overlay permission and notifications are off, doodle only in the Vault")
            return
        }
        appContext.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Doodles (when pop-ups are off)", NotificationManager.IMPORTANCE_HIGH)
        )
        val openIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_VAULT, true)
        }
        val notificationId = doodleNotificationId(senderUid)
        val contentIntent = PendingIntent.getActivity(
            appContext, notificationId, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = doodleNotificationText(isFlashCard)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(doodleNotificationTitle(senderUsername, isFlashCard))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            // On a secure lock screen, don't say who sent it (same as chat).
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(appContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("New doodle")
                    .build()
            )
            .build()
        try {
            manager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Not allowed to post the doodle notification: ${e.message}")
        }
    }
}

fun doodleNotificationTitle(senderUsername: String, isFlashCard: Boolean): String =
    "@$senderUsername sent you a ${if (isFlashCard) "flash card" else "doodle"}"

fun doodleNotificationText(isFlashCard: Boolean): String =
    "Tap to see it in your Vault. To have ${if (isFlashCard) "cards" else "doodles"} pop up on screen, " +
        "turn on \"Show over other apps\" in Profile."

/** One notification per sender: a newer doodle from the same friend replaces the older one. */
fun doodleNotificationId(senderUid: String): Int = "doodle:$senderUid".hashCode()
