package com.example.duudle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.duudle.ui.theme.DuudleColors
import com.example.duudle.ui.theme.DuudleDisplayFont
import com.example.duudle.ui.theme.Pastel

// Small building blocks shared by the restyled screens (Vault, Friends).

private val ComponentPillShape = RoundedCornerShape(percent = 50)

/**
 * Screen root column. [color] fills it and is also painted up into the status-bar strip
 * and down into the navigation-bar strip, which the Scaffold padding leaves outside the
 * screen (the Scaffold behind is white). Pass the Scaffold-padded modifier in.
 */
@Composable
fun DuudlePage(
    modifier: Modifier = Modifier,
    color: Color = DuudleColors.Cream,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    val top = WindowInsets.statusBars.getTop(density).toFloat()
    val bottom = WindowInsets.navigationBars.getBottom(density).toFloat()
    Column(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(color, topLeft = Offset(0f, -top), size = Size(size.width, size.height + top + bottom))
            },
        content = content
    )
}

/**
 * Back arrow, centred heavy title (a heading for TalkBack) and an optional 48dp
 * [trailing] slot. The slot is always reserved so the title stays centred.
 */
@Composable
fun DuudleScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = DuudleColors.Ink)
        }
        Text(
            title,
            fontFamily = DuudleDisplayFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
            color = DuudleColors.Ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            trailing?.invoke()
        }
    }
}

/**
 * White rounded tab control; the selected option is an Ink pill. [badges] holds an
 * optional count per option, shown only when above 0.
 */
@Composable
fun DuudleSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badges: List<Int> = emptyList()
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(DuudleColors.Paper)
            .border(1.dp, DuudleColors.Hairline, shape)
            .padding(4.dp)
            .selectableGroup()
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) DuudleColors.Ink else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    color = if (selected) DuudleColors.Paper else DuudleColors.Ink,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 1
                )
                val badge = badges.getOrElse(index) { 0 }
                if (badge > 0) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 20.dp)
                            .height(20.dp)
                            .clip(ComponentPillShape)
                            .background(DuudleColors.ErrorRed)
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(badge.toString(), color = DuudleColors.Paper, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** 48dp black pill button with white bold text and an optional leading [icon]. */
@Composable
fun DuudleInkPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(ComponentPillShape)
            .background(DuudleColors.Ink)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 28.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = DuudleColors.Paper, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = DuudleColors.Paper, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

/** 48dp outlined pill: the quieter partner of [DuudleInkPill]. */
@Composable
fun DuudleOutlinePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(ComponentPillShape)
            .border(1.5.dp, DuudleColors.Ink.copy(alpha = 0.3f), ComponentPillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = DuudleColors.Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

private val UidPastels = listOf(
    DuudleColors.Mint, DuudleColors.Lilac, DuudleColors.Bubblegum, DuudleColors.Sunny,
    DuudleColors.Tangerine, DuudleColors.Sky, DuudleColors.Coral
)

/** The pastel for a user's avatar: always the same for a given uid. */
fun avatarPastelFor(uid: String): Pastel = UidPastels[Math.floorMod(uid.hashCode(), UidPastels.size)]

/** A user's emoji on their pastel circle. Decorative: hidden from TalkBack. */
@Composable
fun DuudleAvatar(
    uid: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    emoji: String = emojiForUid(uid)
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarPastelFor(uid).light)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = (size.value * 0.48f).sp)
    }
}
