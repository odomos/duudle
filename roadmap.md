# Duudle — Engineering and Google Play Launch Roadmap

> **Scope:** This roadmap consolidates the findings and recommendations from reviewing `README.md` and `ARCHITECTURE.md`: implementation gaps, security and privacy fixes, content moderation, analytics/observability, reliability, scaling, testing and Play Store launch requirements.
>
> **Date checked:** 26 October 2026. Play policies can change; re-check the linked official pages immediately before submission.
>
> **Important boundary:** The review was based on the two Markdown documents, not the full Kotlin source, manifest, Gradle files, deployed Firestore rules, Cloud Functions code, Firebase Console or Play Console. Every item below must be verified in the real implementation before it is marked complete. The target SDK is documented as 36, but the generated release artifact and Play Console still need to be checked.

## 0. Executive summary

Duudle's documented baseline is a Kotlin + Jetpack Compose Android app using Firebase Anonymous Authentication, Cloud Firestore, Cloud Functions 2nd gen (Node.js 24) and Firebase Cloud Messaging. Its main differentiator is a doodle/flash-card pop-up overlay over other apps, with chat and friendships.

**Do not start by replacing Firebase or rewriting the app.** First make the current app safe to release, then improve measurement/reliability, and refactor architecture incrementally.

### Release blockers / highest-risk items

- [ ] **Lock down Firestore rules.** The architecture document explicitly identifies broad signed-in-user write access to `friend_requests`, `friendships` and `doodles`, allowing fields/relationships to be forged.
- [ ] **Audit FCM token privacy.** The docs say `fcmToken` is stored in `users/{uid}` while user documents are readable by any signed-in user. If the deployed rules match, a user's device token may be exposed. Move it to an owner-only/private device record and verify with emulator tests.
- [ ] **Add report and block.** Mute alone is not a complete block or UGC moderation system.
- [ ] **Add a real account-deletion path** in-app plus a working external web deletion request page; complete Play Console Data safety deletion questions.
- [ ] **Add account recovery/linking** so users are not permanently stranded if anonymous app data is lost.
- [ ] **Implement server-side abuse/rate limiting** for content and push-triggering actions; add App Check with a monitored rollout.
- [ ] **Verify overlay safety and fallback behaviour** across OS versions and devices, including revoked/missing permission.
- [ ] **Replace watermarked flash-card assets 01, 02 and 05** with original or properly licensed material.
- [ ] **Create staging separate from production**, automated security/function tests and a rollback process.
- [ ] **Verify Play Store declarations and release build**, including target API 36, privacy policy, Data safety, content rating, account deletion URL and correct UGC controls.

## 1. Status vocabulary

Use these statuses in PRs/issues so documentation does not accidentally imply that an unimplemented recommendation is done:

- **Documented current:** README/architecture says the feature exists.
- **Known gap:** architecture explicitly describes the weakness or the reviewed documents do not document a needed control.
- **Needs verification:** only source/deployment/Play Console inspection can establish status.
- **Done:** code, tests and evidence have been reviewed; production configuration is confirmed where relevant.

A blank checkbox below does not mean the feature definitely does not exist in code; it means it has not been proven complete by this documentation review.

## 2. P0 — Security and policy blockers before public launch

### 2.1 Firestore security rules and data access

**Finding from architecture:** rules are described as requiring authentication, but `friend_requests`, `friendships` and `doodles` are documented as broadly writable by any signed-in user. The function code reportedly repeats some checks for push delivery, but function checks do not prevent unauthorized writes from reaching Firestore.

- [x] Read the actual `firestore.rules` and `firebase.json`; map every match path, wildcard, allow statement and collection.
  - **Tests passed** _(Phase 0, 2026-10-26)_: mapped in the audit (findings F1, F2, F9, F11, F24). The repo rules equal the deployed rules (last deployed from this repo on 2026-10-24).
- [ ] Make sensitive operations least-privilege and deny-by-default.
- [ ] Friend requests: require `fromUid == request.auth.uid`; reject self-requests, invalid recipients and invalid status changes; only recipient may accept/decline a request addressed to them; handle duplicate requests.
  - **Tests passed (emulator), deployed to staging, NOT in production** _(Phase 1, 2026-10-26)_: sender must be the caller, no self-requests, `status` must be `pending`, field allow-list, only the two people can read or delete, no updates. Not yet: server-side duplicate prevention (the app checks before sending).
- [ ] Friendships: do not allow a client to invent either participant or create a friendship without a valid accepted request. Use rules or a trusted callable function for the transition and multi-document consistency.
  - **Tests passed (emulator), deployed to staging, NOT in production** _(Phase 1, 2026-10-26)_: created only by the accepting user, naming a real pending request the other person sent them (checked with `get()`), in one batch with deleting it, so it can't be replayed. Only members can read or delete. `FriendsScreen.acceptFriendRequest` now uses the batch.
- [ ] Doodles/cards: require sender identity to match the authenticated caller, validate receiver and payload, bound stroke/card sizes, enforce permitted status changes and relationship/block rules.
  - **Tests passed (emulator), deployed to staging, NOT in production** _(Phase 1, 2026-10-26)_: sender = caller, receiver must be someone else, field allow-list, at most 500 strokes, card id must match `card[0-9][0-9]`, created as `unseen`, receiver may only set `status` to `removed`, no deletes, only sender and receiver can read. `onDoodleCreated` now checks friendship and uses the sender's stored username (6 unit tests). Not yet: block (Phase 4), and friendship isn't checked in rules (random friendship ids), so a stranger's doodle can still land in your Vault, but it never pops up.
- [ ] Chat: verify both participants and fixed membership; sender identity must equal caller; clients must not add themselves to arbitrary chats or edit immutable fields; preserve message size/type validation and self-only read receipts.
- [ ] Profiles: constrain each user's mutable fields; validate username format and uniqueness flow; prevent server-controlled fields from being client-edited.
  - **Tests passed (emulator), deployed to staging, NOT in production** _(Phase 1, 2026-10-26)_: owner only, allowed fields only, usernames 3-20 of `A-Z a-z 0-9 _ .` with `usernameLower == username.lower()` (same rule in `WelcomeScreen`, 4 unit tests), no profile deletes. Not yet: server-enforced uniqueness (needs a usernames reservation doc and a backfill: live-data change, Phase 1b). `fcmToken` is still allowed here until Phase 2.
- [ ] Private subcollections (mutes, favorites, blocks, device tokens) must be readable/writable only by their owner or trusted server paths as designed.
- [ ] Write Firebase Emulator Suite / `@firebase/rules-unit-testing` tests for allow and deny cases. Test anonymous authenticated users separately from unauthenticated requests.
  - **In progress** _(Phase 0, 2026-10-26)_: `firestore-tests/` added. Regression suite `npm test`: **25/25 passing** on the emulator (signed-out vs signed-in, owner-only subcollections, chat rules, and the real app flows that must survive the lockdown). Target suite `npm run test:targets`: **0/25 passing, 25 failing as expected**: each failure proves an operation today's rules wrongly allow. Becomes the Phase 1 acceptance gate.
- [ ] Include tests for forged UIDs, changed sender/receiver, forged friendship, unauthorized read, invalid field additions, oversize content, replayed state changes, blocked users and non-member chat access.
  - **In progress** _(Phase 0, 2026-10-26)_: written for forged UIDs, sender/receiver changes, forged friendships, unauthorized reads, extra fields, oversize drawings and non-member chat access. Not yet: blocked users (block doesn't exist yet) and replayed state changes.
- [ ] Deploy rule changes to staging first; review a diff and test results before production.
  - **In progress** _(Phase 1, 2026-10-26)_: rules deployed to `duudle-staging`; a `staging` build type ("Duudle Staging", `com.example.duudle.staging`) talks only to staging. Production deploy waits for the staging phone test and owner approval.

**Acceptance gate:** no public launch while a normal authenticated client can forge relationships, send as another UID, read private records, or alter a field it does not own.

**Phase 1 security review (2026-10-26), fixed before any production deploy:** one request makes exactly one friendship (`existsAfter`: the request must be deleted in the same batch); doodle `senderUsername` and request `fromUsername` must equal the caller's stored username (no sending under someone else's name); legacy profiles can still add their search key; `onDoodleCreated` handles lookup errors. Mutation-checked: removing either new protection makes exactly its own test fail. Gate: **69/69 rules tests, 118/118 unit tests, 6/6 function tests**. Deployed to `duudle-staging` only.

**Release order (from the review):** ship the new app first (it works with the old rules too), then deploy the functions, then the rules. The old app's Accept is denied by the new rules (tested). Pushes from older functions are still accepted by the new app.

Reference: [Firebase Security Rules unit tests](https://firebase.google.com/docs/rules/unit-tests), [Firebase guide to insecure rules](https://firebase.google.com/docs/firestore/security/insecure-rules).

### 2.2 Make device tokens private

**Finding from architecture:** the documented `users/{uid}` document includes `fcmToken`; user documents are described as readable by any signed-in user. Confirm this against the deployed rules immediately.

- [ ] Stop storing the token in a publicly readable profile document.
  - **Stopgap implemented, tests passed, staging only** _(Phase 1 security review, 2026-10-26)_: pushes now carry `receiverId` and the app drops pushes for another account (`PushGuard.kt`, 4 unit tests), so a token planted on someone else's profile can't redirect pops-ups; rules require the token to be a string. The token itself is still public until Phase 2 moves it.
- [ ] Move to an owner-only device record (for example `users/{uid}/devices/{deviceId}`) or another private structure; ensure only the owner and trusted Admin SDK functions can access it.
- [ ] Support multiple devices/tokens if the same account can be linked across devices.
- [ ] Update token on `FirebaseMessagingService.onNewToken`; safely retire old tokens and remove invalid/unregistered tokens after send errors.
- [ ] Ensure account deletion and sign-out clean up relevant registrations.
- [ ] Add rule tests proving User A cannot read User B's token or private device record.
- [ ] Never log raw FCM tokens in analytics, crash reports or ordinary logs.
  - **Implemented** _(Phase 1, 2026-10-26)_: the raw-token log line in `DuudleMessagingService.onNewToken` is removed. Not yet released.

### 2.3 UGC policy: terms, reporting, blocking and moderation

Duudle includes user-created doodles and one-to-one chat. Google Play's UGC policy expects robust ongoing moderation, acceptance of terms before posting/uploading UGC, in-app reporting and a user-blocking function for one-to-one interactions.

- [ ] Write clear Terms of Use/User Policy that covers prohibited content, harassment, abuse, reporting, enforcement and consequences.
- [ ] Require policy acceptance before users create/send UGC; store acceptance version/time in a defensible way.
- [ ] Add **Report user** and **Report content/message** actions at relevant entry points, not only an email address buried elsewhere.
- [ ] Define minimal report fields (reporter, target user/content ID, reason, timestamp, status, review metadata); constrain who can create/read/change each field.
- [ ] Implement **Block user**, distinct from mute. Enforce it across user interaction, friend requests, chat sending/access and Cloud Function push eligibility. Do not rely on UI hiding.
- [ ] Define a moderator/admin process to review reports, remove content/disable accounts as appropriate, deal with repeat offenders and record resolution without keeping unnecessary sensitive data.
- [ ] Provide a route for urgent safety reports and maintain a response process during beta/production.
- [ ] Check age screening/target audience requirements and answer the Play content-rating questionnaire accurately for user-generated content and social features.
- [ ] Test the entire report/block lifecycle with two accounts, including direct Firestore/API attempts that bypass the UI.

**Acceptance gate:** a user can report another user and a piece of content from inside the app, block someone, and the block is enforced server-side so new direct interactions/pushes do not bypass it.

Reference: [Google Play — User Generated Content](https://support.google.com/googleplay/android-developer/answer/9876937).

### 2.4 Account recovery and account deletion

**Finding from architecture:** auth is anonymous-only; uninstalling/clearing app data may create a new UID and lose the old identity. No account deletion flow is documented.

- [ ] Choose a supported account-linking/recovery method and link the existing anonymous Firebase account to it without changing its UID where possible.
- [ ] Handle credential conflicts, linking interruption, sign-in from a second device and migration/recovery UX.
- [ ] Explain the anonymous-account limitation until recovery is available.
- [ ] Add a clearly discoverable in-app deletion path (for example, Settings/Profile → Delete account) with confirmation and clear description of consequences.
- [ ] Publish a functioning external web page where a user can request account and associated-data deletion even after uninstalling; the page must identify Duudle/developer and make the request process easy to find.
- [ ] Implement a trusted, retryable deletion workflow: stop future writes/notifications, revoke device tokens, enumerate/remove or anonymize associated records, handle nested collections and related records, then delete the auth account at a safe point.
- [ ] Decide and disclose how content visible in other users' chats is handled, and which limited records may be retained for abuse prevention/legal compliance. Do not promise erasure beyond what the system actually does.
- [ ] Cover profiles, friend requests/friendships, sent/received doodles, chat messages and metadata, favorites, mutes, blocks, private tokens, analytics identifiers, local cached files and pending jobs as applicable.
- [ ] Make repeated delete requests idempotent and recover cleanly from partial failure; record completion status with minimal retained data.
- [ ] Complete Play Console Data safety's data-deletion questions and provide the web resource URL.
- [ ] Verify the user can request deletion when not logged in to the app (web flow should not simply require reinstalling/logging back into the app).

**Acceptance gate:** independently verify the in-app route and external URL, test full deletion on staging, and show that no account-accessible tokens or content remain except documented lawful/security retention.

Reference: [Google Play — Account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111).

### 2.5 Abuse controls and Firebase App Check

- [ ] Add server-enforced rate limiting/quotas for registration, username lookups, friend requests, doodle sends, chat messages and actions that trigger pushes.
- [ ] Reject malformed or over-sized payloads before expensive work.
- [ ] Configure App Check for the Android app using Play Integrity where appropriate.
- [ ] Begin by monitoring App Check metrics and validating test devices/real traffic; enable enforcement gradually for supported services after validating legitimate traffic.
- [ ] Remember App Check supplements, but does not replace, auth and Firestore rules.
- [ ] Add spam/abuse alerts for bursts in content writes, rejected requests and unusual push volume.

Reference: [Firebase App Check](https://firebase.google.com/docs/app-check).

## 3. P1 — Reliability, monitoring, delivery correctness, and operations

### 3.1 Staging and production separation

**Finding from architecture:** one Firebase project (`duudle-296a9`) and no staging are documented; deploying rules/functions goes live immediately.

- [ ] Create a separate staging Firebase project; keep development, staging and production project selection explicit.
- [x] Ensure local/emulator tests cannot accidentally use live resources.
  - **Tests passed** _(Phase 0, 2026-10-26)_: rules tests run only under the offline-only project id `demo-duudle` against the local emulator (`firebase.json` emulators block).
- [ ] Set up staging and production app IDs/configuration intentionally; avoid mixing production users/tokens into staging.
- [ ] Version `firebase.json`, rules, indexes and function source/configuration; document secret management without committing private keys.
- [ ] Add a reviewed production deployment command/workflow and manual approval.
- [ ] Document rollback steps for app release, Firestore rules and Cloud Functions; preserve a known-safe rules version that is not broadly open.

### 3.2 Automated testing / CI

The README reports 110 JVM unit tests and manual verification on a Samsung Galaxy A34. Keep those tests, but they cover only the behaviours they actually exercise.

- [ ] Run Android compile and JVM unit tests on pull requests.
  - **Implemented, not yet run on GitHub** _(Phase 0, 2026-10-26)_: `.github/workflows/ci.yml` (Android build + unit tests, both rules suites (targets non-blocking until Phase 1), function syntax check). Starts running once the workflow is pushed.
- [ ] Add Firestore emulator tests for all rules and sensitive operations.
  - **In progress** _(Phase 0, 2026-10-26)_: see 2.1; also runs in CI.
- [ ] Add Cloud Functions lint/unit tests, using emulator/test fixtures where possible.
  - **In progress** _(Phase 1, 2026-10-26)_: `functions/test/doodlePush.test.js`: 6 tests on the doodle push decision (`npm --prefix functions test`, also in CI). No lint yet.
- [ ] Test duplicate trigger execution, missing/deleted users, missing/invalid tokens, block/mute relationships, malformed fields and FCM send failures.
- [ ] Add integration scenarios for account creation, sending/receiving doodles, chat, report/block, delete account and notification deep links.
- [ ] Preserve an updated phone test checklist and record device model, Android version, build, result and known issue.
- [ ] Add a release bundle build to CI; do not ship just because unit tests pass.

### 3.3 Crash and performance monitoring

- [ ] Integrate Firebase Crashlytics; verify an intentional test crash in a non-production build appears correctly, then remove/guard test-only crash triggers.
- [ ] Enable Firebase Performance Monitoring for startup and important app flows; measure actual slow paths instead of guessing.
- [ ] Configure Cloud Logging/Monitoring dashboards and alert policies for Cloud Functions errors/duration, unusual invocation volume, Firestore access failures and service errors.
- [ ] Set up budget/usage alerts for Firestore, Functions, logs and other enabled Google Cloud services.
- [ ] Assign an owner and response procedure for high crash rate, function outage, abuse spike and billing spike.

References: [Crashlytics for Android](https://firebase.google.com/docs/crashlytics/android/get-started), [Performance Monitoring for Android](https://firebase.google.com/docs/perf-mon/get-started-android), [Firestore best practices](https://firebase.google.com/docs/firestore/best-practices).

### 3.4 Distinguish push-send success from actual delivery

A successful FCM send does not prove a device received or displayed a message. Treat these as distinct states/events.

- [ ] Define minimal event names, for example `doodle_created`, `push_attempted`, `push_send_accepted`, `push_send_failed`, `device_received` (where measurable), `overlay_displayed`, `fallback_displayed`, `overlay_actioned`.
- [ ] Use a stable doodle/message/event ID so duplicate triggers do not lead to duplicate pushes or duplicate metrics.
- [ ] Avoid showing duplicate overlays/notifications if the same event reaches a device more than once.
- [ ] Handle invalid/expired FCM tokens and token refresh.
- [ ] Add overlay permission failure and fallback outcomes, so a push that reaches the app but cannot draw over other apps is visible in metrics.
- [ ] Do not mark a message “seen” just because a push API call succeeded. Delivery acknowledgements may never arrive when offline or force-stopped.
- [ ] Use bounded retries and backoff where appropriate; ensure retries cannot produce uncontrolled repeated pushes.
- [ ] Keep analytics writes lean; aggregate or sample nonessential events to prevent analytics from becoming a major Firestore cost.

### 3.5 Firestore cost and reliability

- [ ] Review snapshot listeners for correct lifecycle ownership and cleanup; test recomposition/navigation does not multiply listeners.
- [ ] Keep histories bounded and use cursor pagination; the documented chat screen loads the newest 50 messages.
- [ ] Review indexes and exempt large stroke/map/array fields from indexing if not queried on those fields.
- [ ] Monitor reads/writes/deletes, stored bytes, hot documents, query latency and cost per active user.
- [ ] Load-test likely traffic patterns; do not infer scalability solely from a passing unit-test suite.
- [ ] Decide backup/export retention and perform a test restore to non-production.
- [ ] Choose a database region deliberately and document latency/cost implications before any region migration.

Official guidance: [Cloud Firestore best practices](https://firebase.google.com/docs/firestore/best-practices).

## 4. P1 — Overlay and Android-device reliability

- [ ] Inspect `AndroidManifest.xml` for `SYSTEM_ALERT_WINDOW`, services, receivers, exported components, permissions and intent filters.
- [ ] Explain overlay value before directing a user to “Display over other apps”; request permission only after a clear user action.
- [ ] Provide settings to disable overlays and easy dismissal.
- [ ] Handle missing/revoked permission with no crash, no infinite permission nagging, and a fallback such as a notification or in-app Vault.
- [ ] Verify the overlay does not impersonate system UI, trap input, block essential app controls or behave deceptively.
- [ ] Test both target and minimum supported device environments, including Android 8+, current Android versions, notification permission, Doze, battery optimization, reboot, app update, rotation, split screen and OEM restrictions.
- [ ] Verify all window operations run on the main thread, windows are removed on dismissal/error and no Activity/context/window leaks occur.
- [ ] Check message/notification taps and exported `MainActivity` intents cannot be spoofed to open a forged UID/recipient. Validate extras and only trust authenticated/authorized state fetched from Firebase.
- [ ] Verify notification channels, lock-screen privacy, PendingIntent flags, service lifecycle and accessibility.
- [ ] Keep snooze state/rescheduling robust after reboot, app update, permission changes and process death; test cancellation and duplicate alarms.

**Acceptance gate:** overlay permission is optional, the user understands it, overlay failures are harmless, and the core doodle remains accessible via a fallback.

## 5. P1 — Architecture and code maintainability

The documented code currently uses one `MainActivity`, a plain screen enum, Compose-owned state and no ViewModels/navigation library. This can be acceptable for an early prototype but will become harder to reason about as features and tests grow.

- [ ] Introduce ViewModels for screen state and feature actions, one feature at a time.
- [ ] Expand repository boundaries around Firebase so UI code does not repeat queries/rules assumptions.
- [ ] Extract overlay setup and lifecycle into a dedicated controller/service boundary.
- [ ] Add a navigation/back stack incrementally while preserving notification routing.
- [ ] Keep pure logic (stroke simplification, avatars, chat helpers, snooze timing) in unit-testable classes.
- [ ] Use typed data models and explicit loading/error/empty states; centralize payload/document validation.
- [ ] Keep Firestore document/push contracts backward-compatible for already-installed app versions; use additive changes and documented migrations.
- [ ] Do not perform a wholesale rewrite merely to adopt fashionable architecture; refactor around tested behaviour and high-risk seams.

## 6. P2 — Analytics and actionable product insights

### Product insights

- [ ] Define event names and metric definitions before enabling analytics.
- [ ] Track acquisition/new accounts, onboarding completion, DAU/WAU/MAU, friend requests/acceptance, doodles/cards sent, replies, active chats, and report/block outcomes.
- [ ] Track overlay permission completion, overlay render success/failure and fallback usage.
- [ ] Add dashboards to identify where users abandon onboarding, which core actions are used and whether new releases improve or harm retention.

### Operational insights

- [ ] Track crash-free users/sessions, ANRs, startup and key interaction latency.
- [ ] Monitor Firestore operations and errors, Cloud Functions invocation/error/duration, FCM send outcomes, invalid tokens, and billing/cost per active user.
- [ ] Use alerts for crash spikes, data-access failures, function error spikes and unexpected spend.
- [ ] Consider BigQuery export for larger analytical queries when regular dashboard needs outgrow lightweight Firebase Analytics; do not use Firestore as a general analytics warehouse.

### Privacy requirements

- [ ] Do not log chat text, private doodle strokes, raw FCM tokens, authentication secrets or unnecessary sensitive IDs.
- [ ] Set access controls and retention for logs/reports/analytics and document relevant SDKs/data collection.
- [ ] Make Play Console Data safety answers match the production implementation, not intended future behaviour.

## 7. P2 — Scale strategy (only when supported by data)

- [ ] Establish baseline traffic, latency, error and cost metrics during internal/closed testing.
- [ ] Test expected peaks and identify the actual bottleneck before adding infrastructure.
- [ ] Optimize Firestore query scope, listener lifetimes, indexes and data size first.
- [ ] Add Cloud Tasks for deferred/throttled/retryable jobs only where the workload benefits from a queue.
- [ ] Use Cloud Storage if larger media assets are introduced; avoid storing large binary blobs in Firestore documents.
- [ ] Use BigQuery for aggregate event analysis at scale.
- [ ] Consider a separate custom backend only if measured needs cannot be addressed with Firebase or a specific feature requires it. Kubernetes is not a launch prerequisite.
- [ ] Avoid high-frequency updates to a single shared document/counter; load-test contention before choosing aggregation designs.

## 8. P2 — Content/assets and privacy documentation

- [ ] Replace watermarked flash-card assets **01, 02 and 05**, explicitly flagged in the README, with original artwork or assets whose licence permits the intended distribution.
- [ ] Keep attribution/licence evidence for all third-party assets, fonts, sounds and other content included in the app.
- [ ] Publish a privacy policy that accurately describes anonymous/linked accounts, user-generated doodles/chat, data storage, notifications, analytics/crash SDKs, retention, reports, account deletion and contact/support route.
- [ ] Provide a support/contact channel and a clear route for policy/privacy requests.
- [ ] Verify public profile fields and app analytics do not expose private data or tokens.

## 9. P2 — Google Play technical checklist

### 9.1 Target SDK and release package

The README says **min SDK 26, target/compile SDK 36**. Google Play's current target API guidance states that, from **31 August 2026**, new Android mobile apps and app updates must target Android 16 / API level 36 or higher for submission. That means the documented target is aligned, but the actual release configuration must be verified.

- [ ] Verify `compileSdk`, `targetSdk` and `minSdk` in the actual Gradle files and merged manifest.
- [ ] Build a signed Android App Bundle (AAB) for Play, not only a debug APK.
- [ ] Verify the AAB's effective target SDK and package ID in the release artifact/Play Console.
- [ ] Test API 36 behaviour and regression-test overlays, exact/inexact alarms, notification permission, background execution and exported components.
- [ ] Store signing credentials safely and document key ownership/recovery. Do not commit keystores/passwords to source control.
- [ ] Check app name, icon, version code/name, crash diagnostics and privacy policy URL.
- [ ] Confirm any relevant policy exception/extension only if required; do not assume one is granted.

Reference: [Google Play target API level requirements](https://support.google.com/googleplay/android-developer/answer/11926878).

### 9.2 Play Console policy forms and store listing

- [ ] Create/verify Play Console app record, package name and ownership.
- [ ] Complete the Data safety form accurately (including all collected/shared data, Firebase/analytics/crash SDK behaviour, security practices and deletion details).
- [ ] Provide a public privacy policy that loads and matches actual behaviour.
- [ ] Complete content rating questionnaire, including UGC/chat/social interaction and relevant audience questions.
- [ ] Declare target audience correctly; verify any Families requirements if the app is available to children.
- [ ] Provide terms/user policy and in-app acceptance before UGC creation.
- [ ] Add report and block, plus a functioning moderation workflow before public release.
- [ ] Submit and test the account deletion web URL and in-app deletion flow; complete data-deletion questions.
- [ ] Ensure app screenshots, description and claims represent the app accurately, including the fact that overlays require user-granted permission.
- [ ] Review permission declarations and any Play Console forms relevant to special permissions. Explain use of overlay permission clearly and ensure it is central to the user-requested feature, user-controlled and non-deceptive.
- [ ] Replace unlicensed/watermarked assets; confirm intellectual property rights for all store and in-app content.
- [ ] Provide reviewer access/test instructions and valid credentials if review features require login. If the anonymous path remains available, document a reliable way for reviewers to reach all relevant features.

### 9.3 Personal developer account testing rule

If the Play Console account is a **new personal developer account created after 13 November 2023**, Google currently requires a closed test with at least **12 testers continuously opted in for at least 14 days** before applying for production access. This is account-type-specific; check the exact requirement shown in the relevant Play Console account. Testers who opt out interrupt their consecutive 14-day period.

- [ ] Determine account type and creation date in Play Console.
- [ ] If the requirement applies, recruit at least 12 testers, keep them opted in throughout the consecutive 14-day period and provide a feedback channel.
- [ ] Track engagement/feedback, crashes and fixes; do not treat the calendar requirement as proof of quality.
- [ ] Apply for production access only when policy, reliability and moderation gates are actually met.

Reference: [Google Play — Testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465).

## 10. P3 — Release and post-launch runbook

### Before internal testing

- [ ] P0 rules, token privacy, report/block and account deletion paths are implemented and covered by tests.
- [ ] Watermarked/unlicensed assets are removed.
- [ ] Staging exists; CI and rollback plan are documented.
- [ ] Crash/performance monitoring and basic function/database alerts are active.
- [ ] App's policy documents and data handling are aligned with the code.

### Internal test

- [ ] Upload internal-test AAB; install from Play testing flow.
- [ ] Test onboarding, usernames, friend requests, doodles, flash cards, chat, notifications, overlay permission, snooze, Vault, block/report and deletion.
- [ ] Test notification/overlay behaviour when foregrounded, backgrounded, process killed, device rebooted, permission denied and network unavailable.
- [ ] Confirm no crashes/ANRs or severe authorization leaks; check logs for secrets/UGC content.

### Closed beta

- [ ] Run the required account-specific testing period; keep a bug and feedback tracker.
- [ ] Test on varied Android versions/manufacturers, not just the documented Samsung Galaxy A34.
- [ ] Monitor crash-free users, ANRs, failed functions, push outcomes, report rates, and Firestore/billing usage.
- [ ] Fix release-blocking defects before production access/review.

### Production rollout

- [ ] Review current Play policy pages and Console warnings immediately before submission.
- [ ] Capture build/version, rules revision and function revision; retain the release checklist.
- [ ] Roll out in stages where available; monitor the dashboard after release.
- [ ] Define a kill switch for new push/overlay behaviour or other risky features where feasible, plus incident-response/rollback ownership.
- [ ] Review reports and user feedback promptly; continue moderation and security updates after launch.

## 11. Suggested order of implementation

| Order | Workstream | Reason / exit criterion |
|---|---|---|
| 1 | Inspect actual deployed rules and token exposure | Urgent possible data exposure; write/execute tests before changes |
| 2 | Restrict Firestore rules and add emulator tests | Prevent forged relationships and unauthorized data access |
| 3 | Report, block and moderation enforcement | Required for UGC readiness; block must work beyond the UI |
| 4 | Account linking/recovery and deletion + web resource | Protects identity and satisfies account-deletion requirements |
| 5 | Rate limits + App Check | Reduce scripted abuse and unauthorized clients |
| 6 | Overlay fallback/device tests and safe intent handling | Core feature is platform-sensitive and can fail silently |
| 7 | Staging, CI, function idempotency and rollback | Prevents risky direct-to-production changes |
| 8 | Crash/performance/Cloud monitoring and delivery metrics | Makes failures and scaling/cost issues observable |
| 9 | Architecture refactor in small slices | Improve maintainability without destabilizing working features |
| 10 | Assets, privacy/docs/forms, target/API, store listing | Complete Play submission checklist and verify package |
| 11 | Internal/closed test and monitored production | Demonstrate behaviour and resolve actual user issues |

## 12. Files/resources to inspect for the next implementation pass

Do not assume these paths or contents beyond what the documents say; locate them in the repository and confirm names.

- [ ] `firestore.rules` and `firebase.json` — real access-control rules, indexes and emulator wiring.
- [ ] Cloud Functions source (the architecture describes Node.js 24 and two document-created triggers) — validation, mute/block checks, idempotency, FCM token cleanup and error handling.
- [ ] `app/build.gradle.kts` / project Gradle files, version catalog and merged manifest — actual target/compile/min SDK, dependency versions and permissions.
- [ ] `AndroidManifest.xml` — overlay permission, exported components, services/receivers, intent filters and permissions.
- [ ] `MainActivity.kt` and `DuudleMessagingService.kt` — auth/token handling, notification/deep-link validation, payload validation and overlay lifecycle.
- [ ] Doodle/friend/chat repositories or call sites — authorization assumptions, field validation, bounded queries and listener cleanup.
- [ ] Account/profile settings UI — account linking/recovery and delete entry points.
- [ ] Flash-card catalogue/assets 01, 02 and 05 — replace with clean rights-confirmed assets.
- [ ] `PHONE_TEST_CHECKLIST.md` — update with rules/privacy/moderation/account deletion and OS-specific overlay test cases.
- [ ] Play Console — developer account type/date, target API, Data safety, content rating, deletion URL, testing track and policy warnings.

## 13. Definition of “ready for public Play release”

Only call Duudle ready when all applicable checks below have evidence:

- [ ] No known critical/high authorization issue; Firestore rules tests prove denial of forged IDs, reads/writes and relationship changes.
- [ ] Device tokens and private data cannot be read by unrelated users.
- [ ] Terms acceptance, user/content reports, blocking and moderation are usable and enforced.
- [ ] In-app and web account deletion work and match the published data-retention policy.
- [ ] Anonymous account recovery/linking strategy is tested and communicated.
- [ ] Abuse limits/App Check are enabled after an appropriate validation rollout.
- [ ] Overlay is optional, dismissible, permission-aware and has a working fallback.
- [ ] Function/push processing is safe against duplicate events and invalid tokens.
- [ ] Crash/performance/error/cost monitoring and alert ownership exist.
- [ ] CI, staging, test results, backups/restore and rollback process are documented.
- [ ] Target API and AAB are verified; Play forms/privacy policy/content rating/deletion link are complete.
- [ ] No watermarked or unlicensed assets remain.
- [ ] Required testing track and account-specific production-access requirements are complete.
- [ ] Internal/closed beta issues are resolved or accepted with explicit risk ownership.

## 14. Official references

- Google Play target API level: https://support.google.com/googleplay/android-developer/answer/11926878
- Google Play User Generated Content policy: https://support.google.com/googleplay/android-developer/answer/9876937
- Google Play account deletion: https://support.google.com/googleplay/android-developer/answer/13327111
- Google Play testing for new personal developer accounts: https://support.google.com/googleplay/android-developer/answer/14151465
- Firebase App Check: https://firebase.google.com/docs/app-check
- Firebase Security Rules unit tests: https://firebase.google.com/docs/rules/unit-tests
- Firebase insecure rules guidance: https://firebase.google.com/docs/firestore/security/insecure-rules
- Firestore best practices: https://firebase.google.com/docs/firestore/best-practices
- Firebase Crashlytics Android: https://firebase.google.com/docs/crashlytics/android/get-started
- Firebase Performance Monitoring Android: https://firebase.google.com/docs/perf-mon/get-started-android

---

**Reminder:** these files are a systematic engineering plan based on the README and architecture documentation. They are not a code audit, legal opinion, or guarantee of Play approval. Reconcile every item with the actual repository and live Play/Firebase configuration, and re-check official requirements at submission time.
