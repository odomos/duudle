package com.example.duudle

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleLinkTest {
    @Test
    fun cancellingTheAccountPickerIsSilent() {
        assertNull(googlePickMessage(GooglePick.Cancelled))
        assertNull(googlePickMessage(GooglePick.Token("t")))
        assertNotNull(googlePickMessage(GooglePick.NoAccount))
        assertNotNull(googlePickMessage(GooglePick.Failed))
    }

    @Test
    fun aGoogleAccountUsedElsewhereSaysSo() {
        assertTrue(linkResultMessage(LinkResult.ALREADY_USED).contains("another Duudle account"))
        assertTrue(linkResultMessage(LinkResult.LINKED).startsWith("Linked"))
    }
}
