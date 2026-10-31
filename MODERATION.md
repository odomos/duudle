# Moderation runbook

How reports made in the app are reviewed and acted on. Google Play requires apps with
user content and private messaging to act on reports, not just collect them.

## Where reports go

Every report is a document in the Firestore `reports` collection (production project
`duudle-296a9`). Clients can only create reports; nobody can read them in the app,
including the reporter. Only the developer sees them, in the Firebase console.

| Field | Meaning |
|---|---|
| `reporterId` | uid of the person reporting (never shown to the reported person) |
| `reportedUid` | uid of the person reported |
| `reason` | `harassment`, `hate`, `sexual`, `violence`, `spam`, `impersonation`, `underage`, `other` |
| `contentType` | `user` (the person), `doodle` (a doodle they sent the reporter) or `chat` (the chat between the two) |
| `contentId` | the doodle id (`doodles/{id}`) or chat id (`chats/{id}`); absent for `user` |
| `details` | optional text from the reporter (max 1000 characters) |
| `createdAt` | server time |
| `status` | `open` when created; the developer changes it (below) |

The rules check that a doodle report names a doodle the reported person really sent the
reporter, and a chat report the chat between the two, so content can't be pinned on
someone else.

## Routine

1. **At least every 2 days** (daily once there are outside testers): Firebase console >
   Firestore > `reports`, filter `status == open`, oldest first.
2. For each report, look at the evidence:
   - `doodle`: open `doodles/{contentId}` (strokes are drawn in the app; `cardId` is a
     flash card).
   - `chat`: open `chats/{contentId}/messages`, sorted by `createdAt`.
   - `user`: their profile `users/{reportedUid}`, plus other reports about them
     (filter `reportedUid ==`).
3. Decide and act:
   - **No violation:** set `status` to `dismissed`.
   - **Violation:** delete the offending doodle or message document in the console, then
     set `status` to `actioned`. For repeated or serious violations, disable the account:
     Authentication > Users > find `reportedUid` > Disable account. (Anonymous accounts
     can't be recovered by the user, so disabling is a permanent ban in practice.)
   - Optionally add a `note` field saying what was done (the console can add fields the
     app can't).
4. Never share who reported whom.

## Urgent cases (same day)

- **Child sexual abuse material or child endangerment:** don't download or forward it.
  Disable the account, keep the documents as evidence (don't delete them yet), and report
  it to the authorities (in the US, the NCMEC CyberTipline; elsewhere the national
  hotline listed by INHOPE). Then delete the content once you're told it's no longer
  needed as evidence.
- **Credible threats of violence or self-harm:** act on the account and contact local
  authorities if someone may be in danger.
- `underage` reports: if the account looks under 13, disable it.

## Known limits (to improve later)

- No alert yet when a report arrives; check the console on the routine above. Phase 7
  (monitoring) can add an alert.
- No rate limit on reports yet (Phase 6).
- Account deletion (Phase 5) must keep reported content until the report is closed.
