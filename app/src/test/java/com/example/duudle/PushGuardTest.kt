package com.example.duudle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushGuardTest {
    @Test
    fun showsPushesForThisAccount() = assertTrue(isPushForThisAccount("me", "me"))

    @Test
    fun dropsPushesForAnotherAccount() = assertFalse(isPushForThisAccount("someone-else", "me"))

    @Test
    fun dropsAddressedPushesBeforeSignIn() = assertFalse(isPushForThisAccount("me", null))

    @Test
    fun allowsPushesFromOlderFunctionsWithoutReceiverId() {
        assertTrue(isPushForThisAccount(null, "me"))
        assertTrue(isPushForThisAccount(null, null))
    }
}
