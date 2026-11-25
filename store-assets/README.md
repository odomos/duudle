# Play store graphics

Original art, drawn for Duudle (see `stickers/README.md` for the art-source rule).

- `play-icon-512.png`: the 512 x 512 Play icon (full-bleed square; Play applies its own rounded mask).
- `feature-graphic-1024x500.png`: the Play feature graphic (text set in Fredoka, SIL OFL).
- `icon-sunny-d.svg`: the icon's source, option A "Sunny d".

The app's launcher icon is the same art as vector drawables:
`app/src/main/res/drawable/ic_launcher_foreground.xml` and `ic_launcher_background.xml`.

## Phone screenshots (`screenshots/`, 1080 x 1920)

Play takes up to 8; upload `01` to `08` in this order. `extra-friends.png` is a spare.

1. `01-popup` a real pop-up from a friend over the home screen
2. `02-home`  3. `03-draw`  4. `04-stickers`  5. `05-vault`
6. `06-keep`  7. `07-chat-themes`  8. `08-profile`

Taken on the Pixel emulator (staging build, display temporarily set to 1080 x 1920, status bar
in demo mode) with the test accounts `newme` and `arnav222`. Android hides other apps' pop-ups
over the Settings app, so use the home screen or an ordinary app behind a pop-up shot.
