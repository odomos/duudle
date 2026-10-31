package com.example.duudle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyTest {
    @Test
    fun reasonIdsMatchTheRules() {
        // firestore.rules: reason in [...]; keep these in step.
        assertEquals(
            listOf("harassment", "hate", "sexual", "violence", "spam", "impersonation", "underage", "other"),
            ReportReason.entries.map { it.id }
        )
    }

    @Test
    fun userReportHasNoContentId() {
        val fields = reportFields("me", ReportSubject.user("bob", "bob"), ReportReason.SPAM, "")
        assertEquals("user", fields["contentType"])
        assertFalse(fields.containsKey("contentId"))
        assertFalse(fields.containsKey("details"))
        assertEquals("open", fields["status"])
        assertEquals("me", fields["reporterId"])
        assertEquals("bob", fields["reportedUid"])
    }

    @Test
    fun doodleAndChatReportsCarryTheirId() {
        assertEquals("d1", reportFields("me", ReportSubject.doodle("bob", "bob", "d1"), ReportReason.SEXUAL, "")["contentId"])
        val chat = reportFields("bob", ReportSubject.chat("me", "me", "bob_me"), ReportReason.HARASSMENT, "")
        assertEquals("chat", chat["contentType"])
        assertEquals("bob_me", chat["contentId"])
    }

    @Test
    fun detailsAreTrimmedAndCapped() {
        assertEquals("rude", reportFields("me", ReportSubject.user("bob", "bob"), ReportReason.OTHER, "  rude  ")["details"])
        val long = reportFields("me", ReportSubject.user("bob", "bob"), ReportReason.OTHER, "x".repeat(5000))["details"] as String
        assertEquals(MAX_REPORT_DETAILS, long.length)
        assertFalse(reportFields("me", ReportSubject.user("bob", "bob"), ReportReason.OTHER, "   ").containsKey("details"))
    }

    @Test
    fun onlyFieldsTheRulesAllow() {
        val allowed = setOf("reporterId", "reportedUid", "reason", "contentType", "contentId", "details", "createdAt", "status")
        val fields = reportFields("me", ReportSubject.doodle("bob", "bob", "d1"), ReportReason.OTHER, "why")
        assertTrue(allowed.containsAll(fields.keys))
    }

    @Test
    fun termsSayTheMinimumAge() {
        assertEquals(13, MINIMUM_AGE)
        assertTrue(TERMS_SECTIONS.first().second.contains("13"))
    }
}
