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

## Square doodle canvas (verified on the A34, 2026-10-20)

Files: `DrawScreen.kt` (draw canvas is a centered 1:1 square),
`VaultScreen.kt` (detail view square), `IncomingDoodleScreen.kt` (in-app
viewer square). Emulator: square measured in portrait and landscape, a drawn
square stays square across rotation, and the Reset-zoom pill sits at the
square's top-right. Not checked on the emulator: a real send/receive.

- [x] Draw a circle (as round as you can) and send it to a friend. On the
      receiver it arrives as a circle, not an oval, in all four places:
      the push overlay, the Vault grid thumbnail, the Vault detail view, and
      the in-app viewer (tap the mascot on Home before opening the overlay)
- [x] Draw screen on the A34 in portrait: white canvas is a full-width square,
      centered between the header and the brush row, nothing cut off
- [x] Draw screen on the A34 in landscape: white canvas is a square using the
      full height between the header and the brush row, centered horizontally;
      brushes, colours, Undo and Clear are still reachable
- [x] Draw something in portrait, rotate to landscape and back: the drawing
      keeps its shape and position
- [x] Doodles sent **before** this change look horizontally stretched (wider
      than tall) in the Vault and the in-app viewer. This is expected and
      acceptable; just confirm it's only the old ones

## Profile screen (not yet verified on the A34)

Files: `ProfileScreen.kt` (new), `BottomNav.kt` (Me opens Profile instead of
the "coming soon" toast), `MainActivity.kt` (`Screen.PROFILE`), `HomeScreen.kt`,
`FriendsScreen.kt`, `VaultScreen.kt` (pass `onMeClick`). Emulator (2026-10-20):
Me opens Profile from Home/Friends/Vault, stats matched the other screens,
Back/system back/tabs/Draw FAB/Overlay Debug row all worked. Not checked on the
emulator: the ❌ + Fix state (both permissions were already on there).

- [ ] Tap **Me** from Home, Friends and Vault: each opens Profile (no "coming
      soon" toast), and the Me label is pink and bold there
- [ ] Under the character: the correct `@username`, and "Joined <date>"
      matching the day this A34 account was created (the most recent fresh
      install/signup). (The animal-emoji header was replaced by the character;
      see "Profile character" below.)
- [ ] The four stats change from "…" to numbers (none stay "…" or show "—").
      **Friends** equals the "N Friends" header on the Friends screen,
      **Saved** equals Vault "★ Saved (n)", and **Sent + Received** equals
      Vault "All (n)" plus the number of doodles you sent to yourself
- [ ] Settings > Apps > Duudle > Appear on top: turn it **off**, open Profile:
      "Float over other apps" shows ❌ with a pink **Fix**. Tap Fix: it opens
      Duudle's "Appear on top" page (not a generic list or the app info page).
      Turn it on, press back: the row is now ✅ On without leaving Profile
- [ ] Settings > Apps > Duudle > Battery: set to **Optimized** (or Restricted),
      open Profile: "Battery: Unrestricted" shows ❌ + **Fix**. Tap Fix: note
      what One UI shows (an "Allow / Stop optimizing" dialog, the app's battery
      page, or the app info page). Allow / set Unrestricted, come back: the row
      is ✅ On. Afterwards leave battery on **Unrestricted** again
- [ ] With either row ❌, the hint says Settings > Battery > Background usage
      limits > "Never sleeping apps". Check that this menu path exists on the
      A34's One UI; if it's different, write the real path here:
      ____________________
- [ ] Settings > Display > Font size and style: set the largest font size,
      open Profile and scroll to the bottom: the "Make sure doodles pop up"
      card, its "Test a pop-up" row and the account footnote are fully
      readable above the bottom nav and nothing overlaps; then restore your
      normal font size. (The About card was removed on purpose.)

## Redesign: Welcome, Home, bottom nav (not yet verified on the A34)

**Paused 2026-10-22, all uncommitted.** The whole redesign (this section, plus
Friends and Vault below) is built and passes the build and unit tests, but
isn't committed. Still to do before committing:
- [ ] Phone checks in these three sections
- [ ] Code review of the Vault restyle and of the FaceBlobs performance pass
      ("rest after 15 s"), and a re-check of the Friends correction pass
- [ ] UI review of Welcome, Home, Friends and Vault against the references
- [ ] Commit as separate features: redesign (Welcome/Home/nav), Friends,
      Vault, and the agent workflow rule (`.claude/` files)

Files: `WelcomeScreen.kt`, `HomeScreen.kt`, `BottomNav.kt` (restyled),
`FaceBlobs.kt` and `ui/theme/DuudleColors.kt` (new), `Theme.kt` (white
background), `AndroidManifest.xml` (`adjustResize`). Emulator (2026-10-21):
Home, dialogs, tabs, Draw button, character tap, eyes following a drag, and
Remove animations all worked. Since then: the Welcome keyboard double-shift
and the cut-off green hair were fixed (hair confirmed on the A34), the
headline uses "Who’s" with normal spacing, and the faces sit above the nav
(emulator). Idle CPU on Home measured on the A34 on 2026-10-22: 52–55% of one
core at about 34 fps, before the "rest after 15 s" change. That change hasn't
been measured yet (see Motion and battery).

Home
- [ ] Home looks like the mood reference: "Duudle" top left, Overlay pill top
      right, big "Who's getting a doodle today?", grey "Design your doodle" pill
      with a black arrow circle, About / How to use pills, and the pastel
      characters filling the space down to the bottom nav. Nothing is cut off
      except characters cropped at the screen edges
- [ ] The characters bob, blink and wobble smoothly, with no stutter or lag.
      Scroll or tap around for 30 s: the rest of the app still feels as quick
      as before the redesign
- [ ] Put a finger anywhere on Home and drag it slowly around: the googly and
      dot eyes follow your finger, then drift back when you lift it
- [ ] Tap a character: it squishes, bounces and grins for about a second, and
      the app does NOT navigate anywhere. Try two fingers on two characters at
      once (real multi-touch): no crash, nothing opens
- [ ] "Design your doodle" opens Draw; system back returns to Home
- [ ] About and How to use open their dialogs. How to use mentions
      "Profile → Make sure doodles pop up"; Got it closes them
- [ ] Overlay pill: with Appear on top ON it says "Overlay on" (green dot) and
      tapping does nothing. Turn Appear on top OFF, reopen the app: it says
      "Overlay off" (red dot) and tapping opens Overlay Debug Tools. Turn it
      back ON afterwards
- [ ] Have a friend send a doodle while Duudle is open on Home: the headline
      becomes "@name sent you a doodle!", the characters grin and look up, a
      black "Open it" pill appears with "or draw one" under it. "Open it" opens
      that doodle; "or draw one" opens Draw

Bottom nav
- [ ] On Home, Friends, Vault and Profile the black floating bar shows Home /
      Friends / Vault / Me with the current tab in a yellow highlight, and the
      yellow pencil button sits in the middle, fully visible (not clipped at the
      top)
- [ ] Every tab opens its screen from every other screen, and the pencil button
      opens Draw from each one
- [ ] On Friends, Vault and Profile, scroll to the very bottom: the last item
      can be read in full above the bar. Note whether the strip under the bar
      (gesture area) is a different colour from the screen: ________
- [ ] Largest font size: the tab labels aren't cut off. **Known issue:** on the
      emulator at 320dp wide with 1.3× font they clipped to "Hon", "Frie",
      "Vau" (2026-10-22, found during the Profile character test; BottomNav.kt
      not changed yet)

Welcome (needs a fresh install: only do this when you're ready to sign up again)
- [ ] After a fresh install Welcome looks like the moimoi reference: pale sky
      gradient, huge "Duudle" across the width, soft gradient characters with
      hair and googly eyes, the "Hello~" bubble, cream bottom panel with a
      white username pill and a black Get Started pill. The green character's
      hair at the top left isn't cut off with a flat edge (fixed; seen on the
      A34 2026-10-22)
- [ ] Tap the username field: the keyboard opens, and the field and Get Started
      sit just above it, with no big empty gap and with "Duudle" or the
      characters still visible at the top (was broken on the emulator, fixed
      since; you signed up on the A34 after the fix, so just confirm it looked
      right next time you see Welcome)
- [ ] Type a username, tap Get Started: it says "Setting up..." and then
      opens Home signed in as that name (the same as before the redesign)

Motion and battery
- [ ] Settings > Accessibility > Visibility enhancements > Remove animations
      ON, then reopen Duudle: Home and Welcome characters are still (no bobbing
      or blinking) and everything still works. Tapping a character still grins.
      Turn Remove animations OFF afterwards
- [ ] Rest: leave Home untouched for about 15 s: the characters ease into a
      still pose and stop moving. Touch the screen anywhere: they wake up
      smoothly with no jump. Same for the Welcome "Hello~" bubble and the
      Friends/Vault illustrations
- [ ] Re-measure idle CPU on Home over USB (the team lead can do this): it
      should be near 0 once resting, versus 52–55% of one core before the change
- [ ] Leave Duudle open on Home with the screen on for 5 minutes: the phone
      doesn't get noticeably warm, and Settings > Battery shows nothing unusual
      for Duudle
- [ ] Switch to another app or lock the screen, then come back: the characters
      continue animating (they pause while hidden)

## Friends restyle (not yet verified on the A34)

Files: `FriendsScreen.kt` (restyled), `FriendsIllustration.kt` (new),
`AndroidManifest.xml` (`windowSoftInputMode="adjustResize"`). A test run on
2026-10-22 installed this build on the A34 but stopped at the lock screen,
so none of the items below have been seen on the phone yet. Notes marked
"emulator" come from the emulator account, which has friends, so the empty
states couldn't be seen there.

After that run a correction pass changed: the search bar now sits **below**
the tabs (only shown once you have friends), a short spinner shows while
friends load, the page is cream behind the status and navigation bars, the
nav fades out with the keyboard, "@name" works in search, and request cards
stack Accept/Decline. These haven't been seen on any device yet.

Empty account (0 friends, 0 requests)
- [ ] Friends tab: back arrow, centred "Friends" title, no friend counter and
      no QR button; Friends / Add / Requests tabs with a black pill on the
      active tab and no "0" badge on Requests; no indigo overlay banner (passed
      on emulator 2026-10-22, before the correction pass)
- [ ] Switch between Friends, Add and Requests: the header and tab bar stay in
      exactly the same place (they used to jump)
- [ ] Opening Friends shows a brief spinner, then the right content. No flash
      of "No friends yet!" on an account that has friends
- [ ] No white strip above or below the cream page (status bar and the bar
      at the bottom)
- [ ] Empty Friends shows the illustration (two tilted phones with face
      characters, yellow "Add" tags, sparkles, pink blob), "No friends yet!",
      the subtitle and a black **Add a friend** button. The button switches to
      the Add tab
- [ ] Add tab: tap the search field and type a couple of letters. The field
      and any results stay visible above the keyboard, and the bottom nav is
      hidden while the keyboard is open. Close the keyboard: the nav comes back
      (the same behaviour passed on emulator 2026-10-22 for the Friends-tab
      search; the Add-tab run was interrupted)
- [ ] Requests tab with no requests: sleeping lilac blob and "No requests
      right now"
- [ ] Back arrow on Friends returns to Home; the bottom nav from Friends opens
      Vault and Me

Needs a real friend or request
- [ ] With a real friend, the card's **Pop-ups** switch mutes and unmutes them
      (their next doodle doesn't pop up while muted, does again once unmuted).
      Emulator 2026-10-22: the switch flipped Off ("Muted", Unmute pill) and
      back On ("Send doodle"); the effect on real pop-ups wasn't tested
- [ ] With a real friend, **Send doodle** on their card opens Draw addressed to
      them
- [ ] With friends: the grey search bar sits under the tabs; typing "@" plus
      part of a name finds them, and the list jumps back to the top as you type
- [ ] A real incoming friend request shows a badge with the count on the
      Requests tab, and Accept and Decline both work (the request leaves the
      list; Accept adds them to Friends)

Accessibility and motion
- [ ] TalkBack on: the Friends / Add / Requests tabs are announced as tabs
      (with "selected" on the current one), and the illustrations and blobs
      are skipped rather than read out
- [ ] Settings > Accessibility > Visibility enhancements > Remove animations
      ON: the Friends and Requests artwork is static. Turn it OFF afterwards

## Vault restyle (not yet verified on any device)

Files: `VaultScreen.kt` (restyled), `VaultIllustration.kt` and
`DuudleComponents.kt` (new, shared with Friends). Built and checked against
the old code (same saving, reply and back behaviour), but never run yet.

- [ ] Vault opens with a brief spinner, then "All (n)" / "Saved (n)" tabs and a
      2-column grid of white cards: square white doodle, small pastel avatar,
      "From @name" / "To @name", time ago. No white strips at the top or bottom
- [ ] New account with no doodles: the open-box illustration with two
      characters peeking out, "No doodles yet" and a black **Draw a doodle**
      button that opens Draw
- [ ] Saved tab with nothing saved: small yellow face and "Nothing saved yet"
- [ ] Open a doodle: back arrow, "From @name" title, star on the right, the
      doodle in a white square card, time ago, and **Draw a reply** on doodles
      you received (not on ones you sent)
- [ ] Tap the star: it turns gold, "Saved (1)" updates and the card shows a
      star; tap again to unsave
- [ ] Back (arrow and system back) closes the doodle first, then leaves Vault
- [ ] **Draw a reply** opens Draw addressed to the sender
- [ ] TalkBack reads each card as one item (e.g. "Doodle from @name, 2d ago")
      and the star as "Save"/"Saved"; Remove animations makes the art static

## Profile character (not yet verified on the A34)

Files: `Avatar.kt`, `AvatarStore.kt`, `AvatarArt.kt` (new), `ProfileScreen.kt`
(restyled), `AvatarConfigTest.kt` (new). The character is saved on this phone
only (`duudle_prefs`, key `avatar_v1_<uid>`); friends still see the emoji.
Emulator (2026-10-22): every tab, swatch and grid updated the hero at once,
the look survived a force-stop, "Test a pop-up" opened the overlay test
screen, and nothing overflowed at 320dp with 1.3× font. Stats were only seen
in their offline error state there (the emulator had no DNS).

Look and alignment
- [ ] Profile opens on a big square character card (yellow background, curly
      hair, purple hoodie, cat) with a white palette button bottom-right, then
      @username, "Joined …" and "Only you can see your character for now."
- [ ] The art is crisp on the A34 (no blurry or jagged outlines on the big card)
- [ ] Optional, needs an Android 8.0/8.1 (API 26/27) emulator image, which
      isn't installed: the big card is equally sharp there. (The art is drawn
      at the real pixel size so it should be; older Android blurred scaled
      drawing.)
- [ ] Head tab: each skin tone and each of the 6 hairstyles changes the big
      card instantly; the thumbnails show the hair clearly (no item on top)
- [ ] Body tab: each clothing colour and outfit changes the card instantly; on
      Navy and Black the hood strings, collar and arm lines are still visible
- [ ] Item tab: every item sits ON the hair (not floating above it, not sunk
      into it) for at least curly, bob and bun hair
- [ ] Glasses tab: round, square and sunglasses sit on the eyes and the arm
      reaches the ear, with bob and long hair as well
- [ ] Palette button: opens a row of background colours under the card, the
      button turns black while open, picking one recolours the card

Saving
- [ ] Pick a distinctive look, swipe Duudle away from recents, reopen it, go to
      Me: the same look is there
- [ ] Rotate the phone (or change dark mode) on Profile: the look and the
      selected tab stay the same
- [ ] After a phone restart the look is still there

Pop-ups, stats, accessibility
- [ ] The "Make sure doodles pop up" card still shows both rows and Fix works
      (see the Profile screen section above); "Test a pop-up" opens the
      overlay test screen. (Back from there goes to Home, not Profile: this
      was already the case before.)
- [ ] The four stats show real numbers (see the Profile screen section above)
- [ ] Largest font: nothing on Profile scrolls sideways or overlaps. Note
      whether "Glasses" shows in full or ends in "…" (an ellipsis is
      acceptable at the very largest size): ________
- [ ] TalkBack: the card reads "Your character: …" including skin and
      background; swatches read "Skin: …", "Clothing: …", "Background: …";
      grid cells read as radio buttons with their names
- [ ] Remove animations ON: changing the look doesn't do the little "pop"

## Already passed on the phone

- Mute (#4), long doodles (#2), case-insensitive friend search (#3)
