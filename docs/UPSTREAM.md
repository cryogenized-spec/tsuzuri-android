# Upstream and licensing

Tsuzuri began as a fork of [Ownkey Keyboard](https://github.com/MajesteitBart/ownkey-keyboard) at commit `5fafed3602a9d250f9c97de50300e927147c92d6` (2026-11-14 fork). Ownkey itself derives from [FlorisBoard](https://github.com/florisboard/florisboard).

- Preserve upstream copyright notices and Apache 2.0 licence text.
- Keep a record of upstream commits imported later and review them before merging.
- Track third-party licences for dictionaries, icons, fonts, native models and bundled assets separately. Apache 2.0 for the repository does **not** imply all data/assets are Apache 2.0.
- Ownkey logos, website links and imagery are inherited during M0 and must not be used as Tsuzuri branding for publication.
- Do not represent this independent fork as an official Ownkey or FlorisBoard release.

## Migration policy

Initially modify the Android **application ID** and user-facing names while retaining the Kotlin source namespace. Rename source packages only in a dedicated migration with instrumentation tests. For new features, prefer distinct modules and provider interfaces over editing the core input loop.

The initial external AI integration plan uses user-supplied API credentials. API usage is optional, separately consented and isolated from normal keystroke processing.
