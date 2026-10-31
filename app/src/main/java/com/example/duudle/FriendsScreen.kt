package com.example.duudle

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.duudle.ui.theme.DuudleColors
import com.example.duudle.ui.theme.DuudleDisplayFont
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay

data class Friend(val uid: String, val username: String, val avatarEmoji: String, val muted: Boolean = false)
data class FriendRequest(val docId: String, val fromUid: String, val fromUsername: String)

enum class RelationshipStatus { NONE, ALREADY_FRIENDS, REQUEST_ALREADY_SENT }

private val CardShape = RoundedCornerShape(22.dp)
private val FieldShape = RoundedCornerShape(14.dp)
// A warm grey that still reads on the cream page (PillGrey all but vanishes on Cream).
private val SearchFieldGrey = Color(0xFFEBE8E3)
// Placeholder text on SearchFieldGrey: Ink at 65% clears 4.5:1 (Muted was ~4.1:1).
private val PlaceholderInk = DuudleColors.Ink.copy(alpha = 0.65f)
private val FriendsTabLabels = listOf("Friends", "Add", "Requests")
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
    onMeClick: () -> Unit = {},
    onChatClick: (friendUid: String, friendUsername: String) -> Unit = { _, _ -> }
) {
    // onOverlayDebugClick is unused since the overlay banner left this screen (the status
    // now lives on Home and Profile); kept so MainActivity's call site doesn't change.
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(FriendsTab.FRIENDS) }
    var searchQuery by remember { mutableStateOf("") }
    val gaze = rememberFaceGaze()
    // The window no longer pans for the keyboard (adjustResize), so lift the content above
    // it ourselves and tuck the floating nav away while typing. Derived, so the screen only
    // recomposes when the keyboard shows or hides, not on every frame of its animation.
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val imeVisible by remember(imeInsets, density) { derivedStateOf { imeInsets.getBottom(density) > 0 } }

    var friendUidsAsUserA by remember { mutableStateOf<List<String>>(emptyList()) }
    var friendUidsAsUserB by remember { mutableStateOf<List<String>>(emptyList()) }
    var friends by remember { mutableStateOf<List<Friend>>(emptyList()) }
    var requests by remember { mutableStateOf<List<FriendRequest>>(emptyList()) }
    var mutedUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Friends-tab loading: both friendship listeners have answered and the username lookup
    // has finished (or there was nobody to look up). Until then the tab shows a spinner.
    var friendshipsAReported by remember { mutableStateOf(false) }
    var friendshipsBReported by remember { mutableStateOf(false) }
    var friendsLoaded by remember { mutableStateOf(false) }
    var friendsLoadFailed by remember { mutableStateOf(false) }
    val safety = rememberSafetyActions()
    val blocked by Blocks.state.collectAsState()
    // Blocking deletes pending requests too; this hides one until the listener catches up.
    val visibleRequests = remember(requests, blocked) { requests.filter { it.fromUid !in blocked } }

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
                    friendsLoadFailed = true
                    friendshipsAReported = true
                    return@addSnapshotListener
                }
                friendUidsAsUserA = snapshot?.documents?.mapNotNull { it.getString("userB") } ?: emptyList()
                friendshipsAReported = true
            }

        val listenerB = db.collection("friendships").whereEqualTo("userB", currentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("DuudleDebug", "Friendships B failed: ${error.message}")
                    friendsLoadFailed = true
                    friendshipsBReported = true
                    return@addSnapshotListener
                }
                friendUidsAsUserB = snapshot?.documents?.mapNotNull { it.getString("userA") } ?: emptyList()
                friendshipsBReported = true
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

    // Friends whose chat has a message I haven't read yet: a red dot on their Chat button.
    var unreadChatUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    DisposableEffect(currentUid) {
        if (currentUid.isBlank()) return@DisposableEffect onDispose { }
        val chatsListener = ChatRepository.listenToMyChats(
            currentUid,
            onUpdate = { chats ->
                unreadChatUids = chats.filter { it.hasUnreadFor(currentUid) }
                    .mapNotNull { it.otherMember(currentUid) }
                    .toSet()
            },
            onError = { }
        )
        onDispose { chatsListener.remove() }
    }

    LaunchedEffect(friendUidsAsUserA, friendUidsAsUserB, friendshipsAReported, friendshipsBReported) {
        // Wait for both sides, or someone whose friendships are all on one side would
        // briefly look friendless.
        if (!friendshipsAReported || !friendshipsBReported) return@LaunchedEffect
        val allUids = (friendUidsAsUserA + friendUidsAsUserB).filter { it.isNotBlank() }.distinct()
        if (allUids.isEmpty()) {
            friends = emptyList()
            friendsLoaded = true
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
                friendsLoadFailed = false
                friendsLoaded = true
            }
            .addOnFailureListener { e ->
                android.util.Log.e("DuudleDebug", "Friend username lookup failed: ${e.message}")
                friendsLoadFailed = true
                friendsLoaded = true
            }
    }

    // Rebuilt only when their inputs change, not on every recomposition.
    val friendsWithMute = remember(friends, mutedUids) { friends.map { it.copy(muted = it.uid in mutedUids) } }
    val friendUids = remember(friends) { friends.map { it.uid }.toSet() }

    DuudlePage(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
            .faceGazeSource(gaze)
    ) {
        DuudleScreenHeader(title = "Friends", onBack = onBack)
        HorizontalDivider(color = DuudleColors.Hairline)

        Spacer(Modifier.height(14.dp))
        DuudleSegmentedControl(
            options = FriendsTabLabels,
            selectedIndex = selectedTab.ordinal,
            onSelect = { selectedTab = FriendsTab.entries[it] },
            badges = listOf(0, 0, visibleRequests.size),
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(14.dp))
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                FriendsTab.FRIENDS -> FriendsTabContent(
                    friends = friendsWithMute,
                    loaded = friendsLoaded,
                    loadFailed = friendsLoadFailed,
                    searchQuery = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onToggleMute = { target ->
                        setFriendMuted(currentUid, target.uid, muted = !target.muted) { error ->
                            Toast.makeText(context, "Couldn't update mute: $error", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSelectFriend = onSelectFriend,
                    unreadChatUids = unreadChatUids,
                    onChatClick = { friend -> onChatClick(friend.uid, friend.username) },
                    onAddFriend = { selectedTab = FriendsTab.ADD },
                    safety = safety,
                    gaze = gaze
                )
                FriendsTab.ADD -> AddFriendTab(
                    currentUid = currentUid,
                    currentUsername = currentUsername,
                    friendUids = friendUids,
                    blockedUids = blocked.keys
                )
                FriendsTab.REQUESTS -> RequestsTab(
                    requests = visibleRequests,
                    safety = safety,
                    onAccept = { req -> acceptFriendRequest(req.docId, currentUid, req.fromUid) { } },
                    onDecline = { req -> declineFriendRequest(req.docId) },
                    gaze = gaze
                )
            }
        }

        AnimatedVisibility(
            visible = !imeVisible,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
        ) {
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
    SafetyDialogs(safety, currentUid)
}

/** Grey rounded search field with a magnifier, used on the Friends and Add tabs. */
@Composable
private fun GreySearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = 16.sp, color = DuudleColors.Ink),
        cursorBrush = SolidColor(DuudleColors.Ink),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(FieldShape)
                    .background(SearchFieldGrey)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = PlaceholderInk, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(placeholder, fontSize = 16.sp, color = PlaceholderInk, maxLines = 1)
                    }
                    innerTextField()
                }
            }
        }
    )
}

/** Friends tab: spinner while loading, then the empty state, an error line, or search + list. */
@Composable
private fun FriendsTabContent(
    friends: List<Friend>,
    loaded: Boolean,
    loadFailed: Boolean,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onToggleMute: (Friend) -> Unit,
    onSelectFriend: (Friend) -> Unit,
    unreadChatUids: Set<String>,
    onChatClick: (Friend) -> Unit,
    onAddFriend: () -> Unit,
    safety: SafetyActions,
    gaze: FaceGaze
) {
    when {
        !loaded -> Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
                    .size(28.dp),
                color = DuudleColors.Ink,
                strokeWidth = 2.5.dp
            )
        }
        friends.isEmpty() && loadFailed -> Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Text("Couldn't load your friends.", color = DuudleColors.ErrorRed, textAlign = TextAlign.Center)
        }
        friends.isEmpty() -> FriendsEmptyState(gaze = gaze, onAddFriend = onAddFriend)
        else -> Column(modifier = Modifier.fillMaxSize()) {
            GreySearchField(
                value = searchQuery,
                onValueChange = onQueryChange,
                placeholder = "Search your friends",
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(12.dp))
            FriendsList(
                friends = friends,
                searchQuery = searchQuery,
                onToggleMute = onToggleMute,
                onSelectFriend = onSelectFriend,
                unreadChatUids = unreadChatUids,
                onChatClick = onChatClick,
                safety = safety,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FriendsList(
    friends: List<Friend>,
    searchQuery: String,
    onToggleMute: (Friend) -> Unit,
    onSelectFriend: (Friend) -> Unit,
    unreadChatUids: Set<String>,
    onChatClick: (Friend) -> Unit,
    safety: SafetyActions,
    modifier: Modifier = Modifier
) {
    // Typing "@maya" finds maya too.
    val needle = searchQuery.trim().removePrefix("@")
    val filtered = remember(friends, needle) {
        if (needle.isBlank()) friends
        else friends.filter { it.username.contains(needle, ignoreCase = true) }
    }
    // A new query starts from the top, so the first match isn't left half off-screen.
    val listState = rememberLazyListState()
    LaunchedEffect(needle) { listState.scrollToItem(0) }

    if (filtered.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.TopCenter) {
            Text("No friends match \"${searchQuery.trim()}\"", color = DuudleColors.Muted, textAlign = TextAlign.Center)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)
    ) {
        items(filtered, key = { it.uid }) { friend ->
            FriendCard(
                friend = friend,
                onToggleMute = onToggleMute,
                onSelectFriend = onSelectFriend,
                hasUnreadChat = friend.uid in unreadChatUids,
                onChatClick = onChatClick,
                onReport = { safety.report(ReportSubject.user(friend.uid, friend.username)) },
                onBlock = { safety.block(friend.uid, friend.username) }
            )
        }
    }
}

@Composable
private fun FriendsEmptyState(gaze: FaceGaze, onAddFriend: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FriendsEmptyIllustration(gaze = gaze)
        Spacer(Modifier.height(12.dp))
        Text(
            "No friends yet!",
            fontFamily = DuudleDisplayFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 24.sp,
            color = DuudleColors.Ink,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Add a friend by their username and start sending doodles.",
            fontSize = 15.sp,
            color = DuudleColors.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp)
        )
        Spacer(Modifier.height(24.dp))
        DuudleInkPill(text = "Add a friend", onClick = onAddFriend)
    }
}

@Composable
private fun FriendCard(
    friend: Friend,
    onToggleMute: (Friend) -> Unit,
    onSelectFriend: (Friend) -> Unit,
    hasUnreadChat: Boolean,
    onChatClick: (Friend) -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(DuudleColors.Paper)
            .border(1.dp, DuudleColors.Hairline, CardShape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DuudleAvatar(uid = friend.uid, size = 52.dp, emoji = friend.avatarEmoji)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "@${friend.username}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = DuudleColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (friend.muted) "Muted: their doodles won't pop up" else "Their doodles pop up on your screen",
                    fontSize = 13.sp,
                    color = DuudleColors.Muted
                )
            }
            SafetyMenuButton(username = friend.username, onReport = onReport, onBlock = onBlock)
        }

        Spacer(Modifier.height(12.dp))
        // The whole row toggles, so the label and the switch are one 48dp+ target.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(FieldShape)
                .background(DuudleColors.Cream)
                .toggleable(value = !friend.muted, role = Role.Switch, onValueChange = { onToggleMute(friend) })
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Pop-ups",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = DuudleColors.Ink,
                modifier = Modifier.weight(1f)
            )
            // Visual only: the toggle already announces on/off.
            Text(
                if (friend.muted) "Off" else "On",
                fontSize = 13.sp,
                color = DuudleColors.Muted,
                modifier = Modifier.clearAndSetSemantics { }
            )
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = !friend.muted,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = DuudleColors.Paper,
                    checkedTrackColor = DuudleColors.Ink,
                    checkedBorderColor = DuudleColors.Ink,
                    uncheckedThumbColor = DuudleColors.Muted,
                    uncheckedTrackColor = DuudleColors.Paper,
                    uncheckedBorderColor = DuudleColors.Muted
                )
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (friend.muted) {
                DuudleOutlinePill(text = "Unmute", onClick = { onToggleMute(friend) }, modifier = Modifier.weight(1f))
            } else {
                FriendActionPill(
                    text = "Send doodle",
                    onClick = { onSelectFriend(friend) },
                    filled = true,
                    icon = Icons.Filled.Edit,
                    modifier = Modifier.weight(1f)
                )
            }
            FriendActionPill(
                text = "Chat",
                onClick = { onChatClick(friend) },
                filled = false,
                showDot = hasUnreadChat,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private val ActionPillShape = RoundedCornerShape(percent = 50)

/**
 * Half-width 48dp pill for the friend card's action row: Ink-filled or outlined, with
 * tighter padding than [DuudleInkPill] so "Send doodle" fits beside "Chat". [showDot]
 * adds a small red unread dot (and says so to TalkBack).
 */
@Composable
private fun FriendActionPill(
    text: String,
    onClick: () -> Unit,
    filled: Boolean,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    showDot: Boolean = false
) {
    val content = if (filled) DuudleColors.Paper else DuudleColors.Ink
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(ActionPillShape)
            .then(
                if (filled) Modifier.background(DuudleColors.Ink)
                else Modifier.border(1.5.dp, DuudleColors.Ink.copy(alpha = 0.3f), ActionPillShape)
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { if (showDot) stateDescription = "Unread messages" }
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            color = content,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (showDot) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(DuudleColors.ErrorRed)
            )
        }
    }
}

data class UserSearchResult(val uid: String, val username: String)

private enum class AddState { IDLE, WORKING, SENT, ALREADY_SENT, ALREADY_FRIENDS, FAILED }

@Composable
private fun AddFriendTab(currentUid: String, currentUsername: String, friendUids: Set<String>, blockedUids: Set<String>) {
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
            results = found.filter { it.uid != currentUid && it.uid !in friendUids && it.uid !in blockedUids }
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
                    // Generic on purpose: a refusal can mean they blocked you, and the
                    // raw error would hint at that.
                    if (!success) {
                        android.util.Log.e("DuudleDebug", "Friend request failed: $error")
                        errorMessage = "Couldn't send a request to @${user.username}. Try again later."
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Add a friend by username", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DuudleColors.Ink)
        Spacer(Modifier.height(10.dp))
        GreySearchField(
            value = searchText,
            onValueChange = { searchText = it },
            placeholder = "Start typing, e.g. maya"
        )
        Spacer(Modifier.height(8.dp))

        val hint = when {
            errorMessage != null -> errorMessage
            query.isEmpty() -> null
            isSearching -> "Searching..."
            results.isEmpty() -> "No one new matching \"${searchText.trim()}\""
            else -> null
        }
        hint?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (errorMessage != null) DuudleColors.ErrorRed else DuudleColors.Muted
            )
        }

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
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(DuudleColors.Paper)
            .border(1.dp, DuudleColors.Hairline, CardShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DuudleAvatar(uid = user.uid, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            "@${user.username}",
            fontWeight = FontWeight.Bold,
            color = DuudleColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        when (state) {
            AddState.IDLE, AddState.FAILED -> DuudleInkPill(
                text = if (state == AddState.FAILED) "Retry" else "Add",
                onClick = onAdd
            )
            AddState.WORKING -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = DuudleColors.Ink,
                strokeWidth = 2.dp
            )
            AddState.SENT -> Text("Requested ✓", color = DuudleColors.Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            AddState.ALREADY_SENT -> Text("Pending", color = DuudleColors.Muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            AddState.ALREADY_FRIENDS -> Text("Friends", color = DuudleColors.Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
    // requestId lets the rules check this friendship comes from a real request sent to
    // me. One batch, so the request is used up in the same write and can't be replayed.
    val friendshipData = hashMapOf(
        "userA" to myUid,
        "userB" to friendUid,
        "createdAt" to System.currentTimeMillis(),
        "requestId" to requestDocId
    )
    db.batch()
        .set(db.collection("friendships").document(), friendshipData)
        .delete(db.collection("friend_requests").document(requestDocId))
        .commit()
        .addOnSuccessListener { onDone(true) }
        .addOnFailureListener { onDone(false) }
}

fun declineFriendRequest(requestDocId: String) {
    FirebaseFirestore.getInstance().collection("friend_requests").document(requestDocId).delete()
}

@Composable
private fun RequestsTab(
    requests: List<FriendRequest>,
    safety: SafetyActions,
    onAccept: (FriendRequest) -> Unit,
    onDecline: (FriendRequest) -> Unit,
    gaze: FaceGaze
) {
    if (requests.isEmpty()) {
        RequestsEmptyState(gaze = gaze)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)
    ) {
        items(requests, key = { it.docId }) { req ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(DuudleColors.Paper)
                    .border(1.dp, DuudleColors.Hairline, CardShape)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DuudleAvatar(uid = req.fromUid, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "@${req.fromUsername}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = DuudleColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    SafetyMenuButton(
                        username = req.fromUsername,
                        onReport = { safety.report(ReportSubject.user(req.fromUid, req.fromUsername)) },
                        onBlock = { safety.block(req.fromUid, req.fromUsername) }
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    DuudleInkPill(text = "Accept", onClick = { onAccept(req) }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    DuudleOutlinePill(text = "Decline", onClick = { onDecline(req) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// A dozing lilac blob keeps the empty Requests tab company.
private val RequestsEmptyCast = listOf(
    FaceCharacter(
        BodyShape.Blob, DuudleColors.Lilac, size = 0.8f, x = 0.5f, y = 0.55f,
        eyes = EyeStyle.Sleepy, mouth = MouthStyle.SmallSmile, gradient = false, floatSeconds = 3.2f
    )
)

@Composable
private fun RequestsEmptyState(gaze: FaceGaze) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FaceScene(characters = RequestsEmptyCast, modifier = Modifier.size(120.dp), gaze = gaze)
        Spacer(Modifier.height(12.dp))
        Text(
            "No requests right now",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = DuudleColors.Ink,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "When someone adds you, they'll show up here.",
            fontSize = 14.sp,
            color = DuudleColors.Muted,
            textAlign = TextAlign.Center
        )
    }
}
