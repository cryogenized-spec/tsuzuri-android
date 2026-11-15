# Tsuzuri Pre-screen Workspace — experimental milestone

Status: local authoring + proposal preview feature branch. **Not a release.**
This branch stacks on PR #3, which itself depends on the security foundation in PR #1.
Do not merge into main until strict CI, Android instrumentation, and privacy review pass.

## Design

Normal Android IME typing remains the default. The pre-screen is an optional,
native Compose draft workspace accessible through Home in this first slice.
It does not use an embedded browser or execute untrusted HTML or JavaScript.
A source draft is retained in Compose memory for the current screen only.

The user can manually paste a draft, format the active selection or the word
under the cursor, invoke an already-configured BYOK rewrite provider, review
the proposed changes, and explicitly approve or reject. Approval updates
**the local draft only**. Copying approved text to Android's system clipboard
is a separate explicit action. No implicit third-party editor mutations.

A full keyboard toolbar launch + secure transfer to the focused host editor
is a later integration gate, not silently claimed as part of this milestone.

## User-facing workflow

1. Open Home → Pre-screen editing workspace (experimental).
2. Type or paste text; choose Markdown or WhatsApp output syntax.
3. Select a word/phrase and choose Bold, Italic, Strike, Inline code,
   Code block, Heading, or Quote. Long-press keyboard actions are a
   follow-on: the first local screen provides visible formatting controls.
4. Type a change instruction, then tap **Propose edit**. The app uses the
   BYOK provider already configured under AI settings, with an explicit
   request and no automatic submission on typing or opening the screen.
5. Diff preview keeps unchanged content at default colour, additions in a
   configurable colour (default #43A047), and removals struck through in
   the theme's error colour. The current algorithm is a coarse linear-time
   Unicode code-point-safe prefix/suffix diff; more granular line/word
   change-grouping is future work.
6. **Approve** adopts proposed text into the local draft. **Reject**
   discards the proposal. Neither action sends a message.
7. After approval, **Copy approved text** allows manual pasting into
   another app. Choosing any model-suggested edit invokes another **explicit**
   paid provider request and maintains the original draft until approval.

## Response contract

Human-readable schema intent (YAML, for documentation):

```yaml
schema_version: 1
revised_text: "Complete edited draft, not a patch or instructions"
follow_up: "Is this what you had in mind?"
suggestions:
  - id: warm
    label: Change to a warmer tone
    kind: tone
    instruction: "Rewrite this in a warm, conversational tone."
  - id: concise
    label: Make more concise
    kind: length
    instruction: "Shorten without omitting material facts."
  - id: headings
    label: Add emoji next to headings
    kind: emoji
    instruction: "Add relevant emoji only next to existing headings."
```

**Actual model wire format is JSON** and must match
[`schemas/prescreen-response-v1.schema.json`](schemas/prescreen-response-v1.schema.json).
The native Kotlin `PreScreenContract` enforces the same shape plus user-selected
**exactly 3, 4 or 5** options, duplicate-ID rejection, bounded string lengths,
known categories and explicit version. No model-produced component tree,
function calls, URLs, code or actions are allowed. Only a hardcoded Compose
renderer creates UI controls. An invalid response never edits the draft.

## Prompt budget, learned preferences and privacy

- Users choose 3–5 model suggestions per call. Each follow-up option is
  another charged request to the provider; never assume free inference.
- Pre-screen has no built-in provider keys. Keys remain in the existing
  encrypted BYOK provider store. Provider endpoint/policy security is part
  of PR #1's separate review.
- Preference learning is **off by default**. If enabled, accepted **model
  suggestions** increment *category counts only*, bounded to 100 per kind.
  No text, contacts, app identities, model responses or instruction history
  are saved. Rejecting an edit teaches nothing. Users can clear counts.
- Counts are provided to subsequent model requests as coarse priority
  categories and reorder locally displayed options. Categories are
  `tone`, `length`, `format`, `structure`, `emoji`, `typography`.
- Future work: explicit import/export and per-app profiles with user consent.
  No silent global clipboard capture or network access.

## Platform formatting limits

Ordinary Markdown and WhatsApp use different markup. In WhatsApp,
bold uses a single `*`, italic uses `_`, strike uses `~`, and
monospace/code can use backticks. Markdown heading syntax `# Heading`
cannot be assumed to render as headings in WhatsApp: in WhatsApp mode,
the Heading action uses a bold-like presentation instead.

The renderer does **not** change the destination application's font and does
not guarantee interpretation of HTML/Markdown by apps such as Notion,
Obsidian, or WhatsApp. "Convert the font" is reserved as a controlled,
previewable typography action, not unrestricted Unicode mutation.

## Host editor transfer (blocking follow-up)

Before introducing a keyboard-toolbar shortcut that transfers results back to
an arbitrary app:

- Capture app/window/editor identity, selection and the exact original text
  or supported bounded excerpt; never infer unknown hidden document text.
- Require a live editor snapshot that matches the captured text and selection.
  Abort if the focus, input type, connection, selection or source changed.
- Block password, sensitive, incognito and unsupported raw editor fields.
- Perform one replacement in an editor batch, provide rollback only when
  the editor supports it; never retry blindly after an uncertain result.
- Provide a copy-only fallback when the target app cannot confirm edit
  semantics or formatting support.
- Add connected tests across standard, multiline and uncooperative host
  editors, emoji/combining-character selections and app switching.
- Verify Android IME lifecycle and background/cancellation handling.
- Use rich MIME negotiation when available; never claim Markdown formatting
  is automatically applied to every receiving app.

## Acceptance checklist

- [x] Versioned JSON contract implemented with strict parser and bounded data.
- [x] Local formatter, Unicode-safe coarse diff and opt-in category memory.
- [x] Navigable local Compose workspace with explicit Approve/Reject and Copy.
- [x] Configurable 3–5 suggestions and diff colour; no default learning.
- [x] Dedicated JVM tests added for schema, formatting, diff and memory.
- [ ] Fresh Android build/unit tests and strict CI succeed on integrated branch.
- [ ] Connected Android tests demonstrate state/cancellation/privacy boundaries.
- [ ] Independent code/security review completes with no high-severity findings.
- [ ] Keyboard long-press formatting and IME toolbar entry implemented.
- [ ] Safe original-app transfer under host editor identity/content guard.
- [ ] Granular word/line diff and accessibility/high-contrast checks.
- [ ] Real-device user acceptance, WhatsApp/Obsidian/Notion trials.
