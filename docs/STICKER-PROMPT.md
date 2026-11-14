# Sticker maker — default generation contract

**Status:** planned prompt preset, not yet a running model integration.

## Provider and cost

Do not select a paid model solely because the app is free. Gemini Nano Banana image-generation **API** has no verified free tier in the current Google pricing table (2026-11-14); model choice is BYOK with pricing shown before execution. Free local cutting/import/manual stickers are always available. Provider names and model IDs must be configurable because model availability changes.

## Default preset: Tsuzuri Ink Sticker

> Create one isolated sticker of the following subject: {user_description}. Use a visually clean illustration with **thick, smooth black contour outlines**, clear shapes, balanced contrast, legible at thumbnail size, and a single coherent silhouette. Keep the entire subject inside the canvas with padding. Use a transparent background, no background scene, no lettering, no logo, no watermark, no mockup or drop shadow. Preserve the essential features of the user's subject. Output a still 2D illustration suitable for chat sticker insertion, not a photograph. Proposed dimensions: {width} × {height} pixels. Intended export: PNG with alpha.

Store the final prompt only after showing it to the user, keeping user-supplied details editable.

## Export

- Default canvas: 512×512 pixels, user-configurable width × height and aspect ratio subject to device/provider limits.
- Default **black outline** surrounds the subject, NOT a mandatory black canvas/frame. Offer optional outside frame.
- Canonical: PNG RGBA or WebP with transparency.
- Requested JPG: flatten onto an explicitly chosen matte colour (default white); **JPEG cannot store transparency**.
- Model output may not have a true alpha channel: use verified segmentation/matting, allow manual mask correction, inspect edges, retain original.
- Normalize orientation, strip unnecessary EXIF/metadata and preview actual target-app size before saving.
