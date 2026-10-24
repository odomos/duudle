package com.example.duudle

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.duudle.ui.theme.DuudleColors
import com.example.duudle.ui.theme.DuudleDisplayFont
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Darker than the app's #3DBE64 so "On" text passes contrast on white.
private val GreenOnText = Color(0xFF1E7E3E)

private val HeroShape = RoundedCornerShape(28.dp)
private val CardShape = RoundedCornerShape(22.dp)
private val TileShape = RoundedCornerShape(20.dp)
private val PillShape = RoundedCornerShape(percent = 50)

private const val STAT_LOADING = "…"
private const val STAT_FAILED = "—"


@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    currentUid: String,
    currentUsername: String,
    onBack: () -> Unit = {},
    onFriendsClick: () -> Unit = {},
    onVaultClick: () -> Unit = {},
    onDesignDoodleClick: () -> Unit = {},
    onOverlayDebugClick: () -> Unit = {},
    onEditCharacter: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isBatteryUnrestricted by remember { mutableStateOf(readBatteryUnrestricted(context)) }

    // Re-check when the user comes back from a Settings screen opened by "Fix".
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
                isBatteryUnrestricted = readBatteryUnrestricted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var sentCount by remember { mutableStateOf(STAT_LOADING) }
    var receivedCount by remember { mutableStateOf(STAT_LOADING) }
    var friendsCount by remember { mutableStateOf(STAT_LOADING) }
    var savedCount by remember { mutableStateOf(STAT_LOADING) }
    var statsRetryCount by remember { mutableIntStateOf(0) }
    val anyStatFailed = listOf(sentCount, receivedCount, friendsCount, savedCount).any { it == STAT_FAILED }

    LaunchedEffect(currentUid, statsRetryCount) {
        sentCount = STAT_LOADING
        receivedCount = STAT_LOADING
        friendsCount = STAT_LOADING
        savedCount = STAT_LOADING
        if (currentUid.isBlank()) {
            sentCount = STAT_FAILED
            receivedCount = STAT_FAILED
            friendsCount = STAT_FAILED
            savedCount = STAT_FAILED
            return@LaunchedEffect
        }
        // A slow callback from an earlier run must not overwrite a retry's newer result.
        val thisRun = statsRetryCount
        fun showStat(count: Long?, setStat: (String) -> Unit) {
            if (thisRun == statsRetryCount) setStat(count?.toString() ?: STAT_FAILED)
        }
        countSentDoodles(currentUid) { showStat(it) { value -> sentCount = value } }
        countReceivedDoodles(currentUid) { showStat(it) { value -> receivedCount = value } }
        countFriends(currentUid) { showStat(it) { value -> friendsCount = value } }
        countSavedDoodles(currentUid) { showStat(it) { value -> savedCount = value } }
    }

    val joinedDate = remember {
        FirebaseAuth.getInstance().currentUser?.metadata?.creationTimestamp?.let {
            SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(it))
        }
    }

    // The character is stored on this phone only (see AvatarStore). Profile only shows it;
    // the Edit Character screen is the one place that changes and saves it.
    val avatar = remember(currentUid) { AvatarStore.load(context, currentUid) }

    DuudlePage(modifier = modifier) {
        DuudleScreenHeader(title = "Profile", onBack = onBack)
        HorizontalDivider(color = DuudleColors.Hairline)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            CharacterHero(config = avatar, onEditCharacter = onEditCharacter)
            Spacer(Modifier.height(14.dp))
            Text(
                "@$currentUsername",
                fontFamily = DuudleDisplayFont,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 26.sp,
                color = DuudleColors.Ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            if (joinedDate != null) {
                Spacer(Modifier.height(2.dp))
                Text("Joined $joinedDate", fontSize = 14.sp, color = DuudleColors.Muted, textAlign = TextAlign.Center)
            }
            // Friends still see the emoji avatar everywhere else; the character is local only.
            Spacer(Modifier.height(2.dp))
            Text(
                "Only you can see your character for now.",
                fontSize = 13.sp,
                color = DuudleColors.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(16.dp))

            DuudleInkPill(text = "Edit character", onClick = onEditCharacter, icon = Icons.Filled.Edit)
            Spacer(Modifier.height(28.dp))

            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                SectionTitle("Your doodle stats")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(label = "Sent", value = sentCount, accent = DuudleColors.Sky.base, modifier = Modifier.weight(1f))
                    StatTile(label = "Received", value = receivedCount, accent = DuudleColors.Mint.base, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(label = "Friends", value = friendsCount, accent = DuudleColors.Bubblegum.base, modifier = Modifier.weight(1f))
                    StatTile(label = "Saved", value = savedCount, accent = DuudleColors.Sunny.base, modifier = Modifier.weight(1f))
                }
                if (anyStatFailed) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        buildAnnotatedString {
                            append("Couldn't load your stats. Check your connection, then ")
                            withStyle(SpanStyle(color = DuudleColors.Ink, fontWeight = FontWeight.Bold)) {
                                append("tap to retry")
                            }
                            append(".")
                        },
                        fontSize = 13.sp,
                        color = DuudleColors.Muted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClickLabel = "Retry loading stats", role = Role.Button) { statsRetryCount++ }
                            .wrapContentHeight(Alignment.CenterVertically)
                    )
                }

                Spacer(Modifier.height(20.dp))
                PhoneSetupCard(
                    hasOverlayPermission = hasOverlayPermission,
                    isBatteryUnrestricted = isBatteryUnrestricted,
                    onFixOverlay = { openOverlaySettings(context) },
                    onFixBattery = { openBatterySettings(context) },
                    onTestPopUp = onOverlayDebugClick
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    "Your account lives on this phone. Uninstalling Duudle or clearing its data starts you fresh.",
                    fontSize = 13.sp,
                    color = DuudleColors.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        DuudleBottomNav(
            activeTab = DuudleNavTab.ME,
            onHomeClick = onBack,
            onFriendsClick = onFriendsClick,
            onDrawClick = onDesignDoodleClick,
            onVaultClick = onVaultClick,
            onMeClick = { /* already here */ }
        )
    }
}

// ===== CHARACTER =====

// The full-body card is portrait: capped at this height (and at a share of the screen, so
// the "Edit character" pill stays above the fold on small phones); its width follows the
// framing's aspect.
private val HeroMaxHeight = 420.dp
private const val HERO_MAX_SCREEN_FRACTION = 0.36f

/** The full-body character card. Tapping it opens the editor. */
@Composable
private fun CharacterHero(config: AvatarConfig, onEditCharacter: () -> Unit) {
    val aspect = AvatarFraming.FULL.aspect
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxHeight = min(HeroMaxHeight, screenHeight * HERO_MAX_SCREEN_FRACTION)
    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .widthIn(max = maxHeight * aspect)
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(HeroShape)
            .background(Color(config.backdrop.color))
            .border(1.dp, DuudleColors.Hairline, HeroShape)
            .clickable(onClickLabel = "Edit character", role = Role.Button, onClick = onEditCharacter)
    ) {
        DuudleCharacter(
            config = config,
            modifier = Modifier.fillMaxSize(),
            framing = AvatarFraming.FULL,
            drawBackdrop = false,
            contentDescription = "Your character: ${config.describe()}"
        )
        // Always on screen with the card, so the editor is reachable without scrolling
        // even when the "Edit character" pill below is under the bottom nav.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(DuudleColors.Paper)
                .border(1.5.dp, DuudleColors.Ink.copy(alpha = 0.2f), CircleShape)
                .clickable(role = Role.Button, onClick = onEditCharacter)
                .semantics { contentDescription = "Edit character" },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null, tint = DuudleColors.Ink, modifier = Modifier.size(22.dp))
        }
    }
}

// ===== STATS AND SETUP =====

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = DuudleColors.Ink,
        modifier = Modifier.semantics { heading() }
    )
}

@Composable
private fun StatTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    val spokenValue = when (value) {
        STAT_LOADING -> "loading"
        STAT_FAILED -> "unavailable"
        else -> value
    }
    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = "$label: $spokenValue" }
            .clip(TileShape)
            .background(DuudleColors.Paper)
            .border(1.dp, DuudleColors.Hairline, TileShape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 13.sp, color = DuudleColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            fontFamily = DuudleDisplayFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp,
            color = DuudleColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PhoneSetupCard(
    hasOverlayPermission: Boolean,
    isBatteryUnrestricted: Boolean,
    onFixOverlay: () -> Unit,
    onFixBattery: () -> Unit,
    onTestPopUp: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(DuudleColors.Paper)
            .border(1.dp, DuudleColors.Hairline, CardShape)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        SectionTitle("Make sure doodles pop up")
        Spacer(Modifier.height(2.dp))
        Text(
            "Duudle needs these so a friend's doodle can float onto your screen.",
            fontSize = 13.sp,
            color = DuudleColors.Muted
        )
        Spacer(Modifier.height(8.dp))
        SetupRow(label = "Show over other apps", isOn = hasOverlayPermission, onFix = onFixOverlay)
        SetupRow(label = "Unrestricted battery", isOn = isBatteryUnrestricted, onFix = onFixBattery)
        if (hasOverlayPermission && isBatteryUnrestricted) {
            Spacer(Modifier.height(6.dp))
            Text("All set! Doodles can pop up even when Duudle is closed.", fontSize = 13.sp, color = DuudleColors.Muted)
        } else if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
            Spacer(Modifier.height(6.dp))
            Text(
                "On Samsung, also add Duudle to Settings > Battery > Background usage limits > Never sleeping apps.",
                fontSize = 13.sp,
                color = DuudleColors.Muted
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = DuudleColors.Hairline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onTestPopUp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Test a pop-up",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = DuudleColors.Ink,
                modifier = Modifier.weight(1f)
            )
            Text(
                "›",
                fontSize = 22.sp,
                color = DuudleColors.Ink,
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
    }
}

@Composable
private fun SetupRow(label: String, isOn: Boolean, onFix: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SetupStatusIcon(isOn = isOn)
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 15.sp, color = DuudleColors.Ink, modifier = Modifier.weight(1f))
        if (isOn) {
            Text("On", color = GreenOnText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        } else {
            // The compact pill is the clickable (so the ripple matches it); the minimum
            // interactive size keeps the touch target at 48dp around it.
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(PillShape)
                    .background(DuudleColors.Ink)
                    .clickable(role = Role.Button, onClick = onFix)
                    .semantics { contentDescription = "Fix: $label" }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Fix", color = DuudleColors.Paper, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

/** On: an Ink circle with a tick. Off: an ErrorRed ring with a dot. Decorative (the row text says it). */
@Composable
private fun SetupStatusIcon(isOn: Boolean) {
    Spacer(
        modifier = Modifier
            .size(22.dp)
            .drawWithCache {
                val strokeWidth = 2.dp.toPx()
                val ring = Stroke(width = strokeWidth)
                val r = size.minDimension / 2f - strokeWidth / 2f
                val tick = Path().apply {
                    moveTo(size.width * 0.29f, size.height * 0.52f)
                    lineTo(size.width * 0.44f, size.height * 0.66f)
                    lineTo(size.width * 0.72f, size.height * 0.37f)
                }
                val tickStroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val dotRadius = size.minDimension * 0.14f
                onDrawBehind {
                    if (isOn) {
                        drawCircle(DuudleColors.Ink, radius = r, style = ring)
                        drawPath(tick, DuudleColors.Ink, style = tickStroke)
                    } else {
                        drawCircle(DuudleColors.ErrorRed, radius = r, style = ring)
                        drawCircle(DuudleColors.ErrorRed, radius = dotRadius)
                    }
                }
            }
    )
}

private fun readBatteryUnrestricted(context: Context): Boolean =
    (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .isIgnoringBatteryOptimizations(context.packageName)

private fun openOverlaySettings(context: Context) {
    openSettings(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
}

private fun openBatterySettings(context: Context) {
    openSettings(
        context,
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    )
}

/** Some phones don't expose these screens; fall back to the app's own settings page. */
private fun openSettings(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.e("DuudleDebug", "Settings screen ${intent.action} missing: ${e.message}")
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            )
        } catch (e2: ActivityNotFoundException) {
            Log.e("DuudleDebug", "App details settings missing too: ${e2.message}")
            Toast.makeText(context, "Open Settings > Apps > Duudle", Toast.LENGTH_LONG).show()
        }
    }
}

// ===== STATS (COUNT aggregations where possible, so no documents are downloaded) =====

private fun countDocs(query: Query, what: String, onResult: (Long?) -> Unit) {
    query.count().get(AggregateSource.SERVER)
        .addOnSuccessListener { snapshot -> onResult(snapshot.count) }
        .addOnFailureListener { e ->
            Log.e("DuudleDebug", "Profile $what count failed: ${e.message}")
            onResult(null)
        }
}

private fun countSentDoodles(uid: String, onResult: (Long?) -> Unit) {
    val query = FirebaseFirestore.getInstance().collection("doodles").whereEqualTo("senderId", uid)
    countDocs(query, "sent", onResult)
}

private fun countReceivedDoodles(uid: String, onResult: (Long?) -> Unit) {
    val query = FirebaseFirestore.getInstance().collection("doodles").whereEqualTo("receiverId", uid)
    countDocs(query, "received", onResult)
}

/**
 * Not a count(): crossed requests can leave duplicate friendship docs for the same pair,
 * so fetch both sides (userA/userB) and count distinct friend uids, like FriendsScreen does.
 */
private fun countFriends(uid: String, onResult: (Long?) -> Unit) {
    val friendships = FirebaseFirestore.getInstance().collection("friendships")
    friendships.whereEqualTo("userA", uid).get()
        .addOnSuccessListener { asUserA ->
            friendships.whereEqualTo("userB", uid).get()
                .addOnSuccessListener { asUserB ->
                    val friendUids = asUserA.documents.mapNotNull { it.getString("userB") } +
                        asUserB.documents.mapNotNull { it.getString("userA") }
                    onResult(friendUids.filter { it.isNotBlank() }.distinct().size.toLong())
                }
                .addOnFailureListener { e ->
                    Log.e("DuudleDebug", "Profile friends (userB) fetch failed: ${e.message}")
                    onResult(null)
                }
        }
        .addOnFailureListener { e ->
            Log.e("DuudleDebug", "Profile friends (userA) fetch failed: ${e.message}")
            onResult(null)
        }
}

private fun countSavedDoodles(uid: String, onResult: (Long?) -> Unit) {
    val favorites = FirebaseFirestore.getInstance().collection("users").document(uid).collection("favorites")
    countDocs(favorites, "saved", onResult)
}
