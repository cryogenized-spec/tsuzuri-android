# Tsuzuri quick-start / Unicode customisation

Development UX acceptance plan · 2026-11-15 · draft PR #3

## Design rules

- First-run evaluators can try an appearance, a familiar keyboard layout, and a typing geometry directly on Home.
- Nothing requires JSON editing, ADB, a terminal, theme downloads, scripts or a cloud service.
- Keep the full catalogue available for enthusiasts while exposing only common presets initially.
- Never silently change every language's layout; only the active language subtype is modified.
- User-defined Unicode is local preference data, not a password or a cloud integration.
- Do not turn off existing accent menus when adding custom shortcuts.

## Bundled assets verified in Ownkey parent

- **72 theme variants**: 6 Floris Classic + 63 Material/Glass + 3 Ownkey Signal.
- **77 character layout descriptors** in `org.florisboard.layouts` (including QWERTY, QWERTZ, AZERTY, BÉPO, Dvorak and Colemak).
- `SplitLayoutMode.NEVER/AUTO/ALWAYS` exists, with supported character/symbol/advanced numeric split arrangements. Automatic split threshold: 600 dp.
- `ImeWindowController.Actions` already implements compact left/right keyboard positioning.
- `SubtypeLayoutMap` already defines NUMERIC, NUMERIC_ADVANCED, PHONE, PHONE2, SYMBOLS and SYMBOLS2 keyboard modes.
- Existing keyboard long-press delay supports 100–700 ms.

## Test sequence on Android device / emulator

1. Install, enable and select the keyboard using Android's standard IME controls. If not selected, Home should present an enable/select instruction.
2. Home: pick Graphite → Black → Warm Light → AMOLED Glass → Follow phone. Verify each switches the actual keyboard theme, not only an image.
3. Home: try QWERTY → QWERTZ → AZERTY → Dvorak → Colemak → BÉPO; type in a real editor. Verify the current language subtype updates and unrelated language subtypes do not.
4. Home: choose Full-width → Split for two thumbs → Split automatically. Verify the split inserts two space keys where appropriate and preserves all keys; avoid unsupported modes silently.
5. Home: choose Left thumb → Right thumb → Full keyboard. Verify compact width, left/right screen alignment, touch targets, and state after IME restart and phone rotation. Split and one-handed modes must not accidentally combine.
6. Expand **Test number and phone keypads**. Switch Text → Numbers → Phone. Focus the test input field for each; Android `KeyboardOptions` should send the appropriate input type and Tsuzuri should display a numeric or dialpad layout. Do not assume the OS renders this keyboard itself.
7. Home → Custom long-press symbols: choose **E**, select `€` and `∑` (or paste `U+1F43B`). Verify the menu retains built-in é/è/ê accents plus the new choices, and tapping a choice inserts the expected Unicode text.
8. Confirm that the custom shortcut is not offered in password, URL or email fields. Change the long-press timeout in Keyboard settings and verify it affects the menu delay.
9. Add four alternatives to one key; the fifth should be rejected visibly. Restart the app/IME; the four remain. Remove one and verify it disappears after the editor refreshes.
10. Test on narrow phone and tablet; keyboard toggles, sheet pickers and symbol grids should fit without clipping or illegible touch targets.

## Known constraints

- Android IMEs don't themselves force a host to accept rich content; editors choose their input type.
- Unicode symbols are not ASCII glyphs. Multi-codepoint emoji may render differently across fonts/apps.
- The first symbol editor supports **A–Z** and a maximum of four custom long-press alternatives per letter; it does not yet implement drag-and-drop key geometry or arbitrary layout reshaping.
- This work intentionally lives in its own draft UX PR, separate from the clipboard/crypto/security foundation. Do **not** merge into main before PR #1, a rebased diff, and passing CI/emulator tests.
