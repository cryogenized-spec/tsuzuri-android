# Tsuzuri — automatic cleanup of recorded dictation (experimental)

This milestone follows the separate voice-provider configuration PR. The keyboard remains a keyboard; it does not send messages or create tasks automatically.

## Flow and boundaries

1. The user explicitly selects **Off**, **Natural** or **Concise** in AI Settings. Default is **Off**.
2. Recorded-file dictation uses the selected existing transcription provider (Groq Whisper, Mistral, OpenAI or supported custom endpoint). The local speech dictionary may remove known fillers as before.
3. If the user enabled cleanup, Tsuzuri sends the completed transcription text to the separately configured **text rewrite provider**, using that provider's own Android-encrypted API key. This is an independent request that may incur fees; choosing Groq's free voice transcription does **not** make paid cleanup free.
4. The model is prompted to preserve facts, names, numbers, negation, certainty/uncertainty, slang and the speaker's natural tone while removing filler words and unnecessary repetitions.
5. If the model fails or no rewrite key exists, the locally processed transcript is inserted unchanged and an on-screen notice explains why.
6. It inserts text into the active editor only if the original input session, lease and AI privacy policy remain valid. It **never** automatically presses Send.
7. Android local live compositional dictation is currently outside this path; record-stop transcription is the first supported route.

A future follow-up PR can handoff both raw and cleaned text to the temporary Pre-screen workspace and let the user review/diff before committing to the host app; preserving raw and cleaned in the editor is not silently claimed complete in this slice. True WebSocket speech streaming is a separate PR.

## Acceptance before integration

- Unit tests: default off; no provider call if off; natural/concise wording; provider failure; empty/oversize output.
- Android CI: Kotlin compile and tests.
- Codex review: inadvertent cloud calls, consent, prompt injection, secure/incognito editor transitions and cancellation, networking on IO, preserving original on failure.
- Device: Groq end-to-end recorded dictation + optional rewrite, host switch during cleanup, key-not-configured fallback, no automatic sending.
- Security: no transcript or audio persisted by cleanup layer, no text-based provider keys exported to the clipboard, and no developer-owned relay.


## Dependency reconciliation

The current branch includes PR #6's shared transcription/provider credential snapshot guard and durable key deletion; do not regress them when adding cleanup. This stacked change remains a draft pending compile/review/device verification.
