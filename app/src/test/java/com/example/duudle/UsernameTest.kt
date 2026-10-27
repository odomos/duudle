package com.example.duudle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Must match validUsername() in firestore.rules, or sign-up fails with a permission error. */
class UsernameTest {
    @Test
    fun acceptsLettersDigitsUnderscoreAndDot() {
        listOf("bob", "Alice_99", "a.b.c", "emulator2", "x".repeat(20)).forEach {
            assertTrue(it, isValidUsername(it))
        }
    }

    @Test
    fun rejectsTooShortOrTooLong() {
        assertFalse(isValidUsername("ab"))
        assertFalse(isValidUsername("x".repeat(21)))
    }

    @Test
    fun rejectsSpacesSymbolsAndNonAscii() {
        listOf("my name", "bob!", "héllo", "名前名前", "bob\n", "😀😀😀").forEach {
            assertFalse(it, isValidUsername(it))
        }
    }

    @Test
    fun searchKeyIsTheLowerCasedName() {
        assertEquals("alice_99", usernameKey("  Alice_99 "))
    }
}
