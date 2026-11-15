# Tsuzuri browser-installable keyboard preview

**This is an interactive DESIGN SIMULATOR, not the actual Android IME.** It is not compiled from Compose and does not receive privileged Android input events. Native IME, on-device model, private media, backup/restore, app-specific text insertion and input permissions require a physical Android device and the native build.

## Purpose
- Inspect keyboard ergonomics on a 9:16 phone, compact phone, or landscape viewport.
- Test the preview's tappable letters, Delete, Shift, number row, symbols, mock messaging input and Unicode conversion experiments.
- Explore the sticker tab using **emoji placeholders**, not Android image stickers.
- Explore the conceptual voice provider configuration. Real microphone capture, Groq and OpenAI transcriptions are NOT wired into this web simulator.
- Try temporary Pre-screen local text formatting and mock approval into the mock chat.
- Change palette, layout, one-handed sizing, key radius and accent colour.
- Install a standalone PWA when supported by the browser. Static files cache for offline use.

## Run locally
From this directory: `python3 -m http.server 8765`; open `http://localhost:8765`.

## Trust boundary
No API keys/passwords are accepted or persisted. No outgoing network calls other than fetching an optional Google Fonts stylesheet and explicit user clicks on provider signup links. The simulator doesn't persist typed text or record speech; the keyboard itself operates without server access. The preview does not use the real system clipboard or request microphone permissions. A browser may offer to install the PWA. External provider signup pages only open on an explicit user click; all site assets use system fonts without external font requests.

## Known gaps / native parity
Many controls are representative rather than exact native Compose renderings. The 20 Unicode transformations in this *web prototype* are not present in the native Android PRs yet. Emoji shown in the sticker preview are NOT actual PNG stickers. Preview voice cleanup uses deterministic local JavaScript heuristics, not AI. No actual data is sent from the mock conversation. Device touch interaction and IME lifecycle behaviour must be tested separately.

## Release hygiene
This prototype is in an independent `web-preview/` folder. It must not weaken the strict Android CI quality gate, bypass unresolved clipboard/media security findings or turn unfinished voice PRs into a beta release. The UI/feature notes are informational rather than a replacement for Android acceptance tests.
