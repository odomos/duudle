# Phone test checklist

Verified on the Samsung Galaxy A34 on 2026-10-17. For future rounds, install the current build
from Android Studio first (Run with the phone selected).

## Real Snooze (roadmap #5), commit d082321

Pushed before phone testing; verified on the Samsung afterwards.

Files: `DoodleSnooze.kt` (new), `MainActivity.kt` (overlay buttons, in-app
Remove/Reply cancel snooze), `DuudleMessagingService.kt` (shared stroke JSON),
`AndroidManifest.xml` (snooze receiver).

- [x] Overlay Snooze shows 5 min / 15 min / 1 hour, with Back underneath
- [x] Back returns to Snooze / Remove / Draw
- [x] Snooze 5 min, lock the phone, kill the app: the same doodle reappears.
      Note how late it is (Samsung may only allow an inexact alarm)
- [x] Snooze, then open the doodle in the app and Remove it: it should not
      reappear when the snooze time passes
- [x] Snooze the same doodle twice: it reappears only once
- [x] If Samsung's "Sleeping apps" blocks it, note that here

Logs if something fails: `adb logcat -s DuudleSnooze:* DuudleFCM:*`

## Vault favorites (roadmap #6), commit b8fcfe3

Files: `VaultScreen.kt`, `IncomingDoodleScreen.kt`, `MainActivity.kt`
(passes `currentUid`), `firestore.rules` (favorites rule, already deployed).

- [x] Vault shows **All (n)** and **★ Saved (n)** filters
- [x] Open a doodle in the Vault, tap **☆ Save**: it turns **★ Saved**, and the
      thumbnail gets a gold ★
- [x] **★ Saved** filter shows only saved doodles; survives an app restart
- [x] Tap **★ Saved** again to unsave: it disappears from the filter
- [x] Open a new incoming doodle in the app (tap the mascot on Home):
      **☆ Save** in its header works too (not tested on the emulator)
- [x] Saving on one account doesn't show as saved for the other person

## Zoom while drawing (roadmap #7), commit f4a9481

Files: `DrawScreen.kt` (gesture handling, view transform, Reset pill),
`ZoomMathTest.kt` (new unit tests).

- [x] One finger still draws normally at 1×
- [x] Two-finger pinch zooms (up to 4×) around your fingers; two-finger drag pans
- [x] Putting a second finger down mid-stroke leaves no stray line
- [x] Draw fine detail at ~3×, tap **Reset zoom**: it shrinks to the right
      place and size, same line thickness as everything else
- [x] Send a doodle drawn partly while zoomed: the receiver's overlay looks
      the same as the sender's 1× canvas
- [x] Panning can't drag the canvas off-screen; rotating resets zoom to 1×

## Committed but not yet tested on the phone

- [x] Overlay **Draw** with Duudle already open: opens a fresh canvas
      addressed to the sender (commit e6f01f8)
- [x] Signup with a taken name in different case (e.g. `PHONEUSER2`) says
      "That username is taken". Needs a fresh install/account (commit 3b3ad6f)
- [x] Roadmap #1: Samsung as the **receiver** with the app fully killed,
      overlay appears over the home screen and over another app
- [x] Roadmap #1: Snooze / Remove / Draw from a real push (covered by the
      snooze checks above)
- [ ] Overlay test screen: turn the overlay permission or battery
      "Unrestricted" off/on in Settings, come back, and the status updates
      (`LocalLifecycleOwner` moved to `androidx.lifecycle.compose`)

## Already passed on the phone

- Mute (#4), long doodles (#2), case-insensitive friend search (#3)
