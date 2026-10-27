package com.example.duudle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// The held-doodle file is JSON on the device; org.json is an Android stub in local unit
// tests, so the format is tested through the Map the JSON is built from and read back into.
class DoodleSnoozeTest {
    private val strokes = """[{"c":-16777216,"w":8.0,"p":[[0.1,0.2],[0.3,0.4]]}]"""
    private val minute = 60_000L

    // ----- held-doodle file format -----

    @Test
    fun payloadRoundTripsWithEverything() {
        val payload = SnoozePayload("doc1", "uidA", "maya", strokes, heldAt = 1_800_000_000_000L, cardId = "card03")
        assertEquals(payload, snoozePayloadFromMap(payload.toMap(), fallbackDoodleId = "ignored"))
    }

    @Test
    fun payloadRoundTripsForADoodleWithoutCard() {
        val payload = SnoozePayload("doc1", "uidA", "maya", strokes, heldAt = 1_800_000_000_000L, cardId = null)
        val map = payload.toMap()
        assertFalse(map.containsKey("cardId"))
        assertEquals(payload, snoozePayloadFromMap(map, fallbackDoodleId = "ignored"))
    }

    @Test
    fun heldPushWithoutEmbeddedStrokesStoresAnEmptyList() {
        val payload = SnoozePayload("doc2", "uidA", "maya", "[]", heldAt = 5L, cardId = null)
        assertEquals("[]", snoozePayloadFromMap(payload.toMap(), "doc2")!!.strokesJson)
    }

    @Test
    fun oldestFileFormatMigratesToAHeldDoodle() {
        // Before per-doodle snooze tracked time: no doodleId (it's the file name), no times, no card.
        val old = mapOf("senderUid" to "uidA", "senderUsername" to "maya", "strokes" to strokes)
        val parsed = snoozePayloadFromMap(old, fallbackDoodleId = "doc7")!!
        assertEquals("doc7", parsed.doodleId)
        assertNull(parsed.heldAt) // the device fills this in from the file's modified time
        assertNull(parsed.cardId)
        assertEquals(strokes, parsed.strokesJson)
    }

    @Test
    fun perDoodleSnoozeFileMigratesAndItsTriggerAtIsIgnored() {
        // What the previous (per-doodle alarm) snooze wrote.
        val previous = mapOf(
            "doodleId" to "doc8", "senderUid" to "uidA", "senderUsername" to "maya",
            "strokes" to "[]", "triggerAt" to 1_800_000_300_000L, "cardId" to "card05"
        )
        val parsed = snoozePayloadFromMap(previous, fallbackDoodleId = "other")!!
        assertEquals("doc8", parsed.doodleId)
        assertEquals("card05", parsed.cardId)
        assertNull(parsed.heldAt)
        assertFalse(parsed.toMap().containsKey("triggerAt"))
    }

    @Test
    fun jsonIntegersAreAcceptedForHeldAt() {
        // org.json hands back an Int for small numbers.
        val map = mapOf("senderUid" to "u", "senderUsername" to "n", "strokes" to "[]", "heldAt" to 42)
        assertEquals(42L, snoozePayloadFromMap(map, "doc")!!.heldAt)
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

    // ----- pause -----

    @Test
    fun pausedOnlyBeforeQuietUntil() {
        val now = 1_000_000L
        assertFalse(isPaused(now, quietUntil = 0L))
        assertTrue(isPaused(now, quietUntil = now + 1))
        assertFalse(isPaused(now, quietUntil = now))
        assertFalse(isPaused(now, quietUntil = now - minute))
    }

    @Test
    fun newPauseEndsMinutesFromNow() {
        assertEquals(1_000_000L + 5 * minute, extendPause(existing = 0L, now = 1_000_000L, minutes = 5))
    }

    @Test
    fun snoozingAgainKeepsTheLaterEnd() {
        val now = 1_000_000L
        // Paused for 15 min, then Snooze 5 min: the 15-minute end stays.
        assertEquals(now + 15 * minute, extendPause(existing = now + 15 * minute, now = now, minutes = 5))
        // Paused with 2 min left, then Snooze 5 min: extends to 5 min from now.
        assertEquals(now + 5 * minute, extendPause(existing = now + 2 * minute, now = now, minutes = 5))
        // An old, already-passed end doesn't hold the new one back.
        assertEquals(now + 5 * minute, extendPause(existing = now - 10 * minute, now = now, minutes = 5))
    }

    @Test
    fun pauseStartIsKeptWhileAPauseIsPending() {
        assertEquals(900L, pauseStartFor(existingQuietUntil = 0L, existingStartedAt = 0L, now = 900L))
        assertEquals(100L, pauseStartFor(existingQuietUntil = 5_000L, existingStartedAt = 100L, now = 900L))
    }

    @Test
    fun countsOnlyDoodlesHeldDuringThisPause() {
        val start = 1_000L
        assertEquals(2, heldDuringPause(listOf(999L, 1_000L, 2_000L, null), start))
        assertEquals(0, heldDuringPause(emptyList(), start))
    }

    @Test
    fun notifyAtEndOnlyIfSomethingWasHeldAndNotYetHandled() {
        assertTrue(shouldNotifyPauseEnd(heldInPause = 3, endHandled = false))
        assertFalse(shouldNotifyPauseEnd(heldInPause = 0, endHandled = false))
        assertFalse(shouldNotifyPauseEnd(heldInPause = 3, endHandled = true))
    }

    @Test
    fun notificationTextIsSingularForOne() {
        assertEquals("1 doodle arrived while pop-ups were paused", pausedDoodlesText(1))
        assertEquals("3 doodles arrived while pop-ups were paused", pausedDoodlesText(3))
    }

    @Test
    fun pauseEndActions() {
        val now = 1_000_000L
        assertEquals(PauseEndAction.NONE, pauseEndAction(now, quietUntil = 0L))
        assertEquals(PauseEndAction.REARM, pauseEndAction(now, quietUntil = now + 5 * minute))
        assertEquals(PauseEndAction.END, pauseEndAction(now, quietUntil = now - 10 * minute)) // missed end
        // Boundary: within the 5 s window counts as on time.
        assertEquals(PauseEndAction.END, pauseEndAction(now, quietUntil = now + PAUSE_END_WINDOW_MS))
        assertEquals(PauseEndAction.REARM, pauseEndAction(now, quietUntil = now + PAUSE_END_WINDOW_MS + 1))
    }
}
