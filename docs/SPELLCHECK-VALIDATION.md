# Tsuzuri — spelling underlines and manual correction acceptance

Status: **substantial native functionality inherited, but not yet confirmed on a Tsuzuri device build**.
This checklist belongs to the everyday typing milestone. This document is an audit and test plan, **not a claim that a custom cursor popup is already implemented**.

## Implemented and verified in source

- `app/src/main/AndroidManifest.xml`: public Android `FlorisSpellCheckerService` registered with `android.permission.BIND_TEXT_SERVICE`.
- `FlorisSpellCheckerService.kt`: spells through `NlpManager.spell` and returns Android `SuggestionsInfo` for the system's spell checker sessions.
- `LatinLanguageProvider.spell`: checks dictionaries and creates ranked correction candidates; `SpellingResult.typo()` marks typos with `RESULT_ATTR_LOOKS_LIKE_TYPO`. Unknown tokens without good suggestions may intentionally remain unmarked.
- `TypingScreen.kt`: Autocorrect choices Off / Gentle / Normal / Strong; Show suggestions control; Spell checker status/selection row.
- `EditorInstance.kt`: an *already autocorrected* word may be marked by Android's `SuggestionSpan.FLAG_AUTO_CORRECTION` with the original typing as a replacement option.
- `KeyboardManager.kt`: existing candidate selection, automatic commit, and undo/revert.

## Desired interaction

**Manual correction mode** (autocorrect Off, suggestions On, spell checker enabled):
Type `definately`, press Space, keep the original spelling. In compatible
host editors Android should underline it in red and provide correction
suggestions on tap. The exact red zigzag design is determined by the OS/host
and cannot be forced by Tsuzuri's IME in all applications.

**Automatic correction mode** (Gentle / Normal / Strong): word-completion
autocorrection, tap-to-undo where the host supports correction spans, and the
keyboard's own undo/revert behaviour.

**Fallback**: when host editors ignore native typo spans or don't provide
an accessible popup, implement a keyboard-owned correction panel with
3–5 ranked choices, Original spelling, Add to Dictionary, Ignore Once and
Undo where applicable. Place it above the keyboard or near the selection,
not as a destructive overlay on the host application.

The Compose-based Pre-screen draft field introduced in PR #4 may not
automatically display Android spellcheck underlines. It requires its own
visual spellcheck/picker implementation or a carefully scoped Android
`EditText` bridge; investigate this separately.

## Device acceptance matrix

| Test | Expected |
| --- | --- |
| Select Tsuzuri as Android system spell checker | System shows it as available/enabled; user grants explicitly |
| Native `EditText`, typo + Space, autocorrect Off | No automatic replacement; visible native typo marker if host supports |
| Tap marked word | Native replacement choices appear where editor supports them |
| Autocorrect Gentle/Normal | Eligible high-confidence typo corrected at word boundary |
| Undo/Backspace after autocorrect | Restore typed form; avoid immediate re-correction |
| Unknown name/technical term | No aggressive unwanted correction |
| Code/password/URL/incognito | No spellcheck network or unsafe automatic editor edits |
| Chrome/WebView, WhatsApp, Compose TextField | Record host-specific results, do not claim universal underline |
| Emoji, combining marks, apostrophes | No broken grapheme substitutions |
| Rapid typing, cursor jumps, field switching | Never replace stale selection or source |

## Next engineering increment

1. Complete this matrix with the existing Android spell checker before
   creating a second correction engine.
2. Add targeted Android instrumentation for spell checker service and a
   standard editable field; verify `RESULT_ATTR_LOOKS_LIKE_TYPO`, candidate
   ordering and disablement modes. Tests should not require cloud AI.
3. If host behaviour proves inconsistent, implement an explicit keyboard
   correction picker that looks up the word under the current selection
   through a bounded `InputConnection` read; validate live field identity,
   cursor, selected range and original word *again* before replacing.
4. Preserve user dictionary support and manual ignore/undo. Keep normal
   keyboard input responsive and offline.
5. Validate via Codex and device test before merging.

No changes to Android system text editors are possible from an IME; the
fallback stays inside Tsuzuri's keyboard surface.
