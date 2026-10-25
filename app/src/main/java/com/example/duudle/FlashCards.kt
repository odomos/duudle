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

// Flash cards: prebuilt images sent like a doodle. A card is a normal doodles doc with
// empty strokes plus "cardId"; the Cloud Function copies cardId into the push.

/**
 * [res] is the full-size image (overlay, viewer, Vault detail); [thumbRes] a pre-resized
 * 288px square for small cells (Draw grid, Vault thumbnails). [label] is display and
 * TalkBack text only, never stored.
 */
data class FlashCard(val id: String, @DrawableRes val res: Int, @DrawableRes val thumbRes: Int, val label: String)

/**
 * Every card this build knows. The ids are a STABLE storage and push-payload contract
 * (they're saved in Firestore doodle docs, FCM pushes and snooze files): never rename or
 * reuse one. To retire a card, drop it from this list but keep its id reserved forever.
 * Labels are display-only and can change.
 */
val FlashCardCatalog: List<FlashCard> = listOf(
    FlashCard("card01", R.drawable.flashcard_01, R.drawable.flashcard_01_thumb, "Grinning girl with a flower"),
    FlashCard("card02", R.drawable.flashcard_02, R.drawable.flashcard_02_thumb, "Wide-eyed girl"),
    FlashCard("card03", R.drawable.flashcard_03, R.drawable.flashcard_03_thumb, "Big-eyed white-haired kid"),
    FlashCard("card04", R.drawable.flashcard_04, R.drawable.flashcard_04_thumb, "Shocked drooling girl"),
    FlashCard("card05", R.drawable.flashcard_05, R.drawable.flashcard_05_thumb, "Cat-eared girl"),
    FlashCard("card06", R.drawable.flashcard_06, R.drawable.flashcard_06_thumb, "Pink-haired girl with candy"),
    FlashCard("card07", R.drawable.flashcard_07, R.drawable.flashcard_07_thumb, "Pointing girl with GAY text"),
    FlashCard("card08", R.drawable.flashcard_08, R.drawable.flashcard_08_thumb, "Pixel cat in front of a fire"),
    FlashCard("card09", R.drawable.flashcard_09, R.drawable.flashcard_09_thumb, "Grinning girl with a trident")
)

/** Null for a missing id or one this build doesn't have (a newer app sent it). */
fun flashCardById(id: String?): FlashCard? = if (id == null) null else FlashCardCatalog.firstOrNull { it.id == id }

/** The cardId from a push or snooze file: blank means "not a card". Unknown ids are kept. */
fun cardIdFromPayload(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }

/**
 * True for an embedded strokes payload of "[]". Real doodles always have strokes, so this
 * is a flash card pushed by a Cloud Function that predates cardId: fetch the doc instead.
 */
fun isEmptyStrokesPayload(json: String?): Boolean = json != null && json.trim().matches(Regex("""\[\s*]"""))

fun flashCardSentLine(senderUsername: String): String = "@$senderUsername sent you a flash card"

/** Shown instead of a card this build doesn't have. [senderUsername] null = I sent it. */
fun unknownFlashCardText(senderUsername: String?): String =
    if (senderUsername == null) "Update Duudle to see this flash card"
    else "@$senderUsername sent a flash card — update Duudle to see it"

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
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    unknownFlashCardText(placeholderName),
                    color = DuudleColors.Muted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(12.dp)
                )
            }
        }
    }
}
