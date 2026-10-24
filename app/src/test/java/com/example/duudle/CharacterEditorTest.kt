package com.example.duudle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterEditorTest {
    private val saved = AvatarConfig.DEFAULT.copy(
        hair = AvatarHair.BUN, skin = AvatarSkin.TAN, item = AvatarItem.FLOWER,
        glasses = AvatarGlasses.ROUND, eyes = AvatarEyes.HAPPY, cheeks = AvatarCheeks.BLUSH,
    )
    private val fresh = CharacterEditorState(saved = saved)

    // ---- select / dirty ----

    @Test
    fun selectChangesTheDraftButNotTheSavedLook() {
        val s = fresh.select(saved.copy(bottom = AvatarBottom.SKIRT))
        assertEquals(AvatarBottom.SKIRT, s.draft.bottom)
        assertEquals(saved, s.saved)
        assertTrue(s.isDirty)
    }

    @Test
    fun selectingTheSameLookIsANoOp() {
        assertSame(fresh, fresh.select(saved))
    }

    @Test
    fun dirtyClearsWhenTheDraftIsBackToExactlyTheSavedLookByHand() {
        val s = fresh.select(saved.copy(hair = AvatarHair.SHORT)).select(saved)
        assertFalse(s.isDirty)
    }

    @Test
    fun dirtyClearsWhenUndoingBackToTheSavedLook() {
        val s = fresh.select(saved.copy(hair = AvatarHair.SHORT)).select(saved.copy(hair = AvatarHair.BOB)).undo().undo()
        assertEquals(saved, s.draft)
        assertFalse(s.isDirty)
    }

    @Test
    fun choosingJeansKeepsTheHair() {
        val withSkirt = fresh.select(saved.copy(bottom = AvatarBottom.SKIRT))
        val s = withSkirt.select(withSkirt.draft.copy(bottom = AvatarBottom.JEANS))
        assertEquals(AvatarHair.BUN, s.draft.hair)
        assertEquals(AvatarBottom.JEANS, s.draft.bottom)
    }

    @Test
    fun applyingAPresetKeepsHeadFaceHairGlassesAndItem() {
        for (preset in AvatarOutfitPresets) {
            val d = fresh.select(fresh.draft.withOutfit(preset)).draft
            assertEquals(saved.hair, d.hair)
            assertEquals(saved.skin, d.skin)
            assertEquals(saved.eyes, d.eyes)
            assertEquals(saved.cheeks, d.cheeks)
            assertEquals(saved.glasses, d.glasses)
            assertEquals(saved.item, d.item)
            assertEquals(saved.backdrop, d.backdrop)
            assertTrue(d.matches(preset))
        }
    }

    // ---- steps ----

    @Test
    fun stepNavigationNeverChangesTheDraftAndIsBounded() {
        val edited = fresh.select(saved.copy(shoes = AvatarShoes.BOOTS))
        assertEquals(EditorStep.OUTFITS, edited.back().step)
        var s = edited
        repeat(20) { s = s.next() }
        assertEquals(EditorStep.REVIEW, s.step)
        assertEquals(edited.draft, s.draft)
        repeat(20) { s = s.back() }
        assertEquals(EditorStep.OUTFITS, s.step)
        assertEquals(edited.draft, s.draft)
        for (step in EditorStep.entries) {
            val g = edited.goTo(step)
            assertEquals(step, g.step)
            assertEquals(edited.draft, g.draft)
            assertEquals(edited.undo, g.undo)
        }
    }

    @Test
    fun stepIndexCountAndNextLabels() {
        assertEquals(8, fresh.stepCount)
        assertEquals(0, fresh.stepIndex)
        assertEquals("Next: Head & Hair", fresh.nextLabel)
        assertEquals("Next: Review", fresh.goTo(EditorStep.EXTRAS).nextLabel)
        assertEquals("Save character", fresh.goTo(EditorStep.REVIEW).nextLabel)
        assertEquals(null, fresh.previousStep)
        assertEquals(EditorStep.SHOES, fresh.goTo(EditorStep.EXTRAS).previousStep)
    }

    // ---- undo / redo / reset ----

    @Test
    fun undoAndRedoWalkThroughTheHistory() {
        val a = saved.copy(hair = AvatarHair.SHORT)
        val b = saved.copy(hair = AvatarHair.BOB)
        val s = fresh.select(a).select(b)
        assertEquals(a, s.undo().draft)
        assertEquals(saved, s.undo().undo().draft)
        assertEquals(a, s.undo().undo().redo().draft)
        assertEquals(b, s.undo().undo().redo().redo().draft)
        assertFalse(s.undo().undo().canUndo)
        assertFalse(s.canRedo)
    }

    @Test
    fun aNewSelectionClearsRedo() {
        val s = fresh.select(saved.copy(hair = AvatarHair.SHORT)).undo()
        assertTrue(s.canRedo)
        assertFalse(s.select(saved.copy(hair = AvatarHair.BOB)).canRedo)
    }

    @Test
    fun undoAndRedoOnEmptyHistoryAreNoOps() {
        assertSame(fresh, fresh.undo())
        assertSame(fresh, fresh.redo())
    }

    @Test
    fun undoHistoryIsCapped() {
        var s = fresh
        val colours = AvatarOutfitColor.entries
        repeat(EDITOR_UNDO_LIMIT + 25) { i -> s = s.select(s.draft.copy(outfitColor = colours[(i + 1) % colours.size], skin = AvatarSkin.entries[i % 6])) }
        assertEquals(EDITOR_UNDO_LIMIT, s.undo.size)
        var undone = 0
        while (s.canUndo) {
            s = s.undo()
            undone++
        }
        assertEquals(EDITOR_UNDO_LIMIT, undone)
    }

    @Test
    fun resetToSavedIsUndoableAndANoOpWhenClean() {
        assertSame(fresh, fresh.resetToSaved())
        val edited = fresh.select(saved.copy(hair = AvatarHair.SHORT))
        val reset = edited.resetToSaved()
        assertEquals(saved, reset.draft)
        assertFalse(reset.isDirty)
        assertEquals(edited.draft, reset.undo().draft)
    }

    // ---- closing ----

    @Test
    fun closeDecisionAsksOnlyWhenDirty() {
        assertEquals(CloseDecision.CLOSE_NOW, fresh.closeDecision())
        assertEquals(CloseDecision.CONFIRM_DISCARD, fresh.select(saved.copy(hair = AvatarHair.SHORT)).closeDecision())
        assertEquals(CloseDecision.CLOSE_NOW, fresh.goTo(EditorStep.SHOES).closeDecision())
    }

    @Test
    fun discardRestoresTheSavedLook() {
        val s = fresh.select(saved.copy(hair = AvatarHair.SHORT)).discard()
        assertEquals(saved, s.draft)
        assertFalse(s.isDirty)
    }

    // ---- saving ----

    @Test
    fun saveStartedIsIgnoredWhenNothingChanged() {
        assertSame(fresh, fresh.saveStarted())
        assertFalse(fresh.saveStarted().saving)
    }

    @Test
    fun saveSucceededMakesTheDraftTheSavedLook() {
        val edited = fresh.select(saved.copy(shoes = AvatarShoes.LOAFERS))
        val done = edited.saveStarted().saveSucceeded()
        assertEquals(edited.draft, done.saved)
        assertFalse(done.isDirty)
        assertFalse(done.saving)
        assertFalse(done.canUndo)
        assertEquals(CloseDecision.CLOSE_NOW, done.closeDecision())
    }

    @Test
    fun saveFailedKeepsTheDraftAndStaysDirty() {
        val edited = fresh.select(saved.copy(shoes = AvatarShoes.LOAFERS))
        val failed = edited.saveStarted().saveFailed()
        assertEquals(edited.draft, failed.draft)
        assertEquals(saved, failed.saved)
        assertTrue(failed.isDirty)
        assertTrue(failed.saveFailed)
        assertFalse(failed.saving)
        // Trying again works, and a new edit clears the failure message.
        assertTrue(failed.saveStarted().saving)
        assertFalse(failed.select(failed.draft.copy(hair = AvatarHair.SHORT)).saveFailed)
    }

    @Test
    fun editsAreIgnoredWhileSaving() {
        val saving = fresh.select(saved.copy(shoes = AvatarShoes.LOAFERS)).saveStarted()
        assertSame(saving, saving.select(saved.copy(hair = AvatarHair.SHORT)))
        assertSame(saving, saving.undo())
        assertSame(saving, saving.resetToSaved())
        assertSame(saving, saving.saveStarted())
    }

    @Test
    fun saveResultsWithoutASaveInFlightAreIgnored() {
        val edited = fresh.select(saved.copy(shoes = AvatarShoes.LOAFERS))
        assertSame(edited, edited.saveSucceeded())
        assertSame(edited, edited.saveFailed())
    }

    // ---- summaries ----

    @Test
    fun reviewSummariesReadNaturally() {
        val d = AvatarConfig.DEFAULT
        assertEquals("Cosy", d.summaryFor(EditorStep.OUTFITS))
        assertEquals("Your own mix", d.copy(shoeColor = AvatarShoeColor.RED).summaryFor(EditorStep.OUTFITS))
        assertEquals("Peach skin, curly hair", d.summaryFor(EditorStep.HEAD_HAIR))
        assertEquals("Dot eyes, no cheeks", d.summaryFor(EditorStep.FACE))
        assertEquals("Purple hoodie", d.summaryFor(EditorStep.TOPS))
        assertEquals("Denim jeans", d.summaryFor(EditorStep.BOTTOMS))
        assertEquals("White sneakers", d.summaryFor(EditorStep.SHOES))
        assertEquals("Cat, no glasses, sunny yellow background", d.summaryFor(EditorStep.EXTRAS))
    }

    // ---- surviving rotation / process death ----

    @Test
    fun editorStateRoundTripsDraftSavedAndStep() {
        val s = fresh.select(saved.copy(bottom = AvatarBottom.CARGO, shoeColor = AvatarShoeColor.BLUE))
            .goTo(EditorStep.BOTTOMS)
        val restored = decodeEditorState(encodeEditorState(s), diskSaved = saved)
        assertEquals(s.saved, restored.saved)
        assertEquals(s.draft, restored.draft)
        assertEquals(s.step, restored.step)
        assertTrue(restored.isDirty)
        assertFalse(restored.saving)
    }

    @Test
    fun restoreTakesTheSavedLookFromDiskNotFromTheSnapshot() {
        // Rotated mid-save: the snapshot was taken while saving, then the write finished.
        val edited = fresh.select(saved.copy(bottom = AvatarBottom.CARGO))
        val snapshot = encodeEditorState(edited.saveStarted())
        val restored = decodeEditorState(snapshot, diskSaved = edited.draft)
        assertEquals(edited.draft, restored.saved)
        assertFalse(restored.isDirty)
        assertFalse(restored.saving)
        assertEquals(CloseDecision.CLOSE_NOW, restored.closeDecision())
        // ...and if the write never landed, the draft is still there and still dirty.
        val notWritten = decodeEditorState(snapshot, diskSaved = saved)
        assertEquals(edited.draft, notWritten.draft)
        assertTrue(notWritten.isDirty)
        assertEquals(saved, notWritten.discard().draft)
    }

    @Test
    fun everyStepRoundTrips() {
        for (step in EditorStep.entries) {
            assertEquals(step, decodeEditorState(encodeEditorState(fresh.goTo(step)), saved).step)
        }
    }

    @Test
    fun unreadableEditorStateStartsFreshFromTheFallback() {
        for (raw in listOf(null, "", "garbage", "e2|step=FACE", "e1|step=NOPE", "e1")) {
            val s = decodeEditorState(raw, saved)
            assertEquals("input: $raw", saved, s.saved)
            assertEquals(saved, s.draft)
            assertEquals(EditorStep.OUTFITS, s.step)
        }
    }
}
