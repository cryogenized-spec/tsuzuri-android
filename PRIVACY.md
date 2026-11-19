# Tsuzuri — development privacy notice

**Status:** Pre-release development notice, last reviewed 14 November 2026. This is **not yet a final Google Play privacy policy**. Tsuzuri has not passed its independent code-security and device-behavior audit.

Tsuzuri is an independent open-source fork of Ownkey Keyboard. As currently inherited from Ownkey:

- Ordinary typing, local autocorrection and prediction are designed to run on the device.
- Clipboard history, dictionaries and settings can be stored locally; Android backup and user-exported backup settings need further verification.
- Cloud dictation sends the deliberately recorded audio to the endpoint configured by the user, with any optional recognition vocabulary hints.
- AI rewriting sends selected text (or potentially the active field's text if no selection is made) and the instruction to the user's configured provider.
- API credentials are stored with Android Keystore-backed encrypted preferences in the inherited implementation.
- Downloading an optional offline model involves a separate network request; on-device transcription keeps recorded audio local.

**Do not use these early builds for passwords, banking or other sensitive content until field-specific privacy behavior has been tested.** Cloud features should be disabled for password and incognito fields; verify this before publication. No Tsuzuri-hosted transcription service is provided.

This fork is not operated by the original Ownkey author. Provider requests and retention follow each configured provider's terms; normal API costs are the user's responsibility. No developer API keys are bundled in the application.

Source, issues and current development notes: https://github.com/cryogenized-spec/tsuzuri-android

Before distributing on Google Play, replace this notice with the final audited privacy policy, data-safety declarations, contact details and third-party disclosure.
