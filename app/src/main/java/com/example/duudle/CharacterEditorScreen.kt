package com.example.duudle

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.duudle.ui.theme.DuudleColors
import com.example.duudle.ui.theme.DuudleDisplayFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The Edit Character screen: a step-by-step editor over a draft of the look. Nothing is
// written until Save, and the look counts as saved only once AvatarStore.save returns true.
// All the rules live in CharacterEditorState (CharacterEditor.kt); this file is layout.

private val EditorPillShape = RoundedCornerShape(percent = 50)
private val PreviewShape = RoundedCornerShape(28.dp)
private val DialogShape = RoundedCornerShape(28.dp)

private const val PREVIEW_HEIGHT_FRACTION = 0.30f
private val PreviewMinHeight = 150.dp
private val PreviewMaxHeight = 260.dp
private val WideLayoutMinWidth = 600.dp
private val FourColumnMinWidth = 520.dp
// Below this height (landscape phones) the editor goes compact: no section labels, swatches
// on one line, a shorter action bar and smaller cells, so a full row of options is still
// visible.
private val CompactMaxHeight = 480.dp
private val CompactTargetCell = 92.dp
// Outfit cells show the whole figure, so they're portrait.
private const val OUTFIT_CELL_ASPECT = 0.75f

@Composable
fun CharacterEditorScreen(
    modifier: Modifier = Modifier,
    currentUid: String,
    onClose: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val loaded = remember(currentUid) { AvatarStore.load(context, currentUid) }
    var state by rememberSaveable(
        currentUid,
        stateSaver = Saver<CharacterEditorState, String>(
            save = { encodeEditorState(it) },
            restore = { decodeEditorState(it, loaded) }
        )
    ) { mutableStateOf(CharacterEditorState(saved = loaded)) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun requestClose() {
        if (state.saving) return
        when (state.closeDecision()) {
            CloseDecision.CLOSE_NOW -> onClose()
            CloseDecision.CONFIRM_DISCARD -> showDiscardDialog = true
        }
    }

    fun save() {
        val started = state.saveStarted()
        if (!started.saving || state.saving) return
        state = started
        val look = started.draft
        val appContext = context.applicationContext
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    AvatarStore.save(appContext, currentUid, look)
                } catch (e: Exception) {
                    Log.e("DuudleDebug", "Saving the character failed: ${e.message}")
                    false
                }
            }
            if (ok) {
                state = state.saveSucceeded()
                onSaved()
            } else {
                state = state.saveFailed()
            }
        }
    }

    // Wins over MainActivity's handler (registered later), so back never skips the dialog.
    BackHandler(enabled = true) { requestClose() }

    DuudlePage(modifier = modifier) {
        EditorHeader(
            canSave = state.canSave,
            saving = state.saving,
            onClose = ::requestClose,
            onSave = ::save
        )
        HorizontalDivider(color = DuudleColors.Hairline)

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val wide = maxWidth >= WideLayoutMinWidth
            // Depends only on the window, never on the step, so the layout doesn't jump.
            val previewHeight = (maxHeight * PREVIEW_HEIGHT_FRACTION).coerceIn(PreviewMinHeight, PreviewMaxHeight)
            val previewPaneWidth = min(maxWidth * 0.45f, 480.dp)
            val compact = maxHeight < CompactMaxHeight
            val controls: @Composable (Modifier) -> Unit = { controlsModifier ->
                EditorControls(
                    state = state,
                    compact = compact,
                    onStateChange = { state = it },
                    onSave = ::save,
                    modifier = controlsModifier
                )
            }
            val preview: @Composable (Modifier) -> Unit = { previewModifier ->
                EditorPreviewCard(
                    config = state.draft,
                    canUndo = state.canUndo,
                    canRedo = state.canRedo,
                    canReset = state.canReset,
                    onUndo = { state = state.undo() },
                    onRedo = { state = state.redo() },
                    onReset = { state = state.resetToSaved() },
                    modifier = previewModifier
                )
            }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    preview(
                        Modifier
                            .width(previewPaneWidth)
                            .fillMaxHeight()
                            .padding(20.dp)
                    )
                    controls(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    preview(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 12.dp)
                            .height(previewHeight)
                    )
                    controls(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showDiscardDialog) {
        DiscardDialog(
            onKeepEditing = { showDiscardDialog = false },
            onDiscard = {
                showDiscardDialog = false
                state = state.discard()
                onClose()
            }
        )
    }
}

// ===== HEADER =====

@Composable
private fun EditorHeader(canSave: Boolean, saving: Boolean, onClose: () -> Unit, onSave: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = DuudleColors.Ink)
        }
        Text(
            "Edit Character",
            fontFamily = DuudleDisplayFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            color = DuudleColors.Ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        SavePill(
            text = "Save",
            enabled = canSave,
            saving = saving,
            onClick = onSave,
            modifier = Modifier.padding(end = 8.dp)
        )
    }
}

/** Compact Ink pill; Muted on PillGrey when disabled; spinner + "Saving…" while saving. */
@Composable
private fun SavePill(text: String, enabled: Boolean, saving: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val active = enabled && !saving
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(EditorPillShape)
            .background(if (enabled || saving) DuudleColors.Ink else DuudleColors.PillGrey)
            .clickable(enabled = active, role = Role.Button, onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (saving) {
            CircularProgressIndicator(
                color = DuudleColors.Paper,
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            if (saving) "Saving…" else text,
            color = if (enabled || saving) DuudleColors.Paper else DuudleColors.Muted,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            maxLines = 1
        )
    }
}

// ===== PREVIEW =====

/**
 * The draft in a rounded card on its backdrop colour, with Undo / Redo (left corners) and
 * Reset to saved (top right). The character pops a little, from the feet, on every change.
 */
@Composable
private fun EditorPreviewCard(
    config: AvatarConfig,
    canUndo: Boolean,
    canRedo: Boolean,
    canReset: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motionEnabled = rememberMotionEnabled()
    val pop = remember { Animatable(1f) }
    val isFirstConfig = remember { mutableStateOf(true) }
    LaunchedEffect(config) {
        if (isFirstConfig.value) {
            isFirstConfig.value = false
            return@LaunchedEffect
        }
        if (!motionEnabled) return@LaunchedEffect
        pop.animateTo(1.03f, spring(stiffness = Spring.StiffnessHigh))
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }

    Box(
        modifier = modifier
            .clip(PreviewShape)
            .background(Color(config.backdrop.color))
            .border(1.dp, DuudleColors.Hairline, PreviewShape),
        contentAlignment = Alignment.Center
    ) {
        DuudleCharacter(
            config = config,
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 6.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
            framing = AvatarFraming.FULL,
            drawBackdrop = false,
            contentDescription = "Your character: ${config.describe()}"
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CornerButton(description = "Undo", enabled = canUndo, onClick = onUndo) { tint ->
                UndoRedoIcon(redo = false, color = tint)
            }
            CornerButton(description = "Redo", enabled = canRedo, onClick = onRedo) { tint ->
                UndoRedoIcon(redo = true, color = tint)
            }
        }
        // A word, not a third arrow icon, so it can't be confused with Redo.
        ResetPill(
            enabled = canReset,
            onClick = onReset,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
        )
    }
}

/** Small Paper pill reading "Reset" (48dp touch target); 38% alpha and disabled when [enabled] is false. */
@Composable
private fun ResetPill(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .alpha(if (enabled) 1f else 0.38f)
            .clip(EditorPillShape)
            .background(DuudleColors.Paper)
            .border(1.5.dp, DuudleColors.Ink.copy(alpha = 0.2f), EditorPillShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Reset to saved character" }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Reset",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = DuudleColors.Ink,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics { }
        )
    }
}

/** 48dp Paper circle with an Ink icon; 38% alpha and disabled semantics when [enabled] is false. */
@Composable
private fun CornerButton(
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (Color) -> Unit
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(CircleShape)
            .background(DuudleColors.Paper)
            .border(1.5.dp, DuudleColors.Ink.copy(alpha = 0.2f), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        icon(DuudleColors.Ink)
    }
}

/** A U-turn arrow (no Undo/Redo in the core icon set, so drawn by hand); [redo] mirrors it. */
@Composable
private fun UndoRedoIcon(redo: Boolean, color: Color) {
    Spacer(
        modifier = Modifier
            .size(24.dp)
            .drawWithCache {
                val u = size.minDimension / 24f
                fun x(v: Float) = (if (redo) 24f - v else v) * u
                val arrow = Path().apply {
                    moveTo(x(9f), 5.5f * u)
                    lineTo(x(5.5f), 9f * u)
                    lineTo(x(9f), 12.5f * u)
                    moveTo(x(5.5f), 9f * u)
                    lineTo(x(14f), 9f * u)
                    quadraticTo(x(19.5f), 9f * u, x(19.5f), 14f * u)
                    quadraticTo(x(19.5f), 19f * u, x(14f), 19f * u)
                    lineTo(x(10f), 19f * u)
                }
                val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                onDrawBehind { drawPath(arrow, color, style = stroke) }
            }
    )
}

// ===== CONTROLS =====

@Composable
private fun EditorControls(
    state: CharacterEditorState,
    compact: Boolean,
    onStateChange: (CharacterEditorState) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motionEnabled = rememberMotionEnabled()
    BoxWithConstraints(modifier = modifier) {
        val columns = when {
            // Enough columns that a cell (plus its label) fits under the tabs and swatches.
            compact -> maxOf(3, ((maxWidth - 40.dp) / CompactTargetCell).toInt())
            maxWidth >= FourColumnMinWidth -> 4
            else -> 3
        }
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
            EditorProgress(
                step = state.step,
                stepIndex = state.stepIndex,
                stepCount = state.stepCount,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(4.dp))
            ScrollableTextTabRow(
                labels = EditorStep.entries.map { it.title },
                selectedIndex = state.stepIndex,
                onSelect = { onStateChange(state.goTo(EditorStep.entries[it])) },
                animate = motionEnabled
            )

            // Only the current step is composed; its scroll position starts at the top.
            key(state.step) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = if (compact) 4.dp else 8.dp, bottom = 16.dp)
                ) {
                    StepContent(
                        state = state,
                        columns = columns,
                        compact = compact,
                        onPick = { onStateChange(state.select(it)) },
                        onGoTo = { onStateChange(state.goTo(it)) }
                    )
                }
            }

            if (state.saveFailed) {
                SaveFailedBanner(
                    onRetry = onSave,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
                )
            }
            HorizontalDivider(color = DuudleColors.Hairline)
            EditorActionBar(
                state = state,
                compact = compact,
                onBack = { onStateChange(state.back()) },
                onPrimary = { if (state.nextStep == null) onSave() else onStateChange(state.next()) }
            )
        }
    }
}

/**
 * 8 thin segments, Ink up to and including the current step. There's no visible step
 * heading (the tabs and this bar already show the step), so this row is also the polite
 * live region that tells TalkBack "Face, step 3 of 8" when the step changes, in every layout.
 */
@Composable
private fun EditorProgress(step: EditorStep, stepIndex: Int, stepCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clearAndSetSemantics {
                contentDescription = "${step.title}, step ${stepIndex + 1} of $stepCount"
                liveRegion = LiveRegionMode.Polite
            },
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(stepCount) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(EditorPillShape)
                    .background(if (i <= stepIndex) DuudleColors.Ink else DuudleColors.Hairline)
            )
        }
    }
}

@Composable
private fun StepContent(
    state: CharacterEditorState,
    columns: Int,
    compact: Boolean,
    onPick: (AvatarConfig) -> Unit,
    onGoTo: (EditorStep) -> Unit
) {
    val draft = state.draft
    val sectionGap = if (compact) 8.dp else 16.dp
    // Section labels are dropped in compact mode; the cells and swatches are labelled anyway.
    @Composable
    fun Section(text: String) {
        if (!compact) OptionLabel(text)
    }
    when (state.step) {
        EditorStep.OUTFITS -> {
            if (draft.matchingPreset() == null) {
                Text(
                    "Your own mix",
                    fontSize = 13.sp,
                    color = DuudleColors.Muted,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            OptionGrid(
                options = AvatarOutfitPresets,
                isSelected = { draft.matches(it) },
                label = { it.label },
                preview = { draft.withOutfit(it) },
                onSelect = { onPick(draft.withOutfit(it)) },
                framing = AvatarFraming.FULL,
                columns = columns,
                cellAspect = OUTFIT_CELL_ASPECT
            )
        }
        EditorStep.HEAD_HAIR -> {
            Section("Skin tone")
            SwatchRow(
                options = AvatarSkin.entries,
                selected = draft.skin,
                color = { Color(it.color) },
                label = { "Skin: ${it.label}" },
                onSelect = { onPick(draft.copy(skin = it)) },
                singleLine = compact
            )
            Spacer(Modifier.height(sectionGap))
            Section("Hair")
            OptionGrid(
                options = AvatarHair.entries,
                isSelected = { draft.hair == it },
                label = { it.label },
                // Without the item, so the cat doesn't hide the hair.
                preview = { draft.copy(hair = it, item = AvatarItem.NONE) },
                onSelect = { onPick(draft.copy(hair = it)) },
                framing = AvatarFraming.HEAD,
                columns = columns,
                itemRoom = false
            )
        }
        EditorStep.FACE -> {
            Section("Eyes")
            OptionGrid(
                options = AvatarEyes.entries,
                isSelected = { draft.eyes == it },
                label = { it.label },
                preview = { draft.copy(eyes = it, item = AvatarItem.NONE, glasses = AvatarGlasses.NONE) },
                onSelect = { onPick(draft.copy(eyes = it)) },
                framing = AvatarFraming.FACE,
                columns = columns
            )
            Spacer(Modifier.height(sectionGap))
            Section("Cheeks")
            OptionGrid(
                options = AvatarCheeks.entries,
                isSelected = { draft.cheeks == it },
                label = { it.label },
                preview = { draft.copy(cheeks = it, item = AvatarItem.NONE, glasses = AvatarGlasses.NONE) },
                onSelect = { onPick(draft.copy(cheeks = it)) },
                framing = AvatarFraming.FACE,
                columns = columns,
                isNone = { it == AvatarCheeks.NONE }
            )
        }
        EditorStep.TOPS -> {
            Section("Top colour")
            SwatchRow(
                options = AvatarOutfitColor.entries,
                selected = draft.outfitColor,
                color = { Color(it.color) },
                label = { "Top colour: ${it.label}" },
                onSelect = { onPick(draft.copy(outfitColor = it)) },
                singleLine = compact
            )
            Spacer(Modifier.height(sectionGap))
            Section("Top")
            OptionGrid(
                options = AvatarOutfit.entries,
                isSelected = { draft.outfit == it },
                label = { it.label },
                preview = { draft.copy(outfit = it) },
                onSelect = { onPick(draft.copy(outfit = it)) },
                framing = AvatarFraming.TORSO,
                columns = columns
            )
        }
        EditorStep.BOTTOMS -> {
            Section("Bottoms colour")
            SwatchRow(
                options = AvatarBottomColor.entries,
                selected = draft.bottomColor,
                color = { Color(it.color) },
                label = { "Bottoms colour: ${it.label}" },
                onSelect = { onPick(draft.copy(bottomColor = it)) },
                singleLine = compact
            )
            Spacer(Modifier.height(sectionGap))
            Section("Bottoms")
            OptionGrid(
                options = AvatarBottom.entries,
                isSelected = { draft.bottom == it },
                label = { it.label },
                preview = { draft.copy(bottom = it) },
                onSelect = { onPick(draft.copy(bottom = it)) },
                framing = AvatarFraming.LEGS,
                columns = columns
            )
        }
        EditorStep.SHOES -> {
            Section("Shoe colour")
            SwatchRow(
                options = AvatarShoeColor.entries,
                selected = draft.shoeColor,
                color = { Color(it.color) },
                label = { "Shoe colour: ${it.label}" },
                onSelect = { onPick(draft.copy(shoeColor = it)) },
                singleLine = compact
            )
            Spacer(Modifier.height(sectionGap))
            Section("Shoes")
            OptionGrid(
                options = AvatarShoes.entries,
                isSelected = { draft.shoes == it },
                label = { it.label },
                preview = { draft.copy(shoes = it) },
                onSelect = { onPick(draft.copy(shoes = it)) },
                framing = AvatarFraming.FEET,
                columns = columns
            )
        }
        EditorStep.EXTRAS -> {
            Section("On your head")
            OptionGrid(
                options = AvatarItem.entries,
                isSelected = { draft.item == it },
                label = { it.label },
                preview = { draft.copy(item = it) },
                onSelect = { onPick(draft.copy(item = it)) },
                framing = AvatarFraming.HEAD,
                columns = columns,
                // Same headroom in every cell, "No item" included.
                itemRoom = true,
                isNone = { it == AvatarItem.NONE }
            )
            Spacer(Modifier.height(sectionGap))
            Section("Glasses")
            OptionGrid(
                options = AvatarGlasses.entries,
                isSelected = { draft.glasses == it },
                label = { it.label },
                preview = { draft.copy(glasses = it, item = AvatarItem.NONE) },
                onSelect = { onPick(draft.copy(glasses = it)) },
                framing = AvatarFraming.FACE,
                columns = columns,
                isNone = { it == AvatarGlasses.NONE }
            )
            Spacer(Modifier.height(sectionGap))
            Section("Background")
            SwatchRow(
                options = AvatarBackdrop.entries,
                selected = draft.backdrop,
                color = { Color(it.color) },
                label = { "Background: ${it.label}" },
                onSelect = { onPick(draft.copy(backdrop = it)) },
                singleLine = compact
            )
        }
        EditorStep.REVIEW -> ReviewContent(draft = draft, onGoTo = onGoTo)
    }
}

/** One row per step: its title, a short summary of the draft, and a "Change" pill. */
@Composable
private fun ReviewContent(draft: AvatarConfig, onGoTo: (EditorStep) -> Unit) {
    EditorStep.entries.filter { it != EditorStep.REVIEW }.forEach { step ->
        val summary = draft.summaryFor(step)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { }
            ) {
                Text(step.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DuudleColors.Ink)
                Text(
                    summary,
                    fontSize = 13.sp,
                    color = DuudleColors.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(EditorPillShape)
                    .border(1.5.dp, DuudleColors.Ink.copy(alpha = 0.3f), EditorPillShape)
                    .clickable(role = Role.Button, onClick = { onGoTo(step) })
                    .semantics { contentDescription = "Change ${step.title}" }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text("Change", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DuudleColors.Ink)
            }
        }
        HorizontalDivider(color = DuudleColors.Hairline)
    }
}

/** Shown after a failed save, above the action bar; announced politely. */
@Composable
private fun SaveFailedBanner(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(DuudleColors.Paper)
            .border(1.dp, DuudleColors.ErrorRed, shape)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Couldn't save your character. Your changes are still here.",
            fontSize = 13.sp,
            color = DuudleColors.Ink,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(EditorPillShape)
                .background(DuudleColors.Ink)
                .clickable(role = Role.Button, onClick = onRetry)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text("Try again", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DuudleColors.Paper)
        }
    }
}

/**
 * 72dp bar: an outlined Back circle (none on the first step, where the pill takes the full
 * width) and the main pill. The pill says "Next: <step>" (falling back to "Next" when that won't fit, with
 * the full text still read out), and "Save character" on Review.
 */
@Composable
private fun EditorActionBar(
    state: CharacterEditorState,
    compact: Boolean,
    onBack: () -> Unit,
    onPrimary: () -> Unit
) {
    val nothingToSave = state.nextStep == null && !state.isDirty && !state.saving
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 60.dp else 72.dp)
            .padding(horizontal = 20.dp, vertical = if (nothingToSave) 6.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val previous = state.previousStep
        if (previous != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, DuudleColors.Ink.copy(alpha = 0.3f), CircleShape)
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = "Back to ${previous.title}" },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = DuudleColors.Ink)
            }
            Spacer(Modifier.width(12.dp))
        }
        val isSave = state.nextStep == null
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            PrimaryActionPill(
                label = state.nextLabel,
                enabled = if (isSave) state.canSave else true,
                saving = isSave && state.saving,
                onClick = onPrimary,
                modifier = Modifier.fillMaxWidth()
            )
            // Helper under the disabled Save, inside the bar, so it can't be mistaken for
            // part of the review list.
            if (nothingToSave) {
                Text(
                    "No changes to save",
                    fontSize = 12.sp,
                    color = DuudleColors.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private val PrimaryLabelStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
private val PrimaryPillPadding = 20.dp

@Composable
private fun PrimaryActionPill(
    label: String,
    enabled: Boolean,
    saving: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val available: Dp = maxWidth - PrimaryPillPadding * 2
        val fits = remember(label, available, density) {
            val width = measurer.measure(label, PrimaryLabelStyle, maxLines = 1, softWrap = false).size.width
            with(density) { width <= available.toPx() }
        }
        val shown = when {
            saving -> "Saving…"
            fits -> label
            else -> "Next"
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(EditorPillShape)
                .background(if (enabled || saving) DuudleColors.Ink else DuudleColors.PillGrey)
                .clickable(enabled = enabled && !saving, role = Role.Button, onClick = onClick)
                // The full label is always what's read, even when "Next" is shown.
                .semantics { contentDescription = if (saving) "Saving" else label }
                .padding(horizontal = PrimaryPillPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (saving) {
                CircularProgressIndicator(color = DuudleColors.Paper, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                shown,
                style = PrimaryLabelStyle,
                color = if (enabled || saving) DuudleColors.Paper else DuudleColors.Muted,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
    }
}

// ===== DIALOG =====

/** "Unsaved work". Tapping outside or back = Keep editing. Buttons stack (Keep editing first) when narrow. */
@Composable
private fun DiscardDialog(onKeepEditing: () -> Unit, onDiscard: () -> Unit) {
    // Our own width (not the platform's), so a 320dp screen still keeps 16dp side margins.
    Dialog(onDismissRequest = onKeepEditing, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .clip(DialogShape)
                .background(DuudleColors.Paper)
                .padding(24.dp)
        ) {
            Text(
                "Unsaved work",
                fontFamily = DuudleDisplayFont,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = DuudleColors.Ink,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Are you sure you want to discard your changes?",
                fontSize = 15.sp,
                color = DuudleColors.Muted
            )
            Spacer(Modifier.height(20.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val keep: @Composable (Modifier) -> Unit = { m ->
                    DialogPill("Keep editing", filled = false, onClick = onKeepEditing, modifier = m)
                }
                val discard: @Composable (Modifier) -> Unit = { m ->
                    DialogPill("Discard", filled = true, onClick = onDiscard, modifier = m)
                }
                if (maxWidth < 300.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        keep(Modifier.fillMaxWidth())
                        discard(Modifier.fillMaxWidth())
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        keep(Modifier.weight(1f))
                        discard(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** 48dp pill: outlined Ink text, or [filled] ErrorRed with Paper text. */
@Composable
private fun DialogPill(text: String, filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(EditorPillShape)
            .then(
                if (filled) {
                    Modifier.background(DuudleColors.ErrorRed)
                } else {
                    Modifier.border(1.5.dp, DuudleColors.Ink.copy(alpha = 0.3f), EditorPillShape)
                }
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (filled) DuudleColors.Paper else DuudleColors.Ink,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
