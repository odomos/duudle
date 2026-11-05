# Play release guide

How to get Duudle onto Google Play (Phase 8). App ID: **`com.arnavchauhan.duudle`**
(permanent once uploaded). Owner: Arnav, personal Play developer account.

## 1. Before the first upload

Everything in `PHONE_TEST_CHECKLIST.md` that's still open for Phases 5a–8 is tested on the
A34, and Phases 6 and 7 are live (see `roadmap.md`). Then:

### Upload key (you, once)

Play signs the app for users with its own key (Play App Signing). You sign uploads with an
**upload key** that only you hold. Keep it outside the repo folder, with two backups (e.g. a
USB stick and a password manager for the passwords); losing it means asking Google for a
reset.

```
"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore C:\Users\arnav\duudle-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

Then create `keystore.properties` next to `settings.gradle.kts` (git ignores it, and `*.jks`):

```
storeFile=C:/Users/arnav/duudle-upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

Claude never reads or types these passwords. With the file present, `bundleRelease` signs the
bundle; without it (CI), the bundle comes out unsigned.

### Build

1. Bump `versionCode` (every upload needs a higher one) and `versionName` in
   `app/build.gradle.kts`.
2. `.\gradlew.bat bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.

### Firebase and keys after the first upload

Release builds are signed by Play's key, not the laptop's debug key, so until these are done
sign-in, Firestore and Google linking fail in the Play build:

1. Play Console > Test and release > App integrity > App signing: copy the **app signing key**
   SHA-1 and SHA-256, and the **upload key** SHA-1.
2. Firebase console (duudle-296a9) > Project settings > the `com.arnavchauhan.duudle` app:
   add all three fingerprints. Download nothing; the config doesn't change.
3. Add `com.arnavchauhan.duudle` + each release SHA-1 to **both** "Android key (auto created by
   Firebase)" keys on duudle-296a9 (Google Cloud console > Credentials). Claude can do this
   with your OK.
4. Firebase console > App Check > the app: register **Play Integrity** (link the Play Console
   app). Watch the App Check metrics for a week of real traffic before enforcing.

## 2. Play Console forms (draft answers)

**Privacy policy URL:** `https://duudle-296a9.web.app/privacy` (`hosting/privacy.html`,
published 2026-11-05). **Account deletion URL:** `https://duudle-296a9.web.app/delete`.

**Data safety**
- Data encrypted in transit: yes. Users can request deletion: yes (in app + web page).
- Collected, not shared (Google acts as a service provider):

| Data type (Play's names) | Why | Optional? |
|---|---|---|
| Personal info > Name (username) | App functionality | Required |
| Personal info > Email address (only if Google is linked) | Account management | Optional |
| Personal info > User IDs | App functionality, account management | Required |
| Messages > Other in-app messages (chat) | App functionality | Required for chat |
| Photos and videos > Photos (doodles count as user images) | App functionality | Required |
| App activity > Other user-generated content (reports, character) | App functionality, fraud prevention/safety | Required |
| App info and performance > Crash logs, Diagnostics | Analytics (app stability) | Required |
| Device or other IDs (FCM token, installation ID, App Check) | App functionality, fraud prevention | Required |

- No location, contacts, financial, health, audio, files, calendar, web history or ads data.

**Content rating:** users interact and exchange user-generated content (doodles, chat);
no in-app purchases, no location sharing, no gambling. Expect a 12+/Teen-type rating.

**Target audience:** 13+ only (matches the Terms age gate). Not designed for children; don't
opt in to Families.

**Special permissions:** "Display over other apps" (`SYSTEM_ALERT_WINDOW`) is the core
feature: friends' doodles float over other apps. The user turns it on in Android settings;
without it doodles still arrive as notifications and in the Vault.

**App access for reviewers:** no login needed; open the app, pick a username, accept the
Terms. To see a pop-up, add a test friend: provide a second test account's username in the
review notes, or "Profile > Test a pop-up" shows a sample.

**Store listing:** screenshots from the A34 (Draw, a pop-up over another app, Friends, Vault,
chat). Don't claim pop-ups work without the permission.

## 3. Closed test (personal account rule)

New personal accounts need **12+ testers opted in for 14 days in a row** on a closed test
before applying for production. Plan:
1. Create the closed test track, upload the first bundle, add testers by Google Group or
   email list.
2. Give testers the opt-in link and a feedback route (the contact email).
3. Keep at least 12 opted in for 14 consecutive days (someone opting out restarts nothing for
   others, but keep a margin: aim for 15+).
4. During the test watch Crashlytics, the alerts (`MONITORING.md`) and reports
   (`MODERATION.md`); fix and upload new builds as needed (testers stay opted in).
5. Then apply for production access and roll out in stages.

## 4. After release

- App Check: after a week of mostly-verified traffic, enforce for Firestore and
  Authentication, then add an App Check check to the `deleteAccount` function.
- Keep the old `com.example.duudle` Firebase app registrations until no tester runs the old
  build, then remove them and their package from the API keys.
