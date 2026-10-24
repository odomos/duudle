package com.example.duudle

// State for the Edit Character screen. Pure Kotlin (no Android or Compose) so every rule is
// unit-testable: each function returns a new state and never touches storage. The screen
// writes to AvatarStore only on the final Save, and marks the look saved only once that
// write has succeeded.

/** The editor's steps, in order. */
enum class EditorStep(val title: String) {
    OUTFITS("Outfits"),
    HEAD_HAIR("Head & Hair"),
    FACE("Face"),
    TOPS("Tops"),
    BOTTOMS("Bottoms"),
    SHOES("Shoes"),
    EXTRAS("Extras"),
    REVIEW("Review"),
}

/** What closing (X or system back) should do. */
enum class CloseDecision { CLOSE_NOW, CONFIRM_DISCARD }

/** How many earlier looks Undo remembers. */
const val EDITOR_UNDO_LIMIT = 50

/**
 * [saved] is what's on disk; [draft] is what's on screen. [undo] holds earlier drafts
 * (newest last), [redo] the drafts undone (newest last). While [saving], edits are ignored
 * so the look being written can't change underneath the save.
 */
data class CharacterEditorState(
    val saved: AvatarConfig,
    val draft: AvatarConfig = saved,
    val step: EditorStep = EditorStep.OUTFITS,
    val undo: List<AvatarConfig> = emptyList(),
    val redo: List<AvatarConfig> = emptyList(),
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    val isDirty: Boolean get() = draft != saved
    val canUndo: Boolean get() = !saving && undo.isNotEmpty()
    val canRedo: Boolean get() = !saving && redo.isNotEmpty()
    val canReset: Boolean get() = !saving && isDirty
    val canSave: Boolean get() = !saving && isDirty

    val stepIndex: Int get() = step.ordinal
    val stepCount: Int get() = EditorStep.entries.size
    val previousStep: EditorStep? get() = EditorStep.entries.getOrNull(stepIndex - 1)
    val nextStep: EditorStep? get() = EditorStep.entries.getOrNull(stepIndex + 1)

    /** The main button: "Next: Head & Hair" ... "Next: Review", then "Save character". */
    val nextLabel: String
        get() = nextStep?.let { "Next: ${it.title}" } ?: "Save character"

    /** Shows [newDraft]; the old draft goes on the undo stack and redo is cleared. */
    fun select(newDraft: AvatarConfig): CharacterEditorState {
        if (saving || newDraft == draft) return this
        return copy(
            draft = newDraft,
            undo = (undo + draft).takeLast(EDITOR_UNDO_LIMIT),
            redo = emptyList(),
            saveFailed = false,
        )
    }

    fun next(): CharacterEditorState = nextStep?.let { goTo(it) } ?: this
    fun back(): CharacterEditorState = previousStep?.let { goTo(it) } ?: this
    fun goTo(step: EditorStep): CharacterEditorState = if (step == this.step) this else copy(step = step)

    fun undo(): CharacterEditorState {
        if (!canUndo) return this
        return copy(
            draft = undo.last(),
            undo = undo.dropLast(1),
            redo = (redo + draft).takeLast(EDITOR_UNDO_LIMIT),
            saveFailed = false,
        )
    }

    fun redo(): CharacterEditorState {
        if (!canRedo) return this
        return copy(
            draft = redo.last(),
            redo = redo.dropLast(1),
            undo = (undo + draft).takeLast(EDITOR_UNDO_LIMIT),
            saveFailed = false,
        )
    }

    /** Back to the saved look, as one undoable change. */
    fun resetToSaved(): CharacterEditorState = if (!canReset) this else select(saved)

    /** Ask before closing only when there's something to lose. The screen ignores close while saving. */
    fun closeDecision(): CloseDecision = if (isDirty) CloseDecision.CONFIRM_DISCARD else CloseDecision.CLOSE_NOW

    /** Drops the draft; the caller then leaves the editor. */
    fun discard(): CharacterEditorState =
        copy(draft = saved, undo = emptyList(), redo = emptyList(), saving = false, saveFailed = false)

    fun saveStarted(): CharacterEditorState = if (!canSave) this else copy(saving = true, saveFailed = false)

    /** The write reached disk: the draft is now the saved look. */
    fun saveSucceeded(): CharacterEditorState {
        if (!saving) return this
        return copy(saved = draft, saving = false, saveFailed = false, undo = emptyList(), redo = emptyList())
    }

    /** The write failed: keep the draft (still dirty) so the user can try again. */
    fun saveFailed(): CharacterEditorState = if (!saving) this else copy(saving = false, saveFailed = true)
}

/** The preset whose six clothing fields the look wears exactly, if any. */
fun AvatarConfig.matchingPreset(): AvatarOutfitPreset? = AvatarOutfitPresets.firstOrNull { matches(it) }

/** One-line summary of what a step controls, for the Review step, e.g. "Peach skin, curly hair". */
fun AvatarConfig.summaryFor(step: EditorStep): String {
    val text = when (step) {
        EditorStep.OUTFITS -> matchingPreset()?.label ?: "Your own mix"
        EditorStep.HEAD_HAIR -> "${skin.label} skin, ${hair.label.lowercase()} hair"
        EditorStep.FACE -> "${eyes.label}, ${cheeks.label.lowercase()}"
        EditorStep.TOPS -> "${outfitColor.label} ${outfit.label.lowercase()}"
        EditorStep.BOTTOMS -> "${bottomColor.label} ${bottom.label.lowercase()}"
        EditorStep.SHOES -> "${shoeColor.label} ${shoes.label.lowercase()}"
        EditorStep.EXTRAS -> "${item.label}, ${glasses.label.lowercase()}, ${backdrop.label.lowercase()} background"
        EditorStep.REVIEW -> describe()
    }
    return text.replaceFirstChar { it.uppercaseChar() }
}

// ---- Saving the editor across rotation and process death ------------------------------

private const val EDITOR_STATE_VERSION = "e1"

/**
 * e.g. "e1|step=FACE|draft=v1;hair=...". The saved look is deliberately not kept: on
 * restore it's re-read from disk (see [decodeEditorState]). Undo/redo and the in-flight
 * save flag aren't kept either: the draft survives (still dirty if it wasn't saved), so the
 * user can simply save again.
 */
fun encodeEditorState(state: CharacterEditorState): String =
    "$EDITOR_STATE_VERSION|step=${state.step.name}|draft=${state.draft.encode()}"

/**
 * Reads [encodeEditorState]'s format. [diskSaved] is what's on disk now and always becomes
 * the saved look: a save can finish after the state was captured (e.g. rotating mid-save
 * cancels the screen but not the write), and then disk is the truth, so the draft isn't
 * wrongly dirty and Discard really goes back to what's stored. Anything unreadable starts
 * fresh from [diskSaved]. Never throws.
 */
fun decodeEditorState(raw: String?, diskSaved: AvatarConfig): CharacterEditorState {
    val fresh = CharacterEditorState(saved = diskSaved)
    if (raw.isNullOrBlank()) return fresh
    val parts = raw.split('|')
    if (parts.first() != EDITOR_STATE_VERSION) return fresh
    val values = parts.drop(1).mapNotNull { part ->
        val eq = part.indexOf('=')
        if (eq <= 0) null else part.substring(0, eq) to part.substring(eq + 1)
    }.toMap()
    val draft = values["draft"]?.let { decodeAvatarConfig(it) } ?: diskSaved
    val step = EditorStep.entries.firstOrNull { it.name == values["step"] } ?: EditorStep.OUTFITS
    return CharacterEditorState(saved = diskSaved, draft = draft, step = step)
}
