# Duudle

Android app (Kotlin + Jetpack Compose) for sending hand-drawn doodles between
friends. A sent doodle triggers an FCM push that shows the drawing as a floating
system overlay on the recipient's phone, even with the app killed.

## Stack
- Client: single `MainActivity` with manual screen switching via `enum class Screen`
  (no Navigation Component). Code lives in `app/src/main/java/com/example/duudle/`.
- Backend: Firebase project `duudle-296a9`. Anonymous Auth (user picks a username),
  Firestore (`users`, `friend_requests`, `friendships`, `doodles`, plus per-user
  `users/{uid}/mutes` and `users/{uid}/favorites`), Cloud Function
  `functions/index.js` (`onDoodleCreated`, sends the push), FCM.
- Firestore rules are versioned in `firestore.rules` and deployed with the Firebase CLI.
- `insights-service/` is a separate class-assignment microservice; the app doesn't use it.

## Architecture decisions (don't undo these casually)
- Strokes are vector data: `DoodleStroke(points, color, width)`, with points stored
  normalized to 0-1 of canvas width/height. Zoom in `DrawScreen` is view-only.
- Every doodle canvas is a 1:1 square (Draw screen, overlay, Vault, in-app viewer) so the
  0-1 points keep their proportions. Any new place that draws or renders strokes must be
  square too. Doodles sent before this rule were drawn on a tall canvas and look stretched.
- Stroke JSON is embedded in the FCM payload (cap `MAX_STROKES_BYTES` in
  `functions/index.js`, FCM's hard limit is 4KB). Oversized doodles fall back to a
  blocking Firestore fetch in `DuudleMessagingService`. Senders simplify strokes
  (Ramer-Douglas-Peucker) and round to 3 decimals to stay under the cap.
- Overlays are `TYPE_APPLICATION_OVERLAY` windows, created on the main thread via
  `Handler(Looper.getMainLooper())`.
- Firestore rules require `request.auth != null` everywhere; per-user subcollections
  are owner-only so, e.g., a muted friend can't see they're muted.
- Snooze (`DoodleSnooze.kt`) saves the doodle to a local file and sets an alarm that
  re-shows it through `showRealDoodleOverlay`.
- `android:allowBackup="false"` so a reinstall can't restore a stale username.

## Build and test
- `./gradlew assembleDebug testDebugUnitTest` (unit tests in `app/src/test/`).
- On the owner's Windows laptop, use Android Studio's bundled JDK:
  `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug testDebugUnitTest`
- Lint: `lintDebug` (never run yet; expect pre-existing warnings). The Cloud Function has
  no test suite, so use `node --check functions/index.js`. Emulator AVDs:
  `Medium_Phone_API_36.0`, `Pixel_7_Pro`; app log tags `DuudleFCM`, `DuudleSnooze`,
  `DuudleDebug`.
- Cloud sessions: the Android SDK comes from `scripts/cloud-setup.sh` (pasted into the
  cloud environment's setup script). There's no emulator or phone in the cloud, so
  anything touching overlays, pushes, alarms or gestures needs a check on the real
  device afterwards.

## Working with the owner
- The real test device is a Samsung Galaxy A34; its battery optimization is the main
  real-world risk. Add anything that needs on-device verification to
  `PHONE_TEST_CHECKLIST.md` (unchecked) instead of claiming it works.
- One commit per feature. Don't sweep unrelated edits into a commit.
- In cloud sessions, work on a branch and open a PR rather than pushing to `main`.
- Don't deploy Firebase (functions or rules) from a cloud session; there are no
  credentials there. Note what needs deploying and the owner deploys from their laptop.
- Never commit secrets: `*.pem`, `serviceAccountKey.json`, `node_modules/` are ignored.

## Team workflow (/project-partner)
- For engineering tasks the owner runs `/project-partner <task>`
  (`.claude/skills/project-partner/SKILL.md`). The main session is the **team lead**: it
  plans, delegates, verifies and reports to the owner. It never just forwards agent output.
- Specialists in `.claude/agents/`: `repo-analyst` (read-only analysis),
  `feature-developer` (the only production-code writer), `test-engineer` (runs checks;
  writes only tests and `PHONE_TEST_CHECKLIST.md`), and read-only reviewers
  `code-reviewer`, `security-reviewer`, `ui-ux-reviewer`.
- One writer per file at a time. Record `git status` before starting; uncommitted files
  that already existed are the owner's work and must not be reverted or attributed to
  the task.
- Approval gates, always: commit, push, merge, history rewrites, `firebase deploy`, live
  Firestore writes, rules/auth/data-format changes, new dependencies, messaging real
  users. There's no staging Firebase project.
- Never report a check as passed unless it actually ran. Anything that needs the
  emulator or the Samsung and wasn't run is reported as unverified.
