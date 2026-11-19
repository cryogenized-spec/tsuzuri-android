# TSUZURI — Interface for Android

Product roadmap · 2026-11-14 · planned features are NOT yet complete.

## Vision and principles

A fast, extensible, privacy-respecting Android input method combining accurate typing, accessible editing, rich clipboard, expressions, stickers and optional BYOK cloud intelligence. The free keyboard must remain fully functional with no account, no network, and no paid provider. A network or model failure never blocks a key press. No behavioural telemetry or silent clipboard uploads. A core principle: **speed, safety, predictability before novelty**.

## Status and release gates

- [x] Ownkey fork (based on FlorisBoard) created as `cryogenized-spec/tsuzuri-android`
- [x] Isolated development branch, unique Android application ID, preliminary branding
- [ ] CI available and green on current head (assemble, unit tests, static review, concurrency tests)
- [ ] Codex code/security review returned for current head, all P0/P1 addressed, no unresolved security blockers
- [ ] Real device typing + clipboard + password-field smoke tests
- [ ] Branch protection requires passing checks and independent review
- [ ] First merged development PR; no release publishing before these gates

## M0 · Secure, reproducible foundation (blocking)

- Verify app build on JDK 17/Android SDK 36/Rust/NDK; produce debug APK + install on device.
- Keep Android application identity distinct; preserve upstream copyright and third-party asset notices.
- Enforce Kotlin compilation, Android lint, app unit tests, prediction tests, secure transport and source-contract checks in CI.
- Add deterministic clipboard event generation checks and concurrent stress tests; audit every asynchronous database mutation and write a single serialized database mutation path.
- Treat sensitive copied data as ephemeral by default; never automatically save sensitive content in persistent history, thumbnails, logs, backups or suggestions.
- Fix unrestricted HTTP for secret-bearing network requests; avoid logging audio/transcripts/API keys.
- Profile startup/typing, callback cancellation, editor lifecycle changes, and battery use.

## M1 · Excellent everyday typing and correction

- Intelligent on-device typos/autocorrect with adjustable aggressiveness, dictionaries and undo.
- Misspellings underlined with a **wavy/zigzag indicator where the host editor supports spans**. Tap word -> context-like **vertically stacked replacement menu**, with original spelling, suggestions, Add to Dictionary, Ignore, Undo. No forced correction in secure/code fields.
- IME-only fallback suggestion panel for apps that ignore `SuggestionSpan`; cannot promise a drawn underline or a native popup inside every third-party app.
- Correctness in rapid typing (two-thumb bursts, deletes, cursor moves, composing text, autocorrect) and reentrant `InputConnection` events. Never alter characters after newer edits supersede a suggestion.
- Mixed-language language packs, grapheme-cluster-aware selection, punctuation, programmable keyboard gestures; accessibility.

## M2 · Emoji, symbols and typography

- On-device word-to-emoji bindings: `bear` → 🐻 in suggestion row; **explicit tap-to-insert**, never automatic substitution without opting in.
- Multiple emoji candidates, custom synonyms, per-language mappings, favourites and search; no network lookup on keystrokes.
- Text style conversion, including Unicode Mathematical Alphanumeric Symbols: Fraktur / blackletter (e.g. `Gothic` → `𝕲𝖔𝖙𝖍𝖎𝖈`), bold, italic, circled where available.
- These glyphs are **Unicode, not ASCII**, and must include a readable preview, undo, accessibility warning and graceful fallback for unsupported characters. Never distort URLs, passwords or code automatically.

## M3 · Persistent, expressive clipboard

- User-configurable total maximum **1–999 retained items**; default conservative, pinned entries count toward maximum. A separate optional 'saved snippets' collection can be larger.
- Store plain text, Markdown source, HTML/rich text, live hyperlinks with URI and display text, and media items (images) preserving declared MIME types.
- Search, sort, pin, favourites, filter by type, preview, edit text snippets, tags, manual expiry, duplicate detection and per-item deletion.
- Database transactional writes, bounded media file sizes, secure URI grants, deduplication and correct recency ordering under concurrent clipboard events.
- Never automatically retain sensitive clipboard items. Protect sensitive previews with Android clipboard sensitivity flags; purge/migrate existing sensitive legacy rows after a reversible migration plan. No cloud sync by default.
- Where target editor cannot accept rich content, offer safe downgrade (plain text/URL) and explicit Share action rather than claim a successful rich insertion.

## M4 · Sticker collection and maker

- Local sticker library: folders, packs, tags, preview, search, import, deduplication, deletion, offline use.
- Android IME rich insertion through `InputConnectionCompat.commitContent` / Content URI with MIME negotiation and scoped read grants. Receiving application support varies.
- Build sticker from imported photo/crop/mask, local canvas tools, or **optional provider BYOK image generation**. User can choose dimensions, aspect ratio and edge treatment.
- Default *visual style*: bold black contour line around an isolated subject, crisp readable shape, transparent canvas, no text, no watermark. See [STICKER-PROMPT.md](STICKER-PROMPT.md).
- **PNG or WebP for genuine transparency**; JPG export flattens alpha onto a chosen background. Keep originals and working masks.
- Model-agnostic provider interface: Google Gemini/Nano Banana, OpenAI or other API; no promise of a free production inference allowance. Offline/import/manual sticker creation remains free.

## M5 · Voice and AI (optional)

- BYOK cloud transcription, initially GPT Transcribe, with explicit start/stop/cancel, language and insertion preview.
- No automatic transcription of ambient audio; never process voice in password or incognito fields.
- Configurable per-provider endpoints, encrypted keys, usage controls and clear privacy disclosure. No hardcoded developer key, no background text submission.
- Selected-text rewrite, translation, summarisation; preview diff and explicit Insert. Keep cloud integration independent of keyboard rendering.

## M6 · Vault and extensions

- Build the dedicated Keystore-backed **Lockbox** for scoped secrets and variables, with authenticated encryption, no plaintext backup and explicit user consent before insertion.
- Versioned envelope, optional device authentication, key rotation/recovery policy and a safe migration from deprecated AndroidX EncryptedSharedPreferences.
- No Signal Double Ratchet locally: that protocol is designed for conversation-message key evolution. Use AES-256-GCM with Android Keystore, random nonce per write and authenticated metadata.
- Extensible actions/themes and maybe plugins **only after security boundaries, signature verification and sandbox feasibility are validated**.

## M7 · Free release

- Own app icons/visual identity, packaging and copyright notices; licence audit of bundled dictionaries and ML data.
- Play Store policy, privacy disclosure, Data Safety, target SDK, compatibility, accessibility and signed AAB.
- Internal testing, staged rollout with crash reporting explicitly opt-in, performance/energy baselines, support and vulnerability reporting.

## CI / approval rule

Do not merge based on a review emoji or automated comment alone. Require current-head CI success, Codex review completion (with serious findings resolved), verified security tests, a clean diff and any additional required human approval. No checks yet is **NOT** green.
