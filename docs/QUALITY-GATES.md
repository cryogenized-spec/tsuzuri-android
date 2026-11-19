# Mandatory CI, Codex and merge quality gate

## Required jobs

1. **Source-contract**: validates fork identity, independent application ID, no release auto-publication, non-HTTP secret-bearing endpoint policy and presence of security/concurrency tests.
2. **Android quality**: JDK 17, Gradle wrapper verification, Android/Kotlin compilation, Android lint, prediction tests, all app unit tests, reproducible debug APK artifact.
3. **Concurrency regression**: run designated clipboard generation/retention tests repeatedly with deterministic stress fixtures; expand coverage for Room transactions and `InputConnection` lifecycle before release.

## Merge policy

PR is blocked until all required checks pass **at HEAD**. Codex review must complete on the same head commit; P0/P1 findings or unresolved security comments block merge. Explicit reviewer approval is required. A Codex thumbs-up does not overrule failing CI. Repo admin must configure GitHub branch protection/ruleset to actually *enforce* required checks; workflow definitions alone do not prevent manual merge. Do not enable auto-merge before branch protection and review are configured.

## Required test scenarios before first public APK

- Fast repeated typing; rapid space/backspace/cursor move; emoji replace; composition cancellation; text-selection update.
- Concurrent clipboard copy/clear/restore/pin and document MIME changes; stale event callback never wins.
- Sensitive clip is not stored in history/media files, even if max capacity 999 or auto-clean disabled.
- Cancellation and loss of microphone permissions; no transcription data in normal typing logs.
- Image insertion via `commitContent` with supported/unsupported receiving editors; revoke URI grants.
- Password/incognito fields: no autocorrect learning, no clipboard persistence, no network calls, no Lockbox autofill.

## Known limitations (2026-11-14)

The GitHub fork initially had no Actions runs. CI job availability, Android build results and branch protection remain to be verified. The Lockbox and rich sticker system are design work, not implemented features.
