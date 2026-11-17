package com.example.duudle

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.duudle.ui.theme.DuudleColors

// Stickers (the code still calls them flash cards: that's the name in the stored data). A sticker
// is a ready-made image sent like a doodle: a normal doodles doc with empty strokes plus
// "cardId"; the Cloud Function copies cardId into the push. Only the id is ever sent, the
// image ships inside the app, so a pop-up needs no download.
//
// Stickers live in PACKS (Everyday, later Cats, Dogs...). To add a pack, follow stickers/README.md.

/**
 * [res] is the full-size image (overlay, viewer, Vault detail); [thumbRes] a smaller one for
 * small cells, which for vector stickers is just the same drawable. [label] is display and
 * TalkBack text only, never stored. [keywords] only feed the picker's search. [motion] is the
 * loop it plays when shown big.
 */
data class FlashCard(
    val id: String,
    @DrawableRes val res: Int,
    val label: String,
    val keywords: List<String> = emptyList(),
    val motion: StickerMotion = StickerMotion.NONE,
    @DrawableRes val thumbRes: Int = res
)

/** A themed set of stickers shown together in the picker. */
data class StickerPack(val id: String, val title: String, val stickers: List<FlashCard>)

// The packs themselves (StickerPacks) live in StickerPacks.kt.

/** All stickers in all packs, in pack order. */
val FlashCardCatalog: List<FlashCard> = StickerPacks.flatMap { it.stickers }

/**
 * Cards taken out for good. card01, 02 and 05 (Phase 8b) carried third-party watermarks; the
 * rest of the old image set (03, 04, 06 to 09) was replaced by original sticker art because the
 * rights to those images couldn't be confirmed. Their ids stay reserved; doodles that used them
 * show "no longer available" instead of "update".
 */
val RetiredFlashCardIds: Set<String> =
    setOf("card01", "card02", "card03", "card04", "card05", "card06", "card07", "card08", "card09")

/** Null for a missing id or one this build doesn't have (a newer app sent it). */
fun flashCardById(id: String?): FlashCard? = if (id == null) null else FlashCardCatalog.firstOrNull { it.id == id }

/**
 * The picker's search: every word typed must be the start of a word in a sticker's label, its
 * keywords or its pack title (case-insensitive), so "cat" finds cats but not "location", and "lov"
 * finds "love". An empty [query] lists everything. [packId] null = all packs.
 */
private fun searchWords(s: String): List<String> = s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

fun searchStickers(query: String, packId: String? = null, packs: List<StickerPack> = StickerPacks): List<FlashCard> {
    val words = searchWords(query)
    return packs.filter { packId == null || it.id == packId }.flatMap { pack ->
        val packTokens = searchWords(pack.title)
        pack.stickers.filter { s ->
            val tokens = packTokens + searchWords(s.label) + s.keywords.flatMap { searchWords(it) }
            words.all { w -> tokens.any { it.startsWith(w) } }
        }
    }
}

/** The cardId from a push or snooze file: blank means "not a card". Unknown ids are kept. */
fun cardIdFromPayload(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }

/**
 * True for an embedded strokes payload of "[]". Real doodles always have strokes, so this
 * is a flash card pushed by a Cloud Function that predates cardId: fetch the doc instead.
 */
fun isEmptyStrokesPayload(json: String?): Boolean = json != null && json.trim().matches(Regex("""\[\s*]"""))

fun flashCardSentLine(senderUsername: String): String = "@$senderUsername sent you a sticker"

/** Shown instead of a sticker this build doesn't have. [senderUsername] null = I sent it. */
fun unknownFlashCardText(cardId: String, senderUsername: String?): String = when {
    cardId in RetiredFlashCardIds -> "This sticker is no longer available"
    senderUsername == null -> "Update Duudle to see this sticker"
    else -> "@$senderUsername sent a sticker — update Duudle to see it"
}

/**
 * What's inside a doodle frame: the strokes, or the flash card image when [cardId] is set
 * (the placeholder text if this build doesn't know the card). [modifier] is the caller's
 * square frame; the content fills it.
 */
@Composable
fun DoodleOrCard(
    strokes: List<DoodleStroke>,
    cardId: String?,
    modifier: Modifier = Modifier,
    strokeWidthScale: Float = 1f,
    placeholderName: String? = null,
    // Small cells: use the pre-resized thumbnail. Thumbnails sit inside a labelled card, so
    // the image itself stays silent; the full-size image is announced by the card's label.
    thumbnail: Boolean = false
) {
    Box(modifier = modifier) {
        if (cardId == null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                strokes.forEach { stroke ->
                    val scaledPoints = stroke.points.map { Offset(it.x * size.width, it.y * size.height) }
                    for (i in 0 until scaledPoints.size - 1) {
                        drawLine(
                            color = stroke.color,
                            start = scaledPoints[i],
                            end = scaledPoints[i + 1],
                            strokeWidth = stroke.width * strokeWidthScale,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        } else {
            val card = flashCardById(cardId)
            if (card != null) {
                Image(
                    painter = painterResource(if (thumbnail) card.thumbRes else card.res),
                    contentDescription = if (thumbnail) null else card.label,
                    // Fit, never crop: a sticker keeps its own margins. Only big views play the loop.
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().then(if (thumbnail) Modifier else Modifier.stickerMotion(card.motion))
                )
            } else {
                Text(
                    unknownFlashCardText(cardId, placeholderName),
                    color = DuudleColors.Muted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(12.dp)
                )
            }
        }
    }
}
