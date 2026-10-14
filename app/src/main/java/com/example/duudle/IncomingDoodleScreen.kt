package com.example.duudle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class IncomingDoodle(
    val docId: String,
    val senderId: String,
    val senderUsername: String,
    val receiverId: String,
    val receiverUsername: String,
    val strokes: List<DoodleStroke>, // points here are normalized 0..1 fractions, not raw pixels
    val createdAt: Long,
    val status: String
)

@Suppress("UNCHECKED_CAST")
fun parseDoodleDoc(doc: DocumentSnapshot): IncomingDoodle? {
    val senderId = doc.getString("senderId") ?: return null
    val senderUsername = doc.getString("senderUsername") ?: "Unknown"
    val receiverId = doc.getString("receiverId") ?: ""
    val receiverUsername = doc.getString("receiverUsername") ?: "Unknown"
    val createdAt = doc.getLong("createdAt") ?: 0L
    val status = doc.getString("status") ?: "unknown"
    val rawStrokes = doc.get("strokes") as? List<Map<String, Any>> ?: return null

    val strokes = rawStrokes.mapNotNull { strokeMap ->
        val rawPoints = strokeMap["points"] as? List<Map<String, Any>> ?: return@mapNotNull null
        val points = rawPoints.mapNotNull { p ->
            val x = (p["x"] as? Number)?.toFloat() ?: return@mapNotNull null
            val y = (p["y"] as? Number)?.toFloat() ?: return@mapNotNull null
            Offset(x, y)
        }
        val colorInt = (strokeMap["color"] as? Number)?.toInt() ?: return@mapNotNull null
        val width = (strokeMap["width"] as? Number)?.toFloat() ?: 8f
        DoodleStroke(points = points, color = Color(colorInt), width = width)
    }

    return IncomingDoodle(doc.id, senderId, senderUsername, receiverId, receiverUsername, strokes, createdAt, status)
}

fun listenForIncomingDoodles(myUid: String, onUpdate: (List<IncomingDoodle>) -> Unit): ListenerRegistration {
    return FirebaseFirestore.getInstance().collection("doodles")
        .whereEqualTo("receiverId", myUid)
        .whereEqualTo("status", "unseen")
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                android.util.Log.e("DuudleDebug", "Incoming doodles listener failed: ${error.message}")
                return@addSnapshotListener
            }
            val doodles = snapshot?.documents?.mapNotNull { parseDoodleDoc(it) } ?: emptyList()
            onUpdate(doodles)
        }
}

fun markDoodleStatus(docId: String, status: String) {
    FirebaseFirestore.getInstance().collection("doodles").document(docId)
        .update("status", status)
}

/**
 * Powers the Vault: every doodle you've ever sent or received, newest first.
 * "Removing" a doodle only ever flipped its status field — the document was
 * never deleted — so this is free history, no separate save step needed.
 */
fun listenForVaultDoodles(myUid: String, onUpdate: (List<IncomingDoodle>) -> Unit): List<ListenerRegistration> {
    val db = FirebaseFirestore.getInstance()
    var sent = listOf<IncomingDoodle>()
    var received = listOf<IncomingDoodle>()

    fun publish() {
        val merged = (sent + received).distinctBy { it.docId }.sortedByDescending { it.createdAt }
        onUpdate(merged)
    }

    val sentListener = db.collection("doodles").whereEqualTo("senderId", myUid)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                android.util.Log.e("DuudleDebug", "Vault sent listener failed: ${error.message}")
                return@addSnapshotListener
            }
            sent = snapshot?.documents?.mapNotNull { parseDoodleDoc(it) } ?: emptyList()
            publish()
        }

    val receivedListener = db.collection("doodles").whereEqualTo("receiverId", myUid)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                android.util.Log.e("DuudleDebug", "Vault received listener failed: ${error.message}")
                return@addSnapshotListener
            }
            received = snapshot?.documents?.mapNotNull { parseDoodleDoc(it) } ?: emptyList()
            publish()
        }

    return listOf(sentListener, receivedListener)
}

@Composable
fun IncomingDoodleScreen(
    modifier: Modifier = Modifier,
    doodle: IncomingDoodle,
    onSnooze: () -> Unit,
    onRemove: () -> Unit,
    onReply: (senderUid: String, senderUsername: String) -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("🖍 Doodle from ${doodle.senderUsername}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }

        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.White)
        ) {
            doodle.strokes.forEach { stroke ->
                val scaledPoints = stroke.points.map { Offset(it.x * size.width, it.y * size.height) }
                for (i in 0 until scaledPoints.size - 1) {
                    drawLine(
                        color = stroke.color,
                        start = scaledPoints[i],
                        end = scaledPoints[i + 1],
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = onSnooze) { Text("Snooze") }
            Button(onClick = onRemove) { Text("Remove") }
            Button(onClick = { onReply(doodle.senderId, doodle.senderUsername) }) { Text("Draw") }
        }
    }
}