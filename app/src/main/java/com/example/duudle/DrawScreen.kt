package com.example.duudle

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore

data class DoodleStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float = 8f
)

private data class BrushSize(val label: String, val width: Float, val dotSize: androidx.compose.ui.unit.Dp)

private val brushSizes = listOf(
    BrushSize("Thin", 4f, 10.dp),
    BrushSize("Medium", 8f, 16.dp),
    BrushSize("Thick", 14f, 22.dp)
)

private val palette = listOf(
    Color.Black,
    Color(0xFF5C5C5C), // Gray
    Color.White,       // doubles as an eraser on the white canvas
    Color.Red,
    Color(0xFFFF8A00), // Orange
    Color(0xFFF9A825), // Yellow
    Color(0xFF2E7D32), // Green
    Color(0xFF00BCD4), // Cyan
    Color(0xFF1565C0), // Blue
    Color(0xFF6A1B9A), // Purple
    Color(0xFFD81B60)  // Pink
)

private val LavenderBackground = Color(0xFFEDE3FA)

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
    // Saveable so a rotation or other config change doesn't wipe the drawing.
    val strokes = rememberSaveable(saver = StrokeListSaver) { mutableStateListOf<DoodleStroke>() }
    var currentPoints by remember { mutableStateOf(listOf<Offset>()) }
    var selectedColorArgb by rememberSaveable { mutableIntStateOf(Color.Black.toArgb()) }
    var selectedBrushIndex by rememberSaveable { mutableIntStateOf(1) } // Medium by default
    val selectedColor = Color(selectedColorArgb)
    val selectedBrush = brushSizes[selectedBrushIndex]
    var isSending by remember { mutableStateOf(false) }
    // Saved with the strokes: after a rotation it still holds the size the
    // restored strokes were drawn at, so onSizeChanged can rescale them.
    var canvasSize by rememberSaveable(stateSaver = IntSizeSaver) { mutableStateOf(IntSize.Zero) }
    // View-only zoom: strokes stay in un-zoomed canvas pixels, so what gets
    // sent (normalized to 0-1) never depends on the zoom level.
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffset by remember { mutableStateOf(Offset.Zero) }

    // The recipient can change after the screen first opens — picking a friend
    // from the in-canvas picker below updates these without losing the drawing.
    var activeRecipientUid by rememberSaveable { mutableStateOf(recipientUid) }
    var activeRecipientUsername by rememberSaveable { mutableStateOf(recipientUsername) }

    var showFriendPicker by remember { mutableStateOf(false) }
    var friends by remember { mutableStateOf<List<Friend>>(emptyList()) }
    var friendsLoading by remember { mutableStateOf(true) }

    // Load the current user's friends once, the same way FriendsScreen does,
    // so the picker has something to show the moment Send is tapped with
    // nobody chosen yet.
    DisposableEffect(currentUid) {
        if (currentUid.isBlank()) {
            friendsLoading = false
            return@DisposableEffect onDispose {}
        }
        val db = FirebaseFirestore.getInstance()
        var uidsAsA = listOf<String>()
        var uidsAsB = listOf<String>()

        fun resolveFriends() {
            val allUids = (uidsAsA + uidsAsB).filter { it.isNotBlank() }.distinct()
            if (allUids.isEmpty()) {
                friends = emptyList()
                friendsLoading = false
                return
            }
            db.collection("users")
                .whereIn(FieldPath.documentId(), allUids)
                .get()
                .addOnSuccessListener { snapshot ->
                    friends = snapshot.documents.map { doc ->
                        Friend(
                            uid = doc.id,
                            username = doc.getString("username") ?: "Unknown",
                            avatarEmoji = emojiForUid(doc.id)
                        )
                    }
                    friendsLoading = false
                }
                .addOnFailureListener { friendsLoading = false }
        }

        val listenerA = db.collection("friendships").whereEqualTo("userA", currentUid)
            .addSnapshotListener { snapshot, _ ->
                uidsAsA = snapshot?.documents?.mapNotNull { it.getString("userB") } ?: emptyList()
                resolveFriends()
            }
        val listenerB = db.collection("friendships").whereEqualTo("userB", currentUid)
            .addSnapshotListener { snapshot, _ ->
                uidsAsB = snapshot?.documents?.mapNotNull { it.getString("userA") } ?: emptyList()
                resolveFriends()
            }

        onDispose {
            listenerA.remove()
            listenerB.remove()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text(
                text = activeRecipientUsername?.let { "To: $it" } ?: "Draw",
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(
                onClick = {
                    when {
                        strokes.isEmpty() -> Toast.makeText(
                            context, "Draw something first!", Toast.LENGTH_SHORT
                        ).show()
                        activeRecipientUid == null -> {
                            // The fix: open a picker right here instead of failing
                            // silently or navigating away and losing the drawing.
                            if (friends.isEmpty() && !friendsLoading) {
                                Toast.makeText(
                                    context, "Add a friend first from the Friends tab", Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                showFriendPicker = true
                            }
                        }
                        canvasSize.width == 0 || canvasSize.height == 0 -> Toast.makeText(
                            context, "Canvas not ready yet, try again", Toast.LENGTH_SHORT
                        ).show()
                        else -> {
                            isSending = true
                            sendDoodle(
                                senderId = currentUid,
                                senderUsername = currentUsername,
                                receiverId = activeRecipientUid!!,
                                receiverUsername = activeRecipientUsername ?: "Unknown",
                                strokes = strokes,
                                canvasWidth = canvasSize.width.toFloat(),
                                canvasHeight = canvasSize.height.toFloat()
                            ) { success, error ->
                                isSending = false
                                if (success) {
                                    Toast.makeText(context, "Doodle sent to $activeRecipientUsername!", Toast.LENGTH_LONG).show()
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

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .background(Color.White)
                    .onSizeChanged { newSize ->
                        // Strokes are in canvas pixels. When the canvas changes size
                        // (e.g. rotation), map them to the new size the same way the
                        // 0-1 normalization will, so what's shown is what gets sent.
                        val old = canvasSize
                        if (old.width > 0 && old.height > 0 && newSize.width > 0 && newSize.height > 0 && newSize != old) {
                            val scaleX = newSize.width.toFloat() / old.width
                            val scaleY = newSize.height.toFloat() / old.height
                            for (i in strokes.indices) {
                                val stroke = strokes[i]
                                strokes[i] = stroke.copy(points = stroke.points.map { Offset(it.x * scaleX, it.y * scaleY) })
                            }
                            zoomScale = 1f
                            zoomOffset = Offset.Zero
                        }
                        canvasSize = newSize
                    }
                    .pointerInput(Unit) {
                        // One finger draws; two fingers pinch-zoom and pan. pointerInput(Unit)
                        // captures this lambda once, so it reads state directly rather than
                        // the selectedColor/selectedBrush values from the first composition.
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val viewSize = size.toSize()
                            var drawing = true
                            currentPoints = listOf(screenToCanvas(down.position, zoomScale, zoomOffset, viewSize))
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size > 1) {
                                    // A second finger turns this gesture into zoom/pan and
                                    // drops the stroke it interrupted, so no stray line.
                                    drawing = false
                                    currentPoints = emptyList()
                                    val (newScale, newOffset) = applyZoomPan(
                                        zoomScale, zoomOffset,
                                        event.calculateCentroid(), event.calculateZoom(), event.calculatePan(),
                                        viewSize
                                    )
                                    zoomScale = newScale
                                    zoomOffset = newOffset
                                } else if (drawing) {
                                    currentPoints = currentPoints +
                                        screenToCanvas(pressed[0].position, zoomScale, zoomOffset, viewSize)
                                }
                                event.changes.forEach { it.consume() }
                            }
                            if (drawing && currentPoints.size > 1) {
                                strokes.add(
                                    DoodleStroke(currentPoints, Color(selectedColorArgb), brushSizes[selectedBrushIndex].width)
                                )
                            }
                            currentPoints = emptyList()
                        }
                    }
            ) {
                withTransform({
                    translate(zoomOffset.x, zoomOffset.y)
                    scale(zoomScale, zoomScale, pivot = Offset.Zero)
                }) {
                    strokes.forEach { drawStroke(it) }
                    if (currentPoints.size > 1) {
                        drawStroke(DoodleStroke(currentPoints, selectedColor, selectedBrush.width))
                    }
                }
            }

            if (zoomScale > 1.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xCC333333))
                        .clickable {
                            zoomScale = 1f
                            zoomOffset = Offset.Zero
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Reset zoom · ${"%.1f".format(zoomScale)}×", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        // Brush size picker
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            brushSizes.forEach { brush ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { selectedBrushIndex = brushSizes.indexOf(brush) }
                        .border(
                            width = if (brush == selectedBrush) 2.dp else 0.dp,
                            color = Color.DarkGray,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(brush.dotSize)
                            .clip(CircleShape)
                            .background(Color.Black)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(brush.label, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Color palette — scrollable so it never overflows on smaller screens
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(palette) { color ->
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (color == selectedColor) 3.dp else 1.dp,
                            color = if (color == selectedColor) Color.DarkGray else Color.LightGray,
                            shape = CircleShape
                        )
                        .clickable { selectedColorArgb = color.toArgb() }
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

    if (showFriendPicker) {
        AlertDialog(
            onDismissRequest = { showFriendPicker = false },
            title = { Text("Send to...") },
            text = {
                if (friendsLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (friends.isEmpty()) {
                    Text("You don't have any friends yet. Add one from the Friends tab first.")
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(friends, key = { it.uid }) { friend ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(LavenderBackground, RoundedCornerShape(12.dp))
                                    .clickable {
                                        activeRecipientUid = friend.uid
                                        activeRecipientUsername = friend.username
                                        showFriendPicker = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(friend.avatarEmoji, fontSize = 20.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(friend.username, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFriendPicker = false }) { Text("Cancel") }
            }
        )
    }
}

private val IntSizeSaver = Saver<IntSize, IntArray>(
    save = { intArrayOf(it.width, it.height) },
    restore = { IntSize(it[0], it[1]) }
)

// Flattens each stroke to [colorArgb: Int, width: Float, xy: FloatArray] — all
// Bundle-friendly types. Points stay in canvas pixels, matching live drawing.
private val StrokeListSaver = listSaver<SnapshotStateList<DoodleStroke>, Any>(
    save = { list ->
        list.flatMap { stroke ->
            listOf(
                stroke.color.toArgb(),
                stroke.width,
                FloatArray(stroke.points.size * 2) { i ->
                    val p = stroke.points[i / 2]
                    if (i % 2 == 0) p.x else p.y
                }
            )
        }
    },
    restore = { saved ->
        saved.chunked(3).map { (color, width, xy) ->
            val coords = xy as FloatArray
            DoodleStroke(
                points = List(coords.size / 2) { i -> Offset(coords[2 * i], coords[2 * i + 1]) },
                color = Color(color as Int),
                width = width as Float
            )
        }.toMutableStateList()
    }
)

internal const val MAX_ZOOM = 4f

/** Maps a touch position on the zoomed view to un-zoomed canvas pixels, clamped to the canvas. */
internal fun screenToCanvas(point: Offset, scale: Float, offset: Offset, viewSize: Size): Offset {
    val canvasPoint = (point - offset) / scale
    return Offset(canvasPoint.x.coerceIn(0f, viewSize.width), canvasPoint.y.coerceIn(0f, viewSize.height))
}

/**
 * Applies one pinch step: zoom by [zoom] around [centroid] (the point under the
 * fingers stays put), then pan by [pan]. The result is clamped to 1x..MAX_ZOOM
 * and so the canvas always fills the view.
 */
internal fun applyZoomPan(
    scale: Float,
    offset: Offset,
    centroid: Offset,
    zoom: Float,
    pan: Offset,
    viewSize: Size
): Pair<Float, Offset> {
    val newScale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
    val raw = centroid - (centroid - offset) * (newScale / scale) + pan
    val clamped = Offset(
        raw.x.coerceIn(viewSize.width * (1 - newScale), 0f),
        raw.y.coerceIn(viewSize.height * (1 - newScale), 0f)
    )
    return newScale to clamped
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

// Max distance (as a fraction of the canvas) a dropped point may sit from the
// simplified line. ~1px on a typical phone canvas, well under any brush width.
private const val SIMPLIFY_EPSILON = 0.0012f

private fun DoodleStroke.toFirestoreMap(canvasWidth: Float, canvasHeight: Float): Map<String, Any> {
    val normalized = points.map {
        Offset(
            if (canvasWidth > 0f) it.x / canvasWidth else 0f,
            if (canvasHeight > 0f) it.y / canvasHeight else 0f
        )
    }
    // Fewer, rounded points keep most doodles under the FCM embed cap, so the
    // receiver can skip the slower Firestore fallback. Same format as before.
    return mapOf(
        "points" to simplifyStroke(normalized, SIMPLIFY_EPSILON).map {
            mapOf("x" to roundTo3(it.x), "y" to roundTo3(it.y))
        },
        "color" to color.toArgb(),
        "width" to width.toDouble()
    )
}

private fun roundTo3(value: Float): Double = Math.round(value * 1000.0) / 1000.0

/**
 * Ramer-Douglas-Peucker: drops points lying within [epsilon] of the line between
 * the points kept around them, so smooth curves need far fewer points. Iterative
 * to avoid deep recursion on long strokes.
 */
internal fun simplifyStroke(points: List<Offset>, epsilon: Float): List<Offset> {
    if (points.size < 3) return points
    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.lastIndex] = true
    val ranges = ArrayDeque<Pair<Int, Int>>()
    ranges.addLast(0 to points.lastIndex)
    while (ranges.isNotEmpty()) {
        val (start, end) = ranges.removeLast()
        var maxDistance = 0f
        var maxIndex = -1
        for (i in start + 1 until end) {
            val d = distanceToSegment(points[i], points[start], points[end])
            if (d > maxDistance) {
                maxDistance = d
                maxIndex = i
            }
        }
        if (maxIndex != -1 && maxDistance > epsilon) {
            keep[maxIndex] = true
            ranges.addLast(start to maxIndex)
            ranges.addLast(maxIndex to end)
        }
    }
    return points.filterIndexed { i, _ -> keep[i] }
}

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared == 0f) return (p - a).getDistance()
    val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSquared).coerceIn(0f, 1f)
    return (p - Offset(a.x + t * dx, a.y + t * dy)).getDistance()
}

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
