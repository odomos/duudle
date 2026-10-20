package com.example.duudle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LavenderBackground = Color(0xFFEDE3FA)
private val IndigoPrimary = Color(0xFF5B3FD9)
private val PinkAction = Color(0xFFE8637E)
private val SavedGold = Color(0xFFF2A900)

@Composable
fun VaultScreen(
    modifier: Modifier = Modifier,
    currentUid: String,
    onBack: () -> Unit = {},
    onFriendsClick: () -> Unit = {},
    onDesignDoodleClick: () -> Unit = {},
    onReply: (uid: String, username: String) -> Unit = { _, _ -> }
) {
    var doodles by remember { mutableStateOf<List<IncomingDoodle>>(emptyList()) }
    var selectedDoodle by remember { mutableStateOf<IncomingDoodle?>(null) }
    var savedOnly by rememberSaveable { mutableStateOf(false) }
    val favoriteIds = rememberFavoriteDoodleIds(currentUid)
    val shown = if (savedOnly) doodles.filter { it.docId in favoriteIds } else doodles

    DisposableEffect(currentUid) {
        if (currentUid.isBlank()) return@DisposableEffect onDispose {}
        val listeners = listenForVaultDoodles(currentUid) { doodles = it }
        onDispose { listeners.forEach { it.remove() } }
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
            onToggleSaved = { setDoodleFavorite(currentUid, doodle.docId, doodle.docId !in favoriteIds) },
            onBack = { selectedDoodle = null },
            onReply = { uid, username ->
                selectedDoodle = null
                onReply(uid, username)
            }
        )
    } else {
        Column(modifier = modifier.fillMaxSize().background(LavenderBackground)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("← Back", color = IndigoPrimary, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.weight(1f))
                Text("Vault", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(64.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
            ) {
                VaultFilterPill("All (${doodles.size})", selected = !savedOnly) { savedOnly = false }
                VaultFilterPill(
                    "★ Saved (${doodles.count { it.docId in favoriteIds }})",
                    selected = savedOnly
                ) { savedOnly = true }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (shown.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (savedOnly) "Nothing saved yet — open a doodle and tap ☆ to keep it here."
                            else "No doodles yet — send or receive one and it'll show up here!",
                            color = Color.DarkGray,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(shown, key = { it.docId }) { d ->
                            VaultThumbnail(
                                doodle = d,
                                currentUid = currentUid,
                                isSaved = d.docId in favoriteIds,
                                onClick = { selectedDoodle = d }
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
                onVaultClick = { /* already here */ }
            )
        }
    }
}

@Composable
private fun VaultFilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) IndigoPrimary else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (selected) Color.White else IndigoPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun VaultThumbnail(doodle: IncomingDoodle, currentUid: String, isSaved: Boolean, onClick: () -> Unit) {
    val isSent = doodle.senderId == currentUid
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
        ) {
            doodle.strokes.forEach { stroke ->
                val scaledPoints = stroke.points.map { Offset(it.x * size.width, it.y * size.height) }
                for (i in 0 until scaledPoints.size - 1) {
                    drawLine(
                        color = stroke.color,
                        start = scaledPoints[i],
                        end = scaledPoints[i + 1],
                        strokeWidth = stroke.width * 0.6f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isSent) "To ${doodle.receiverUsername}" else "From ${doodle.senderUsername}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.Black
                )
                Text(relativeTime(doodle.createdAt), fontSize = 10.sp, color = Color.Gray)
            }
            if (isSaved) Text("★", color = SavedGold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun VaultDoodleDetail(
    modifier: Modifier = Modifier,
    doodle: IncomingDoodle,
    currentUid: String,
    isSaved: Boolean,
    onToggleSaved: () -> Unit,
    onBack: () -> Unit,
    onReply: (uid: String, username: String) -> Unit
) {
    val isSent = doodle.senderId == currentUid
    Column(modifier = modifier.fillMaxSize().background(LavenderBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Back", color = IndigoPrimary, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.weight(1f))
            Text(
                if (isSent) "To ${doodle.receiverUsername}" else "From ${doodle.senderUsername}",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onToggleSaved, modifier = Modifier.width(88.dp)) {
                Text(
                    if (isSaved) "★ Saved" else "☆ Save",
                    color = if (isSaved) SavedGold else IndigoPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
                .wrapContentSize()
                .aspectRatio(1f) // square, like the sender's Draw canvas
                .clip(RoundedCornerShape(20.dp))
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

        if (!isSent) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(PinkAction)
                    .clickable { onReply(doodle.senderId, doodle.senderUsername) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("✏️ Draw a reply", color = Color.White, fontWeight = FontWeight.Bold)
            }
        } else {
            Spacer(Modifier.height(16.dp))
        }
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