# Tsuzuri — keyboard + clipboard alpha handoff

**Status:** experimental debug build, not a certified FUTO replacement or a public release. GitHub Actions verifies compilation, Lint, unit tests and Android clipboard security tests. Physical-device typing and UX still require manual inspection.

## Get the APK without Android Studio or an SSD

1. Open [Tsuzuri GitHub Actions](https://github.com/cryogenized-spec/tsuzuri-android/actions/workflows/android.yml) and select the latest **successful** Android strict quality-gate run for `feat/tsuzuri-clipboard-alpha`.
2. On a normal modern ARM64 Android phone, download the **`tsuzuri-alpha-arm64-phone-apk`** artifact. Extract **`app-arm64-v8a-debug.apk`** and install that APK. GitHub may require signing in to download Actions artifacts.
3. If your phone requires another ABI, download `tsuzuri-debug-apks` and select **`app-armeabi-v7a-debug.apk`** for 32-bit ARM. The **x86** and **x86_64** APKs are mainly for emulators, not ordinary phones. A phone connected by ADB can report supported architectures with `adb shell getprop ro.product.cpu.abilist`. Do **not** install the `androidTest` test-runner APK.
4. Android may prompt you to permit installation from the app you used to open the APK. Open Tsuzuri. In Android Settings, enable Tsuzuri under **System → Languages & input → On-screen keyboard / Keyboard list** (labels vary by manufacturer), then select it using the keyboard picker.

**Do not uninstall FUTO.** Tsuzuri uses its own debug application ID and should coexist with your existing keyboard; switch back from Android's keyboard picker if the alpha does not meet your needs. FUTO's private dictionary and clipboard history are not imported automatically.

## Intended alpha interaction

- A permanent clipboard button sits at the end of the suggestion/smartbar row on default Tsuzuri layouts. It opens the **in-keyboard** clipboard panel, with no separate app.
- The other default keyboard actions prioritize **Undo, Copy, Paste and Select All**; microphone and AI actions remain available but do not dominate the surface. Existing user-customized action arrangements are preserved.
- **Tap a stored text clip** to paste into the current editor. On success, Tsuzuri returns to the normal keyboard so you can continue typing. A rejected rich-content insertion leaves the panel visible and reports failure.
- **Long-press a clip** to pin/unpin or delete. Clipboard history starts enabled with a conservative 20-item limit; Settings → Clipboard can enable/disable history and change related options. The **Return to keyboard after paste** setting may be disabled when you want to paste several clips consecutively.
- Sensitive clips are not stored in history; media content and paste support depend on the target app's accepted MIME types. The clipboard panel is hidden when the device is locked. Android limits background clipboard access, so pasting or copying in other apps while Tsuzuri is not the active keyboard may not create a history entry immediately.

## Physical phone acceptance checklist

Use ordinary sample text, not passwords or private documents:

- [ ] Enable Tsuzuri as the active input method, open Notes or a browser search field, type a paragraph without missing characters.
- [ ] Verify **full-width spacebar**, shift, numbers, symbols, punctuation, delete and long-press work in portrait and landscape.
- [ ] Copy text from a regular editor while Tsuzuri is active; open the clipboard button and verify the copied text appears.
- [ ] Tap the copied clip: the exact text is inserted once and the keyboard returns automatically.
- [ ] Pin a clip, copy two more items, return to the clipboard and verify the pinned clip persists; then unpin/delete deliberately.
- [ ] In Settings → Clipboard, switch off **Return to keyboard after paste** and verify successive pastes can be performed from the same open panel.
- [ ] Verify the clipboard can be opened and closed without entering a duplicate text input or freezing the editor.
- [ ] Test switching between apps, field focus changes and the keyboard switcher; check that typing remains responsive with no internet.
- [ ] Ensure password/incognito fields do not leak their contents into clipboard history.
- [ ] Switch back to FUTO from Android's keyboard selector and confirm the existing keyboard still functions.

If any of these checks fails, **do not promote Tsuzuri to your only keyboard**. Record Android version, app, steps to reproduce and observed vs expected result.

## Engineering gate

This branch is stacked on the Android foundation PR #1 and must pass the full strict quality gate (including emulator clipboard privacy tests) and CodeQL. Current-head Codex review and the real-device acceptance checks above are independent gates. Voice provider PRs #6/#7 are out of scope for a minimal clipboard-first alpha; do not merge unfinished voice cleanup merely to get this build.

Debug APKs are not Play Store builds, and their signing identity is not a release/update guarantee.
