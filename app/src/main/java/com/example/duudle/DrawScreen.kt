package com.example.duudle

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore

data class DoodleStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float = 8f
)

@Composable
fun DrawScreen(
    modifier: Modifier = Modifier,
    recipientUid: String? = null,
    recipientUsername: String? = null,
    currentUid: String = "",
    currentUsername: String = "",
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val strokes = remember { mutableStateListOf<DoodleStroke>() }
    var currentPoints by remember { mutableStateOf(listOf<Offset>()) }
    var selectedColor by remember { mutableStateOf(Color.Black) }
    var isSending by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val palette = listOf(
        Color.Black, Color.Red, Color(0xFF2E7D32),
        Color(0xFF1565C0), Color(0xFFF9A825)
    )

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text(
                text = if (recipientUsername != null) "To: $recipientUsername" else "Draw (practice)",
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(
                onClick = {
                    when {
                        recipientUid == null -> Toast.makeText(
                            context, "Pick a friend from your Friends list first", Toast.LENGTH_SHORT
                        ).show()
                        strokes.isEmpty() -> Toast.makeText(
                            context, "Draw something first!", Toast.LENGTH_SHORT
                        ).show()
                        canvasSize.width == 0 || canvasSize.height == 0 -> Toast.makeText(
                            context, "Canvas not ready yet, try again", Toast.LENGTH_SHORT
                        ).show()
                        else -> {
                            isSending = true
                            sendDoodle(
                                senderId = currentUid,
                                senderUsername = currentUsername,
                                receiverId = recipientUid,
                                receiverUsername = recipientUsername ?: "Unknown",
                                strokes = strokes,
                                canvasWidth = canvasSize.width.toFloat(),
                                canvasHeight = canvasSize.height.toFloat()
                            ) { success, error ->
                                isSending = false
                                if (success) {
                                    Toast.makeText(context, "Doodle sent to $recipientUsername!", Toast.LENGTH_LONG).show()
                                    onBack()
                                } else {
                                    Toast.makeText(context, "Failed to send: $error", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                },
                enabled = !isSending
            ) {
                Text(if (isSending) "Sending..." else "Send")
            }
        }

        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.White)
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> currentPoints = listOf(offset) },
                        onDrag = { change, _ ->
                            change.consume()
                            currentPoints = currentPoints + change.position
                        },
                        onDragEnd = {
                            if (currentPoints.size > 1) {
                                strokes.add(DoodleStroke(currentPoints, selectedColor))
                            }
                            currentPoints = emptyList()
                        }
                    )
                }
        ) {
            strokes.forEach { drawStroke(it) }
            if (currentPoints.size > 1) {
                drawStroke(DoodleStroke(currentPoints, selectedColor))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            palette.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (color == selectedColor) 3.dp else 0.dp,
                            color = Color.DarkGray,
                            shape = CircleShape
                        )
                        .clickable { selectedColor = color }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }) {
                Text("Undo")
            }
            Button(onClick = { strokes.clear() }) {
                Text("Clear")
            }
        }
    }
}

private fun DrawScope.drawStroke(stroke: DoodleStroke) {
    for (i in 0 until stroke.points.size - 1) {
        drawLine(
            color = stroke.color,
            start = stroke.points[i],
            end = stroke.points[i + 1],
            strokeWidth = stroke.width,
            cap = StrokeCap.Round
        )
    }
}

private fun DoodleStroke.toFirestoreMap(canvasWidth: Float, canvasHeight: Float): Map<String, Any> = mapOf(
    "points" to points.map {
        mapOf(
            "x" to (if (canvasWidth > 0f) (it.x / canvasWidth).toDouble() else 0.0),
            "y" to (if (canvasHeight > 0f) (it.y / canvasHeight).toDouble() else 0.0)
        )
    },
    "color" to color.toArgb(),
    "width" to width.toDouble()
)

fun sendDoodle(
    senderId: String,
    senderUsername: String,
    receiverId: String,
    receiverUsername: String,
    strokes: List<DoodleStroke>,
    canvasWidth: Float,
    canvasHeight: Float,
    onResult: (Boolean, String?) -> Unit
) {
    val doodleData = hashMapOf(
        "senderId" to senderId,
        "senderUsername" to senderUsername,
        "receiverId" to receiverId,
        "receiverUsername" to receiverUsername,
        "strokes" to strokes.map { it.toFirestoreMap(canvasWidth, canvasHeight) },
        "createdAt" to System.currentTimeMillis(),
        "status" to "unseen"
    )
    FirebaseFirestore.getInstance().collection("doodles")
        .add(doodleData)
        .addOnSuccessListener { onResult(true, null) }
        .addOnFailureListener { e -> onResult(false, e.message) }
}