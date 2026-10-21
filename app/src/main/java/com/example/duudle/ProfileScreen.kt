package com.example.duudle

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LavenderBackground = Color(0xFFEDE3FA)
private val IndigoPrimary = Color(0xFF5B3FD9)
private val PinkAction = Color(0xFFE8637E)
// Darker than the app's #3DBE64 so "On" text passes contrast on white.
private val GreenOnText = Color(0xFF1E7E3E)

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
    onOverlayDebugClick: () -> Unit = {}
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
    val versionName = remember { appVersionName(context) }

    Column(modifier = modifier.fillMaxSize().background(LavenderBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Back", color = IndigoPrimary, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.weight(1f))
            Text("Profile", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(64.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ProfileHeaderCard(uid = currentUid, username = currentUsername, joinedDate = joinedDate)

            Text("Your doodle stats 📊", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(emoji = "📤", label = "Sent", value = sentCount, modifier = Modifier.weight(1f))
                StatCard(emoji = "📥", label = "Received", value = receivedCount, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(emoji = "👥", label = "Friends", value = friendsCount, modifier = Modifier.weight(1f))
                StatCard(emoji = "⭐", label = "Saved", value = savedCount, modifier = Modifier.weight(1f))
            }
            if (anyStatFailed) {
                Text(
                    buildAnnotatedString {
                        append("Couldn't load your stats. Check your connection, then ")
                        withStyle(SpanStyle(color = IndigoPrimary, fontWeight = FontWeight.Bold)) {
                            append("tap to retry")
                        }
                        append(".")
                    },
                    fontSize = 13.sp,
                    color = Color.DarkGray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = "Retry loading stats", role = Role.Button) { statsRetryCount++ }
                        .wrapContentHeight(Alignment.CenterVertically)
                )
            }

            PhoneSetupCard(
                hasOverlayPermission = hasOverlayPermission,
                isBatteryUnrestricted = isBatteryUnrestricted,
                onFixOverlay = { openOverlaySettings(context) },
                onFixBattery = { openBatterySettings(context) }
            )

            AboutCard(versionName = versionName, onOverlayDebugClick = onOverlayDebugClick)

            Spacer(Modifier.height(8.dp))
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

@Composable
private fun ProfileHeaderCard(uid: String, username: String, joinedDate: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(IndigoPrimary)
            .padding(vertical = 24.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(96.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Text(emojiForUid(uid), fontSize = 52.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text("@$username", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        if (joinedDate != null) {
            Spacer(Modifier.height(4.dp))
            Text("Joined $joinedDate", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun StatCard(emoji: String, label: String, value: String, modifier: Modifier = Modifier) {
    val spokenValue = when (value) {
        STAT_LOADING -> "loading"
        STAT_FAILED -> "unavailable"
        else -> value
    }
    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) { contentDescription = "$label: $spokenValue" }
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(14.dp)
    ) {
        Text("$emoji $label", fontSize = 13.sp, color = Color.DarkGray)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = IndigoPrimary)
    }
}

@Composable
private fun PhoneSetupCard(
    hasOverlayPermission: Boolean,
    isBatteryUnrestricted: Boolean,
    onFixOverlay: () -> Unit,
    onFixBattery: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(16.dp)
    ) {
        Text("Make sure doodles pop up 🔔", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            "Duudle needs these so a friend's doodle can float onto your screen.",
            fontSize = 13.sp,
            color = Color.DarkGray
        )
        Spacer(Modifier.height(8.dp))
        SetupRow(label = "Show over other apps", isOn = hasOverlayPermission, onFix = onFixOverlay)
        SetupRow(label = "Unrestricted battery", isOn = isBatteryUnrestricted, onFix = onFixBattery)
        if (hasOverlayPermission && isBatteryUnrestricted) {
            Spacer(Modifier.height(6.dp))
            Text("🎉 All set! Doodles can pop up even when Duudle is closed.", fontSize = 13.sp, color = Color.DarkGray)
        } else if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
            Spacer(Modifier.height(6.dp))
            Text(
                "📱 On Samsung, also add Duudle to Settings > Battery > Background usage limits > Never sleeping apps.",
                fontSize = 13.sp,
                color = Color.DarkGray
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
        Text(if (isOn) "✅" else "❌", fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (isOn) {
            Text("On", color = GreenOnText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        } else {
            Button(
                onClick = onFix,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Fix: $label" },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = PinkAction, contentColor = Color.White)
            ) {
                Text("Fix", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AboutCard(versionName: String, onOverlayDebugClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(16.dp)
    ) {
        Text("About Duudle ℹ️", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Version", fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(versionName, fontSize = 15.sp, color = Color.DarkGray)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOverlayDebugClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🔧 Overlay Debug Tools", fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("›", fontSize = 20.sp, color = IndigoPrimary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "📱 Your account lives on this phone. If you uninstall Duudle or clear its data, you'll start " +
                "fresh with a new username and need to re-add your friends.",
            fontSize = 13.sp,
            color = Color.DarkGray
        )
    }
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

private fun appVersionName(context: Context): String {
    val info = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
    } catch (e: PackageManager.NameNotFoundException) {
        Log.e("DuudleDebug", "Couldn't read app version: ${e.message}")
        null
    }
    return info?.versionName ?: "?"
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
