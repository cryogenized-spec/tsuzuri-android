# Tsuzuri development roadmap

## M0 · Independent bootstrapping

- [x] Public GitHub fork, linked to Ownkey
- [x] First development branch
- [x] Independent Android application ID and display name (requires compile/device verification)
- [x] Replace upstream auto-publication workflow with read-only debug build / test CI
- [ ] Green CI run on this branch
- [ ] Android physical-device installation, IME enable/select flow, first text input, clipboard and prediction smoke tests
- [ ] New Tsuzuri artwork, icon, screenshots and theme choices
- [ ] Audit remaining Ownkey names/URLs in code, asset metadata and tests

## M1 · Essential keyboard quality

Measure cold-open latency and key press latency; verify spacebar cursor control, multiline input, backspace/undo, languages, password/secure fields, third-party editor compatibility, dictionary licensing, battery and RAM. Add failure-injection coverage for key-repeat, clipboard, prediction and no-network behavior.

## M2 · Speech and AI

Default recommended cloud transcription: BYOK OpenAI `gpt-transcribe`, using a replaceable provider interface. Preserve existing dictation abstraction while adding microphone permission management, explicit recording state, cancel/stop switch, maximum audio duration, errors, user review before insertion, and no request from password/incognito fields. Harden remote endpoints to HTTPS; assess whether local HTTP loopback can be supported safely. AI rewrite remains opt-in and should preview changes.

## M3 · Productivity

Programmable action bar, searchable snippets/clipboard, full-screen editor/buffer, undo, selectable keybindings, per-app profiles with privacy safeguards, theme/customisation ergonomics. Avoid vertical settings sprawl.

## M4 · Distribution

Own visual assets and trademarks; final privacy policy and contact channel; third-party legal notices; security review; Play SDK requirements; signed AAB; emulator and physical-device tests; staged free release.

**Non-goals for M0:** hosted keys, forced account login, analytics or advertising, automatic clipboard uploads, plugin marketplace, full package namespace migration.
