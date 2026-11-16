# Sticker packs

Stickers are ready-made images sent like a doodle (the in-app tab is **Stickers**; in the code and
the stored data they're still called flash cards / `cardId`). Only a sticker's **id** is ever
sent. The image ships inside the app, so a pop-up needs no download and works with Duudle closed.

Each pack has a folder here (`stickers/<pack id>/`) holding one file, `PACK.md`, which records
where the art came from. The art itself lives in the app (`app/src/main/res/drawable/` or
`drawable-nodpi/`), and the pack is listed in `StickerPacks` in
`app/src/main/java/com/example/duudle/FlashCards.kt`.

## The rights rule (read this first)

**This repository is public, and so is the Play Store app.** Only art we have the right to publish
goes in. That means:

- Your own drawings, or art made for Duudle.
- Art with a written licence that allows use in an app (record the licence and a link).
- CC0 / public-domain art (record where you found it and the licence).

**Not allowed**, even though it is everywhere online: frames, screenshots or stills from TV shows,
films or games; characters, logos or brand art that belong to someone else; memes with other
people's captions; images with watermarks or channel logos. A missing watermark does not mean
free to use.

Every `PACK.md` must have a `Source:` line (a unit test fails without it). If a pack's source
can't be written down honestly, the pack doesn't ship.

## Adding a pack

1. Pick a pack id (lower case, letters and digits, e.g. `cats`) and make `stickers/<id>/PACK.md`
   (copy `everyday/PACK.md`): title, which ids it uses, `Source:`, licence, what was checked.
2. Add the art to `app/src/main/res/`, named `sticker_<pack>_<name>` (lower case, underscores):
   - **Vector (best):** an Android VectorDrawable, 100 x 100 viewport. Sharp at any size,
     a few KB, and it can move (see below).
   - **Bitmap:** a square WebP or PNG with a transparent background, 512 x 512, subject centred
     with some padding, under about 100 KB. Put it in `drawable-nodpi/`. Bitmaps can't be made
     sharper than their pixels, and the pop-up shows them at about 700 px.
   - Not suitable: wide or tall scenes with a background, blurry video frames, text added by
     someone else, black bars.
3. Add a `StickerPack` to `StickerPacks` with one `FlashCard(id, drawable, label, keywords)` per
   sticker. `label` is what TalkBack reads; `keywords` (at least 3, lower case) feed the picker's
   search ("cat", "sleepy", ...).
4. **Ids:** `card10` to `card99`, the next free number, never reused or renamed (they're stored in
   doodles and pushes). The Firestore rules accept exactly `card` plus two digits, so past `card99`
   the rules must be loosened first (staging, then the owner's OK for live).
5. Optional: give a sticker a loop (`StickerMotion.WOBBLE`, `BOUNCE` or `PULSE`). It plays only
   when shown big, never in grids, and never with "Remove animations" on.
6. Run `./gradlew testDebugUnitTest`. It checks the ids, the keywords and each pack's `PACK.md`.
7. Check the new stickers in the picker and as a pop-up on a phone (see
   `PHONE_TEST_CHECKLIST.md`), then commit one pack per commit.

## Delivery and updates

Packs are bundled in the app, so a new pack means a new app release through Play. Older apps that
get a sticker they don't have show "update Duudle to see it", so adding stickers is safe.
(Hosting packs on the website for instant updates would need downloading and caching; not built.)

## Retiring a sticker

Remove it from its pack but add its id to `RetiredFlashCardIds`. The id stays reserved forever;
doodles that used it show "This sticker is no longer available".
`card01` to `card09` (the old image cards) are all retired.
