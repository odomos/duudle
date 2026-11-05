package com.example.duudle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RateLimitsTest {
    private val hour = 60 * 60 * 1000L
    private val start = 1_000_000_000_000L

    @Test
    fun aNewWindowStartsWithNoCounterOrOnceTheOldOneHasPassed() {
        assertTrue(startsNewWindow(RateLimit.DOODLES, null, start))
        val counter = LimitCounter(count = 60, windowStartMillis = start)
        assertFalse(startsNewWindow(RateLimit.DOODLES, counter, start + hour))
        assertTrue(startsNewWindow(RateLimit.DOODLES, counter, start + hour + 1))
        // Friend requests count per day.
        assertFalse(startsNewWindow(RateLimit.FRIEND_REQUESTS, counter, start + 23 * hour))
    }

    @Test
    fun onlyAFullCounterInAnOpenWindowMakesYouWait() {
        assertEquals(0, waitBeforeNextSend(RateLimit.DOODLES, null, start))
        assertEquals(0, waitBeforeNextSend(RateLimit.DOODLES, LimitCounter(59, start), start + 1))
        assertEquals(hour - 10 * 60_000, waitBeforeNextSend(RateLimit.DOODLES, LimitCounter(60, start), start + 10 * 60_000))
        assertEquals(0, waitBeforeNextSend(RateLimit.DOODLES, LimitCounter(60, start), start + hour + 1))
        assertEquals(1, waitBeforeNextSend(RateLimit.DOODLES, LimitCounter(60, start), start + hour))
    }

    @Test
    fun theMessageSaysWhatAndRoughlyHowLong() {
        assertEquals("You've sent a lot of doodles lately. Try again in a minute.", rateLimitMessage(RateLimit.DOODLES, 1))
        assertEquals("You've sent a lot of messages lately. Try again in 50 minutes.", rateLimitMessage(RateLimit.MESSAGES, 49 * 60_000 + 1))
        assertEquals("You've sent a lot of reports lately. Try again in an hour.", rateLimitMessage(RateLimit.REPORTS, hour))
        assertEquals("You've sent a lot of friend requests lately. Try again in 3 hours.", rateLimitMessage(RateLimit.FRIEND_REQUESTS, 2 * hour + 1))
    }

    @Test
    fun onlyTheRateLimitIsSpelledOut() {
        assertEquals("slow down", rateLimitedOr(RateLimitedException("slow down"), "fallback"))
        assertEquals("fallback", rateLimitedOr(IllegalStateException("blocked"), "fallback"))
        assertEquals("fallback", rateLimitedOr(null, "fallback"))
    }

    @Test
    fun limitsMatchTheFirestoreRules() {
        val rules = File("../firestore.rules").readText()
        val max = Regex("""limitMax\(kind\) \{\s*return \{([^}]*)\}""").find(rules)!!.groupValues[1]
        RateLimit.entries.forEach { limit ->
            assertTrue("${limit.id} max", max.contains("'${limit.id}': ${limit.max}"))
        }
        val hourly = Regex("""kind in \[([^\]]*)\] \? duration.value\(1, 'h'\) : duration.value\(1, 'd'\)""").find(rules)!!.groupValues[1]
        RateLimit.entries.forEach { limit ->
            assertEquals("${limit.id} window", if ("'${limit.id}'" in hourly) hour else 24 * hour, limit.windowMillis)
        }
    }
}
