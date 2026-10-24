package com.example.duudle

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.duudle.ui.theme.DuudleColors

// Pickers for the character editor: colour swatches, option grids that preview the
// character wearing each option, and a scrollable text tab row.

private val CellShape = RoundedCornerShape(20.dp)
private val PickerPillShape = RoundedCornerShape(percent = 50)
private val TabRowPadding = 8.dp

/** Small Muted section label above a picker. */
@Composable
internal fun OptionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = DuudleColors.Muted,
        modifier = modifier.padding(bottom = 6.dp)
    )
}

/**
 * Colour circles that wrap onto more lines when the screen is narrow, or stay on one line
 * that scrolls sideways when [singleLine] (short landscape screens). [label] is read by TalkBack.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> SwatchRow(
    options: List<T>,
    selected: T,
    color: (T) -> Color,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false
) {
    val swatches: @Composable () -> Unit = {
        options.forEach { option ->
            val isSelected = option == selected
            val swatch = color(option)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(option) })
                    .semantics { contentDescription = label(option) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .then(
                            if (isSelected) Modifier.border(2.5.dp, DuudleColors.Ink, CircleShape).padding(5.dp) else Modifier
                        )
                        .clip(CircleShape)
                        .background(swatch)
                        .then(
                            // Light swatches would vanish on the cream page without an edge.
                            if (swatch.luminance() > 0.8f) Modifier.border(1.dp, DuudleColors.Hairline, CircleShape) else Modifier
                        )
                )
            }
        }
    }
    if (singleLine) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) { swatches() }
    } else {
        FlowRow(
            modifier = modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) { swatches() }
    }
}

/**
 * Cells in rows of [columns], each showing the character wearing that option, with its
 * label underneath. Cells are [cellAspect] wide per tall (1 = square; portrait for the whole
 * figure) and the character contain-fits inside, never cropped. [itemRoom] fixes the HEAD
 * view box for the whole grid (see DuudleCharacter). Plain rows (not a lazy grid) because
 * the content scrolls as a whole.
 */
@Composable
internal fun <T> OptionGrid(
    options: List<T>,
    isSelected: (T) -> Boolean,
    label: (T) -> String,
    preview: (T) -> AvatarConfig,
    onSelect: (T) -> Unit,
    framing: AvatarFraming,
    columns: Int,
    modifier: Modifier = Modifier,
    cellAspect: Float = 1f,
    itemRoom: Boolean? = null,
    isNone: (T) -> Boolean = { false }
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        options.chunked(columns).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { option ->
                    OptionCell(
                        config = preview(option),
                        label = label(option),
                        selected = isSelected(option),
                        framing = framing,
                        showNoneMark = isNone(option),
                        onClick = { onSelect(option) },
                        cellAspect = cellAspect,
                        itemRoom = itemRoom,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One option: a Paper cell with the character (contain-fitted and centred, whatever the
 * framing's shape) and a label below. Selected: 2dp Ink border, PillGrey fill, check badge.
 * The label text is what TalkBack reads, merged into the selectable.
 */
@Composable
internal fun OptionCell(
    config: AvatarConfig,
    label: String,
    selected: Boolean,
    framing: AvatarFraming,
    showNoneMark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cellAspect: Float = 1f,
    itemRoom: Boolean? = null
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(cellAspect)
                .clip(CellShape)
                .background(if (selected) DuudleColors.PillGrey else DuudleColors.Paper),
            contentAlignment = Alignment.Center
        ) {
            DuudleCharacter(
                config = config,
                modifier = Modifier.fillMaxSize(),
                framing = framing,
                drawBackdrop = false,
                itemRoom = itemRoom
            )
            if (showNoneMark) {
                NoneMark(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(16.dp)
                )
            }
            if (selected) {
                CheckBadge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                )
            }
            // Border last, so the character never draws over it.
            Spacer(
                Modifier
                    .matchParentSize()
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) DuudleColors.Ink else DuudleColors.Hairline,
                        shape = CellShape
                    )
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            color = DuudleColors.Ink,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
        )
    }
}

/** Small Ink circle with a Paper tick. Decorative: the cell's selected state is announced. */
@Composable
internal fun CheckBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(DuudleColors.Ink)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = DuudleColors.Paper, modifier = Modifier.size(15.dp))
    }
}

/** A small slashed circle marking the "None" option. Decorative: the cell is labelled. */
@Composable
internal fun NoneMark(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .clearAndSetSemantics { }
            .drawWithCache {
                val strokeWidth = 1.6.dp.toPx()
                val stroke = Stroke(width = strokeWidth)
                val r = size.minDimension / 2f - strokeWidth / 2f
                val d = r * 0.7071f
                onDrawBehind {
                    drawCircle(DuudleColors.Muted, radius = r, style = stroke)
                    drawLine(
                        DuudleColors.Muted,
                        Offset(center.x - d, center.y + d),
                        Offset(center.x + d, center.y - d),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
    )
}

/**
 * Text tabs that scroll sideways: bold Ink with a short underline when selected, Muted
 * otherwise. The selected tab is scrolled to the middle whenever it changes.
 */
@Composable
internal fun ScrollableTextTabRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    scrollState: ScrollState = rememberScrollState()
) {
    val tabLefts = remember(labels) { IntArray(labels.size) }
    val tabWidths = remember(labels) { IntArray(labels.size) }
    var viewportWidth by remember { mutableIntStateOf(0) }
    val rowPaddingPx = with(LocalDensity.current) { TabRowPadding.roundToPx() }

    LaunchedEffect(selectedIndex, viewportWidth) {
        if (viewportWidth == 0 || selectedIndex !in labels.indices) return@LaunchedEffect
        // Tab positions are measured inside the row's padding, so add it back.
        val centre = rowPaddingPx + tabLefts[selectedIndex] + tabWidths[selectedIndex] / 2
        val target = (centre - viewportWidth / 2).coerceIn(0, scrollState.maxValue)
        if (animate) scrollState.animateScrollTo(target) else scrollState.scrollTo(target)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { viewportWidth = it.width }
            .horizontalScroll(scrollState)
            .selectableGroup()
            .padding(horizontal = TabRowPadding)
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            // Read only while drawing, so the fade doesn't recompose the row every frame.
            val underline = animateFloatAsState(if (selected) 1f else 0f, label = "tab-underline")
            Column(
                modifier = Modifier
                    .onGloballyPositioned {
                        tabLefts[index] = it.positionInParent().x.toInt()
                        tabWidths[index] = it.size.width
                    }
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    label,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) DuudleColors.Ink else DuudleColors.Muted,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(Modifier.height(4.dp))
                Spacer(
                    Modifier
                        .size(width = 18.dp, height = 3.dp)
                        .clip(PickerPillShape)
                        .drawBehind { drawRect(DuudleColors.Ink.copy(alpha = underline.value)) }
                )
            }
        }
    }
}
