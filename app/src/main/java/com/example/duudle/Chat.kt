package com.example.duudle

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Pure chat logic (no Android or Firebase types) so it can be unit tested.
// Firestore access lives in ChatRepository.kt, UI in ChatScreen.kt / ChatThemes.kt.

const val MAX_CHAT_MESSAGE_LENGTH = 1000
/** The chat doc's lastMessage preview cap; firestore.rules enforces the same 120. */
const val CHAT_PREVIEW_LENGTH = 120
const val CHAT_PAGE_SIZE = 50
/** The draft's character counter appears once it gets this close to the limit. */
const val CHAT_COUNTER_THRESHOLD = 900
/** Consecutive messages from one sender further apart than this start a new group. */
private const val CHAT_GROUP_GAP_MILLIS = 5 * 60 * 1000L

/** Both members, lower uid first: the order the chat doc stores and the rules check. */
fun chatMembersFor(a: String, b: String): List<String> = if (a < b) listOf(a, b) else listOf(b, a)

/** One chat per pair of users: the two uids sorted and joined with "_". */
fun chatIdFor(a: String, b: String): String {
    val (low, high) = chatMembersFor(a, b)
    return "${low}_$high"
}

private val UID_PATTERN = Regex("^[A-Za-z0-9]{1,128}$")

/**
 * Checks a uid that arrived from outside the app (an intent extra). Firebase uids are
 * alphanumeric; anything else, or my own uid, is rejected.
 */
fun isAcceptableFriendUid(uid: String?, currentUid: String?): Boolean =
    uid != null && UID_PATTERN.matches(uid) && uid != currentUid

/** Notification id for a chat, so its messages replace each other in the shade. */
fun chatNotificationId(chatId: String): Int = chatId.hashCode()

/**
 * The chat currently on screen, set by ChatScreen while it's started. The messaging
 * service reads it from the FCM thread to skip notifications for the open chat.
 */
object ChatPresence {
    @Volatile
    var openChatId: String? = null
}

enum class ChatThemeId(val id: String, val displayName: String) {
    CLASSIC("classic", "Classic"),
    GOTHIC("gothic", "Gothic Pastel"),
    INFERNO("inferno", "Inferno"),
    POOKIE("pookie", "Pookie"),
    SERPENT("serpent", "Serpent"),
    SPOOKY("spooky", "Spooky Cute"),
    POND("pond", "Cozy Pond");

    companion object {
        /** Unknown, missing or future theme ids fall back to Classic instead of crashing. */
        fun fromId(raw: String?): ChatThemeId {
            val key = raw?.trim()?.lowercase() ?: return CLASSIC
            return entries.firstOrNull { it.id == key } ?: CLASSIC
        }
    }
}

enum class ChatMessageKind(val wireName: String) {
    TEXT("text"),
    THEME("theme");

    companion object {
        fun fromWire(raw: String?): ChatMessageKind? = entries.firstOrNull { it.wireName == raw }
    }
}

/**
 * A chat message. [createdAtMillis] is null only when the server timestamp is unknown;
 * [pending] is true while the write is still only in Firestore's local cache.
 * For [ChatMessageKind.THEME] messages, [text] is the new theme id.
 */
data class ChatMessage(
    val id: String,
    val senderId: String,
    val kind: ChatMessageKind,
    val text: String,
    val createdAtMillis: Long?,
    val pending: Boolean = false
)

/** The chat doc, decoded. [lastReadMillis] maps uid to when they last read the chat. */
data class ChatSummary(
    val chatId: String,
    val members: List<String>,
    val themeId: ChatThemeId,
    val lastSenderId: String?,
    val lastAtMillis: Long?,
    val lastReadMillis: Map<String, Long>
) {
    fun otherMember(me: String): String? = members.firstOrNull { it != me }
    fun hasUnreadFor(me: String): Boolean = hasUnread(lastAtMillis, lastSenderId, lastReadMillis[me], me)
}

/** Unread when the last message came from the other person after I last read the chat. */
fun hasUnread(lastAtMillis: Long?, lastSenderId: String?, lastReadForMeMillis: Long?, me: String): Boolean {
    if (lastAtMillis == null || lastSenderId == null || lastSenderId == me) return false
    return lastReadForMeMillis == null || lastAtMillis > lastReadForMeMillis
}

sealed interface ChatDraftCheck {
    data class Ok(val text: String) : ChatDraftCheck
    data object Empty : ChatDraftCheck
    data object TooLong : ChatDraftCheck
}

/** Trims the draft; blank or over-long drafts can't be sent (the rules reject them too). */
fun checkChatDraft(raw: String): ChatDraftCheck {
    val text = raw.trim()
    return when {
        text.isEmpty() -> ChatDraftCheck.Empty
        text.length > MAX_CHAT_MESSAGE_LENGTH -> ChatDraftCheck.TooLong
        else -> ChatDraftCheck.Ok(text)
    }
}

/** One-line preview for the chat doc: whitespace collapsed, capped without splitting an emoji. */
fun chatPreview(text: String): String {
    val oneLine = text.trim().replace(Regex("\\s+"), " ")
    if (oneLine.length <= CHAT_PREVIEW_LENGTH) return oneLine
    val end = if (Character.isHighSurrogate(oneLine[CHAT_PREVIEW_LENGTH - 1])) CHAT_PREVIEW_LENGTH - 1 else CHAT_PREVIEW_LENGTH
    return oneLine.substring(0, end)
}

/** Oldest first; messages still waiting for the server go last, in id order. */
fun orderChatMessages(messages: Collection<ChatMessage>): List<ChatMessage> =
    messages.sortedWith(
        compareBy<ChatMessage>({ it.pending }, { it.createdAtMillis ?: Long.MAX_VALUE }, { it.id })
    )

/** A row in the chat list: a day separator, a centred system note, or a message bubble. */
sealed interface ChatRow {
    val key: String

    data class DaySeparator(val epochDay: Long, val label: String) : ChatRow {
        override val key: String get() = "day-$epochDay"
    }

    data class Note(val message: ChatMessage) : ChatRow {
        override val key: String get() = message.id
    }

    data class Bubble(
        val message: ChatMessage,
        val mine: Boolean,
        val firstInGroup: Boolean,
        val lastInGroup: Boolean
    ) : ChatRow {
        override val key: String get() = message.id
    }
}

private fun localDate(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

/** "Today", "Yesterday", "Mon, 12 Oct" this year, "12 Oct 2025" before that. */
fun chatDayLabel(millis: Long, nowMillis: Long, zone: ZoneId, locale: Locale): String {
    val day = localDate(millis, zone)
    val today = localDate(nowMillis, zone)
    return when {
        day == today -> "Today"
        day == today.minusDays(1) -> "Yesterday"
        day.year == today.year -> DateTimeFormatter.ofPattern("EEE, d MMM", locale).format(day)
        else -> DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(day)
    }
}

fun formatChatTime(millis: Long, zone: ZoneId, use24Hour: Boolean, locale: Locale): String =
    DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", locale)
        .format(Instant.ofEpochMilli(millis).atZone(zone))

/** True when [cur] belongs in the same bubble group as the message before it. */
fun continuesGroup(prev: ChatMessage?, cur: ChatMessage, nowMillis: Long, zone: ZoneId): Boolean {
    if (prev == null) return false
    if (prev.kind != ChatMessageKind.TEXT || cur.kind != ChatMessageKind.TEXT) return false
    if (prev.senderId != cur.senderId) return false
    val prevAt = prev.createdAtMillis ?: nowMillis
    val curAt = cur.createdAtMillis ?: nowMillis
    if (localDate(prevAt, zone) != localDate(curAt, zone)) return false
    return curAt - prevAt in 0..CHAT_GROUP_GAP_MILLIS
}

/**
 * Turns [messages] (already in [orderChatMessages] order) into list rows, oldest first:
 * a day separator before each new day, theme changes as notes, and text bubbles marked
 * with where they sit in their sender's group.
 */
fun buildChatRows(
    messages: List<ChatMessage>,
    me: String,
    nowMillis: Long,
    zone: ZoneId,
    locale: Locale
): List<ChatRow> {
    val rows = ArrayList<ChatRow>(messages.size + 4)
    var lastDay: LocalDate? = null
    messages.forEachIndexed { index, message ->
        val at = message.createdAtMillis ?: nowMillis
        val day = localDate(at, zone)
        if (day != lastDay) {
            rows.add(ChatRow.DaySeparator(day.toEpochDay(), chatDayLabel(at, nowMillis, zone, locale)))
            lastDay = day
        }
        if (message.kind == ChatMessageKind.THEME) {
            rows.add(ChatRow.Note(message))
        } else {
            val prev = messages.getOrNull(index - 1)
            val next = messages.getOrNull(index + 1)
            rows.add(
                ChatRow.Bubble(
                    message = message,
                    mine = message.senderId == me,
                    firstInGroup = !continuesGroup(prev, message, nowMillis, zone),
                    lastInGroup = next == null || !continuesGroup(message, next, nowMillis, zone)
                )
            )
        }
    }
    return rows
}

/** The centred note for a theme change, e.g. "@arnav changed the theme to Inferno". */
fun themeChangeNote(byMe: Boolean, senderUsername: String, themeId: String): String {
    val who = if (byMe) "You" else "@$senderUsername"
    return "$who changed the theme to ${ChatThemeId.fromId(themeId).displayName}"
}

/** What TalkBack reads for a bubble: "You: hey, 4:05 PM" or "@arnav: hey, sending". */
fun chatBubbleLabel(mine: Boolean, senderUsername: String, text: String, timeLabel: String?, pending: Boolean): String {
    val who = if (mine) "You" else "@$senderUsername"
    val status = if (pending) "sending" else timeLabel
    return if (status.isNullOrBlank()) "$who: $text" else "$who: $text, $status"
}
