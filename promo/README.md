# Duudle promo video

`Duudle_promo_9x16.mp4` is the finished cut: 1080x1920, 30 fps, 44 s, H.264 + AAC.

## Editable project

The whole film is one HTML timeline, rendered frame by frame in headless Chrome.

| File | What it is |
| --- | --- |
| `index.html` | Every scene, caption, camera move and timing. Open it in Chrome with `#play` on the URL for a real-time preview (no sound). |
| `cues.json` | Sound-effect cue times and the music's section boundaries (in bars; 1 bar = 2 s). |
| `audio.py` | Synthesises the music and sound effects from scratch (numpy + scipy) into `soundtrack.wav`. Nothing is licensed from anyone. |
| `render.mjs` | Screenshots each frame through Chrome's DevTools protocol. No npm packages needed (Node 22+). |
| `assets/screens/` | The app screenshots shown inside the phones, untouched. |
| `assets/clips/` | Frames from the screen recording ("open a doodle" and "draw yo! Sam"), cropped to the app area and upscaled. |
| `assets/doodle_yo_sam.png` | The real "yo! Sam" doodle, cut out of the recording. |

## Re-render after an edit

```bash
python promo/audio.py promo/soundtrack.wav
```

```bash
node promo/render.mjs frames
```

```bash
ffmpeg -y -framerate 30 -start_number 0 -i frames/f_%05d.jpg -i promo/soundtrack.wav -vf "scale=in_range=full:out_range=tv,format=yuv420p" -c:v libx264 -preset slow -crf 16 -c:a aac -b:a 192k -shortest -movflags +faststart promo/Duudle_promo_9x16.mp4
```

`node promo/render.mjs frames 600 720` re-renders only frames 600-719, which is handy for
checking one scene.

## Things you may want to change

- The closing button says "Coming soon on Android" (`#cta` in `index.html`).
- Captions are the `CAPS` list in `index.html`; what each phone shows is `screenAt()`.
- The pop-up scene uses a plain grey placeholder phone, because the recording has no
  footage of the real overlay. Swap in real overlay footage when you have some.
