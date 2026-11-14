# Tsuzuri — Interface for Android

**Status: early development fork. Not yet a tested Tsuzuri release.**

Tsuzuri (綴り) is an open-source Android input interface focused on fast everyday typing, advanced editing, flexible layouts, and optional AI-powered voice transcription and rewriting. The first priority is a responsive, dependable keyboard; network-backed features must never block normal input.

## Foundation and provenance

Tsuzuri is derived from [Ownkey Keyboard](https://github.com/MajesteitBart/ownkey-keyboard), itself a fork of [FlorisBoard](https://github.com/florisboard/florisboard). The inherited source is provided under Apache License 2.0, subject to individual dependencies and asset licences. See [LICENSE](LICENSE) and [docs/UPSTREAM.md](docs/UPSTREAM.md).

The fork was created from Ownkey commit `5fafed3602a9d250f9c97de50300e927147c92d6` (its 0.12.0 development baseline). Existing transcription, rewriting, predictive typing, clipboard, themes, and other capabilities remain **inherited implementations, not independently validated Tsuzuri features**.

## Development build

Prerequisites: JDK 17, Android SDK API 36, the configured Android NDK/CMake, and Rust toolchain (see `gradle/tools.versions.toml` and upstream `CONTRIBUTING.md`).

```bash
./gradlew :app:assembleDebug
./gradlew :lib:prediction:test :app:testDebugUnitTest
```

Debug variants use a distinct application ID: `io.github.cryogenizedspec.tsuzuri.debug`. The Kotlin namespace remains `dev.patrickgold.florisboard` during migration to avoid an unnecessary mass refactor.

The CI workflow builds a debug APK and runs unit tests on every pull request. **An APK should be considered experimental until successfully installed and tested on a physical device.**

## Direction

- **M0 — bootstrap:** independent app ID/name, safe CI, reproducible build, baseline keyboard typing tests
- **M1 — everyday input:** audit prediction, corrections, clipboard, multilingual behavior, editor compatibility
- **M2 — voice:** optional BYOK OpenAI transcription (`gpt-transcribe`) with cancel, preview, provider controls
- **M3 — productivity:** programmable action bar, text buffer/full-screen editor, app profiles
- **M4 — publish:** own icon/branding, accessibility, privacy/security, third-party notices, Play Store compliance, signed AAB

See [docs/ROADMAP-TSUZURI.md](docs/ROADMAP-TSUZURI.md).

## Privacy and development safety

Normal typing should stay local. Network operations require direct action from the user and clear disclosure; no hosted keys are embedded in the APK. This fork inherits Ownkey's local storage and network implementation, **which is undergoing independent review**. See the [development privacy notice](PRIVACY.md).

## Upstream relationship

Ownkey and FlorisBoard remain credited as upstream authors. Tsuzuri is independent and is not their official application. Internal code identifiers, existing visual assets and some inherited documentation may still refer to those projects while the fork is being separated. Do not publish the current APK as a finished Tsuzuri product.
