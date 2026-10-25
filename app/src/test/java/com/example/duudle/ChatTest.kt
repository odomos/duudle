package com.example.duudle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class ChatTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val locale = Locale.US

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private val now = at(2026, 10, 24, 15, 0)

    private fun text(id: String, sender: String, millis: Long?, pending: Boolean = false) =
        ChatMessage(id, sender, ChatMessageKind.TEXT, "msg $id", millis, pending)

    // ----- ids -----

    @Test
    fun chatIdIsTheSameFromBothSides() {
        assertEquals(chatIdFor("alice", "bob"), chatIdFor("bob", "alice"))
        assertEquals("alice_bob", chatIdFor("bob", "alice"))
    }

    @Test
    fun membersAreSortedLowFirstAndMatchTheId() {
        val members = chatMembersFor("zed", "amy")
        assertEquals(listOf("amy", "zed"), members)
        assertEquals(members[0] + "_" + members[1], chatIdFor("zed", "amy"))
    }

    @Test
    fun uppercaseSortsBeforeLowercaseLikeTheRules() {
        // Firebase uids mix cases; both sides must agree with the rules' string comparison.
        assertEquals("Zq_aB", chatIdFor("aB", "Zq"))
    }

    @Test
    fun intentUidsMustBeAlphanumericAndNotMine() {
        assertTrue(isAcceptableFriendUid("aZ09xYz", "me"))
        assertTrue(isAcceptableFriendUid("a".repeat(128), "me"))
        assertFalse(isAcceptableFriendUid("a".repeat(129), "me"))
        assertFalse(isAcceptableFriendUid("", "me"))
        assertFalse(isAcceptableFriendUid(null, "me"))
        assertFalse(isAcceptableFriendUid("me", "me"))
        assertFalse(isAcceptableFriendUid("a_b", "me"))
        assertFalse(isAcceptableFriendUid("../users/x", "me"))
        assertFalse(isAcceptableFriendUid("abc\n", "me"))
    }

    // ----- validation -----

    @Test
    fun draftIsTrimmed() {
        assertEquals(ChatDraftCheck.Ok("hey"), checkChatDraft("  hey \n"))
    }

    @Test
    fun blankDraftIsRejected() {
        assertEquals(ChatDraftCheck.Empty, checkChatDraft(""))
        assertEquals(ChatDraftCheck.Empty, checkChatDraft("   \n\t "))
    }

    @Test
    fun draftLengthLimitIsInclusive() {
        val max = "a".repeat(MAX_CHAT_MESSAGE_LENGTH)
        assertEquals(ChatDraftCheck.Ok(max), checkChatDraft(max))
        assertEquals(ChatDraftCheck.TooLong, checkChatDraft(max + "a"))
        // Surrounding whitespace doesn't count against the limit.
        assertEquals(ChatDraftCheck.Ok(max), checkChatDraft("  $max  "))
    }

    @Test
    fun previewIsOneLineAndCapped() {
        assertEquals("hi there you", chatPreview("hi\n\nthere   you"))
        assertEquals(CHAT_PREVIEW_LENGTH, chatPreview("b".repeat(500)).length)
    }

    @Test
    fun previewDoesNotSplitAnEmoji() {
        val text = "a".repeat(CHAT_PREVIEW_LENGTH - 1) + "😀" // emoji straddles the cap
        val preview = chatPreview(text)
        assertEquals(CHAT_PREVIEW_LENGTH - 1, preview.length)
        assertFalse(Character.isHighSurrogate(preview.last()))
    }

    // ----- unread -----

    @Test
    fun unreadWhenFriendWroteAfterMyLastRead() {
        assertTrue(hasUnread(lastAtMillis = 200, lastSenderId = "friend", lastReadForMeMillis = 100, me = "me"))
        assertTrue(hasUnread(lastAtMillis = 200, lastSenderId = "friend", lastReadForMeMillis = null, me = "me"))
    }

    @Test
    fun notUnreadWhenReadOrMineOrEmpty() {
        assertFalse(hasUnread(200, "friend", 200, "me"))
        assertFalse(hasUnread(200, "friend", 300, "me"))
        assertFalse(hasUnread(200, "me", null, "me"))
        assertFalse(hasUnread(null, "friend", null, "me"))
        assertFalse(hasUnread(200, null, null, "me"))
    }

    @Test
    fun summaryUsesMyOwnReadReceipt() {
        val summary = ChatSummary("a_b", listOf("a", "b"), ChatThemeId.CLASSIC, "b", 200, mapOf("a" to 100, "b" to 300))
        assertTrue(summary.hasUnreadFor("a"))
        assertFalse(summary.hasUnreadFor("b"))
        assertEquals("b", summary.otherMember("a"))
    }

    // ----- themes -----

    @Test
    fun themeIdsDecodeWithClassicFallback() {
        assertEquals(ChatThemeId.INFERNO, ChatThemeId.fromId("inferno"))
        assertEquals(ChatThemeId.POND, ChatThemeId.fromId(" Pond "))
        assertEquals(ChatThemeId.CLASSIC, ChatThemeId.fromId(null))
        assertEquals(ChatThemeId.CLASSIC, ChatThemeId.fromId(""))
        assertEquals(ChatThemeId.CLASSIC, ChatThemeId.fromId("some-future-theme"))
    }

    @Test
    fun themeIdsFitTheRulesLimit() {
        ChatThemeId.entries.forEach { assertTrue(it.id, it.id.length in 1..32) }
        assertEquals(7, ChatThemeId.entries.map { it.id }.toSet().size)
    }

    @Test
    fun themeNoteNamesWhoChangedIt() {
        assertEquals("@arnav changed the theme to Inferno", themeChangeNote(false, "arnav", "inferno"))
        assertEquals("You changed the theme to Cozy Pond", themeChangeNote(true, "me", "pond"))
    }

    @Test
    fun messageKindDecodes() {
        assertEquals(ChatMessageKind.TEXT, ChatMessageKind.fromWire("text"))
        assertEquals(ChatMessageKind.THEME, ChatMessageKind.fromWire("theme"))
        assertEquals(null, ChatMessageKind.fromWire("image"))
    }

    // ----- dates and times -----

    @Test
    fun dayLabels() {
        assertEquals("Today", chatDayLabel(at(2026, 10, 24, 0, 5), now, zone, locale))
        assertEquals("Yesterday", chatDayLabel(at(2026, 10, 23, 23, 59), now, zone, locale))
        assertEquals("Mon, 12 Oct", chatDayLabel(at(2026, 10, 12), now, zone, locale))
        assertEquals("3 Oct 2025", chatDayLabel(at(2025, 10, 3), now, zone, locale))
    }

    @Test
    fun timeLabels() {
        val t = at(2026, 10, 24, 16, 5)
        assertEquals("4:05 PM", formatChatTime(t, zone, use24Hour = false, locale = locale))
        assertEquals("16:05", formatChatTime(t, zone, use24Hour = true, locale = locale))
    }

    // ----- ordering and rows -----

    @Test
    fun pendingMessagesSortLast() {
        val ordered = orderChatMessages(
            listOf(
                text("p", "me", null, pending = true),
                text("b", "friend", 200),
                text("a", "friend", 100)
            )
        )
        assertEquals(listOf("a", "b", "p"), ordered.map { it.id })
    }

    @Test
    fun rowsHaveDaySeparatorsAndGroups() {
        val messages = listOf(
            text("1", "friend", at(2026, 10, 23, 10, 0)),
            text("2", "me", at(2026, 10, 24, 9, 0)),
            text("3", "me", at(2026, 10, 24, 9, 1)),
            text("4", "me", at(2026, 10, 24, 9, 30)), // > 5 min later: a new group
            text("5", "friend", at(2026, 10, 24, 9, 31))
        )
        val rows = buildChatRows(messages, "me", now, zone, locale)

        assertEquals(
            listOf("day", "1", "day", "2", "3", "4", "5"),
            rows.map { if (it is ChatRow.DaySeparator) "day" else it.key }
        )
        assertEquals("Yesterday", (rows[0] as ChatRow.DaySeparator).label)
        assertEquals("Today", (rows[2] as ChatRow.DaySeparator).label)

        val bubbles = rows.filterIsInstance<ChatRow.Bubble>().associateBy { it.message.id }
        assertFalse(bubbles.getValue("1").mine)
        assertTrue(bubbles.getValue("2").mine)
        // 2 and 3 are one group: 2 starts it, 3 ends it (and shows the time).
        assertTrue(bubbles.getValue("2").firstInGroup)
        assertFalse(bubbles.getValue("2").lastInGroup)
        assertFalse(bubbles.getValue("3").firstInGroup)
        assertTrue(bubbles.getValue("3").lastInGroup)
        // 4 stands alone after the gap; 5 is another sender.
        assertTrue(bubbles.getValue("4").firstInGroup)
        assertTrue(bubbles.getValue("4").lastInGroup)
        assertTrue(bubbles.getValue("5").firstInGroup)
    }

    @Test
    fun themeChangeIsANoteAndBreaksTheGroup() {
        val messages = listOf(
            text("1", "me", at(2026, 10, 24, 9, 0)),
            ChatMessage("t", "me", ChatMessageKind.THEME, "gothic", at(2026, 10, 24, 9, 1)),
            text("2", "me", at(2026, 10, 24, 9, 2))
        )
        val rows = buildChatRows(messages, "me", now, zone, locale)
        assertTrue(rows[2] is ChatRow.Note)
        val bubbles = rows.filterIsInstance<ChatRow.Bubble>()
        assertTrue(bubbles.all { it.firstInGroup && it.lastInGroup })
    }

    @Test
    fun pendingMessageWithoutTimeLandsToday() {
        val rows = buildChatRows(listOf(text("p", "me", null, pending = true)), "me", now, zone, locale)
        assertEquals("Today", (rows[0] as ChatRow.DaySeparator).label)
    }

    // ----- accessibility -----

    @Test
    fun bubbleLabels() {
        assertEquals("You: hey, 4:05 PM", chatBubbleLabel(true, "arnav", "hey", "4:05 PM", pending = false))
        assertEquals("@arnav: yo, 4:06 PM", chatBubbleLabel(false, "arnav", "yo", "4:06 PM", pending = false))
        assertEquals("You: hey, sending", chatBubbleLabel(true, "arnav", "hey", "4:05 PM", pending = true))
        assertEquals("You: hey", chatBubbleLabel(true, "arnav", "hey", null, pending = false))
    }
}
