package com.example.duudle

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.example.duudle.ui.theme.DuudleColors
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.example.duudle.ui.theme.DuudleDisplayFont

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

private data class PaintColor(val color: Color, val name: String)

private val palette = listOf(
    PaintColor(Color.Black, "Black"),
    PaintColor(Color(0xFF5C5C5C), "Grey"),
    PaintColor(Color.White, "White, works as an eraser"),
    PaintColor(Color.Red, "Red"),
    PaintColor(Color(0xFFFF8A00), "Orange"),
    PaintColor(Color(0xFFF9A825), "Yellow"),
    PaintColor(Color(0xFF2E7D32), "Green"),
    PaintColor(Color(0xFF00BCD4), "Cyan"),
    PaintColor(Color(0xFF1565C0), "Blue"),
    PaintColor(Color(0xFF6A1B9A), "Purple"),
    PaintColor(Color(0xFFD81B60), "Pink")
)

private val DrawModeLabels = listOf("Draw", "Stickers")
private val FlashCardShape = RoundedCornerShape(20.dp)
private val CanvasShape = RoundedCornerShape(16.dp)
private val ToolCardShape = RoundedCornerShape(24.dp)
private val DrawPillShape = RoundedCornerShape(percent = 50)
private const val RESTORED_SEND_GRACE_MS = 5000L

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

    // Flash cards mode swaps the canvas for a grid of prebuilt cards; the drawing is kept.
    var flashCardsMode by rememberSaveable { mutableStateOf(false) }
    // The card being sent right now. While set, further taps are ignored (no duplicates).
    // Saveable so a rotation mid-send doesn't re-enable the cards.
    var sendingCardId by rememberSaveable { mutableStateOf<String?>(null) }
    // A send still marked in flight after a rotation reports to the old screen instance, so
    // this one never hears back (the write itself carries on in Firestore). Keep the cards
    // blocked for a short grace period, then let go so the screen isn't stuck.
    LaunchedEffect(Unit) {
        if (sendingCardId != null) {
            delay(RESTORED_SEND_GRACE_MS)
            sendingCardId = null
        }
    }

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

    fun openFriendPicker() {
        if (friends.isEmpty() && !friendsLoading) {
            Toast.makeText(context, "Add a friend first from the Friends tab", Toast.LENGTH_SHORT).show()
        } else {
            showFriendPicker = true
        }
    }

    // Sticker-style: a tap sends the card straight away.
    fun sendCard(card: FlashCard) {
        if (sendingCardId != null) return
        val toUid = activeRecipientUid
        if (toUid == null) {
            openFriendPicker()
            return
        }
        val toName = activeRecipientUsername
        sendingCardId = card.id
        sendFlashCard(
            senderId = currentUid,
            senderUsername = currentUsername,
            receiverId = toUid,
            receiverUsername = toName ?: "Unknown",
            card = card
        ) { error ->
            sendingCardId = null
            if (error == null) {
                Toast.makeText(context, "Sticker sent to ${toName ?: "your friend"}!", Toast.LENGTH_LONG).show()
                onBack()
            } else {
                Toast.makeText(context, rateLimitedOr(error, "Couldn't send. Check your connection."), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun onSendClick() {
        when {
            strokes.isEmpty() -> Toast.makeText(
                context, "Draw something first!", Toast.LENGTH_SHORT
            ).show()
            // Pick a friend right here instead of failing silently or navigating
            // away and losing the drawing.
            activeRecipientUid == null -> openFriendPicker()
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
                ) { error ->
                    isSending = false
                    if (error == null) {
                        Toast.makeText(context, "Doodle sent to $activeRecipientUsername!", Toast.LENGTH_LONG).show()
                        onBack()
                    } else {
                        Toast.makeText(context, rateLimitedOr(error, "Failed to send: ${error.message}"), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    DuudlePage(modifier = modifier) {
        DrawHeader(
            title = activeRecipientUsername?.let { "To: $it" } ?: "Draw",
            onBack = onBack,
            // Stickers send on tap, so there's no Send button in that mode.
            showSend = !flashCardsMode,
            sending = isSending,
            onSend = { onSendClick() }
        )

        DuudleSegmentedControl(
            options = DrawModeLabels,
            selectedIndex = if (flashCardsMode) 1 else 0,
            onSelect = { flashCardsMode = it == 1 },
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))

        if (flashCardsMode) {
            FlashCardPicker(
                recipientName = if (activeRecipientUid != null) activeRecipientUsername ?: "friend" else null,
                sendingCardId = sendingCardId,
                onPickFriend = { openFriendPicker() },
                onCardClick = { sendCard(it) },
                modifier = Modifier.weight(1f)
            )
        } else {
            // The canvas is the largest centered 1:1 square that fits the leftover space, so the
            // 0-1 normalized strokes keep their proportions on the square viewers. wrapContentSize()
            // drops the fill min-constraints so aspectRatio can shrink and center the square.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .wrapContentSize()
                    .aspectRatio(1f)
                    .clip(CanvasShape)
                    .border(1.dp, DuudleColors.Hairline, CanvasShape)
            ) {
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
                            .heightIn(min = 48.dp)
                            .clip(DrawPillShape)
                            .background(DuudleColors.Ink.copy(alpha = 0.85f))
                            .clickable(role = Role.Button) {
                                zoomScale = 1f
                                zoomOffset = Offset.Zero
                            }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Reset zoom · ${"%.1f".format(zoomScale)}×", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            DrawTools(
                brushIndex = selectedBrushIndex,
                onBrush = { selectedBrushIndex = it },
                color = selectedColor,
                onColor = { selectedColorArgb = it.toArgb() },
                onUndo = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                onClear = { strokes.clear() }
            )
        }
    }

    if (showFriendPicker) {
        AlertDialog(
            onDismissRequest = { showFriendPicker = false },
            containerColor = DuudleColors.Paper,
            titleContentColor = DuudleColors.Ink,
            textContentColor = DuudleColors.Ink,
            title = { Text("Send to…", fontWeight = FontWeight.ExtraBold) },
            text = {
                if (friendsLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DuudleColors.Ink)
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
                                    .heightIn(min = 56.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(DuudleColors.Cream)
                                    .clickable(role = Role.Button) {
                                        activeRecipientUid = friend.uid
                                        activeRecipientUsername = friend.username
                                        showFriendPicker = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DuudleAvatar(friend.uid, size = 40.dp, emoji = friend.avatarEmoji)
                                Spacer(Modifier.width(12.dp))
                                Text("@${friend.username}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFriendPicker = false }) {
                    Text("Cancel", color = DuudleColors.Ink, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

/** Back arrow, centred title and the Send pill. Both side slots are equally wide, so the title stays centred. */
@Composable
private fun DrawHeader(title: String, onBack: () -> Unit, showSend: Boolean, sending: Boolean, onSend: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(100.dp), contentAlignment = Alignment.CenterStart) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = DuudleColors.Ink)
            }
        }
        Text(
            title,
            fontFamily = DuudleDisplayFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
            color = DuudleColors.Ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        Box(modifier = Modifier.width(100.dp).padding(end = 8.dp), contentAlignment = Alignment.CenterEnd) {
            if (showSend) {
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(DrawPillShape)
                        .background(if (sending) DuudleColors.Ink.copy(alpha = 0.45f) else DuudleColors.Ink)
                        .clickable(enabled = !sending, role = Role.Button, onClick = onSend)
                        .padding(horizontal = if (sending) 10.dp else 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (sending) "Sending…" else "Send",
                        color = DuudleColors.Paper,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (sending) 13.sp else 16.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** The white card under the canvas: brush size, colours, then Undo and Clear. */
@Composable
private fun DrawTools(
    brushIndex: Int,
    onBrush: (Int) -> Unit,
    color: Color,
    onColor: (Color) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(ToolCardShape)
            .background(DuudleColors.Paper)
            .border(1.dp, DuudleColors.Hairline, ToolCardShape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            brushSizes.forEachIndexed { index, brush ->
                val selected = index == brushIndex
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(DrawPillShape)
                        .background(if (selected) DuudleColors.Ink else DuudleColors.PillGrey)
                        .selectable(selected = selected, role = Role.RadioButton) { onBrush(index) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(brush.dotSize * 0.8f)
                            .clip(CircleShape)
                            .background(if (selected) DuudleColors.Paper else DuudleColors.Ink)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        brush.label,
                        color = if (selected) DuudleColors.Paper else DuudleColors.Ink,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Scrollable so it never overflows on smaller screens.
        LazyRow(modifier = Modifier.fillMaxWidth().selectableGroup()) {
            items(palette) { paint ->
                val selected = paint.color == color
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .selectable(selected = selected, role = Role.RadioButton) { onColor(paint.color) }
                        .semantics { contentDescription = paint.name },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Box(Modifier.size(44.dp).border(3.dp, DuudleColors.Ink, CircleShape))
                    }
                    Box(
                        modifier = Modifier
                            .size(if (selected) 30.dp else 36.dp)
                            .clip(CircleShape)
                            .background(paint.color)
                            .border(1.dp, DuudleColors.Ink.copy(alpha = 0.15f), CircleShape)
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DuudleOutlinePill("Undo", onClick = onUndo, modifier = Modifier.weight(1f))
            DuudleOutlinePill("Clear", onClick = onClear, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Stickers mode: who the sticker goes to (or a prompt to pick a friend), a search box, a row of
 * pack chips (only when there's more than one pack) and a 3-column grid. [recipientName] null =
 * no recipient yet.
 */
@Composable
private fun FlashCardPicker(
    recipientName: String?,
    sendingCardId: String?,
    onPickFriend: () -> Unit,
    onCardClick: (FlashCard) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var packId by rememberSaveable { mutableStateOf<String?>(null) }
    val results = remember(query, packId) { searchStickers(query, packId) }
    Column(modifier = modifier.fillMaxWidth()) {
        if (recipientName != null) {
            Text(
                "Tap a sticker to send it to @$recipientName",
                style = MaterialTheme.typography.bodyMedium,
                color = DuudleColors.Ink,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Pick a friend first",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DuudleColors.Ink,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onPickFriend, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Pick a friend", color = DuudleColors.Ink, fontWeight = FontWeight.Bold)
                }
            }
        }
        GreySearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search stickers (love, lol, sleepy…)",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        if (StickerPacks.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                item(key = "all") { PackChip("All", selected = packId == null) { packId = null } }
                items(StickerPacks, key = { it.id }) { pack ->
                    PackChip(pack.title, selected = packId == pack.id) { packId = pack.id }
                }
            }
        }
        if (results.isEmpty()) {
            Text(
                "No stickers match \"${query.trim()}\"",
                style = MaterialTheme.typography.bodyMedium,
                color = DuudleColors.Muted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            gridItems(results, key = { it.id }) { card ->
                val description = if (recipientName != null) "Send ${card.label} to @$recipientName" else "Send ${card.label}"
                val isSendingThis = card.id == sendingCardId
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(FlashCardShape)
                        .background(DuudleColors.Paper)
                        .border(1.dp, DuudleColors.Hairline, FlashCardShape)
                        .clickable(enabled = sendingCardId == null, role = Role.Button) { onCardClick(card) }
                        .semantics {
                            contentDescription = description
                            if (isSendingThis) stateDescription = "Sending"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(card.thumbRes),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(6.dp)
                    )
                    if (isSendingThis) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = DuudleColors.Ink,
                                strokeWidth = 2.5.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A pill in the picker's pack row. Selected = black, like the app's other active pills. */
@Composable
private fun PackChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) DuudleColors.Ink else DuudleColors.Paper)
            .border(1.dp, if (selected) DuudleColors.Ink else DuudleColors.Hairline, RoundedCornerShape(24.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) DuudleColors.Paper else DuudleColors.Ink
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
    onResult: (Exception?) -> Unit
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
    val doc = FirebaseFirestore.getInstance().collection("doodles").document()
    sendRateLimited(RateLimit.DOODLES, senderId, doc, doodleData, onDone = onResult)
}

/** A flash card is a normal doodle doc with no strokes plus its cardId (see FlashCards.kt). */
fun sendFlashCard(
    senderId: String,
    senderUsername: String,
    receiverId: String,
    receiverUsername: String,
    card: FlashCard,
    onResult: (Exception?) -> Unit
) {
    val doodleData = hashMapOf(
        "senderId" to senderId,
        "senderUsername" to senderUsername,
        "receiverId" to receiverId,
        "receiverUsername" to receiverUsername,
        "strokes" to emptyList<Any>(),
        "cardId" to card.id,
        "createdAt" to System.currentTimeMillis(),
        "status" to "unseen"
    )
    val doc = FirebaseFirestore.getInstance().collection("doodles").document()
    sendRateLimited(RateLimit.DOODLES, senderId, doc, doodleData, onDone = onResult)
}
