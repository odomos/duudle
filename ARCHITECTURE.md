# Duudle architecture

How Duudle is put together: the Android frontend, the Firebase backend, the database, the
"APIs" between them, authentication, the major components, and how information moves
through the system. Field-by-field data shapes and step-by-step feature behaviour are in
[`README.md`](README.md); this document is about structure and flow.

---

## 1. System overview

Duudle is a **serverless mobile app**. There is no custom application server: the Android
client talks straight to Firebase, and a small set of Cloud Functions reacts to database
writes to send push messages.

```mermaid
flowchart LR
    subgraph Phone_A["Sender's phone"]
        A_UI["Compose UI<br/>(MainActivity + screens)"]
        A_SDK["Firebase SDKs<br/>Auth · Firestore · Messaging"]
        A_UI --> A_SDK
    end

    subgraph Firebase["Firebase project duudle-296a9"]
        AUTH["Authentication<br/>(Anonymous)"]
        FS[("Cloud Firestore<br/>+ security rules")]
        CF["Cloud Functions (Node 24)<br/>onDoodleCreated<br/>onChatMessageCreated"]
        FCM["Firebase Cloud Messaging"]
        FS -- "document created" --> CF
        CF -- "admin reads" --> FS
        CF -- "data message" --> FCM
    end

    subgraph Phone_B["Receiver's phone"]
        B_SVC["DuudleMessagingService"]
        B_OV["Overlay window<br/>(doodle / flash card)"]
        B_NOTIF["Notification<br/>(chat)"]
        B_UI["Compose UI"]
        B_SVC --> B_OV
        B_SVC --> B_NOTIF
    end

    A_SDK -- "sign in" --> AUTH
    A_SDK -- "reads / writes / listeners" --> FS
    FCM -- "push" --> B_SVC
    B_UI -- "live listeners" --> FS
```

| Layer | Technology | Responsibility |
|---|---|---|
| Frontend | Kotlin, Jetpack Compose, Android framework | All UI, drawing, overlays, notifications, local storage, alarms |
| Authentication | Firebase Anonymous Auth | Gives every install a stable `uid` |
| Database | Cloud Firestore (+ `firestore.rules`) | Users, friendships, doodles, chats; enforces access |
| Backend logic | Cloud Functions, 2nd gen, Node.js 24, `us-central1` | Turns new doodles and chat messages into pushes |
| Messaging | Firebase Cloud Messaging | Delivers data messages to the receiver, even with the app killed |
| On-device | SharedPreferences, app files, `AlarmManager` | Username cache, profile character, snoozed doodles |

---

## 2. Frontend (Android client)

### 2.1 Structure
- **One activity:** `MainActivity` hosts every screen. Navigation is a plain
  `enum class Screen { WELCOME, HOME, DRAW, FRIENDS, VAULT, OVERLAY_DEBUG, VIEW_DOODLE, PROFILE,
  EDIT_CHARACTER, CHAT }` held in `rememberSaveable`; a `when` picks the screen. There is no
  Navigation library and no back stack: a `BackHandler` routes system back (to Home, or
  Profile from the editor, or Friends from a chat), and screens that need to intercept back
  (Vault detail, the character editor's unsaved-changes dialog) register their own.
- **No ViewModels.** State lives in composables (`remember` / `rememberSaveable`) and in
  `MainActivity` for app-wide values (current screen, recipient, incoming doodles). Firestore
  snapshot listeners are attached in `DisposableEffect` blocks and removed when the screen
  leaves composition.
- **Pure logic is kept out of Compose** so it can be unit tested on the JVM: stroke
  simplification and zoom maths, the avatar format (`Avatar.kt`), the editor state machine
  (`CharacterEditor.kt`), chat helpers (`Chat.kt`), flash card lookup (`FlashCards.kt`) and
  snooze timing (`DoodleSnooze.kt`).

### 2.2 Screens and major components

```mermaid
flowchart TB
    MA["MainActivity<br/>screen switch · sign-in · FCM token · overlay builder"]
    MA --> W[WelcomeScreen]
    MA --> H[HomeScreen]
    MA --> D["DrawScreen<br/>Draw / Flash cards"]
    MA --> F["FriendsScreen<br/>search · requests · mute · Chat"]
    MA --> V["VaultScreen<br/>All / Saved / Snoozed"]
    MA --> VD[IncomingDoodleScreen viewer]
    MA --> P["ProfileScreen<br/>character · stats · setup card"]
    MA --> E[CharacterEditorScreen]
    MA --> C[ChatScreen]
    MA --> T[Overlay test screen]

    D --> FC[FlashCards]
    V --> DS[DoodleSnooze]
    P --> AS[AvatarStore]
    E --> CE[CharacterEditor state]
    E --> AS
    C --> CR[ChatRepository]
    C --> CT[ChatThemes]
    P & E --> AA[AvatarArt renderer]
    H & W & F & V --> FB[FaceBlobs characters]
```

| Component | File(s) | Role |
|---|---|---|
| App shell | `MainActivity.kt` | Screen switching, sign-in and username save, FCM token save, notification-tap handling, **overlay window builder** (`showRealDoodleOverlay`) |
| Drawing | `DrawScreen.kt` | Square canvas with normalized strokes, zoom, recipient picker, doodle and flash card sending |
| Friends | `FriendsScreen.kt` | User search, friend requests, friend list with mute, Send doodle and Chat |
| Vault | `VaultScreen.kt`, `IncomingDoodleScreen.kt` | History of sent/received items, favourites, snoozed items, detail view |
| Profile | `ProfileScreen.kt`, `Avatar*.kt`, `CharacterEditor*.kt` | Vector character (drawn in code), full-screen editor with draft/saved state, stats |
| Chat | `Chat.kt`, `ChatRepository.kt`, `ChatScreen.kt`, `ChatThemes.kt` | Real-time 1:1 chat, all Firestore access in the repository |
| Flash cards | `FlashCards.kt` | Catalogue of bundled images and the shared `DoodleOrCard` renderer |
| Snooze | `DoodleSnooze.kt` | Snooze files, alarms, re-arming, cancel |
| Push handling | `DuudleMessagingService.kt` | FCM entry point; stroke JSON parsing |
| Design system | `DuudleComponents.kt`, `ui/theme/*`, `BottomNav.kt`, `FaceBlobs.kt` | Shared header, tabs, pills, colours, bottom bar, animated characters |

### 2.3 Platform integrations
| Android API | Used for |
|---|---|
| `WindowManager` + `TYPE_APPLICATION_OVERLAY` | Doodle and flash card pop-ups over other apps (needs *Display over other apps*); always created on the main thread |
| `FirebaseMessagingService` | Receiving pushes when the app is in any state |
| `NotificationManager` (channel *Chat messages*) | Chat notifications, private on the lock screen |
| `AlarmManager` + `BroadcastReceiver`s | Snooze: `SnoozeAlarmReceiver` fires, `SnoozeRescheduleReceiver` re-arms after reboot, app update or permission change |
| `SharedPreferences` (`duudle_prefs`), app files | Local-only data (section 4.3) |

---

## 3. Authentication

```mermaid
sequenceDiagram
    participant U as User
    participant App as Duudle app
    participant Auth as Firebase Auth
    participant FS as Firestore
    U->>App: Picks a username (first launch)
    App->>Auth: signInAnonymously()
    Auth-->>App: uid (persisted on the device)
    App->>FS: query users where usernameLower == name
    FS-->>App: taken? (another uid)
    App->>FS: set users/{uid} {uid, username, usernameLower}
    App->>FS: update users/{uid}.fcmToken
```

- **Anonymous accounts only.** The `uid` is the user's identity everywhere: document ids,
  sender fields, rule checks. It is persisted by the Firebase SDK on the device.
- **One device, one account.** There's no email, password or account recovery; uninstalling
  or clearing data starts a fresh account. `android:allowBackup="false"` prevents restoring a
  stale username onto a new uid.
- **Every Firestore request is authenticated:** all rules start with `request.auth != null`,
  and ownership checks compare `request.auth.uid` with document fields or ids.
- **The FCM token** is the device's push address, stored on `users/{uid}` and refreshed in
  `DuudleMessagingService.onNewToken`.

---

## 4. Database

### 4.1 Collections

```mermaid
erDiagram
    USERS ||--o{ MUTES : "owns (private)"
    USERS ||--o{ FAVORITES : "owns (private)"
    USERS ||--o{ FRIEND_REQUESTS : "fromUid / toUid"
    USERS ||--o{ FRIENDSHIPS : "userA / userB"
    USERS ||--o{ DOODLES : "senderId / receiverId"
    USERS ||--o{ CHATS : "members[2]"
    CHATS ||--o{ MESSAGES : contains
    FAVORITES }o--|| DOODLES : "doc id = doodle id"

    USERS {
        string uid
        string username
        string usernameLower
        string fcmToken
    }
    FRIEND_REQUESTS {
        string fromUid
        string toUid
        string status
        number timestamp
    }
    FRIENDSHIPS {
        string userA
        string userB
        number createdAt
    }
    DOODLES {
        string senderId
        string receiverId
        array strokes
        string cardId
        string status
        number createdAt
    }
    CHATS {
        array members
        string theme
        string lastMessage
        string lastSenderId
        timestamp lastAt
        map lastRead
    }
    MESSAGES {
        string senderId
        string kind
        string text
        timestamp createdAt
    }
```

Design decisions:

- **Doodles and flash cards share one collection.** A card is a doodle with `cardId` and no
  strokes, so mute, Vault, snooze, stats and replies need no second code path.
- **Strokes are vector data, normalized to 0–1** of a **square** canvas, simplified and rounded
  before saving, so a doodle renders identically on any screen and usually fits in a push.
- **Deterministic chat ids:** `chats/{uidLow_uidHigh}`. Both people always address the same
  document, and the rules can check membership from the id itself.
- **Private data is a subcollection, not a field.** Mutes and favourites live under
  `users/{uid}/…` with owner-only rules, so a friend can't tell they were muted or what was
  saved.
- **Queries avoid composite indexes:** equality filters, a single-field prefix range for
  search, `array-contains` without `orderBy` for chat lists, and `count()` aggregations for
  stats.

### 4.2 Access control (security rules)

```mermaid
flowchart LR
    R["Request from app<br/>(signed in?)"] -->|no| X[Denied]
    R -->|yes| Q{Collection}
    Q -->|users/uid| U1["read: any user<br/>write: owner"]
    Q -->|mutes, favorites| U2[owner only]
    Q -->|friend_requests, friendships, doodles| U3["any signed-in user<br/>(known gap)"]
    Q -->|chats/id| U4["members only · members fixed<br/>preview by its sender · own lastRead only"]
    Q -->|messages| U5["members read · create as yourself<br/>server time · 1–1000 chars · no edits"]
```

Rules are versioned in [`firestore.rules`](firestore.rules) and deployed with the Firebase CLI.
Because pushes are sent by functions with admin rights, the functions repeat the
trust-sensitive checks themselves (mute, chat friendship, server-side username).

### 4.3 On-device storage
| Data | Store | Reason |
|---|---|---|
| Username cache | SharedPreferences `duudle_prefs` | Decide Welcome vs Home instantly |
| Profile character | `duudle_prefs` key `avatar_v1_<uid>` (versioned string) | Owner-only; additive format so old saves keep loading |
| Snoozed doodles | `files/snoozed/<doodleId>.json` + an alarm | Must re-show offline and with the app killed |

---

## 5. APIs and interfaces

Duudle has no REST API of its own. The interfaces between parts are:

| Interface | Between | Shape |
|---|---|---|
| **Firestore SDK** | App ↔ Firestore | Document reads/writes, batched writes (chat), snapshot listeners (live data), `count()` aggregations, offline cache |
| **Firestore triggers** | Firestore → Cloud Functions | `onDocumentCreated("doodles/{id}")`, `onDocumentCreated("chats/{chatId}/messages/{id}")` |
| **Firebase Admin SDK** | Functions ↔ Firestore / FCM | Admin reads (users, mutes, friendships, chats) and `getMessaging().send()` |
| **FCM data messages** | FCM → app | `type: "new_doodle"` and `type: "chat_message"` payloads, high priority |
| **Android intents** | Notification / overlay → `MainActivity` | `open_chat_uid` (open a chat), `reply_to_uid` / `reply_to_username` (draw a reply); uids are validated because the activity is exported |

### Push payload contract
| `type` | Sent by | Fields | Receiver action |
|---|---|---|---|
| `new_doodle` | `onDoodleCreated` | `doodleId`, `senderId`, `senderUsername`, `strokes` (if ≤ 3600 bytes), `cardId` (cards) | Show the overlay; if strokes are missing, fetch `doodles/{doodleId}` |
| `chat_message` | `onChatMessageCreated` | `chatId`, `senderId`, `senderUsername` (≤ 32), `text` (≤ 200) | Show a notification unless that chat is open |

These payload fields, the stroke format and the card ids are contracts between deployed
functions and installed apps; change them only additively.

---

## 6. How information moves

### 6.1 Doodle or flash card: from one phone to another's screen

```mermaid
sequenceDiagram
    participant S as Sender app
    participant FS as Firestore
    participant CF as onDoodleCreated
    participant FCM as FCM
    participant R as Receiver (DuudleMessagingService)
    participant O as Overlay window

    S->>FS: add doodles/{id} {senderId, receiverId, strokes | cardId, status: unseen}
    FS-->>CF: trigger (document created)
    CF->>FS: read users/{receiver} + mutes/{sender}
    alt muted or no fcmToken
        CF-->>CF: stop (doodle stays in the Vault)
    else
        CF->>FCM: data message new_doodle (strokes embedded if small, cardId)
        FCM->>R: deliver (app may be killed)
        alt strokes or cardId in payload
            R->>O: build overlay on main thread
        else too big to embed
            R->>FS: blocking get doodles/{id}
            R->>O: build overlay on main thread
        end
    end
    O->>FS: Remove / Draw → status: removed
    O->>O: Snooze → local file + alarm (no Firestore)
```

### 6.2 Chat message

```mermaid
sequenceDiagram
    participant A as Sender app
    participant FS as Firestore
    participant CF as onChatMessageCreated
    participant FCM as FCM
    participant B as Receiver app

    A->>FS: batch: add messages/{id} + merge chats/{chatId} preview
    Note over FS: rules: member, as yourself, server time,<br/>getAfter(chat) for the first message
    FS-->>A: listener shows it (clock until confirmed)
    FS-->>B: listener shows it (if the chat is open)
    FS-->>CF: trigger (message created)
    CF->>FS: read chat, friendships, mutes, users
    alt not friends, muted, theme note or no token
        CF-->>CF: stop
    else
        CF->>FCM: data message chat_message
        FCM->>B: notification (skipped if that chat is on screen)
    end
    B->>FS: merge chats/{chatId}.lastRead.{me}
```

### 6.3 Snooze (entirely on the device)

```mermaid
flowchart LR
    T[Tap Snooze 5/15 min] --> F["Write files/snoozed/id.json<br/>(sender, strokes or card, triggerAt)"]
    F --> AL["AlarmManager<br/>exact if allowed, else inexact"]
    AL -->|fires| RC[SnoozeAlarmReceiver] --> OV[Overlay again] --> DEL[Delete file]
    BOOT["Reboot / app update /<br/>exact-alarm permission / app launch"] --> RS[rescheduleAll]
    RS -->|future| AL
    RS -->|overdue| OV
    VAULT[Vault: Cancel snooze] --> CX[Cancel alarm + delete file]
```

### 6.4 Live screens
Home, Friends, Vault and Chat don't poll. They attach Firestore **snapshot listeners**, so a
write by either user shows up on both phones within moments, and Firestore's offline cache
keeps the last data visible without a connection.

| Screen | Listens to |
|---|---|
| Home | `doodles` where `receiverId == me` and `status == "unseen"` |
| Friends | `friendships` (both sides), `friend_requests` to me, my `mutes`, my `chats` (unread dots) |
| Vault | `doodles` sent and received, my `favorites` |
| Chat | newest 50 `messages`, the `chats/{id}` doc |

---

## 7. Reliability and constraints

| Concern | How it's handled |
|---|---|
| App killed when a push arrives | Data messages always reach `onMessageReceived`; the big-doodle fallback blocks on the fetch so the process isn't killed mid-callback |
| FCM 4 KB payload limit | Stroke simplification + rounding; embed only ≤ 3600 bytes, otherwise fetch |
| Overlays need the main thread | Every overlay is posted to `Looper.getMainLooper()` |
| Samsung battery management | Profile setup card: overlay permission, unrestricted battery, exact alarms, *Never sleeping apps* hint |
| Alarms wiped by reboot / update | Snooze files keep `triggerAt`; `SnoozeRescheduleReceiver` + app launch re-arm them |
| Offline sending | Firestore queues writes; chat shows a pending clock |
| Old app versions | Additive formats: unknown avatar keys ignored, unknown card ids show a placeholder, missing `cardId` falls back to a fetch |

---

## 8. Build and deployment

```mermaid
flowchart LR
    DEV["Developer laptop"] -->|"./gradlew assembleDebug"| APK[Debug APK] -->|adb install| PHONE[Samsung A34 / emulator]
    DEV -->|"firebase deploy --only firestore:rules"| RULES[Live rules]
    DEV -->|"firebase deploy --only functions"| FUNCS[Live functions]
    DEV -->|git push| GH["GitHub odomos/duudle"]
```

- There is **one Firebase project and no staging**: rule and function deploys go live
  immediately, so they're reviewed first.
- 110 JVM unit tests cover the pure logic; on-device behaviour (overlays, pushes, alarms,
  notifications) is verified on the A34 and tracked in `PHONE_TEST_CHECKLIST.md`.

---

## 9. Known architectural limits

- **Open collections:** `friend_requests`, `friendships` and `doodles` accept writes from any
  signed-in user, so friendship and doodle sender fields can be forged by a determined user.
  The chat push re-checks friendship server-side; the fix is a stricter rules design.
- **Anonymous identity:** no recovery or multi-device accounts.
- **No server-side rate limiting** on writes or pushes.
- **Profile characters are local**, so friends can't see them yet; syncing would add a field
  to `users/{uid}` and a rules change.
- **No chat inbox list;** chats open from Friends or a notification.
