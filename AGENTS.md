# Agent Instructions

This repository uses the Delano runtime installed under `.agents/` and project contracts under `.project/`. Treat `.agents/README.md`, `HANDBOOK.md`, and `.project/context/` as the shared operating context before starting work.

## Working Flow

- Check the relevant project or task contract in `.project/projects/` when one exists.
- Keep implementation changes scoped to the requested work and preserve unrelated local changes.
- Record project updates through the Delano commands or templates when changing delivery state.
- Run `delano validate` before closeout when the runtime or project contracts are touched, and report any remaining validation failures.

## Application Context

- Ownkey is a privacy-first, open-source Android keyboard derived from FlorisBoard and licensed Apache-2.0.
- The product centers on practical BYOK AI input: configurable Mistral Voxtral ASR dictation, selected-text LLM rewrite, visible provider control, and Android Keystore-backed local API key storage.
- Keep normal typing, suggestions, autocorrect, and keyboard-open behavior responsive; AI network work must not block everyday input.
- Use `AI` as the user-facing umbrella for dictation and rewrite. Keep `Voxtral`, OpenAI, Anthropic, Mistral, OpenRouter, and custom endpoints as provider/configuration details.
- Do not claim Ownkey provides hosted cloud AI, local-only cloud processing, or private provider handling. When cloud AI is used, request data goes to the configured endpoint.

## App Map

- Main Android IME/settings app: `app/`
- Shared Android, Compose, Kotlin, native, and Snygg UI/theme libraries: `lib/`
- Brand assets and Play Store metadata: `assets/branding/`, `docs/brandbook/`, `fastlane/metadata/android/`
- AI settings screen: `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/voxtral/VoxtralScreen.kt`
- Rewrite implementation: `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/rewrite/`
- Dictation implementation: `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/dictation/`
- Brand tokens and English copy: `app/src/main/kotlin/dev/patrickgold/florisboard/app/OwnkeyBrand.kt`, `app/src/main/res/values/strings.xml`

## Runtime Map

- Shared scripts: `.agents/scripts/`
- PM commands: `.agents/scripts/pm/`
- Skills: `.agents/skills/`
- Hooks: `.agents/hooks/`
- Project context: `.project/context/`
- Project registry: `.project/registry/`

Do not publish secrets, raw prompt text, or machine-specific absolute paths in repo docs, contracts, logs, or generated artifacts.

## Code Review Rules (Tsuzuri)

### Input and concurrency boundary
The IME must never drop, duplicate or reorder committed text when users type rapidly, backspace, switch apps, or cancel composition. Flag any asynchronous callback that can write state after a newer event, or any unbounded work on the input/UI thread. New concurrent state transitions need deterministic tests.

### Clipboard and privacy boundary
Sensitive clips, password/incognito fields, vault secrets and private snippets must not be persisted to normal clipboard history, searchable indexes, backup, crash logs, telemetry or remote endpoints without a deliberate scoped action. Flag any clipboard capture/restore/pin race or over-broad content URI grant. The default is fail-closed.

### BYOK network boundary
No network call may start from routine typing. Only user-triggered dictation/rewrite/image generation may send data; secret-bearing endpoints require HTTPS, with visible destination and no hidden fallback provider. Reject debug keys or embedded credentials and audit error logs for leaked content.

### Approval gate
Do not approve a merge with missing or failed CI, unreviewed changed Kotlin, unresolved P0/P1 issues or incomplete sensitive-data threat analysis. Codex review is an independent input, not a substitute for deterministic tests and explicit human approval.
