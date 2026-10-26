# Duudle — Claude Code Project Instructions

These are the automatically loaded entry instructions for Claude Code in this repository.

@CLAUDE_RULES.md

## Project reference documents

- `README.md` — project overview, setup, and documented features.
- `ARCHITECTURE.md` — existing architecture documentation.
- `ARCHITECTURE_FLOW.md` — target architecture, flows, security, observability, and scaling guidance.
- `roadmap.md` — prioritized engineering and Play Store readiness checklist.

Read the relevant project documents before substantial work. For a repository audit, security/backend change, architecture change, or Play Store-readiness task, read all four reference documents and then verify their claims against the actual implementation. Do not load plans as proof that the corresponding code already exists.

## Default working mode

- For a new substantial task, inspect the repository and `git status` first.
- If the user asks for an audit or plan, stay read-only until implementation is requested.
- Otherwise, work in small, testable batches; preserve existing work; run relevant tests; report the exact verification performed.
- Never deploy to production, alter production data, perform destructive migrations, or publish a release without explicit user approval.
- Keep `roadmap.md` and `ARCHITECTURE_FLOW.md` accurate as verified implementation changes.
