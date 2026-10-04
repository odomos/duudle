package com.example.duudle

import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONArray

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
        Log.d("DuudleFCM", "Message received with data: ${remoteMessage.data}")

        val type = remoteMessage.data["type"]
        val doodleId = remoteMessage.data["doodleId"]
        val senderUid = remoteMessage.data["senderId"] ?: ""
        val senderUsername = remoteMessage.data["senderUsername"] ?: "A friend"
        val strokesJson = remoteMessage.data["strokes"]

        if (type != "new_doodle" || doodleId == null) {
            Log.d("DuudleFCM", "Ignoring message: wrong type or missing doodleId")
            return
        }

        if (!Settings.canDrawOverlays(applicationContext)) {
            Log.w("DuudleFCM", "No overlay permission — cannot show doodle overlay")
            return
        }

        if (strokesJson != null) {
            // Parsing is safe on any thread, but showing the overlay is NOT —
            // it creates Android Views, which requires a thread with a running
            // Looper. Whatever thread FCM chose to call us on, hand off to the
            // app's real main thread for this part.
            mainHandler.post {
                try {
                    val strokes = parseStrokesFromJson(strokesJson)
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
            // Fallback for unusually large doodles that couldn't fit in the push.
            FirebaseFirestore.getInstance().collection("doodles").document(doodleId).get()
                .addOnSuccessListener { doc ->
                    val incoming = parseDoodleDoc(doc)
                    if (incoming == null) {
                        Log.w("DuudleFCM", "Could not parse doodle $doodleId")
                        return@addOnSuccessListener
                    }
                    mainHandler.post {
                        showRealDoodleOverlay(
                            context = applicationContext,
                            doodleId = incoming.docId,
                            senderUid = incoming.senderId,
                            senderUsername = senderUsername,
                            strokes = incoming.strokes
                        )
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("DuudleFCM", "Failed to fetch doodle $doodleId: ${e.message}")
                }
        }
    }

    private fun parseStrokesFromJson(json: String): List<DoodleStroke> {
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
}