package com.example.duudle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushDedupeTest {

    @Test
    fun aNewIdIsRememberedNewestLast() {
        assertEquals(listOf("a", "b"), rememberPushId(listOf("a"), "b"))
        assertEquals(listOf("a"), rememberPushId(emptyList(), "a"))
    }

    @Test
    fun aRepeatIsRefused() {
        assertNull(rememberPushId(listOf("a", "b"), "a"))
    }

    @Test
    fun onlyTheNewestIdsAreKept() {
        assertEquals(listOf("b", "c", "d"), rememberPushId(listOf("a", "b", "c"), "d", cap = 3))
        // Once forgotten, an id counts as new again (a doodle id is never reused, so that's fine).
        assertEquals(listOf("c", "d", "a"), rememberPushId(listOf("b", "c", "d"), "a", cap = 3))
    }
}
