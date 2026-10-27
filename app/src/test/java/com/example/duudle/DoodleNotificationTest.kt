package com.example.duudle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DoodleNotificationTest {
    @Test
    fun titleNamesSenderAndKind() {
        assertEquals("@bob sent you a doodle", doodleNotificationTitle("bob", isFlashCard = false))
        assertEquals("@bob sent you a flash card", doodleNotificationTitle("bob", isFlashCard = true))
    }

    @Test
    fun textPointsToTheVaultAndTheSetting() {
        val text = doodleNotificationText(isFlashCard = false)
        assertTrue(text.contains("Vault"))
        assertTrue(text.contains("Show over other apps"))
    }

    @Test
    fun oneNotificationPerSender() {
        assertEquals(doodleNotificationId("alice"), doodleNotificationId("alice"))
        assertNotEquals(doodleNotificationId("alice"), doodleNotificationId("bob"))
    }
}
