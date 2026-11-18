# Tsuzuri — consolidated phone alpha acceptance

**Candidate branch:** `integration/tsuzuri-phone-alpha-preflight` (draft PR #17). This integrates the existing clipboard alpha (#13), native Groq voice support (#6), symbols/layout settings (#14), AI model picker (#15), and keyboard/settings responsiveness (#16) for joint CI. It is **not** a certified release.

## CI before installing

Require all jobs in **Tsuzuri Android — strict quality gate** (source contract, secrets, Kotlin compile/lint/unit tests/APKs, Android emulator clipboard privacy, merge gate), **CodeQL Java/Kotlin**, and latest-head code review to pass. If a source PR updates, rebuild this candidate on its new HEAD and rerun.

Use the **`tsuzuri-alpha-arm64-phone-apk`** artifact from the candidate's successful workflow run. Extract the ARM64 APK. It is a development-debug build and not signed like a future store release. Keep FUTO installed; Tsuzuri is a separate keyboard selection and must be tested as an alternative, not an in-place overwrite.

## Short phone test (ordinary sample text only)

1. **Typing feel:** Open Notes, type three paragraphs rapidly with Shift, Backspace, autocorrect and punctuation; switch to another app and back. Repeat in portrait and landscape. Check dropped/duplicated letters and noticeable delay.
2. **Clipboard:** Copy several text snippets; pin/unpin/delete one; paste into Notes. Try image/video history thumbnails: previews should stay stable while scrolling and should correspond to the item selected. Original media must not be downsampled when pasted.
3. **Symbols:** In Settings → Long-press symbols, check that the picker matches the currently selected layout (QWERTY, AZERTY, Dvorak etc.). Apply **Functional**, **Funky** and **Developer** in turn (each should give three symbols to every A–Z key). Edit one symbol and confirm save persists after restarting settings. Existing assignments must only be replaced when **Apply** is explicitly confirmed.
4. **Editing actions:** Enable A/X/C/V shortcuts. Long-press A to Select All; X to Cut and C to Copy when text is selected; V to Paste. Each action should coexist with the assigned letter's three Unicode symbol choices and inherited accents. Password, URL and email fields must not unexpectedly expose the rich-text action popups.
5. **Settings readability:** On a narrow phone, read the full AI provider/model values below the setting labels. No overlapping text or clipped action controls; scroll normally.
6. **Rewrite:** Select a provider, then a model from the visible list. Verify older OpenAI IDs can be selected, and **Enter another model ID** opens and stays open. Rewrite only when manually invoked; use non-sensitive text.
7. **Voice:** Find **Groq Whisper Large V3 Turbo** under Speech vendor/model; verify selecting it shows the correct endpoint and `whisper-large-v3-turbo` model. Saving/clearing a BYOK key must remain scoped to its host. Explicitly start a dictation recording to test; ordinary typing should never start a voice request.
8. **Privacy and resilience:** Switch airplane mode on and verify ordinary typing and clipboard remain usable. Verify passwords are not captured into clipboard history. Switch back to FUTO using Android's keyboard picker.

## Capture any failure

Record Android version, model, which app/editor was open, exact steps, expected/actual result, and whether it reproduces on a cold start. For lag, record rough keyboard-open delay, whether it occurs with text-only clipboard, and whether it persists after switching off suggestion/rewrite features.

**Promotion gate:** all CI + CodeQL green, no unresolved serious review findings, and the actual phone acceptance above passed. Only then consider promoting development branch PR #1 into `main`.
