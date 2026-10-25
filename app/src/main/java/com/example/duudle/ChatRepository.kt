package com.example.duudle

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions

/**
 * Firestore access for 1:1 chats.
 *
 * chats/{chatId}: members [low, high], theme, lastMessage, lastSenderId, lastAt, lastRead {uid: ts}
 * chats/{chatId}/messages/{auto}: senderId, kind ("text" | "theme"), text, createdAt
 *
 * Writes go through Firestore's offline cache, so they show up at once (as pending) and
 * sync when the network is back. Errors are logged under DuudleDebug and passed on.
 */
object ChatRepository {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private fun chatRef(chatId: String) = db.collection("chats").document(chatId)
    private fun messagesRef(chatId: String) = chatRef(chatId).collection("messages")

    /**
     * A page of messages, newest first as queried. [oldestDoc] is the cursor for the next
     * older page; [full] means the page hit the limit, so there may be more before it.
     */
    class MessagePage(
        val messages: List<ChatMessage>,
        val oldestDoc: DocumentSnapshot?,
        val oldestAtMillis: Long?,
        val full: Boolean
    )

    /**
     * Live newest [CHAT_PAGE_SIZE] messages. Metadata changes are included so a message
     * flips from "sending" to sent when the server confirms it.
     */
    fun listenToLatestMessages(
        chatId: String,
        onPage: (MessagePage) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration =
        messagesRef(chatId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(CHAT_PAGE_SIZE.toLong())
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.e("DuudleDebug", "Chat messages listener failed for $chatId: ${error.message}")
                    onError(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) onPage(snapshot.toPage())
            }

    /** One page of messages older than [before]. */
    fun loadOlder(
        chatId: String,
        before: DocumentSnapshot,
        onResult: (MessagePage) -> Unit,
        onError: (Exception) -> Unit
    ) {
        messagesRef(chatId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .startAfter(before)
            .limit(CHAT_PAGE_SIZE.toLong())
            .get()
            .addOnSuccessListener { snapshot -> onResult(snapshot.toPage()) }
            .addOnFailureListener { e ->
                Log.e("DuudleDebug", "Loading older chat messages failed for $chatId: ${e.message}")
                onError(e)
            }
    }

    /** Live chat doc; null while the chat doesn't exist yet (no message sent). */
    fun listenToChat(
        chatId: String,
        onUpdate: (ChatSummary?) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration =
        chatRef(chatId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("DuudleDebug", "Chat listener failed for $chatId: ${error.message}")
                onError(error)
                return@addSnapshotListener
            }
            onUpdate(if (snapshot != null && snapshot.exists()) snapshot.toChatSummary() else null)
        }

    /** All my chats, for the unread dots. No orderBy, so no composite index is needed. */
    fun listenToMyChats(
        me: String,
        onUpdate: (List<ChatSummary>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration =
        db.collection("chats")
            .whereArrayContains("members", me)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("DuudleDebug", "My chats listener failed: ${error.message}")
                    onError(error)
                    return@addSnapshotListener
                }
                onUpdate(snapshot?.documents?.mapNotNull { it.toChatSummary() } ?: emptyList())
            }

    /**
     * Looks up a user's username from users/{uid}: the trusted source, unlike a name
     * carried in a notification intent. Null if the doc or name is missing or the read fails.
     */
    fun loadUsername(uid: String, onResult: (String?) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc -> onResult(doc.getString("username")?.takeIf { it.isNotBlank() }) }
            .addOnFailureListener { e ->
                Log.e("DuudleDebug", "Username lookup failed for chat: ${e.message}")
                onResult(null)
            }
    }

    /**
     * Adds a text message and updates the chat doc in one batch. The chat doc is
     * set-merged with its members, so the first message also creates the chat.
     */
    fun sendText(chatId: String, me: String, friend: String, text: String, onError: (Exception) -> Unit) {
        val batch = db.batch()
        batch.set(messagesRef(chatId).document(), messageData(me, ChatMessageKind.TEXT, text))
        batch.set(chatRef(chatId), chatUpdate(me, friend, chatPreview(text)), SetOptions.merge())
        batch.commit().addOnFailureListener { e ->
            Log.e("DuudleDebug", "Sending chat message failed for $chatId: ${e.message}")
            onError(e)
        }
    }

    /** Switches the shared theme for both people and leaves a note in the chat. */
    fun changeTheme(chatId: String, me: String, friend: String, theme: ChatThemeId, onError: (Exception) -> Unit) {
        val batch = db.batch()
        batch.set(messagesRef(chatId).document(), messageData(me, ChatMessageKind.THEME, theme.id))
        val update = chatUpdate(me, friend, "changed the theme") + ("theme" to theme.id)
        batch.set(chatRef(chatId), update, SetOptions.merge())
        batch.commit().addOnFailureListener { e ->
            Log.e("DuudleDebug", "Changing chat theme failed for $chatId: ${e.message}")
            onError(e)
        }
    }

    /**
     * Records that I've read the chat up to now. Only call once the chat doc exists:
     * a merge onto a missing doc would be a create without members, which the rules deny.
     */
    fun markRead(chatId: String, me: String) {
        chatRef(chatId)
            .set(mapOf("lastRead" to mapOf(me to FieldValue.serverTimestamp())), SetOptions.merge())
            .addOnFailureListener { e -> Log.e("DuudleDebug", "Marking chat read failed for $chatId: ${e.message}") }
    }

    private fun messageData(me: String, kind: ChatMessageKind, text: String): Map<String, Any> = mapOf(
        "senderId" to me,
        "kind" to kind.wireName,
        "text" to text,
        "createdAt" to FieldValue.serverTimestamp()
    )

    private fun chatUpdate(me: String, friend: String, preview: String): Map<String, Any> = mapOf(
        "members" to chatMembersFor(me, friend),
        "lastMessage" to preview,
        "lastSenderId" to me,
        "lastAt" to FieldValue.serverTimestamp()
    )

    private fun QuerySnapshot.toPage(): MessagePage {
        val docs = documents
        val messages = docs.mapNotNull { it.toChatMessage() }
        // A pending message has no server position yet, so it can't be a paging cursor.
        val oldest = docs.lastOrNull { !it.metadata.hasPendingWrites() }
        return MessagePage(
            messages = messages,
            oldestDoc = oldest,
            oldestAtMillis = oldest?.getTimestamp("createdAt")?.toDate()?.time,
            full = docs.size >= CHAT_PAGE_SIZE
        )
    }

    private fun DocumentSnapshot.toChatMessage(): ChatMessage? {
        val senderId = getString("senderId") ?: return null
        val kind = ChatMessageKind.fromWire(getString("kind")) ?: return null
        // ESTIMATE gives a pending message the local time instead of null, so it still
        // gets a sensible time label and day until the server confirms it.
        val createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        return ChatMessage(
            id = id,
            senderId = senderId,
            kind = kind,
            text = getString("text") ?: "",
            createdAtMillis = createdAt?.toDate()?.time,
            pending = metadata.hasPendingWrites()
        )
    }

    private fun DocumentSnapshot.toChatSummary(): ChatSummary? {
        val members = (get("members") as? List<*>)?.filterIsInstance<String>() ?: return null
        val estimate = DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        val lastRead = (get("lastRead", estimate) as? Map<*, *>)
            ?.mapNotNull { (uid, value) ->
                val ts = value as? com.google.firebase.Timestamp
                if (uid is String && ts != null) uid to ts.toDate().time else null
            }
            ?.toMap()
            ?: emptyMap()
        return ChatSummary(
            chatId = id,
            members = members,
            themeId = ChatThemeId.fromId(getString("theme")),
            lastSenderId = getString("lastSenderId"),
            lastAtMillis = getTimestamp("lastAt", estimate)?.toDate()?.time,
            lastReadMillis = lastRead
        )
    }
}
