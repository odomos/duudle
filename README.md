# Duudle

Duudle is an Android app for sending hand-drawn doodles, ready-made flash cards and chat
messages to friends. A doodle or flash card doesn't land in an inbox: it **pops up as a
floating overlay on the friend's screen**, even when Duudle is closed.

- **Client:** Kotlin + Jetpack Compose, one `MainActivity` (package `com.example.duudle`,
  minSdk 26, target/compile SDK 36).
- **Backend:** Firebase project `duudle-296a9`: Anonymous Authentication, Cloud Firestore,
  Cloud Functions (2nd gen, Node.js 24) and Firebase Cloud Messaging (FCM).

This README describes what works today and how every request flows through Firebase. For
coding rules and the team workflow see [`CLAUDE.md`](CLAUDE.md); for on-device test status
see [`PHONE_TEST_CHECKLIST.md`](PHONE_TEST_CHECKLIST.md).

---

## 1. What works today

Everything below is built and pushed to `main`. The "Verified" column is what the owner has
confirmed on the real test phone (Samsung Galaxy A34); the checklist has the details.

| Feature | What the user can do | Verified on A34 |
|---|---|---|
| **Sign-up** | Pick a username on first launch; no email or password | Yes |
| **Friends** | Search users by name (case-insensitive), send, accept and decline friend requests, mute a friend | Yes |
| **Draw and send a doodle** | Draw on a square canvas (colours, with white working as an eraser; 3 brush sizes; pinch-zoom) and send it to a friend | Yes |
| **Doodle pop-up** | The friend sees the doodle float over whatever app they're in, with **Snooze**, **Remove** and **Draw** (reply) | Yes |
| **Flash cards** | On the Draw screen, switch to *Flash cards* and tap one of 9 images to send it instantly; it pops up like a doodle | Yes |
| **Snooze** | Snooze a pop-up for 5 or 15 minutes; it comes back on time, even after a reboot or an app update; cancel a snooze from the Vault | Yes |
| **Vault** | Every doodle and card sent or received, with *All*, *Saved* (starred) and *Snoozed* tabs; open one full size, star it, or draw a reply | Yes |
| **Home** | Animated characters, plus a banner when a new doodle or card is waiting | Yes |
| **In-app chat** | A *Chat* button on each friend opens a real-time 1:1 chat with 7 shared themes and Android notifications | Yes |
| **Profile** | A full-body customisable character (outfits, head, face, tops, bottoms, shoes, items, glasses, background), doodle stats, and a setup card that checks the phone can show pop-ups | Yes (editor) |
| **Overlay test screen** | Profile → *Test a pop-up* opens a screen that shows a sample overlay or simulates a push after 30 s | Yes |

---

## 2. Architecture at a glance

```
 Android app (sender)                Firebase (duudle-296a9)                 Android app (receiver)
 ────────────────────               ───────────────────────                 ──────────────────────
 Anonymous sign-in ───────────────▶ Authentication (uid)
 Firestore SDK writes ────────────▶ Cloud Firestore ──(security rules)
                                        │  onDocumentCreated trigger
                                        ▼
                                    Cloud Functions
                                    • onDoodleCreated
                                    • onChatMessageCreated
                                        │  data message (high priority)
                                        ▼
                                    Firebase Cloud Messaging ─────────────▶ DuudleMessagingService
                                                                              • doodle/card → overlay window
                                                                              • chat → notification
 Firestore listeners ◀─────────────── live updates (Vault, Friends, Chat) ──▶ Firestore listeners
```

Principles that hold everywhere:

- **Every Firestore request needs a signed-in user.** The only sign-in is Anonymous Auth, so a
  user *is* their device: uninstalling or clearing data creates a new account
  (`android:allowBackup="false"` stops a reinstall restoring a stale username).
- **The app writes directly to Firestore**; the security rules in [`firestore.rules`](firestore.rules)
  decide what's allowed. There is no custom REST API.
- **Cloud Functions only send pushes.** They run with admin rights, read what they need and
  never change user data.
- **Pushes are FCM *data* messages**, so the app always decides what to show (overlay or
  notification), even when it was killed.

---

## 3. Firestore data model

All timestamps marked *client ms* are `System.currentTimeMillis()` from the phone; those marked
*server* are `FieldValue.serverTimestamp()`.

### `users/{uid}`
One document per account; the id is the Firebase Auth uid.

| Field | Type | Notes |
|---|---|---|
| `uid` | string | Same as the document id |
| `username` | string | As typed at sign-up |
| `usernameLower` | string | Lower-cased, trimmed; used for uniqueness and search |
| `fcmToken` | string | Where pushes for this user go; refreshed by the app |

Owner-only subcollections:

| Path | Fields | Meaning |
|---|---|---|
| `users/{uid}/mutes/{friendUid}` | `mutedAt` (client ms) | I muted this friend: their doodles, cards and chat messages don't push. The friend can't see it |
| `users/{uid}/favorites/{doodleId}` | `savedAt` (client ms) | I starred this doodle in the Vault. Private, so the other person can't see what I saved |

### `friend_requests/{autoId}`
`fromUid`, `fromUsername`, `toUid`, `toUsername`, `status` (`"pending"`), `timestamp` (client ms).
Deleted when accepted or declined.

### `friendships/{autoId}`
`userA` (who accepted), `userB` (who sent the request), `createdAt` (client ms). A friendship
is found by querying both `userA == me` and `userB == me`. Two crossed requests can leave two
docs for one pair, so the app counts distinct friend uids.

### `doodles/{autoId}`
Both doodles and flash cards live here.

| Field | Type | Notes |
|---|---|---|
| `senderId`, `senderUsername` | string | |
| `receiverId`, `receiverUsername` | string | |
| `strokes` | array | `[{ points: [{x, y}], color, width }]`; empty for a flash card |
| `cardId` | string, optional | Present only for a flash card: `"card01"` … `"card09"` |
| `createdAt` | number | client ms |
| `status` | string | `"unseen"` when sent, `"removed"` once the receiver removes it or replies |

Stroke points are **normalized 0–1** of the canvas width and height, and every canvas is a
**1:1 square**, so a doodle keeps its proportions on any screen. Before sending, each stroke
is simplified (Ramer–Douglas–Peucker) and coordinates are rounded to 3 decimals to keep the
push small.

### `chats/{chatId}` and `chats/{chatId}/messages/{autoId}`
`chatId` is the two member uids **sorted and joined with `_`**, so a pair always maps to the
same chat.

| Chat field | Notes |
|---|---|
| `members` | `[uidLow, uidHigh]`; fixed at creation |
| `theme` | Theme id (`classic`, `gothic`, `inferno`, `pookie`, `serpent`, `spooky`, `pond`); missing = classic |
| `lastMessage`, `lastSenderId`, `lastAt` | Preview of the newest message (≤ 120 chars), its sender, server time |
| `lastRead` | Map `{uid: server timestamp}`: when each member last read the chat (drives the unread dot) |

| Message field | Notes |
|---|---|
| `senderId` | Must be the writer |
| `kind` | `"text"` or `"theme"` (a "changed the theme" note) |
| `text` | 1–1000 characters (for `theme`, the new theme id) |
| `createdAt` | server timestamp |

### Data kept only on the phone
| What | Where | Why local |
|---|---|---|
| Username (for the session) | SharedPreferences `duudle_prefs`, key `username` | Fast start-up |
| Profile character | `duudle_prefs`, key `avatar_v1_<uid>`, e.g. `v1;hair=curly;skin=peach;outfit=hoodie;…` | Only the owner sees it; friends still see an emoji avatar |
| Snoozed doodles | `files/snoozed/<doodleId>.json` (sender, strokes or card id, pop-up time) | Must re-show offline, with the app killed |
| Notification-permission asked | `duudle_prefs`, `asked_chat_notification_permission` | Ask only once |

---

## 4. How each request is handled

### 4.1 Sign-up
1. `FirebaseAuth.signInAnonymously()` returns a new `uid`.
2. The app queries `users` where `usernameLower == <name lower-cased>`; if another uid owns
   it, the user sees "That username is taken".
3. It writes `users/{uid}` = `{uid, username, usernameLower}`.
4. It fetches the FCM token and saves it to `users/{uid}.fcmToken`.
   `DuudleMessagingService.onNewToken` keeps it current.

On later launches, accounts created before case-insensitive search get `usernameLower`
back-filled once.

### 4.2 Finding and adding friends
- **Search** is a prefix range query on `users.usernameLower` (`>= prefix` and `<= prefix + "\uf8ff"`,
  limit 20). A single-field range needs no composite index.
- **Send a request:** the app first checks there's no friendship either way and no pending
  request, then adds a `friend_requests` doc.
- **Incoming requests** come from a live listener on `friend_requests` where `toUid == me` and
  `status == "pending"`.
- **Accept:** add a `friendships` doc (`userA` = me, `userB` = sender), then delete the request.
  **Decline:** delete the request.
- **Mute / unmute:** create or delete `users/{me}/mutes/{friendUid}`.

### 4.3 Sending a doodle (and how it pops up)
1. **Sender:** the Draw screen adds a `doodles` doc with the normalized strokes and
   `status: "unseen"`.
2. **Trigger:** `onDoodleCreated` (Cloud Function) runs on the new doc. It:
   - reads `users/{receiverId}` and `users/{receiverId}/mutes/{senderId}` in parallel;
   - stops if the receiver muted the sender (the doodle is still stored and stays in the Vault);
   - stops if the receiver has no `fcmToken`;
   - serializes the strokes and, if they fit in **3600 bytes** (FCM's limit is 4 KB), embeds them;
   - sends a high-priority data message (see the payload table in section 6).
3. **Receiver:** `DuudleMessagingService.onMessageReceived` gets `type = "new_doodle"`:
   - **Embedded strokes:** parse them and show the overlay right away.
   - **Too big to embed:** blocking fetch of `doodles/{doodleId}` (with a timeout) on FCM's
     background thread, then show the overlay. Blocking matters: once `onMessageReceived`
     returns, Android may kill a cold-started process before an async callback would run.
   - Overlays are `TYPE_APPLICATION_OVERLAY` windows created on the **main thread**
     (`Handler(Looper.getMainLooper())`) and need the *Display over other apps* permission.
4. **Overlay buttons:**
   - **Remove** sets `status: "removed"` and cancels any snooze.
   - **Draw** sets `status: "removed"` and opens the Draw screen addressed to the sender.
   - **Snooze** (5 or 15 min) saves the doodle locally (section 4.5).
5. **In the app:** Home listens for `receiverId == me` and `status == "unseen"` to show
   "@name sent you a doodle!". The Vault listens for everything sent or received.

### 4.4 Flash cards
- The 9 images ship inside the app (`res/drawable-nodpi/flashcard_0N.jpg`, plus small
  `_thumb.jpg` versions for grids). Each has a **stable id** (`card01`…`card09`) that never
  changes or gets reused.
- **Sending** adds a normal `doodles` doc with `strokes: []` and `cardId`. Because it's a
  doodle, mute, Vault, stats, snooze and Draw reply all work with no extra backend.
- `onDoodleCreated` adds `cardId` to the push. The receiver shows the card image in the same
  overlay frame. For a push without `cardId` but with empty strokes (from before the function
  update), the app fetches the doc to read `cardId`.
- An unknown card id (a newer app version sent it) shows "Update Duudle to see this flash card".

### 4.5 Snooze
1. Snooze writes `files/snoozed/<doodleId>.json` with the sender, strokes or `cardId` and
   `triggerAt`, then sets an `AlarmManager` alarm:
   - **exact** (`setExactAndAllowWhileIdle`) when the user allowed *Alarms & reminders*
     (Profile → *Exact snooze timing*);
   - otherwise **inexact** (`setAndAllowWhileIdle`), which Doze can delay.
2. When the alarm fires, `SnoozeAlarmReceiver` re-shows the overlay from the file, with no
   network needed, then deletes the file.
3. Android wipes alarms on reboot and app update. `SnoozeRescheduleReceiver`
   (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, exact-alarm permission change) and every app
   launch run `rescheduleAll`: future snoozes are re-armed and overdue ones are shown now.
4. **Cancel snooze** (Vault → open a snoozed doodle) deletes the alarm and the file.

Snooze never touches Firestore.

### 4.6 Vault and favourites
- **Live listeners:** `doodles` where `senderId == me` and where `receiverId == me`, merged
  and sorted newest first.
- **Starring** creates or deletes `users/{me}/favorites/{doodleId}`; a listener on that
  subcollection drives the *Saved* tab.
- The *Snoozed* tab reads the local snooze files.

### 4.7 In-app chat
1. **Open:** Friends → *Chat*. The app listens to the newest 50 messages
   (`orderBy createdAt desc, limit 50`) and to the chat doc (theme, read state). Scrolling up
   loads older pages with `startAfter`.
2. **Send:** one **batched write**:
   - add a `messages` doc (`senderId`, `kind: "text"`, `text`, server `createdAt`);
   - set-merge the chat doc (`members`, `lastMessage`, `lastSenderId`, server `lastAt`).

   For the first message this creates the chat. The rules check the message against the chat
   as it will be after the batch (`getAfter`), so both writes succeed together. Firestore's
   offline cache shows the message straight away with a small clock until the server confirms.
3. **Push:** `onChatMessageCreated` runs on each new message. It:
   - skips non-text messages (theme notes);
   - loads the chat and takes the other member as the recipient;
   - **checks the two are friends** (two `friendships` queries), so a stranger who creates a
     chat gets no push out of it;
   - skips if the recipient muted the sender, or has no token;
   - reads the sender's username on the server (it is never trusted from the client), caps it
     at 32 characters and the text at 200;
   - sends a `chat_message` data push.
4. **Notification:** the receiver shows a normal Android notification on the *Chat messages*
   channel. It's private on the lock screen ("New message"), and tapping it opens that chat.
   - It's skipped while that chat is already on screen (`ChatPresence`).
   - On Android 13+ the app asks for notification permission once, the first time a chat
     opens.
   - Tapped notifications are validated: the uid must be alphanumeric, the name is looked up
     in Firestore and never taken from the intent.
5. **Theme change:** a batch updates `chats/{id}.theme` and adds a `kind: "theme"` message.
   Both people see the new theme and a "@x changed the theme to …" note.
6. **Read state:** while the chat is open the app set-merges only `lastRead.{me}`. Friends
   shows a red dot when `lastAt` is newer than my `lastRead` and the last sender wasn't me
   (from a listener on `chats` where `members array-contains me`; no composite index needed).

### 4.8 Profile
- **Character:** loaded from and saved to SharedPreferences only, via the editor's final
  **Save** (`commit()` on a background thread). If the write fails, the in-memory value is
  rolled back so the app never shows an unsaved look as saved.
- **Stats:**
  - *Sent*, *Received* and *Saved* use Firestore **`count()` aggregations**, so no documents
    are downloaded.
  - *Friends* reads both friendship queries and counts distinct uids.
- **Setup card:** checks *Display over other apps*, *Unrestricted battery* and (Android 12+)
  *Exact snooze timing*, each with a **Fix** button that opens the right settings page.

---

## 5. Security rules (deployed)

Full text: [`firestore.rules`](firestore.rules). Every rule requires `request.auth != null`.

| Collection | Read | Write |
|---|---|---|
| `users/{uid}` | Any signed-in user (needed for search) | Owner only |
| `users/{uid}/mutes/*`, `users/{uid}/favorites/*` | Owner only | Owner only |
| `friend_requests/*`, `friendships/*`, `doodles/*` | Any signed-in user | Any signed-in user |
| `chats/{chatId}` | Members only (or, before the doc exists, one of the two uids in the id) | Create by one of the two sorted members with a matching id. Update by a member: members can't change, only known fields, preview must name the writer as sender at server time, and only your own `lastRead` entry. No deletes |
| `chats/{chatId}/messages/*` | Either member | Create only: by a member, as themselves, at server time, `kind` text or theme, 1–1000 chars, no extra fields. No edits or deletes |
| `connection_test/*` | Any signed-in user | Any signed-in user |

**Known gap:** `friend_requests`, `friendships` and `doodles` are open to any signed-in user.
A determined user could forge a friendship or a doodle's sender name. The chat function
re-checks friendship server-side, but tightening these three collections is an open item that
needs a rules change and deploy.

---

## 6. Cloud Functions and push payloads

Source: [`functions/index.js`](functions/index.js), runtime Node.js 24, region `us-central1`.

| Function | Trigger | Does |
|---|---|---|
| `onDoodleCreated` | `doodles/{doodleId}` created | Mute check, then pushes the doodle or card to the receiver |
| `onChatMessageCreated` | `chats/{chatId}/messages/{messageId}` created | Text only. Friendship + mute check, then pushes the message to the other member |

FCM data payloads (all `android.priority: "high"`):

| `type` | Fields |
|---|---|
| `new_doodle` | `doodleId`, `senderId`, `senderUsername`, `strokes` (compact JSON, if ≤ 3600 bytes), `cardId` (cards only) |
| `chat_message` | `chatId`, `senderId`, `senderUsername` (≤ 32 chars), `text` (≤ 200 chars) |

The functions log every skip and send to Cloud Logging (Firebase console → Functions → Logs);
chat text is never logged.

---

## 7. Android components

| Component | Purpose |
|---|---|
| `MainActivity` | Every screen, switched with `enum class Screen` (no Navigation library). Exported (launcher); notification-tap extras are validated |
| `DuudleMessagingService` | Receives FCM: overlays for doodles and cards, notifications for chat |
| `SnoozeAlarmReceiver` | Fires a snoozed overlay |
| `SnoozeRescheduleReceiver` | Re-arms snoozes after reboot, app update or exact-alarm permission change |

Permissions: `INTERNET`, `SYSTEM_ALERT_WINDOW` (overlays),
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `POST_NOTIFICATIONS` (chat, Android 13+),
`SCHEDULE_EXACT_ALARM` (exact snooze, user-granted), `RECEIVE_BOOT_COMPLETED`.

**Samsung note:** One UI's battery management is the main real-world risk to pop-ups and
snoozes. The Profile setup card links to the right settings and suggests adding Duudle to
*Never sleeping apps*.

---

## 8. Build, test and deploy

```bash
# Build the debug APK and run the unit tests
./gradlew assembleDebug testDebugUnitTest
```

On the owner's Windows laptop, use Android Studio's JDK first:
`$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`, then
`.\gradlew.bat assembleDebug testDebugUnitTest`.

- **Unit tests** (`app/src/test`): 110 JVM tests covering stroke simplification, zoom maths,
  the avatar format and editor logic, chat helpers, flash cards and snooze timing.
- **Cloud Function syntax check:** `node --check functions/index.js` (there's no function
  test suite).
- **Deploy** (from the laptop; there is no staging project, so this is live):

```bash
firebase deploy --only firestore:rules
```

```bash
firebase deploy --only functions
```

If the functions deploy times out during discovery, set `FUNCTIONS_DISCOVERY_TIMEOUT=120`.
Deploy a single function with e.g. `--only functions:onChatMessageCreated`.

- **Cloud sessions** get the Android SDK from `scripts/cloud-setup.sh`; they have no emulator,
  phone or Firebase credentials.

---

## 9. Project layout

```
app/src/main/java/com/example/duudle/
  MainActivity.kt            screens, navigation, sign-in, overlay window
  WelcomeScreen.kt  HomeScreen.kt  BottomNav.kt  FaceBlobs.kt
  DrawScreen.kt              drawing, zoom, Flash cards mode, send
  FriendsScreen.kt           search, requests, friends list, mute, Chat button
  VaultScreen.kt             All / Saved / Snoozed, detail, cancel snooze
  IncomingDoodleScreen.kt    doodle model + parsing, in-app viewer, favourites
  ProfileScreen.kt           character hero, stats, pop-up setup card
  Avatar.kt  AvatarArt.kt  AvatarStore.kt  AvatarPickers.kt
  CharacterEditor.kt  CharacterEditorScreen.kt   full-body character editor
  Chat.kt  ChatRepository.kt  ChatScreen.kt  ChatThemes.kt   in-app chat
  FlashCards.kt              card catalogue + DoodleOrCard renderer
  DoodleSnooze.kt            snooze files, alarms, re-arming
  DuudleMessagingService.kt  FCM handling, stroke JSON
  DuudleComponents.kt  ui/theme/              shared UI and colours
app/src/main/res/drawable-nodpi/   flash card images
functions/index.js                 Cloud Functions
firestore.rules                    security rules
insights-service/                  separate class-assignment microservice (not used by the app)
```

**Never commit secrets:** `*.pem`, `serviceAccountKey.json` and `local.properties` are
git-ignored.

---

## 10. Open items

- Every character looks like it's wearing glasses even with *No glasses*: remove that look,
  then keep glasses as a separate option.
- Tighten the `friend_requests`, `friendships` and `doodles` rules (section 5).
- There's no chat inbox list yet; chats open from Friends or a notification.
- Chat notifications use the default launcher icon; Duudle has no monochrome notification
  icon yet.
- Flash card images 01, 02 and 05 carry third-party watermarks or signatures. They're fine
  for testing with friends, but a public release needs rights to every image.
- Some earlier redesign and Profile checks in `PHONE_TEST_CHECKLIST.md` are still unticked.
