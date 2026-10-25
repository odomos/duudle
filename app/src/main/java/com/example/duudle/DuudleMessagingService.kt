package com.example.duudle

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class DuudleMessagingService : FirebaseMessagingService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("DuudleFCM", "New FCM token generated: $token")
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            saveFcmToken(uid, token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        // Type and keys only: the payload can carry private chat text.
        Log.d("DuudleFCM", "Message received: type=${remoteMessage.data["type"]} keys=${remoteMessage.data.keys}")

        val type = remoteMessage.data["type"]
        // Chat pushes are ordinary notifications; everything below is the doodle overlay path.
        if (type == "chat_message") {
            showChatNotification(remoteMessage.data)
            return
        }
        val doodleId = remoteMessage.data["doodleId"]
        val senderUid = remoteMessage.data["senderId"] ?: ""
        val senderUsername = remoteMessage.data["senderUsername"] ?: "A friend"
        val strokesJson = remoteMessage.data["strokes"]
        val cardId = cardIdFromPayload(remoteMessage.data["cardId"])

        if (type != "new_doodle" || doodleId == null) {
            Log.d("DuudleFCM", "Ignoring message: wrong type or missing doodleId")
            return
        }

        if (!Settings.canDrawOverlays(applicationContext)) {
            Log.w("DuudleFCM", "No overlay permission — cannot show doodle overlay")
            return
        }

        if (cardId != null) {
            // A flash card: no strokes to parse, just show the card in the same overlay.
            Log.d("DuudleFCM", "path=card doodle=$doodleId card=$cardId")
            mainHandler.post {
                try {
                    showRealDoodleOverlay(
                        context = applicationContext,
                        doodleId = doodleId,
                        senderUid = senderUid,
                        senderUsername = senderUsername,
                        strokes = emptyList(),
                        cardId = cardId
                    )
                } catch (e: Exception) {
                    Log.e("DuudleFCM", "Failed to show overlay for flash card: ${e.message}")
                }
            }
            return
        }

        // Embedded "[]" with no cardId is a flash card pushed by a Cloud Function that predates
        // cardId; take the Firestore fallback below, which reads cardId from the doc.
        if (strokesJson != null && !isEmptyStrokesPayload(strokesJson)) {
            Log.d("DuudleFCM", "path=embedded doodle=$doodleId bytes=${strokesJson.length}")
            // Parsing is safe on any thread, but showing the overlay is NOT —
            // it creates Android Views, which requires a thread with a running
            // Looper. Whatever thread FCM chose to call us on, hand off to the
            // app's real main thread for this part.
            mainHandler.post {
                try {
                    val strokes = strokesFromJson(strokesJson)
                    showRealDoodleOverlay(
                        context = applicationContext,
                        doodleId = doodleId,
                        senderUid = senderUid,
                        senderUsername = senderUsername,
                        strokes = strokes
                    )
                } catch (e: Exception) {
                    Log.e("DuudleFCM", "Failed to show overlay from embedded strokes: ${e.message}")
                }
            }
        } else {
            // Fallback for doodles too large to fit in the push. onMessageReceived
            // runs on a background thread, so block on the fetch here instead of
            // using async callbacks: once this method returns, Android may kill a
            // cold-started process before a callback would ever fire.
            val incoming = try {
                val doc = Tasks.await(
                    FirebaseFirestore.getInstance().collection("doodles").document(doodleId).get(),
                    FALLBACK_FETCH_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                )
                parseDoodleDoc(doc)
            } catch (e: Exception) {
                Log.e("DuudleFCM", "path=fallback failed doodle=$doodleId: ${e.message}")
                return
            }
            if (incoming == null) {
                Log.w("DuudleFCM", "path=fallback failed doodle=$doodleId: missing or unparseable doc")
                return
            }
            Log.d("DuudleFCM", "path=fallback ok doodle=$doodleId strokes=${incoming.strokes.size} card=${incoming.cardId}")
            mainHandler.post {
                try {
                    showRealDoodleOverlay(
                        context = applicationContext,
                        doodleId = incoming.docId,
                        senderUid = incoming.senderId,
                        senderUsername = senderUsername,
                        strokes = incoming.strokes,
                        cardId = incoming.cardId
                    )
                } catch (e: Exception) {
                    Log.e("DuudleFCM", "Failed to show overlay from fetched doodle: ${e.message}")
                }
            }
        }
    }

    /** Posts (or replaces) the notification for a chat, unless that chat is open right now. */
    private fun showChatNotification(data: Map<String, String>) {
        val chatId = data["chatId"]
        val senderUid = data["senderId"]
        if (chatId.isNullOrBlank() || senderUid.isNullOrBlank()) {
            Log.w("DuudleFCM", "Ignoring chat message: missing chatId or senderId")
            return
        }
        if (ChatPresence.openChatId == chatId) {
            Log.d("DuudleFCM", "Chat $chatId is open, skipping notification")
            return
        }
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled() ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED)
        ) {
            Log.w("DuudleFCM", "Notifications are off, can't show chat message for $chatId")
            return
        }

        val senderUsername = data["senderUsername"] ?: "A friend"
        val text = data["text"].orEmpty()
        // createNotificationChannel is a no-op once the channel exists.
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHAT_CHANNEL_ID, "Chat messages", NotificationManager.IMPORTANCE_HIGH)
        )
        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            // Same routing as the overlay's Draw button: onNewIntent if the app is running.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_chat_uid", senderUid)
            putExtra("open_chat_username", senderUsername)
        }
        val notificationId = chatNotificationId(chatId)
        // A request code per chat, so each chat's PendingIntent keeps its own extras.
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(applicationContext, CHAT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("@$senderUsername")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            // On a secure lock screen, show "New message" instead of who sent what.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(applicationContext, CHAT_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("New message")
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .build()
            )
            .build()
        try {
            manager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            Log.e("DuudleFCM", "Not allowed to post chat notification: ${e.message}")
        }
    }

    private companion object {
        const val FALLBACK_FETCH_TIMEOUT_SECONDS = 10L
    }
}

private const val CHAT_CHANNEL_ID = "chats"

/**
 * The compact stroke format the Cloud Function embeds in pushes:
 * [{"c": colorArgb, "w": width, "p": [[x, y], ...]}] with normalized 0-1 points.
 */
fun strokesFromJson(json: String): List<DoodleStroke> {
    val strokes = mutableListOf<DoodleStroke>()
    val strokesArray = JSONArray(json)
    for (i in 0 until strokesArray.length()) {
        val strokeObj = strokesArray.getJSONObject(i)
        val colorInt = strokeObj.getInt("c")
        val width = strokeObj.getDouble("w").toFloat()
        val pointsArray = strokeObj.getJSONArray("p")
        val points = mutableListOf<Offset>()
        for (j in 0 until pointsArray.length()) {
            val pointPair = pointsArray.getJSONArray(j)
            val x = pointPair.getDouble(0).toFloat()
            val y = pointPair.getDouble(1).toFloat()
            points.add(Offset(x, y))
        }
        strokes.add(DoodleStroke(points = points, color = Color(colorInt), width = width))
    }
    return strokes
}

fun strokesToJson(strokes: List<DoodleStroke>): String {
    val strokesArray = JSONArray()
    for (stroke in strokes) {
        val pointsArray = JSONArray()
        for (p in stroke.points) pointsArray.put(JSONArray().put(p.x.toDouble()).put(p.y.toDouble()))
        strokesArray.put(
            JSONObject().put("c", stroke.color.toArgb()).put("w", stroke.width.toDouble()).put("p", pointsArray)
        )
    }
    return strokesArray.toString()
}