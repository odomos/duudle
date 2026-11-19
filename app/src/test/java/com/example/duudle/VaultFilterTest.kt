package com.example.duudle

import org.junit.Assert.assertEquals
import org.junit.Test

class VaultFilterTest {
    private fun doodle(id: String, from: String, to: String) =
        IncomingDoodle(id, from, from, to, to, emptyList(), 0L, "unseen")

    private val mine = doodle("d1", "me", "stranger")
    private val fromFriend = doodle("d2", "friend", "me")
    private val fromStranger = doodle("d3", "stranger", "me")
    private val fromBlockedFriend = doodle("d4", "blockedFriend", "me")
    private val all = listOf(mine, fromFriend, fromStranger, fromBlockedFriend)

    @Test
    fun receivedDoodlesShowOnlyFromFriends() {
        val shown = visibleVaultDoodles(all, "me", emptySet(), setOf("friend", "blockedFriend"))
        assertEquals(listOf("d1", "d2", "d4"), shown.map { it.docId })
    }

    @Test
    fun blockedFriendsStayHidden() {
        val shown = visibleVaultDoodles(all, "me", setOf("blockedFriend"), setOf("friend", "blockedFriend"))
        assertEquals(listOf("d1", "d2"), shown.map { it.docId })
    }

    @Test
    fun whileFriendsLoadOnlyBlockedAreHidden() {
        val shown = visibleVaultDoodles(all, "me", setOf("blockedFriend"), null)
        assertEquals(listOf("d1", "d2", "d3"), shown.map { it.docId })
    }

    @Test
    fun doodlesISentStayEvenToStrangersOrBlocked() {
        val toBlocked = doodle("d5", "me", "blockedFriend")
        val shown = visibleVaultDoodles(listOf(mine, toBlocked), "me", setOf("blockedFriend"), emptySet())
        assertEquals(listOf("d1", "d5"), shown.map { it.docId })
    }

    @Test
    fun noFriendsMeansNoReceivedDoodles() {
        val shown = visibleVaultDoodles(all, "me", emptySet(), emptySet())
        assertEquals(listOf("d1"), shown.map { it.docId })
    }
}
