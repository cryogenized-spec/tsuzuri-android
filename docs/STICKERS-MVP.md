# Tsuzuri stickers — first Android milestone

Status: **draft PR #5, stacked on PR #4**. Code is not released or
merged. Check the PR Actions run and install it on a real phone before
calling the feature functional.

## What is wired up

1. Open Tsuzuri's media keyboard → **Stickers** (next to Emoji).
2. Tap **Manage**. A private Tsuzuri sticker-manager activity opens.
3. Use **Import images** and Android's document picker; select static
   PNG, JPEG or WebP files. Gallery-wide permissions are *not* requested.
   Up to 20 selections are processed per import batch, 200 total.
4. Optionally create up to 12 named packs. Select a pack before import.
   You can remove individual stickers or a pack with a confirmation dialog.
5. Return to the messaging app and open the Sticker tab again. Stickers
   appear in their packs; tap one to send it to the current editor.
6. If the editor does not accept image content, use the explicit **Copy**
   button under a sticker and paste the copied image into the app manually.

## Image handling and privacy

The sticker manager reads only the URI selected through Android's
system picker. It never requests broad gallery access. Inputs are
bounded at 8 MiB and 12,000 pixels per side, and decoded with scaling.
Images are converted to a 512×512 PNG with preserved aspect ratio and
transparent padding. EXIF rotation is applied for standard 90°, 180°
and 270° orientations. Original photos are *not* retained as part of
the sticker library. Saved files and pack index are local and private.
No AI, third-party sticker service, or network request is involved.

The FileProvider shares **only** the `files/stickers/` directory and
exposes read-only `content://` URIs to supported receiving editors.
The sticker pack manager is not exported. Keyboard sending is only
attempted with a live ordinary editor and an advertised image MIME
type. Rejected attempts never trigger a silent clipboard insertion.

Deleting a pack removes its stickers. No automatic or cloud backup of
sticker files is enabled by this PR.

## Deliberate limitations

- **These are image stickers, not registered native WhatsApp sticker
  packs.** Android `commitContent` only works where the host editor
  advertises support. WhatsApp's interface may require its own native
  sticker-pack integration or an external share path.
- No animated stickers, GIF playback, background removal, border styling,
  image cropping, AI generation, pack sharing or native WhatsApp export yet.
- Static JPEG photographs retain their backgrounds. Transparent PNGs and
  WebP images retain usable transparency through PNG conversion.
- Not all apps accept images on the keyboard's input connection. In those
  apps Copy may work depending on their paste implementation.

## Android device acceptance checklist

- [ ] Launch Manage from the IME and return to the same text field.
- [ ] Import PNG with transparency, rotated JPEG and static WebP.
- [ ] Reject oversized, malformed, non-image and unsupported MIME input.
- [ ] Verify a 512×512 saved file and persistent pack list after restart.
- [ ] Create, switch and delete packs; confirm deleting a pack also deletes
      its sticker files.
- [ ] Tap to send in a host app that advertises `image/png` and verify
      it receives a readable image through Android's content grant.
- [ ] In a host app that lacks support, check the explicit Copy fallback.
- [ ] Verify sending is unavailable for password, email and URI fields.
- [ ] Check thumb touch targets, emoji switch, small screens, landscape,
      keyboard resize, dark/light themes and accessibility labels.
- [ ] Test file removals with an open clipboard/content URI.
- [ ] Run full APK/JVM CI, security checks and independent Codex review.
