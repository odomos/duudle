# Monitoring runbook

What watches Duudle, where alerts go and what to do when one arrives (Phase 7). Owner and
responder: Arnav. Alerts email **doodlebyarnav425@gmail.com**.

## What's watched

| Signal | Where | Alert |
|---|---|---|
| App crashes, freezes (ANRs) | Firebase console > Crashlytics | Crashlytics' own emails for new issues and regressions (console > Crashlytics > settings) |
| Doodle pop-up couldn't be shown | Crashlytics > non-fatals (`reportNonFatal` in `DuudleMessagingService`) | none; check weekly |
| Cloud Function errors | Cloud Logging, `severity>=ERROR` | **Duudle: Cloud Function error**, at most one email per 30 min |
| New report | `onReportCreated` logs `event=report_created` (reason and type only) | **Duudle: new report**, at most one email per 5 min |
| Requests refused by the rules | `firestore.googleapis.com/rules/evaluation_count`, `result=DENY` | **Duudle: burst of refused requests**, over 100 in 10 min |
| Write volume | `firestore.googleapis.com/document/write_count` | **Duudle: burst of writes**, over 2000 in 10 min |
| Spend | Billing > Budgets & alerts | **Duudle monthly budget** ₹900 (about $10; the billing account is in INR) on the live project, emails at 50%, 90%, 100% of actual spend to the billing admins and the alert address. Firebase's own budgets (₹25 live, ₹100 staging) still email the billing admins. |

Alert policies live in Cloud Monitoring (console > Monitoring > Alerting) on each project;
staging has the same set as live once rolled out (staging only until then). Firestore reports each metric twice (`module` `""` and
`__unknown__`), so the two burst alerts group by `module` and one spike may send two emails.

## When an alert arrives

**Crash spike (Crashlytics email).** Open the issue, check the app version and device. If
it started with a new build, tell testers to stay on the previous APK and fix forward.

**Cloud Function error.** Logs Explorer on the project in the email, filter
`severity>=ERROR`. Common causes:
- dead push tokens: logged at info, not an error, so an error here is something else;
- a deploy that broke the function: roll back by redeploying the previous commit's
  `functions/` (`firebase deploy --only functions --project <id>` from that checkout). Never
  roll back past Phase 2 (old functions read only the public token, which the app deletes).

**New report.** Follow `MODERATION.md`. The email doesn't say who reported whom on purpose;
open the `reports` collection in the console.

**Burst of refused requests.** Usually one of:
- someone hitting their rate limit (Phase 6) or a script: Firestore > `users/{uid}/limits`
  shows who is at the limit; disable a clearly abusive account in Authentication;
- an app/rules mismatch right after a rules deploy (old app builds can't satisfy new rules):
  check whether it began at the deploy time; if real users are affected, redeploy the
  previous `firestore.rules`.

**Burst of writes.** Firestore usage tab, then find the collection growing fastest. Rate
limits cap doodles, messages, requests and reports per account, so a burst means many
accounts or an uncapped path (profile, favorites, chat metadata). Disable the accounts,
then add the missing limit.

**Budget email.** Billing > Reports, group by service and SKU. Firestore reads from a
listener loop or function retries are the usual suspects. A budget only warns; it never
stops the app.

## Changing alerts

`scripts/monitoring-setup.js` creates or updates the channel and policies (Cloud Monitoring
API, matched by display name; usage at the top of the file). Change thresholds there, run it
on staging, then on live with the owner's OK. The budget is set separately (Billing). It sends to the
alert address through the email channel on `duudle-staging`, so don't delete that channel.
