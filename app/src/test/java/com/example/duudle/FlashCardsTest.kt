package com.example.duudle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashCardsTest {

    @Test
    fun catalogHasTheNineCards() {
        assertEquals((1..9).map { "card%02d".format(it) }, FlashCardCatalog.map { it.id })
    }

    @Test
    fun idsAreUniqueAndWellFormed() {
        val ids = FlashCardCatalog.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        // The Cloud Function trims cardId to 32 chars; these are far shorter.
        ids.forEach { assertTrue(it, Regex("""^card\d{2}$""").matches(it)) }
    }

    @Test
    fun eachCardHasItsOwnImageAndLabel() {
        assertEquals(FlashCardCatalog.size, FlashCardCatalog.map { it.res }.toSet().size)
        assertTrue(FlashCardCatalog.all { it.label.isNotBlank() })
    }

    @Test
    fun lookupById() {
        assertEquals("card01", flashCardById("card01")?.id)
        assertEquals(FlashCardCatalog[8], flashCardById("card09"))
    }

    @Test
    fun unknownOrMissingIdsLookUpToNull() {
        assertNull(flashCardById(null))
        assertNull(flashCardById(""))
        assertNull(flashCardById("card99")) // a newer app's card
        assertNull(flashCardById("CARD01"))
    }

    @Test
    fun payloadCardIdBlankMeansNotACard() {
        assertNull(cardIdFromPayload(null))
        assertNull(cardIdFromPayload(""))
        assertNull(cardIdFromPayload("   "))
        assertEquals("card03", cardIdFromPayload(" card03 "))
        // Unknown ids are kept, so the overlay can show the "update Duudle" placeholder.
        assertEquals("card42", cardIdFromPayload("card42"))
    }

    @Test
    fun emptyStrokesPayloadMeansAnOldCardPush() {
        assertTrue(isEmptyStrokesPayload("[]"))
        assertTrue(isEmptyStrokesPayload(" [ ] "))
        assertFalse(isEmptyStrokesPayload(null))
        assertFalse(isEmptyStrokesPayload(""))
        assertFalse(isEmptyStrokesPayload("""[{"c":-16777216,"w":8,"p":[[0.1,0.2],[0.3,0.4]]}]"""))
    }

    @Test
    fun textLines() {
        assertEquals("@maya sent you a flash card", flashCardSentLine("maya"))
        assertEquals("@maya sent a flash card — update Duudle to see it", unknownFlashCardText("maya"))
        assertEquals("Update Duudle to see this flash card", unknownFlashCardText(null))
    }
}
