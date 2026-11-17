package com.example.duudle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FlashCardsTest {

    @Test
    fun thePacksAndTheirIds() {
        assertEquals(listOf("everyday", "cats", "dogs"), StickerPacks.map { it.id })
        assertEquals((10..17).map { "card$it" }, StickerPacks[0].stickers.map { it.id })
        assertEquals((18..25).map { "card$it" }, StickerPacks[1].stickers.map { it.id })
        assertEquals((26..33).map { "card$it" }, StickerPacks[2].stickers.map { it.id })
        assertEquals((10..33).map { "card$it" }, FlashCardCatalog.map { it.id })
        assertEquals(StickerPacks.flatMap { it.stickers }, FlashCardCatalog)
    }

    @Test
    fun allTheOldCardIdsAreRetiredAndNeverInACatalog() {
        assertEquals((1..9).map { "card%02d".format(it) }.toSet(), RetiredFlashCardIds)
        RetiredFlashCardIds.forEach { assertNull(flashCardById(it)) }
        assertTrue(FlashCardCatalog.none { it.id in RetiredFlashCardIds })
    }

    @Test
    fun idsAreUniqueTwoDigitAndNeverBelowTen() {
        val ids = FlashCardCatalog.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        // The rules accept card + exactly two digits; 01-09 belong to the retired image cards.
        ids.forEach { assertTrue(it, Regex("""^card([1-9]\d)$""").matches(it)) }
    }

    @Test
    fun eachStickerHasItsOwnImageLabelAndKeywords() {
        assertEquals(FlashCardCatalog.size, FlashCardCatalog.map { it.res }.toSet().size)
        for (s in FlashCardCatalog) {
            assertTrue(s.id, s.label.isNotBlank())
            assertTrue("${s.id} needs search keywords", s.keywords.size >= 3)
            // Search compares against lower-case words.
            assertTrue(s.id, s.keywords.all { it == it.lowercase() && it.isNotBlank() })
        }
    }

    @Test
    fun packsAreWellFormed() {
        assertEquals(StickerPacks.size, StickerPacks.map { it.id }.toSet().size)
        for (p in StickerPacks) {
            assertTrue(p.id, Regex("""^[a-z][a-z0-9]*$""").matches(p.id))
            assertTrue(p.title.isNotBlank())
            assertTrue(p.stickers.isNotEmpty())
        }
    }

    @Test
    fun everyPackHasItsInfoFileSoItsArtSourceIsRecorded() {
        // stickers/README.md: no pack ships without a PACK.md saying where its art came from.
        for (p in StickerPacks) {
            val info = File("../stickers/${p.id}/PACK.md")
            assertTrue("missing ${info.path}", info.isFile)
            assertTrue("${info.path} must say where the art came from", info.readText().contains("Source:"))
        }
    }

    @Test
    fun lookupById() {
        assertEquals("card10", flashCardById("card10")?.id)
        assertEquals("card17", flashCardById("card17")?.id)
        assertEquals("card25", flashCardById("card25")?.id)
        assertEquals(FlashCardCatalog.last(), flashCardById("card33"))
    }

    @Test
    fun unknownOrMissingIdsLookUpToNull() {
        assertNull(flashCardById(null))
        assertNull(flashCardById(""))
        assertNull(flashCardById("card99")) // a newer app's sticker
        assertNull(flashCardById("CARD10"))
    }

    @Test
    fun payloadCardIdBlankMeansNotACard() {
        assertNull(cardIdFromPayload(null))
        assertNull(cardIdFromPayload(""))
        assertNull(cardIdFromPayload("   "))
        assertEquals("card10", cardIdFromPayload(" card10 "))
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
        assertEquals("@maya sent you a sticker", flashCardSentLine("maya"))
        assertEquals("@maya sent a sticker — update Duudle to see it", unknownFlashCardText("card42", "maya"))
        assertEquals("Update Duudle to see this sticker", unknownFlashCardText("card42", null))
        assertEquals("This sticker is no longer available", unknownFlashCardText("card02", "maya"))
        assertEquals("This sticker is no longer available", unknownFlashCardText("card09", null))
    }

    // ---- search ----

    @Test
    fun emptySearchListsEverything() {
        assertEquals(FlashCardCatalog, searchStickers(""))
        assertEquals(FlashCardCatalog, searchStickers("   "))
    }

    @Test
    fun searchMatchesKeywordsLabelsAndPackTitlesIgnoringCase() {
        assertEquals(listOf("card15", "card23", "card30"), searchStickers("LOVE").map { it.id }) // one per pack
        assertEquals(listOf("card14", "card22", "card33"), searchStickers("zzz").map { it.id })
        assertEquals(listOf("card17"), searchStickers("crying").map { it.id }) // from the label
        assertEquals(StickerPacks[0].stickers, searchStickers("everyday")) // from the pack title
        assertEquals(StickerPacks[1].stickers, searchStickers("cats"))
        assertEquals(StickerPacks[1].stickers, searchStickers("kitty"))
        assertEquals(StickerPacks[2].stickers, searchStickers("dogs"))
        assertEquals(StickerPacks[2].stickers, searchStickers("doggo"))
    }

    @Test
    fun everyTypedWordMustMatch() {
        assertEquals(listOf("card11", "card12"), searchStickers("joke").map { it.id })
        assertEquals(listOf("card12"), searchStickers("joke wink").map { it.id })
        assertEquals(listOf("card19"), searchStickers("cat lol").map { it.id }) // "lol" is also on card11, "cat" narrows it
        assertTrue(searchStickers("joke sleepy").isEmpty())
        assertTrue(searchStickers("dinosaur").isEmpty())
    }

    @Test
    fun searchInOnePackOfTheRealCatalog() {
        assertEquals(listOf("card23"), searchStickers("love", "cats").map { it.id })
        assertEquals(listOf("card15"), searchStickers("love", "everyday").map { it.id })
        assertEquals(listOf("card30"), searchStickers("love", "dogs").map { it.id })
        assertEquals(listOf("card31"), searchStickers("growl", "dogs").map { it.id })
        assertEquals(StickerPacks[1].stickers, searchStickers("", "cats"))
    }

    @Test
    fun searchCanBeLimitedToOnePack() {
        val a = StickerPack("cats", "Cats", listOf(FlashCard("card20", 1, "Sleepy cat", listOf("cat", "sleepy"))))
        val b = StickerPack("dogs", "Dogs", listOf(FlashCard("card21", 2, "Sleepy dog", listOf("dog", "sleepy"))))
        assertEquals(listOf("card20", "card21"), searchStickers("sleepy", null, listOf(a, b)).map { it.id })
        assertEquals(listOf("card21"), searchStickers("sleepy", "dogs", listOf(a, b)).map { it.id })
        assertEquals(listOf("card20"), searchStickers("cats", null, listOf(a, b)).map { it.id })
        assertTrue(searchStickers("", "nope", listOf(a, b)).isEmpty())
    }

    @Test
    fun someStickersMoveAndTheRestStayStill() {
        assertTrue(FlashCardCatalog.any { it.motion != StickerMotion.NONE })
        assertTrue(FlashCardCatalog.any { it.motion == StickerMotion.NONE })
    }
}
