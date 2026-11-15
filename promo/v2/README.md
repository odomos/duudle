# Duudle promo V2

`../Duudle_promo_v2_9x16.mp4` is the finished cut: 1080x1920, 30 fps, 46 s, H.264 + AAC.
V1 (`../index.html`, `../Duudle_promo_9x16.mp4`) is untouched; V2 lives in this folder and
shares `../assets`.

| File | What it is |
| --- | --- |
| `index.html` | The whole V2 timeline. Open in Chrome with `#play` on the URL to preview (no sound). |
| `cues.json` | Sound-effect cue times, music sections (in bars; 1 bar = 2 s), the breakdown bar and the "5 minutes later" dip. |
| `audio.py` | V1's synth plus swipe, card-flip, snooze and clock-tick effects. Writes `soundtrack.wav`. |
| `render.mjs` | Frame renderer (same as V1). |

New shared assets: `../assets/doodle_sup.png` (the real "Sup!" doodle from the Vault
screenshot), `../assets/doodle_yo_sam_canvas.png`, `../assets/screens/flashcards.png`,
`../assets/cards/` (the app's own flash card images).

## What is real and what is illustration

- Duudle screens are only the supplied screenshots and screen-recording frames.
- The pop-up is rebuilt from `showRealDoodleOverlay()` in `MainActivity.kt` (colour, 260dp
  square canvas, title line, Snooze / Remove / Draw, the 5 min / 15 min / 1 hour / Back
  choices, the "Pop-ups paused until" toast). The soft shadow under it is added for depth.
- The photo feed ("moments") and the music player are invented stand-ins for "another app".

## Re-render

```bash
python promo/v2/audio.py promo/v2/soundtrack.wav
```

```bash
node promo/v2/render.mjs frames2
```

```bash
ffmpeg -y -framerate 30 -start_number 0 -i frames2/f_%05d.jpg -i promo/v2/soundtrack.wav -vf "scale=in_range=full:out_range=tv,format=yuv420p" -c:v libx264 -preset slow -crf 16 -c:a aac -b:a 192k -shortest -movflags +faststart promo/Duudle_promo_v2_9x16.mp4
```
