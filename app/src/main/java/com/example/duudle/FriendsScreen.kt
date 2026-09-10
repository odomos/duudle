package com.example.duudle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore

data class Friend(val uid: String, val username: String, val avatarEmoji: String, val muted: Boolean = false)
data class FriendRequest(val docId: String, val fromUid: String, val fromUsername: String)

enum class RelationshipStatus { NONE, ALREADY_FRIENDS, REQUEST_ALREADY_SENT }

private val LavenderBackground = Color(0xFFEDE3FA)
private val CardBackground = Color(0xFFFFFFFF)
private val YellowAccent = Color(0xFFFFC72C)
private val avatarEmojis = listOf("🦊", "🐼", "🐨", "🐸", "🐶", "🐱", "🦁", "🐯", "🐰", "🐻")

private fun emojiForUid(uid: String): String {
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
    onSelectFriend: (Friend) -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(FriendsTab.FRIENDS) }

    var friendUidsAsUserA by remember { mutableStateOf<List<String>>(emptyList()) }
    var friendUidsAsUserB by remember { mutableStateOf<List<String>>(emptyList()) }
    var friends by remember { mutableStateOf<List<Friend>>(emptyList()) }
    var requests by remember { mutableStateOf<List<FriendRequest>>(emptyList()) }

    DisposableEffect(currentUid) {
        val db = FirebaseFirestore.getInstance()

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
                val existingMuted = friends.associate { it.uid to it.muted }
                friends = snapshot.documents.map { doc ->
                    Friend(
                        uid = doc.id,
                        username = doc.getString("username") ?: "Unknown",
                        avatarEmoji = emojiForUid(doc.id),
                        muted = existingMuted[doc.id] ?: false
                    )
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("DuudleDebug", "Friend username lookup failed: ${e.message}")
            }
    }

    Column(modifier = modifier.fillMaxSize().background(LavenderBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.weight(1f))
            Text("Friends", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(64.dp))
        }

        TabRow(selectedTabIndex = selectedTab.ordinal) {
            Tab(selected = selectedTab == FriendsTab.FRIENDS, onClick = { selectedTab = FriendsTab.FRIENDS },
                text = { Text("My Friends (${friends.size})") })
            Tab(selected = selectedTab == FriendsTab.ADD, onClick = { selectedTab = FriendsTab.ADD },
                text = { Text("Add Friends") })
            Tab(selected = selectedTab == FriendsTab.REQUESTS, onClick = { selectedTab = FriendsTab.REQUESTS },
                text = { Text("Requests (${requests.size})") })
        }

        when (selectedTab) {
            FriendsTab.FRIENDS -> FriendsList(
                friends = friends,
                onToggleMute = { target ->
                    friends = friends.map { if (it.uid == target.uid) it.copy(muted = !it.muted) else it }
                },
                onSelectFriend = onSelectFriend
            )
            FriendsTab.ADD -> AddFriendTab(currentUid = currentUid, currentUsername = currentUsername)
            FriendsTab.REQUESTS -> RequestsTab(
                requests = requests,
                onAccept = { req -> acceptFriendRequest(req.docId, currentUid, req.fromUid) { } },
                onDecline = { req -> declineFriendRequest(req.docId) }
            )
        }
    }
}

@Composable
private fun FriendsList(friends: List<Friend>, onToggleMute: (Friend) -> Unit, onSelectFriend: (Friend) -> Unit) {
    if (friends.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No friends yet — add one from the Add Friends tab")
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(friends, key = { it.uid }) { friend ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackground, RoundedCornerShape(16.dp))
                    .clickable { onSelectFriend(friend) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(48.dp).background(LavenderBackground, CircleShape), contentAlignment = Alignment.Center) {
                    Text(friend.avatarEmoji, fontSize = 24.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(friend.username, fontWeight = FontWeight.Bold)
                    Text(
                        if (friend.muted) "Muted" else "Tap to send a doodle",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (friend.muted) Color.Gray else Color(0xFF2E7D32)
                    )
                }
                Switch(checked = !friend.muted, onCheckedChange = { onToggleMute(friend) })
            }
        }
    }
}

@Composable
private fun AddFriendTab(currentUid: String, currentUsername: String) {
    var searchText by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var foundUid by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var relationship by remember { mutableStateOf(RelationshipStatus.NONE) }
    var requestSent by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Add a friend by username", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = searchText,
            onValueChange = {
                searchText = it
                foundUid = null
                statusMessage = null
                requestSent = false
                relationship = RelationshipStatus.NONE
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. mayadoodles") },
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (searchText.isBlank()) return@Button
                isSearching = true
                statusMessage = null
                searchUserByUsername(searchText.trim()) { uid, error ->
                    when {
                        error != null -> {
                            isSearching = false
                            statusMessage = "Search failed: $error"
                        }
                        uid == null -> {
                            isSearching = false
                            statusMessage = "No user found with that username"
                        }
                        uid == currentUid -> {
                            isSearching = false
                            statusMessage = "That's your own username!"
                        }
                        else -> {
                            foundUid = uid
                            checkExistingRelationship(currentUid, uid) { status ->
                                isSearching = false
                                relationship = status
                                statusMessage = when (status) {
                                    RelationshipStatus.ALREADY_FRIENDS -> "You're already friends with @${searchText.trim()}"
                                    RelationshipStatus.REQUEST_ALREADY_SENT -> "You already sent a request to @${searchText.trim()} — waiting for them to respond"
                                    RelationshipStatus.NONE -> "Found @${searchText.trim()}"
                                }
                            }
                        }
                    }
                }
            },
            enabled = !isSearching && searchText.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = YellowAccent, contentColor = Color.Black)
        ) {
            Text(if (isSearching) "Searching..." else "Search")
        }

        statusMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
        }

        if (foundUid != null && relationship == RelationshipStatus.NONE && !requestSent) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                sendFriendRequest(
                    fromUid = currentUid,
                    fromUsername = currentUsername,
                    toUid = foundUid!!,
                    toUsername = searchText.trim()
                ) { success, error ->
                    statusMessage = if (success) "Request sent to @${searchText.trim()}!" else "Failed: $error"
                    if (success) requestSent = true
                }
            }) {
                Text("Send Request")
            }
        }
    }
}

fun searchUserByUsername(username: String, onResult: (uid: String?, error: String?) -> Unit) {
    FirebaseFirestore.getInstance().collection("users")
        .whereEqualTo("username", username)
        .limit(1)
        .get()
        .addOnSuccessListener { snapshot ->
            val doc = snapshot.documents.firstOrNull()
            onResult(doc?.getString("uid"), null)
        }
        .addOnFailureListener { e -> onResult(null, e.message) }
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
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No pending requests") }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(requests, key = { it.docId }) { req ->
            Row(
                modifier = Modifier.fillMaxWidth().background(CardBackground, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("👤", fontSize = 24.sp)
                Spacer(Modifier.width(12.dp))
                Text(req.fromUsername, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { onAccept(req) }) { Text("Accept") }
                TextButton(onClick = { onDecline(req) }) { Text("Decline") }
            }
        }
    }
}