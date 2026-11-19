# Tsuzuri input, emoji, clipboard and media specifications

Product behavior specification · 2026-11-14 · proposed, not shipped.

### Spell correction

- Underline misspelled words using editor-provided `SuggestionSpan` / spellchecker where possible. Android hosts, especially custom text fields and WebViews, may ignore spans or choose their own underline style.
- Tapping a candidate invokes a vertical popup with replace choices, Ignore, Add word, Copy. Keep original in list and support undo.
- When host does not expose coordinates/tap callback, provide a keyboard-owned vertical alternatives menu for the current/selected word. **We cannot force a right-click menu within every third-party app.**
- Never override an edit or selection after newer input is received; candidate generation is cancellable and generation-tagged.

### Emoji and text formats

- `bear` → 🐻, `cat` → 🐈, `rose` → 🌹. Inline suggestion chip opens candidate strip; explicit selection inserts Unicode emoji.
- Mapping is editable, language-aware, disabled in secure/code fields, and fully offline.
- Transform selected words into Unicode mathematical Fraktur/blackletter glyphs: example `Gothic` → `𝕲𝖔𝖙𝖍𝖎𝖈`. Warn that this is not an ASCII font and may be inaccessible/search-unfriendly.

### Clipboard

- Adjustable capacity 1…999 total entries, counting pinned items; default small and auto-expiry supported.
- Types: `text/plain`, `text/markdown` (or literal Markdown), `text/html`, URI/hyperlink (with label), images and safe fallbacks.
- Preserve sanitized hyperlink targets and visible text. Don't trust HTML scripts or remote image references; strip active content.
- Store durable images in app-private media storage and use granted `content://` URIs. Limit input file size and image decoding dimensions to avoid memory exhaustion.
- An editor that accepts text only must receive a safe plaintext approximation; media paste uses MIME-aware rich-content insertion only where supported.
- Validate rapid copy/clear/pin/restore, dupes, eviction and process death; ensure sensitive flagged items never persist.

### Sticker storage

- File-based storage with encrypted metadata only if user selects private mode; originals, edited PNG/WebP, thumbnails, packs, tags.
- Custom crop, cut-out, black outline width, offset and blur; visual maker independent of cloud generation.
- Model-agnostic API driver for BYOK providers. Never silently bill user or upload camera/gallery images without explicit action.
- PNG/WebP transparent is canonical. JPEG/JPG is allowed *only* with flattened opaque background; label that clearly.
- Use Android's rich content API to insert stickers. Fall back to Share or image attachment if editor rejects MIME type. Do not spoof support.

### Local performance

- Handle short bursts ≥ 15 key events/second without dropped/reordered input on reference device; establish device-specific p95 latency targets by profiling rather than inventing results.
- Keep network, sticker generation and database I/O off the UI thread.
- Typing must function offline and while the network provider is down.
