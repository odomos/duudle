package com.example.duudle

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.duudle.ui.theme.DuudleColors
import com.example.duudle.ui.theme.DuudleDisplayFont
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val SavedGold = Color(0xFFF2A900)
// Card body around the (always white) doodle canvas: white strokes are the eraser, so the
// canvas itself has to stay pure white.
private val CardTint = Color(0xFFFBF9F5)
private val ThumbShape = RoundedCornerShape(20.dp)
private val ThumbCanvasShape = RoundedCornerShape(14.dp)
private val DetailCanvasShape = RoundedCornerShape(24.dp)

// If neither vault listener has answered by now (listenForVaultDoodles only calls back on
// success), stop the spinner and show the empty state.
private const val LOAD_FALLBACK_MS = 4000L

@Composable
fun VaultScreen(
    modifier: Modifier = Modifier,
    currentUid: String,
    onBack: () -> Unit = {},
    onFriendsClick: () -> Unit = {},
    onDesignDoodleClick: () -> Unit = {},
    onMeClick: () -> Unit = {},
    onReply: (uid: String, username: String) -> Unit = { _, _ -> },
    // Set when the "doodles arrived while pop-ups were paused" notification was tapped.
    openSnoozedTab: Boolean = false,
    onSnoozedTabOpened: () -> Unit = {}
) {
    var allDoodles by remember { mutableStateOf<List<IncomingDoodle>>(emptyList()) }
    // Doodles from people I've blocked are hidden (the ones I sent them stay).
    val blocked by Blocks.state.collectAsState()
    val doodles = remember(allDoodles, blocked) {
        allDoodles.filter { it.senderId == currentUid || it.senderId !in blocked }
    }
    val safety = rememberSafetyActions()
    var loaded by remember { mutableStateOf(false) }
    var selectedDoodle by remember { mutableStateOf<IncomingDoodle?>(null) }
    var savedOnly by rememberSaveable { mutableStateOf(false) }
    var snoozedOnly by rememberSaveable { mutableStateOf(false) }
    val favoriteIds = rememberFavoriteDoodleIds(currentUid)
    val snoozeBox = rememberSnoozeBox()
    val snoozed = snoozeBox.held
    val pausedUntil = snoozeBox.pausedUntil
    val snoozedCount = doodles.count { it.docId in snoozed }
    // The Snoozed tab exists while doodles are held or pop-ups are paused; otherwise the
    // Vault falls back to All.
    val snoozedTabVisible = snoozedCount > 0 || pausedUntil != null
    val showingSnoozed = snoozedOnly && snoozedTabVisible
    // Opening a held doodle takes it out of the held list, so remember that it was held
    // for the detail view's note.
    var openedHeld by remember { mutableStateOf(false) }
    val shown = when {
        showingSnoozed -> doodles.filter { it.docId in snoozed }
        savedOnly -> doodles.filter { it.docId in favoriteIds }
        else -> doodles
    }
    val context = LocalContext.current
    val gaze = rememberFaceGaze()

    LaunchedEffect(openSnoozedTab) {
        if (!openSnoozedTab) return@LaunchedEffect
        selectedDoodle = null
        savedOnly = false
        snoozedOnly = true
        onSnoozedTabOpened()
    }
    LaunchedEffect(showingSnoozed) {
        if (showingSnoozed) DoodleSnooze.clearWaitingNotification(context)
    }

    DisposableEffect(currentUid) {
        if (currentUid.isBlank()) return@DisposableEffect onDispose {}
        val listeners = listenForVaultDoodles(currentUid) {
            allDoodles = it
            loaded = true
        }
        onDispose { listeners.forEach { it.remove() } }
    }
    LaunchedEffect(currentUid) {
        if (currentUid.isNotBlank()) delay(LOAD_FALLBACK_MS)
        loaded = true
    }

    // Registered after MainActivity's handler, so back closes the detail view
    // first instead of leaving the Vault.
    BackHandler(enabled = selectedDoodle != null) { selectedDoodle = null }

    val doodle = selectedDoodle
    if (doodle != null) {
        VaultDoodleDetail(
            modifier = modifier,
            doodle = doodle,
            currentUid = currentUid,
            isSaved = doodle.docId in favoriteIds,
            heldNote = when {
                !openedHeld -> null
                pausedUntil != null -> "Pop-ups paused until ${clockTime(context, pausedUntil)}"
                else -> "Arrived while pop-ups were paused"
            },
            onToggleSaved = { setDoodleFavorite(currentUid, doodle.docId, doodle.docId !in favoriteIds) },
            onBack = { selectedDoodle = null },
            onReply = { uid, username ->
                selectedDoodle = null
                onReply(uid, username)
            },
            onReport = { safety.report(ReportSubject.doodle(doodle.senderId, doodle.senderUsername, doodle.docId)) },
            onBlock = { safety.block(doodle.senderId, doodle.senderUsername) }
        )
    } else {
        DuudlePage(modifier = modifier.faceGazeSource(gaze)) {
            DuudleScreenHeader(title = "Vault", onBack = onBack)
            HorizontalDivider(color = DuudleColors.Hairline)
            Spacer(Modifier.height(14.dp))
            DuudleSegmentedControl(
                options = listOfNotNull(
                    if (loaded) "All (${doodles.size})" else "All",
                    if (loaded) "Saved (${doodles.count { it.docId in favoriteIds }})" else "Saved",
                    if (snoozedTabVisible) "Snoozed ($snoozedCount)" else null
                ),
                selectedIndex = when {
                    showingSnoozed -> 2
                    savedOnly -> 1
                    else -> 0
                },
                onSelect = {
                    savedOnly = it == 1
                    snoozedOnly = it == 2
                },
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(14.dp))

            if (showingSnoozed && pausedUntil != null) {
                PausedBanner(
                    until = clockTime(context, pausedUntil),
                    onResume = {
                        DoodleSnooze.resume(context)
                        Toast.makeText(context, "Pop-ups resumed", Toast.LENGTH_SHORT).show()
                    }
                )
                Spacer(Modifier.height(12.dp))
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    !loaded -> CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 48.dp)
                            .size(28.dp),
                        color = DuudleColors.Ink,
                        strokeWidth = 2.5.dp
                    )
                    // Only reachable while paused: the tab hides when nothing is held and no pause is on.
                    shown.isEmpty() && showingSnoozed -> Text(
                        "Nothing has arrived yet",
                        fontSize = 15.sp,
                        color = DuudleColors.Muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                    )
                    shown.isEmpty() && savedOnly && !showingSnoozed -> SavedEmptyState(gaze = gaze)
                    shown.isEmpty() -> VaultEmptyState(gaze = gaze, onDrawClick = onDesignDoodleClick)
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(top = 2.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(shown, key = { it.docId }) { d ->
                            VaultThumbnail(
                                doodle = d,
                                currentUid = currentUid,
                                isSaved = d.docId in favoriteIds,
                                isSnoozed = d.docId in snoozed,
                                onClick = {
                                    openedHeld = d.docId in snoozed
                                    if (openedHeld) DoodleSnooze.cancel(context, d.docId)
                                    selectedDoodle = d
                                }
                            )
                        }
                    }
                }
            }

            DuudleBottomNav(
                activeTab = DuudleNavTab.VAULT,
                onHomeClick = onBack,
                onFriendsClick = onFriendsClick,
                onDrawClick = onDesignDoodleClick,
                onVaultClick = { /* already here */ },
                onMeClick = onMeClick
            )
        }
    }
    SafetyDialogs(safety, currentUid, onBlocked = { selectedDoodle = null })
}

@Composable
private fun VaultEmptyState(gaze: FaceGaze, onDrawClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        VaultEmptyIllustration(gaze = gaze)
        Spacer(Modifier.height(12.dp))
        Text(
            "No doodles yet",
            fontFamily = DuudleDisplayFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 24.sp,
            color = DuudleColors.Ink,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Send or receive a doodle and it'll live here.",
            fontSize = 15.sp,
            color = DuudleColors.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp)
        )
        Spacer(Modifier.height(24.dp))
        DuudleInkPill(text = "Draw a doodle", onClick = onDrawClick, icon = Icons.Filled.Edit)
    }
}

// A small sunny face for the empty Saved tab.
private val SavedEmptyCast = listOf(
    FaceCharacter(
        BodyShape.Circle, DuudleColors.Sunny, size = 0.78f, x = 0.5f, y = 0.55f,
        eyes = EyeStyle.Googly, mouth = MouthStyle.SmallSmile, gradient = false, floatSeconds = 3.0f
    )
)

@Composable
private fun SavedEmptyState(gaze: FaceGaze) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FaceScene(characters = SavedEmptyCast, modifier = Modifier.size(110.dp), gaze = gaze)
        Spacer(Modifier.height(12.dp))
        Text(
            "Nothing saved yet",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = DuudleColors.Ink,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Open a doodle and tap the star to keep it here.",
            fontSize = 14.sp,
            color = DuudleColors.Muted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun VaultThumbnail(
    doodle: IncomingDoodle,
    currentUid: String,
    isSaved: Boolean,
    isSnoozed: Boolean,
    onClick: () -> Unit
) {
    val isSent = doodle.senderId == currentUid
    val otherUid = if (isSent) doodle.receiverId else doodle.senderId
    val label = if (isSent) "To @${doodle.receiverUsername}" else "From @${doodle.senderUsername}"
    val time = relativeTime(doodle.createdAt)
    val description = buildString {
        val kind = if (doodle.cardId != null) "Flash card" else "Doodle"
        append(if (isSent) "$kind to @${doodle.receiverUsername}" else "$kind from @${doodle.senderUsername}")
        if (time.isNotEmpty()) append(", ").append(time)
        if (isSaved) append(", saved")
        if (isSnoozed) append(", snoozed")
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, label = "thumb-press")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(ThumbShape)
            .background(CardTint)
            .border(1.dp, DuudleColors.Hairline, ThumbShape)
            .semantics { contentDescription = description }
            .clickable(interactionSource = interactionSource, indication = null, onClickLabel = "Open", onClick = onClick)
            .padding(8.dp)
    ) {
        // The card itself is announced via [description]; its parts stay silent.
        Column(modifier = Modifier.clearAndSetSemantics { }) {
            Box {
                DoodleOrCard(
                    strokes = doodle.strokes,
                    cardId = doodle.cardId,
                    strokeWidthScale = 0.6f,
                    placeholderName = if (isSent) null else doodle.senderUsername,
                    thumbnail = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(ThumbCanvasShape)
                        .background(Color.White)
                        .border(1.dp, DuudleColors.Hairline, ThumbCanvasShape)
                )
                if (isSnoozed) {
                    SnoozeClockBadge(modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DuudleAvatar(uid = otherUid, size = 28.dp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = DuudleColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (time.isNotEmpty()) Text(time, fontSize = 11.sp, color = DuudleColors.Muted)
                }
                if (isSaved) StarGlyph(filled = true, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun VaultDoodleDetail(
    modifier: Modifier = Modifier,
    doodle: IncomingDoodle,
    currentUid: String,
    isSaved: Boolean,
    /** "Pop-ups paused until ..." / "Arrived while pop-ups were paused" for a held doodle. */
    heldNote: String?,
    onToggleSaved: () -> Unit,
    onBack: () -> Unit,
    onReply: (uid: String, username: String) -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit
) {
    val isSent = doodle.senderId == currentUid
    val time = relativeTime(doodle.createdAt)
    DuudlePage(modifier = modifier) {
        DuudleScreenHeader(
            title = if (isSent) "To @${doodle.receiverUsername}" else "From @${doodle.senderUsername}",
            onBack = onBack,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconToggleButton(
                        checked = isSaved,
                        onCheckedChange = { onToggleSaved() },
                        modifier = Modifier.semantics { contentDescription = if (isSaved) "Saved" else "Save" }
                    ) {
                        StarGlyph(filled = isSaved, modifier = Modifier.size(26.dp))
                    }
                    if (!isSent) SafetyMenuButton(username = doodle.senderUsername, onReport = onReport, onBlock = onBlock)
                }
            }
        )

        DoodleOrCard(
            strokes = doodle.strokes,
            cardId = doodle.cardId,
            placeholderName = if (isSent) null else doodle.senderUsername,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .wrapContentSize()
                .aspectRatio(1f) // square, like the sender's Draw canvas
                .shadow(elevation = 10.dp, shape = DetailCanvasShape)
                .clip(DetailCanvasShape)
                .background(Color.White)
        )

        if (time.isNotEmpty()) {
            Text(
                time,
                fontSize = 14.sp,
                color = DuudleColors.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (heldNote != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                heldNote,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = DuudleColors.Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (!isSent) {
            DuudleInkPill(
                text = "Draw a reply",
                onClick = { onReply(doodle.senderId, doodle.senderUsername) },
                icon = Icons.Filled.Edit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp)
            )
        } else {
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Held doodles by id, and when pop-ups come back (null = not paused). */
private data class SnoozeBox(val held: Map<String, SnoozedDoodle> = emptyMap(), val pausedUntil: Long? = null)

/**
 * Re-read whenever the held list or pause changes, on resume (the pause can end while the
 * app is in the background), and when the pause's end time passes.
 */
@Composable
private fun rememberSnoozeBox(): SnoozeBox {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val version by DoodleSnooze.changes.collectAsState()
    var resumes by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumes++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var pauseExpired by remember { mutableIntStateOf(0) }
    val box by produceState(SnoozeBox(), version, resumes, pauseExpired) {
        value = withContext(Dispatchers.IO) {
            SnoozeBox(DoodleSnooze.list(context).associateBy { it.doodleId }, DoodleSnooze.pausedUntil(context))
        }
    }
    // The end alarm can be late (inexact) or missing; don't keep showing a past end time.
    LaunchedEffect(box.pausedUntil) {
        val until = box.pausedUntil ?: return@LaunchedEffect
        delay((until - System.currentTimeMillis()).coerceAtLeast(0L) + 500L)
        pauseExpired++
    }
    return box
}

/** The phone's own clock format (12/24h), e.g. "4:35 PM". */
private fun clockTime(context: android.content.Context, millis: Long): String =
    android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(millis))

/** Top of the Snoozed tab while paused: when pop-ups come back, and a way to resume now. */
@Composable
private fun PausedBanner(until: String, onResume: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(DuudleColors.Paper)
            .border(1.dp, DuudleColors.Hairline, RoundedCornerShape(18.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Pop-ups paused until $until",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = DuudleColors.Ink,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        DuudleOutlinePill(text = "Resume pop-ups", onClick = onResume, modifier = Modifier.fillMaxWidth())
    }
}

/** Small Ink badge with a clock on a snoozed doodle's thumbnail. Decorative: the card says "snoozed". */
@Composable
private fun SnoozeClockBadge(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(DuudleColors.Ink)
    ) {
        val c = center
        val r = size.minDimension * 0.3f
        val w = size.minDimension * 0.08f
        drawCircle(DuudleColors.Paper, r, c, style = Stroke(width = w))
        drawLine(DuudleColors.Paper, c, Offset(c.x, c.y - r * 0.65f), w, StrokeCap.Round)
        drawLine(DuudleColors.Paper, c, Offset(c.x + r * 0.5f, c.y), w, StrokeCap.Round)
    }
}

/** Five-point star: gold with an Ink outline when [filled], otherwise an Ink outline. */
@Composable
private fun StarGlyph(filled: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val outer = size.minDimension / 2f
        val inner = outer * 0.45f
        val star = Path().apply {
            for (i in 0 until 10) {
                val radius = if (i % 2 == 0) outer else inner
                val angle = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
                val x = center.x + cos(angle) * radius
                val y = center.y + sin(angle) * radius
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        if (filled) drawPath(star, SavedGold)
        drawPath(star, DuudleColors.Ink, style = Stroke(width = outer * 0.16f, join = StrokeJoin.Round))
    }
}

private fun relativeTime(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return ""
    val diff = System.currentTimeMillis() - timestampMillis
    val minutes = diff / 60000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(timestampMillis))
    }
}
