package com.example.duudle

import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay

data class Friend(val uid: String, val username: String, val avatarEmoji: String, val muted: Boolean = false)
data class FriendRequest(val docId: String, val fromUid: String, val fromUsername: String)

enum class RelationshipStatus { NONE, ALREADY_FRIENDS, REQUEST_ALREADY_SENT }

private val LavenderBackground = Color(0xFFEDE3FA)
private val CardBackground = Color(0xFFFFFFFF)
private val IndigoPrimary = Color(0xFF5B3FD9)
private val PinkAction = Color(0xFFE8637E)
private val GreenOnline = Color(0xFF3DBE64)
private val MutedGray = Color(0xFF9E9E9E)
private val avatarEmojis = listOf("🦊", "🐼", "🐨", "🐸", "🐶", "🐱", "🦁", "🐯", "🐰", "🐻")

fun emojiForUid(uid: String): String {
    val index = kotlin.math.abs(uid.hashCode()) % avatarEmojis.size
    return avatarEmojis[index]
}

private enum class FriendsTab { FRIENDS, ADD, REQUESTS }

@Composable
fun FriendsScreen(
    modifier: Modifier = Modifier,
    currentUid: String,
    currentUsername: String,
    onBack: () -> Unit = {},
    onSelectFriend: (Friend) -> Unit = {},
    onOverlayDebugClick: () -> Unit = {},
    onDesignDoodleClick: () -> Unit = {},
    onVaultClick: () -> Unit = {},
    onMeClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(FriendsTab.FRIENDS) }
    var searchQuery by remember { mutableStateOf("") }
    val overlayGranted = remember { Settings.canDrawOverlays(context) }

    var friendUidsAsUserA by remember { mutableStateOf<List<String>>(emptyList()) }
    var friendUidsAsUserB by remember { mutableStateOf<List<String>>(emptyList()) }
    var friends by remember { mutableStateOf<List<Friend>>(emptyList()) }
    var requests by remember { mutableStateOf<List<FriendRequest>>(emptyList()) }
    var mutedUids by remember { mutableStateOf<Set<String>>(emptySet()) }

    DisposableEffect(currentUid) {
        val db = FirebaseFirestore.getInstance()

        // Mutes live in Firestore so the Cloud Function can skip the push.
        // Owner-only rules keep a muted friend from ever seeing this list.
        val mutesListener = db.collection("users").document(currentUid).collection("mutes")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("DuudleDebug", "Mutes listener failed: ${error.message}")
                    return@addSnapshotListener
                }
                mutedUids = snapshot?.documents?.map { it.id }?.toSet() ?: emptySet()
            }

        val listenerA = db.collection("friendships").whereEqualTo("userA", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("DuudleDebug", "Friendships A failed: ${error.message}")
                    return@addSnapshotListener
                }
                friendUidsAsUserA = snapshot?.documents?.mapNotNull { it.getString("userB") } ?: emptyList()
            }

        val listenerB = db.collection("friendships").whereEqualTo("userB", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("DuudleDebug", "Friendships B failed: ${error.message}")
                    return@addSnapshotListener
                }
                friendUidsAsUserB = snapshot?.documents?.mapNotNull { it.getString("userA") } ?: emptyList()
            }

        val requestsListener = db.collection("friend_requests")
            .whereEqualTo("toUid", currentUid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("DuudleDebug", "Requests listener failed: ${error.message}")
                    return@addSnapshotListener
                }
                requests = snapshot?.documents?.map { doc ->
                    FriendRequest(
                        docId = doc.id,
                        fromUid = doc.getString("fromUid") ?: "",
                        fromUsername = doc.getString("fromUsername") ?: "Unknown"
                    )
                } ?: emptyList()
            }

        onDispose {
            listenerA.remove()
            listenerB.remove()
            requestsListener.remove()
            mutesListener.remove()
        }
    }

    LaunchedEffect(friendUidsAsUserA, friendUidsAsUserB) {
        val allUids = (friendUidsAsUserA + friendUidsAsUserB).filter { it.isNotBlank() }.distinct()
        if (allUids.isEmpty()) {
            friends = emptyList()
            return@LaunchedEffect
        }
        FirebaseFirestore.getInstance().collection("users")
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
            }
            .addOnFailureListener { e ->
                android.util.Log.e("DuudleDebug", "Friend username lookup failed: ${e.message}")
            }
    }

    Column(modifier = modifier.fillMaxSize().background(LavenderBackground)) {
        FriendsHeader(onBack = onBack, friendsCount = friends.size)

        Spacer(Modifier.height(12.dp))
        FriendsTabBar(
            selectedTab = selectedTab,
            friendsCount = friends.size,
            requestsCount = requests.size,
            onSelect = { selectedTab = it }
        )

        Spacer(Modifier.height(14.dp))
        OverlayStatusBanner(overlayGranted = overlayGranted, onSettingsClick = onOverlayDebugClick)

        if (selectedTab == FriendsTab.FRIENDS) {
            Spacer(Modifier.height(14.dp))
            SearchBar(query = searchQuery, onQueryChange = { searchQuery = it })
        }

        Spacer(Modifier.height(14.dp))
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                FriendsTab.FRIENDS -> FriendsList(
                    friends = friends.map { it.copy(muted = it.uid in mutedUids) },
                    searchQuery = searchQuery,
                    onToggleMute = { target ->
                        setFriendMuted(currentUid, target.uid, muted = !target.muted) { error ->
                            Toast.makeText(context, "Couldn't update mute: $error", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSelectFriend = onSelectFriend
                )
                FriendsTab.ADD -> AddFriendTab(
                    currentUid = currentUid,
                    currentUsername = currentUsername,
                    friendUids = friends.map { it.uid }.toSet()
                )
                FriendsTab.REQUESTS -> RequestsTab(
                    requests = requests,
                    onAccept = { req -> acceptFriendRequest(req.docId, currentUid, req.fromUid) { } },
                    onDecline = { req -> declineFriendRequest(req.docId) }
                )
            }
        }

        DuudleBottomNav(
            activeTab = DuudleNavTab.FRIENDS,
            onHomeClick = onBack,
            onFriendsClick = { /* already here */ },
            onDrawClick = onDesignDoodleClick,
            onVaultClick = onVaultClick,
            onMeClick = onMeClick
        )
    }
}

@Composable
private fun FriendsHeader(onBack: () -> Unit, friendsCount: Int) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back", color = IndigoPrimary, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.weight(1f))
            Text("Friends", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { Toast.makeText(context, "QR code invites are coming soon!", Toast.LENGTH_SHORT).show() },
                contentAlignment = Alignment.Center
            ) { Text("▦", fontSize = 16.sp, color = IndigoPrimary) }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(IndigoPrimary))
            Spacer(Modifier.width(6.dp))
            Text("$friendsCount Friends", fontSize = 13.sp, color = IndigoPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FriendsTabBar(
    selectedTab: FriendsTab,
    friendsCount: Int,
    requestsCount: Int,
    onSelect: (FriendsTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(Color.White, RoundedCornerShape(50))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TabPill(label = "My Friends", count = friendsCount, selected = selectedTab == FriendsTab.FRIENDS, modifier = Modifier.weight(1f)) {
            onSelect(FriendsTab.FRIENDS)
        }
        TabPill(label = "Add Friends +", selected = selectedTab == FriendsTab.ADD, modifier = Modifier.weight(1f)) {
            onSelect(FriendsTab.ADD)
        }
        TabPill(label = "Requests", count = requestsCount, countColor = PinkAction, selected = selectedTab == FriendsTab.REQUESTS, modifier = Modifier.weight(1f)) {
            onSelect(FriendsTab.REQUESTS)
        }
    }
}

@Composable
private fun TabPill(
    label: String,
    count: Int? = null,
    countColor: Color = IndigoPrimary,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) IndigoPrimary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = if (selected) Color.White else Color.DarkGray,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 12.sp
        )
        if (count != null) {
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) Color.White.copy(alpha = 0.3f) else countColor)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(count.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun OverlayStatusBanner(overlayGranted: Boolean, onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(IndigoPrimary)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) { Text("✨", fontSize = 18.sp) }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (overlayGranted) "Overlay Active" else "Overlay Off", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.width(6.dp))
                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(if (overlayGranted) GreenOnline else PinkAction))
            }
            Text("Incoming doodles pop directly on your screen", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
                .clickable(onClick = onSettingsClick),
            contentAlignment = Alignment.Center
        ) { Text("⚙️", fontSize = 14.sp) }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(Color.White, RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🔍", fontSize = 16.sp, color = IndigoPrimary)
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = Color.Black)
            )
            if (query.isEmpty()) {
                Text("Search besties or @handle...", fontSize = 14.sp, color = Color.Gray)
            }
        }
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape)
                .clickable { Toast.makeText(context, "Filters are coming soon!", Toast.LENGTH_SHORT).show() },
            contentAlignment = Alignment.Center
        ) { Text("⚙️", fontSize = 14.sp) }
    }
}

@Composable
private fun FriendsList(
    friends: List<Friend>,
    searchQuery: String,
    onToggleMute: (Friend) -> Unit,
    onSelectFriend: (Friend) -> Unit
) {
    val filtered = remember(friends, searchQuery) {
        if (searchQuery.isBlank()) friends
        else friends.filter { it.username.contains(searchQuery, ignoreCase = true) }
    }

    if (friends.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No friends yet — add one from the Add Friends tab", color = Color.DarkGray)
        }
        return
    }
    if (filtered.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No friends match \"$searchQuery\"", color = Color.DarkGray)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(filtered, key = { it.uid }) { friend ->
            FriendCard(friend = friend, onToggleMute = onToggleMute, onSelectFriend = onSelectFriend)
        }
        item { InviteBanner() }
    }
}

@Composable
private fun FriendCard(friend: Friend, onToggleMute: (Friend) -> Unit, onSelectFriend: (Friend) -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().background(CardBackground, RoundedCornerShape(20.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(LavenderBackground),
                contentAlignment = Alignment.Center
            ) { Text(friend.avatarEmoji, fontSize = 24.sp) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(friend.username, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Presence coming soon", fontSize = 12.sp, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(
                    checked = !friend.muted,
                    onCheckedChange = { onToggleMute(friend) },
                    colors = SwitchDefaults.colors(checkedTrackColor = IndigoPrimary)
                )
                Text(
                    if (friend.muted) "MUTED" else "FLOAT ON",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (friend.muted) MutedGray else IndigoPrimary
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (friend.muted) "Notifications paused for now" else "Tap below to send a doodle",
                fontSize = 12.sp,
                color = if (friend.muted) Color.Gray else Color(0xFF2E7D32)
            )
            Text("🔥 Streaks: coming soon", fontSize = 11.sp, color = Color.Gray)
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (friend.muted) {
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(LavenderBackground)
                        .clickable { onToggleMute(friend) }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("🔊 Unmute Floating", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.DarkGray) }
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(LavenderBackground)
                        .clickable { Toast.makeText(context, "Notes are coming soon!", Toast.LENGTH_SHORT).show() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("📝 Note", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray) }
            } else {
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(PinkAction)
                        .clickable { onSelectFriend(friend) }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("✏️ Send Doodle", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(LavenderBackground)
                        .clickable { Toast.makeText(context, "Peep Canvas is coming soon!", Toast.LENGTH_SHORT).show() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("👀 Peep Canvas", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray) }
            }
        }
    }
}

@Composable
private fun InviteBanner() {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(IndigoPrimary, PinkAction)), RoundedCornerShape(18.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Need more doodle pals?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Invite links are coming soon — use Add Friends for now", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .clickable { Toast.makeText(context, "Invite links are coming soon!", Toast.LENGTH_SHORT).show() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("Share Link 🔗", color = IndigoPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

data class UserSearchResult(val uid: String, val username: String)

private enum class AddState { IDLE, WORKING, SENT, ALREADY_SENT, ALREADY_FRIENDS, FAILED }

@Composable
private fun AddFriendTab(currentUid: String, currentUsername: String, friendUids: Set<String>) {
    var searchText by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<UserSearchResult>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var addStates by remember { mutableStateOf<Map<String, AddState>>(emptyMap()) }
    val query = usernameKey(searchText)

    // Search as you type, debounced so each keystroke doesn't hit Firestore.
    LaunchedEffect(query) {
        errorMessage = null
        if (query.isEmpty()) {
            results = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(300)
        searchUsersByPrefix(query) { found, error ->
            if (usernameKey(searchText) != query) return@searchUsersByPrefix // a newer search is running
            isSearching = false
            errorMessage = error?.let { "Search failed: $it" }
            results = found.filter { it.uid != currentUid && it.uid !in friendUids }
        }
    }

    fun setState(uid: String, state: AddState) {
        addStates = addStates + (uid to state)
    }

    fun addFriend(user: UserSearchResult) {
        setState(user.uid, AddState.WORKING)
        checkExistingRelationship(currentUid, user.uid) { status ->
            when (status) {
                RelationshipStatus.ALREADY_FRIENDS -> setState(user.uid, AddState.ALREADY_FRIENDS)
                RelationshipStatus.REQUEST_ALREADY_SENT -> setState(user.uid, AddState.ALREADY_SENT)
                RelationshipStatus.NONE -> sendFriendRequest(
                    fromUid = currentUid,
                    fromUsername = currentUsername,
                    toUid = user.uid,
                    toUsername = user.username
                ) { success, error ->
                    setState(user.uid, if (success) AddState.SENT else AddState.FAILED)
                    if (!success) errorMessage = "Couldn't send request: $error"
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Text("Add a friend by username", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Start typing, e.g. maya") },
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))

        val hint = when {
            errorMessage != null -> errorMessage
            query.isEmpty() -> null
            isSearching -> "Searching..."
            results.isEmpty() -> "No one new matching \"${searchText.trim()}\""
            else -> null
        }
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray) }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(results, key = { it.uid }) { user ->
                SearchResultRow(user = user, state = addStates[user.uid] ?: AddState.IDLE, onAdd = { addFriend(user) })
            }
        }
    }
}

@Composable
private fun SearchResultRow(user: UserSearchResult, state: AddState, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(CardBackground, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(LavenderBackground),
            contentAlignment = Alignment.Center
        ) { Text(emojiForUid(user.uid), fontSize = 20.sp) }
        Spacer(Modifier.width(12.dp))
        Text("@${user.username}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        when (state) {
            AddState.IDLE, AddState.FAILED -> Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = PinkAction, contentColor = Color.White)
            ) { Text(if (state == AddState.FAILED) "Retry" else "Add") }
            AddState.WORKING -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            AddState.SENT -> Text("Requested ✓", color = GreenOnline, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            AddState.ALREADY_SENT -> Text("Pending", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            AddState.ALREADY_FRIENDS -> Text("Friends", color = IndigoPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

/** Case-insensitive prefix search on usernameLower. A single-field range needs no composite index. */
fun searchUsersByPrefix(prefixLower: String, onResult: (List<UserSearchResult>, String?) -> Unit) {
    FirebaseFirestore.getInstance().collection("users")
        .whereGreaterThanOrEqualTo("usernameLower", prefixLower)
        .whereLessThanOrEqualTo("usernameLower", prefixLower + "")
        .limit(20)
        .get()
        .addOnSuccessListener { snapshot ->
            onResult(snapshot.documents.map { UserSearchResult(it.id, it.getString("username") ?: "unknown") }, null)
        }
        .addOnFailureListener { e -> onResult(emptyList(), e.message) }
}

/** Writes or clears users/{myUid}/mutes/{friendUid}; the snapshot listener updates the UI. */
fun setFriendMuted(myUid: String, friendUid: String, muted: Boolean, onError: (String?) -> Unit) {
    val muteRef = FirebaseFirestore.getInstance()
        .collection("users").document(myUid)
        .collection("mutes").document(friendUid)
    val task = if (muted) muteRef.set(mapOf("mutedAt" to System.currentTimeMillis())) else muteRef.delete()
    task.addOnFailureListener { e ->
        android.util.Log.e("DuudleDebug", "Mute update failed: ${e.message}")
        onError(e.message)
    }
}

fun checkExistingRelationship(currentUid: String, otherUid: String, onResult: (RelationshipStatus) -> Unit) {
    val db = FirebaseFirestore.getInstance()

    db.collection("friendships").whereEqualTo("userA", currentUid).whereEqualTo("userB", otherUid).get()
        .addOnSuccessListener { snap1 ->
            if (snap1.documents.isNotEmpty()) {
                onResult(RelationshipStatus.ALREADY_FRIENDS)
                return@addOnSuccessListener
            }
            db.collection("friendships").whereEqualTo("userA", otherUid).whereEqualTo("userB", currentUid).get()
                .addOnSuccessListener { snap2 ->
                    if (snap2.documents.isNotEmpty()) {
                        onResult(RelationshipStatus.ALREADY_FRIENDS)
                        return@addOnSuccessListener
                    }
                    db.collection("friend_requests")
                        .whereEqualTo("fromUid", currentUid)
                        .whereEqualTo("toUid", otherUid)
                        .whereEqualTo("status", "pending")
                        .get()
                        .addOnSuccessListener { snap3 ->
                            onResult(if (snap3.documents.isNotEmpty()) RelationshipStatus.REQUEST_ALREADY_SENT else RelationshipStatus.NONE)
                        }
                        .addOnFailureListener { onResult(RelationshipStatus.NONE) }
                }
                .addOnFailureListener { onResult(RelationshipStatus.NONE) }
        }
        .addOnFailureListener { onResult(RelationshipStatus.NONE) }
}

fun sendFriendRequest(
    fromUid: String,
    fromUsername: String,
    toUid: String,
    toUsername: String,
    onResult: (Boolean, String?) -> Unit
) {
    if (fromUid.isBlank() || fromUsername.isBlank()) {
        onResult(false, "Your account isn't fully set up — try restarting the app")
        return
    }
    val requestData = hashMapOf(
        "fromUid" to fromUid,
        "fromUsername" to fromUsername,
        "toUid" to toUid,
        "toUsername" to toUsername,
        "status" to "pending",
        "timestamp" to System.currentTimeMillis()
    )
    FirebaseFirestore.getInstance().collection("friend_requests")
        .add(requestData)
        .addOnSuccessListener { onResult(true, null) }
        .addOnFailureListener { e -> onResult(false, e.message) }
}

fun acceptFriendRequest(requestDocId: String, myUid: String, friendUid: String, onDone: (Boolean) -> Unit) {
    if (myUid.isBlank() || friendUid.isBlank()) {
        onDone(false)
        return
    }
    val db = FirebaseFirestore.getInstance()
    val friendshipData = hashMapOf(
        "userA" to myUid,
        "userB" to friendUid,
        "createdAt" to System.currentTimeMillis()
    )
    db.collection("friendships").add(friendshipData)
        .addOnSuccessListener {
            db.collection("friend_requests").document(requestDocId).delete()
            onDone(true)
        }
        .addOnFailureListener { onDone(false) }
}

fun declineFriendRequest(requestDocId: String) {
    FirebaseFirestore.getInstance().collection("friend_requests").document(requestDocId).delete()
}

@Composable
private fun RequestsTab(
    requests: List<FriendRequest>,
    onAccept: (FriendRequest) -> Unit,
    onDecline: (FriendRequest) -> Unit
) {
    if (requests.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No pending requests", color = Color.DarkGray) }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(requests, key = { it.docId }) { req ->
            Row(
                modifier = Modifier.fillMaxWidth().background(CardBackground, RoundedCornerShape(18.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(LavenderBackground),
                    contentAlignment = Alignment.Center
                ) { Text(emojiForUid(req.fromUid), fontSize = 20.sp) }
                Spacer(Modifier.width(12.dp))
                Text(req.fromUsername, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(IndigoPrimary)
                        .clickable { onAccept(req) }.padding(horizontal = 14.dp, vertical = 8.dp)
                ) { Text("Accept", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { onDecline(req) }) { Text("Decline", color = Color.Gray) }
            }
        }
    }
}