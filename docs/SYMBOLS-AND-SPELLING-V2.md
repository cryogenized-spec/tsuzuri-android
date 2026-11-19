# Tsuzuri — per-key Unicode and offline spelling milestone

Status: **experimental development on PR #4**, not released or merged. PR #4
is stacked on the existing keyboard UX branch (PR #3) and needs the
security foundation (PR #1). Device behaviour remains unverified until CI
and Android testing pass.

## Assign up to six symbols per Latin letter

Home → Custom long-press symbols → choose A–Z. The original Ownkey popup
alternatives remain the default; custom symbols are added as extras. Slots
1–6 are visible in a 3-column grid, with left/right controls to reorder
them. Tap an assignment to remove it.

Global popup controls:
- **Show inherited accents:** on by default. Turning this off hides
  inherited Latin accents, but not manually assigned accented characters.
- **First to reach:** Original Ownkey order (default), Accents first, or
  My symbols first. The inherited popup/hint system can also affect
  physical placement; confirm accessibility and finger reach on devices.
- Defaults and custom changes can be combined; six limits only *custom*
  assignments. Inherited accents and editing shortcuts are not charged
  against the six custom slots.

The curated offline categories remain. A pasted character or sequence of
up to eight Unicode code points can be assigned directly. Enter a sequence
of U+ identifiers separated by spaces or commas (e.g. `U+2764 U+FE0F`)
to create a composite character. Invalid scalar values, control codes,
bidi control characters and certain invisible characters are rejected.
Complex emoji using zero-width joiners remain supported. For a broader
character reference, the existing Unicode Consortium Charts link opens
https://www.unicode.org/charts/ in the phone browser. No bundled ASCII
art or oversized character library is required.

No customised alternatives are offered in password, URL or email fields.

## AI symbol assistant

A new optional section accepts plain-language instructions. For example,
"Set Q to ∑ and §, keep the existing symbols on A, put ☠️ on X and
recommend five astronomical signs."

The user must have an existing BYOK AI rewrite provider configured. Calls
are explicit and use HTTPS; they may incur provider charges. Existing
per-letter custom mappings are included in the request so the model can
preserve unchanged assignments. The response is a versioned JSON data
object and is parsed strictly by `SymbolAssignmentContract`:

```yaml
schema_version: 1
summary: "Assign mathematical symbols on Q"
changes:
  Q: ["∑", "§"]
  X: ["☠️"]
recommendations:
  - symbol: "♜"
    label: "Chess rook"
```

The YAML above is readable documentation. **Real model responses are JSON**
with exactly these fields. Only capital A–Z letter keys are supported in
this first iteration; the model cannot invent buttons, execute commands,
or directly edit preferences. Every proposed mapping is displayed against
the current assignment. Recommendations can be tapped to stage one symbol
for the currently selected key. **Approve** atomically commits the staged
per-letter mappings if the expected original snapshot has not changed.
**Reject** discards them. Failed or stale proposals do nothing.

## Manual spellcheck in Pre-screen

The existing Android spell checker already identifies typos and offers
candidate replacements to compatible host text editors. The new Pre-screen
workspace additionally performs a **local, debounced** scan of up to 48
completed words from the current draft using the inherited `NlpManager`.
It does not invoke AI. The text field highlights identified typos in
**red zigzag underlines** using a custom Compose glyph-layout drawing
component, including multiline wrapping. Its exact appearance, scrolling
alignment and accessibility still require phone/emulator verification.

Place the cursor in a highlighted word to expose up to five corrections.
Selecting a replacement changes only the verified original span, and
rejects stale text/selection. **Ignore once** suppresses that mark in the
current draft; **Add to dictionary** explicitly saves the word to the
local user dictionary where enabled. Spellcheck can be toggled off in
the workspace. Drafts are not uploaded during spellcheck.

The native Android spell checker still requires explicit system activation
and the receiving host editor decides whether to draw the underline and
show a native selection popup. A keyboard-owned correction picker for all
host fields is **not yet implemented** and requires an editor-identity
and source snapshot guard, focus tests and IME overlay testing.

## Tests and release gates

- [x] Regression tests added for custom limits, Unicode input, accent
      filtering, original ordering and custom priority.
- [x] Regression tests added for strict AI symbol proposals and malformed
      responses, including explicit no-mutation tests.
- [x] Word tokenisation tests added for completed words, Unicode offsets,
      URLs/code text, bounded scans and stale cursor checks.
- [ ] Current-head Android compilation, JVM and connected tests green.
- [ ] Codex independent code/security review, all P0/P1 findings addressed.
- [ ] Real Android keyboard popup layout: six slots and inherited accents;
      validate tap targets on phones and landscape.
- [ ] BYOK provider tests: malformed responses, errors, stale proposals,
      offline mode and no unintended uploads.
- [x] Pre-screen red zigzag drawing component added; Android device validation pending.
- [ ] Keyboard-owned manual correction menu for unsupported host editors.
- [ ] Merge dependency branches in order; no premature main release.
