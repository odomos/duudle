package com.example.duudle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PopUpsTest {
    @Test
    fun popsUpOnlyWhenWantedAndAllowed() {
        assertTrue(showsAsPopUp(popUpsOn = true, canDrawOverlays = true))
        assertFalse(showsAsPopUp(popUpsOn = false, canDrawOverlays = true))
        assertFalse(showsAsPopUp(popUpsOn = true, canDrawOverlays = false))
        assertFalse(showsAsPopUp(popUpsOn = false, canDrawOverlays = false))
    }
}
