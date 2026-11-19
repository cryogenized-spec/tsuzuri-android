# Tsuzuri security and Lockbox architecture

Design contract · 2026-11-14. **The Lockbox is not implemented yet.** This document defines the security target, threat model and test requirements.

## Assets and threats

Assets: cloud API keys, custom user variables, clipboard history, media, typed content, recognizer recordings, editor cursor selection state. Threats: leaked local backups, mistaken clipboard persistence, cross-app URI access, malicious endpoint URLs, unsafe logging, racing callbacks, key use in password/incognito fields, and accidental access following unlock. A compromised rooted OS or malicious process with unlocked app privileges is outside the guaranteed protection boundary.

## Separate stores

1. **Secret Lockbox** — provider API credentials, tokens, recovery values; encrypted before local persistence, never shown or included in search/suggestions, never copied to the system clipboard by default. User-triggered reveal optionally requires biometric or device credential.
2. **Private Variables** — named text macros/snippets with per-entry sensitivity and allowed insertion actions. Plain snippets may live in the regular clipboard database; private values belong in Lockbox.
3. **Clipboard history** — user-facing persistence governed by independent retention policy; sensitive content never automatically added.

Do not conflate keyboard clipboard, Lockbox and Android system clipboard.

## Cryptography

- Android Keystore non-exportable AES-256-GCM key, OS-generated random 96-bit nonce per encryption, 128-bit authentication tag.
- Versioned ciphertext envelope; AAD binds entry name, namespace, account/context version and metadata to ciphertext to prevent substitution.
- Key material never embedded in the APK or logs; use per-profile key alias; do not invent a home-made cipher or misuse Signal Double Ratchet.
- Write to app-private atomic storage with lifecycle-safe cancellation. Use transaction or compare-and-swap revision checks to prevent stale writes restoring deleted secrets.
- Prefer device authentication *on access to sensitive actions*, not on every typed character. Explicit time-limited unlock and fail-closed timeout. No silent fallback on key invalidation.
- Exclude secret ciphertext and Keystore-related metadata from cloud/device backups as appropriate; implement an opt-in user-controlled encrypted export only after independent security review.
- Migrate existing EncryptedSharedPreferences data with explicit consistency checks, rollback, zero-plaintext logs and user notice. **Do not delete old data before the verified new vault is readable.**

## Cloud boundary

- Normal typing/clipboard history must never trigger network traffic.
- Only explicitly initiated dictation, image generation or text rewrite may send user data.
- Secret-bearing endpoints must use HTTPS with valid certificate and host; invalid schemes/credentials/URL fragments rejected. Local self-hosted HTTP needs a separate, conspicuous dev-only exception, not a default.
- Reject auto-forwarding of sensitive text, clipboard history or private variables. Show exact destination host and what is sent.
- Provider API keys BYOK; optional cloud services must be removable without breaking keyboard use.

## Clipboard semantics

- Import only clipboard data read after a real system event and only if the user enabled retention.
- Recognised sensitive items: no persistence in history, pinned lists, file caches, thumbnails or backups; showing ephemeral sensitive clip preview requires explicit tap.
- Android's `ClipDescription.EXTRA_IS_SENSITIVE` is a *display hint*, not an encryption mechanism or a blanket permission. Respect it and set it on outputs containing sensitive items.
- Serialize database writes so clear, restore, pin/unpin and capture do not interleave unpredictably. Prevent stale callbacks from replacing newer clipboard state. Verify media URIs are not retained after cancellation.
- Test with credit card-like strings and dummy API keys, NEVER live credentials.

## Must-pass tests before shipping Lockbox

Known-answer encryption/decryption tests, nonce uniqueness, tamper rejection, wrong-AAD rejection, key invalidation, aborted migration recovery, backup/restore rejection, lock expiry, no-secret logging, concurrent insert/clear races, and process death during write.

No feature should claim Signal-level end-to-end encryption or Double Ratchet security without a separate peer-to-peer messaging specification and audit.
