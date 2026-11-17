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

/**
 * Every pack this build knows. The sticker ids are a STABLE storage and push-payload contract
 * (they're saved in Firestore doodle docs, FCM pushes and snooze files): never rename or
 * reuse one. The rules accept `card10` to `card99` (two digits), so take the next free number;
 * past 99 the rules must be loosened first (staging, then the owner's OK for live).
 * To retire a sticker, drop it from its pack but keep its id reserved forever
 * ([RetiredFlashCardIds]). Labels, keywords and pack titles are display-only and can change.
 */
val StickerPacks: List<StickerPack> = listOf(
    StickerPack(
        id = "everyday",
        title = "Everyday",
        stickers = listOf(
            FlashCard("card10", R.drawable.sticker_everyday_hi, "Friendly hello face",
                listOf("hi", "hello", "hey", "wave", "friendly"), StickerMotion.BOUNCE),
            FlashCard("card11", R.drawable.sticker_everyday_haha, "Laughing face",
                listOf("haha", "lol", "laugh", "funny", "joke"), StickerMotion.WOBBLE),
            FlashCard("card12", R.drawable.sticker_everyday_wink, "Winking face",
                listOf("wink", "cheeky", "playful", "tease", "joke")),
            FlashCard("card13", R.drawable.sticker_everyday_shocked, "Shocked face",
                listOf("shocked", "wow", "omg", "surprised", "gasp")),
            FlashCard("card14", R.drawable.sticker_everyday_sleepy, "Sleepy face",
                listOf("sleepy", "tired", "sleep", "night", "zzz", "bored")),
            FlashCard("card15", R.drawable.sticker_everyday_love, "Heart-eyes face",
                listOf("love", "heart", "like", "crush", "cute", "aww"), StickerMotion.PULSE),
            FlashCard("card16", R.drawable.sticker_everyday_grr, "Grumpy face",
                listOf("grr", "angry", "mad", "grumpy", "annoyed")),
            FlashCard("card17", R.drawable.sticker_everyday_sad, "Crying face",
                listOf("sad", "cry", "tears", "sorry", "upset"))
        )
    ),
    StickerPack(
        id = "cats",
        title = "Cats",
        stickers = listOf(
            FlashCard("card18", R.drawable.sticker_cats_grin, "Cat with a huge toothy grin",
                listOf("cat", "kitty", "grin", "teeth", "smile", "cheeky", "happy"), StickerMotion.BOUNCE),
            FlashCard("card19", R.drawable.sticker_cats_cackle, "Cat laughing so hard it cries",
                listOf("cat", "kitty", "lol", "laugh", "cackle", "haha", "hilarious", "tears"), StickerMotion.WOBBLE),
            FlashCard("card20", R.drawable.sticker_cats_shook, "Shocked cat with huge eyes",
                listOf("cat", "kitty", "shook", "shocked", "wow", "omg", "scared", "surprised")),
            FlashCard("card21", R.drawable.sticker_cats_smug, "Smug cat with half-closed eyes",
                listOf("cat", "kitty", "smug", "sassy", "judging", "unimpressed", "smirk", "cool")),
            FlashCard("card22", R.drawable.sticker_cats_nap, "Cat napping with a snot bubble",
                listOf("cat", "kitty", "nap", "sleepy", "sleep", "tired", "zzz", "night")),
            FlashCard("card23", R.drawable.sticker_cats_love, "Cat with heart eyes",
                listOf("cat", "kitty", "love", "heart", "cute", "aww", "purr"), StickerMotion.PULSE),
            FlashCard("card24", R.drawable.sticker_cats_yum, "Hungry cat licking its lips",
                listOf("cat", "kitty", "yum", "hungry", "tasty", "lick", "food", "treat")),
            FlashCard("card25", R.drawable.sticker_cats_hiss, "Angry cat hissing with fangs",
                listOf("cat", "kitty", "hiss", "angry", "mad", "grumpy", "fangs"))
        )
    ),
    StickerPack(
        id = "dogs",
        title = "Dogs",
        stickers = listOf(
            FlashCard("card26", R.drawable.sticker_dogs_pant, "Happy dog panting with its tongue out",
                listOf("dog", "puppy", "doggo", "happy", "pant", "wag", "excited", "good boy"), StickerMotion.BOUNCE),
            FlashCard("card27", R.drawable.sticker_dogs_derp, "Goofy dog with crossed eyes",
                listOf("dog", "puppy", "doggo", "derp", "silly", "goofy", "blep", "tongue"), StickerMotion.WOBBLE),
            FlashCard("card28", R.drawable.sticker_dogs_tilt, "Confused dog tilting its head",
                listOf("dog", "puppy", "doggo", "confused", "what", "huh", "curious", "question")),
            FlashCard("card29", R.drawable.sticker_dogs_puppy, "Puppy with huge begging eyes",
                listOf("dog", "puppy", "doggo", "please", "beg", "sorry", "sad", "puppy eyes", "cute"), StickerMotion.PULSE),
            FlashCard("card30", R.drawable.sticker_dogs_love, "Dog with heart eyes",
                listOf("dog", "puppy", "doggo", "love", "heart", "cute", "aww")),
            FlashCard("card31", R.drawable.sticker_dogs_grr, "Dog growling with bared fangs",
                listOf("dog", "puppy", "doggo", "grr", "growl", "angry", "mad", "snarl")),
            FlashCard("card32", R.drawable.sticker_dogs_ball, "Excited dog with a tennis ball",
                listOf("dog", "puppy", "doggo", "ball", "play", "fetch", "excited", "game")),
            FlashCard("card33", R.drawable.sticker_dogs_sleep, "Dog sleeping with a Zzz",
                listOf("dog", "puppy", "doggo", "sleep", "sleepy", "nap", "tired", "zzz"))
        )
    )
)

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
 * The picker's search: every word typed must appear in a sticker's label, its keywords or its pack
 * title (case-insensitive). An empty [query] lists everything. [packId] null = all packs.
 */
fun searchStickers(query: String, packId: String? = null, packs: List<StickerPack> = StickerPacks): List<FlashCard> {
    val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    return packs.filter { packId == null || it.id == packId }.flatMap { pack ->
        pack.stickers.filter { s ->
            words.all { w ->
                s.label.lowercase().contains(w) || pack.title.lowercase().contains(w) || s.keywords.any { it.contains(w) }
            }
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
