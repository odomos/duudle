# Duudle — Claude Code Engineering Rules

These rules govern how Claude Code should inspect, modify, test, and document the Duudle repository. Follow them for every task unless the user explicitly approves a specific exception.

## 1. Source of truth and evidence

- Inspect the repository before making assumptions about its structure or implementation.
- Treat `README.md`, `ARCHITECTURE.md`, `ARCHITECTURE_FLOW.md`, and `roadmap.md` as project guidance. Verify important claims against actual source code, configuration, tests, and deployment state where possible.
- Clearly distinguish **implemented**, **partially implemented**, **planned**, **confirmed issue**, **suspected issue**, and **not verified**.
- Do not claim a feature, test, security fix, deployment, or policy requirement is complete without appropriate evidence.
- For current Google Play, Android, Firebase, or third-party platform requirements, verify current official documentation when the requirement could have changed. Label policy requirements separately from recommendations and product improvements.

## 2. Work in small, reviewable batches

- Start each substantial task by stating the intended outcome, inspecting relevant files, and identifying likely risks.
- For audits or planning requests, do not edit application code unless the user has asked for implementation.
- Implement one coherent, independently testable batch at a time. Avoid broad rewrites and unrelated cleanup in the same batch.
- Preserve existing working features, user data, established conventions, and public interfaces unless a deliberate fix requires a change.
- Prefer incremental improvements to the current architecture. Do not migrate away from Firebase, add Kubernetes, or introduce a custom backend without measured evidence and an explicit plan.
- Before a large or risky change, explain the impact and obtain user approval where specified in these rules.

## 3. Git and change safety

- Check `git status` and the current branch before significant changes. Never overwrite, discard, reset, or reformat the user's unrelated uncommitted work.
- Do not run destructive Git commands such as `reset --hard`, destructive clean operations, or force-pushes unless the user explicitly authorizes the exact action after its consequences are explained.
- Do not commit, push, create a release, or open/merge a pull request unless requested.
- Keep changes focused. Report the files changed and summarize important diffs.

## 4. Secrets, privacy, and user data

- Never print, copy into reports, commit, or expose secrets from `.env` files, service-account credentials, signing keys, access tokens, private FCM tokens, or other credentials.
- Do not place secrets in source code, tests, logs, analytics, screenshots, or generated documentation. Use existing environment/configuration mechanisms and document required variable names without revealing values.
- Verify secret files are appropriately excluded from version control; do not modify `.gitignore` blindly without checking the current project setup.
- Do not log message text, doodle contents/strokes, authentication credentials, raw FCM tokens, or other sensitive personal data in analytics or operational logs.
- Use pseudonymous identifiers where appropriate and minimize collection, retention, and access to user data.

## 5. Firebase and backend safety

- Treat all Android client code and client-supplied fields as untrusted. Enforce authorization and data validation in Firestore Security Rules or trusted server-side code, not only in the UI.
- Test both allowed and denied Firestore operations using the Firebase Emulator Suite when practical. Include ownership, friendship/relationship, field validation, and state-transition cases relevant to the real schema.
- Never weaken rules simply to make a failing client operation work. Diagnose the intended authorization model first.
- App Check complements authentication and authorization; it does not replace Security Rules or server-side checks.
- Do not access, mutate, migrate, or delete production data as part of local tests. Use emulators or a verified non-production environment.
- Do not deploy Firestore rules, Cloud Functions, indexes, or other Firebase resources to production without explicit user approval for that deployment.
- Do not run destructive migrations, bulk deletes, or operations affecting live users without explaining scope, backup/rollback options, and obtaining explicit approval.
- Consider abuse controls, validation, idempotency, safe retries, cost, and monitoring for callable functions and event-driven functions.

## 6. Authentication, social features, and account lifecycle

- Verify the actual authentication and account model before changing it, particularly if anonymous Firebase accounts are used.
- Avoid breaking existing anonymous users or orphaning user-owned data when introducing account linking, recovery, or deletion.
- For account deletion, account for related records and user-generated content according to the documented retention policy and applicable requirements. Use safe, auditable server-side deletion workflows where appropriate.
- Social features that involve user-generated content should be evaluated for appropriate terms/policy acceptance, reporting, blocking, moderation workflows, and abuse response. Do not assume mute and block have the same behavior.
- Do not present a feature as compliant until its implementation and relevant store disclosures have been checked.

## 7. Notifications and overlay behavior

- Distinguish a successful FCM send request from device receipt, notification display, overlay display, and user interaction. Do not report delivery or viewing without evidence.
- Make notification processing idempotent where appropriate; handle retries without creating duplicate messages, records, or user-visible events.
- Handle stale/invalid FCM tokens and multiple devices per account safely according to the existing data model.
- Treat overlay permission as explicit user consent. Keep overlays dismissible and controllable, provide a useful fallback when permission is denied, and avoid obstructing critical system controls or misleading users.
- Test lifecycle changes, background restrictions, permission denial, and supported Android versions where feasible.

## 8. Tests and verification

- Add or update tests with behavior changes, especially for authorization, data validation, account lifecycle, notification idempotency, and critical business logic.
- Run the relevant tests, lint, type checks, and builds that are available in the repository. Inspect the result rather than assuming success.
- Report exact commands run and whether each passed, failed, or could not be run. Explain external credentials, devices, emulators, or infrastructure needed for any unverified check.
- Do not remove, skip, or weaken tests merely to obtain a green result. If a test is invalid, explain why and replace it with a meaningful check.
- When behavior changes, include a manual verification procedure if automated verification is insufficient.

## 9. Play Store readiness

- Verify the target SDK from the actual Gradle configuration and release artifact; do not infer it from documentation alone.
- Evaluate applicable Google Play requirements for user-generated content, account creation/deletion, privacy policy, Data safety disclosures, permissions, target API, content rating, release signing, and store listing.
- Use current official policy sources for date-sensitive requirements. Account for the developer account type and enrollment date where testing requirements differ.
- Replace watermarked or unlicensed assets only with original assets or assets the project has permission to distribute. Do not try to conceal/remove a third-party watermark as a substitute for permission.
- Never submit, publish, or roll out a production release without explicit user approval.

## 10. Observability, cost, and scale

- Separate product analytics (such as onboarding completion and feature use) from operational health (crashes, latency, errors, function failures) and notification lifecycle measurements.
- Add observability only when its data collection, privacy, cost, and usefulness are understood. Avoid excessive per-event Firestore writes or high-cardinality, sensitive log fields.
- Monitor crashes/ANRs, API and function errors/latency, Firestore usage and billing, storage, notification outcomes, moderation reports, and relevant cost-per-active-user indicators as appropriate.
- Optimize Firestore queries, indexes, listener lifetimes, fanout, and read/write patterns based on measured behavior. Do not promise scale or capacity without evidence.
- Plan backup and restore procedures and verify restoration in a safe environment; having a backup configured is not proof that recovery works.

## 11. Documentation and roadmap status

- Keep `ARCHITECTURE_FLOW.md` aligned with the actual architecture when implementation changes materially.
- Update `roadmap.md` only when the item's real status changes, and include verification evidence for completed work.
- Use clear statuses, for example: `Not started`, `In progress`, `Implemented — tests pending`, `Tests passed`, `Verified in staging`, and `Released to production`.
- Do not mark an item complete based only on code being written. Distinguish code implementation, automated testing, manual/staging verification, and production release.
- Document required manual Firebase Console, Google Cloud, Play Console, signing, secrets, and deployment steps without including secret values.

## 12. Communication and stopping points

- Use beginner-friendly explanations and concrete file paths/commands when the user needs to take action.
- For a change batch, report: objective, files changed, important design choices, tests run and results, known limitations, manual steps, and the recommended next task.
- Ask for approval before production deployments, destructive data operations, irreversible migrations, release publication, significant architecture migrations, or material changes to privacy/security behavior that affect existing users.
- For routine, reversible local code changes inside the agreed task, proceed without asking for approval after every file edit.
- If blocked, state the precise blocker and the safest next step. Do not silently substitute a mock, skip validation, or claim success.

## 13. Recommended priorities

Use the repository audit and `roadmap.md` to determine priority; do not assume every listed concern is confirmed:

1. **P0:** verified critical access-control/data-exposure issues and applicable launch blockers.
2. **P1:** account safety, abuse prevention, reliability, automated security tests, monitoring, and safe environments/deployments.
3. **P2:** maintainability, analytics, cost/performance improvements, and incremental architectural refactoring.
4. **P3:** nonessential enhancements and longer-term optimization.

Always verify the actual code and environment before declaring a priority item complete.
