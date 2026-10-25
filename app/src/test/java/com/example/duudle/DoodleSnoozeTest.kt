package com.example.duudle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// The file is JSON on the device; org.json is an Android stub in local unit tests, so the
// round-trip is tested through the Map the JSON is built from and read back into.
class DoodleSnoozeTest {
    private val strokes = """[{"c":-16777216,"w":8.0,"p":[[0.1,0.2],[0.3,0.4]]}]"""

    @Test
    fun payloadRoundTripsWithEverything() {
        val payload = SnoozePayload("doc1", "uidA", "maya", strokes, triggerAt = 1_800_000_000_000L, cardId = "card03")
        assertEquals(payload, snoozePayloadFromMap(payload.toMap(), fallbackDoodleId = "ignored"))
    }

    @Test
    fun payloadRoundTripsForADoodleWithoutCard() {
        val payload = SnoozePayload("doc1", "uidA", "maya", strokes, triggerAt = 1_800_000_000_000L, cardId = null)
        val map = payload.toMap()
        assertEquals(false, map.containsKey("cardId"))
        assertEquals(payload, snoozePayloadFromMap(map, fallbackDoodleId = "ignored"))
    }

    @Test
    fun oldFileWithoutTriggerAtCardOrIdUsesTheFileName() {
        // What builds before this one wrote.
        val old = mapOf("senderUid" to "uidA", "senderUsername" to "maya", "strokes" to strokes)
        val parsed = snoozePayloadFromMap(old, fallbackDoodleId = "doc7")!!
        assertEquals("doc7", parsed.doodleId)
        assertNull(parsed.triggerAt)
        assertNull(parsed.cardId)
        assertEquals(strokes, parsed.strokesJson)
    }

    @Test
    fun fileFromTheFlashCardBuildKeepsItsCard() {
        val withCard = mapOf("senderUid" to "uidA", "senderUsername" to "maya", "strokes" to "[]", "cardId" to "card05")
        assertEquals("card05", snoozePayloadFromMap(withCard, fallbackDoodleId = "doc8")!!.cardId)
    }

    @Test
    fun jsonIntegersAreAcceptedForTriggerAt() {
        // org.json hands back an Int for small numbers.
        val map = mapOf("senderUid" to "u", "senderUsername" to "n", "strokes" to "[]", "triggerAt" to 42)
        assertEquals(42L, snoozePayloadFromMap(map, "doc")!!.triggerAt)
    }

    @Test
    fun missingRequiredFieldsAreUnreadable() {
        assertNull(snoozePayloadFromMap(mapOf("senderUsername" to "n", "strokes" to "[]"), "doc"))
        assertNull(snoozePayloadFromMap(mapOf("senderUid" to "u", "strokes" to "[]"), "doc"))
        assertNull(snoozePayloadFromMap(mapOf("senderUid" to "u", "senderUsername" to "n"), "doc"))
        assertNull(snoozePayloadFromMap(mapOf("senderUid" to "u", "senderUsername" to "n", "strokes" to "[]"), ""))
    }

    @Test
    fun blankCardIdMeansNoCard() {
        val map = mapOf("senderUid" to "u", "senderUsername" to "n", "strokes" to "[]", "cardId" to " ")
        assertNull(snoozePayloadFromMap(map, "doc")!!.cardId)
    }

    @Test
    fun oldFilesWithoutTriggerAtFireNow() {
        assertEquals(SnoozeAction.FIRE_NOW, snoozeAction(triggerAt = null, now = 1_000_000L))
    }

    @Test
    fun overdueSnoozesFireNow() {
        assertEquals(SnoozeAction.FIRE_NOW, snoozeAction(triggerAt = 500_000L, now = 1_000_000L))
    }

    @Test
    fun futureSnoozesAreRearmed() {
        assertEquals(SnoozeAction.REARM, snoozeAction(triggerAt = 1_000_000L + 15 * 60_000L, now = 1_000_000L))
    }

    @Test
    fun fiveSecondBoundary() {
        val now = 1_000_000L
        assertEquals(SnoozeAction.FIRE_NOW, snoozeAction(now + SNOOZE_FIRE_NOW_WINDOW_MS, now))
        assertEquals(SnoozeAction.REARM, snoozeAction(now + SNOOZE_FIRE_NOW_WINDOW_MS + 1, now))
        assertEquals(5_000L, SNOOZE_FIRE_NOW_WINDOW_MS)
    }
}
