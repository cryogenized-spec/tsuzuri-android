#!/usr/bin/env python3
"""Minimal enforceable Tsuzuri security/build-source contract; Android tests are separate."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
def source(path):
    return (ROOT / path).read_text(encoding="utf-8")

errors = []
def require(condition, reason):
    if not condition:
        errors.append(reason)

gradle = source("app/build.gradle.kts")
strings = source("app/src/main/res/values/strings.xml")
workflow = source(".github/workflows/android.yml")
clipboard = source("app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardManager.kt")
transcription = source("app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/dictation/TranscriptionClient.kt")
rewrite = source("app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/rewrite/LlmRewriteClient.kt")
provider = source("app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/rewrite/LlmRewriteProvider.kt")

require('applicationId = "io.github.cryogenizedspec.tsuzuri"' in gradle, "independent Android application ID")
require('<string name="app_name">Tsuzuri Keyboard</string>' in strings, "independent Android keyboard name")
require("BIND_INPUT_METHOD" in source("app/src/main/AndroidManifest.xml"), "IME entry point")
require("contents: read" in workflow and "contents: write" not in workflow, "read-only workflow token")
require("pull_request_target:" not in workflow, "no privileged PR-target workflow")
require("action-gh-release" not in workflow and "publish-ci-debug.sh" not in workflow, "no inherited release publication")
require("ClipboardRetentionPolicy.shouldPersist(newItem.isSensitive)" in clipboard, "capture path must guard sensitive clipboard history")
require("ClipboardRetentionPolicy.shouldPersist(item.isSensitive)" in clipboard, "manual insert path must guard sensitive history")
require("ClipboardEventGeneration()" in clipboard and "isCurrent(callbackGeneration)" in clipboard, "stale clipboard callback gate")
require("primaryClipLastFromCallback" not in clipboard, "must not deduplicate uncommitted callbacks")
require("ClipboardCapturePolicy.ownsNewClone" in clipboard, "never delete borrowed URI on cancelled callback")
require("cloneUri = !incomingSensitive" in clipboard, "flagged media must not write app-private media files")
require("wasAppliedExternalMediaSource" in clipboard, "external media dedup only after committed source")
require("clipboardEventGeneration.invalidatePending()" in clipboard, "internal clipboard update must invalidate stale callbacks")
require("currentHistory.all.filterNot { it.isPinned }" in clipboard, "retain pinned image files on partial history clear")
clipdb = source("app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardDatabase.kt")
require("getBoolean(ClipboardRetentionPolicy.SENSITIVE_EXTRA)" in clipdb, "sensitive extras read across Android versions")
require("extras.putBoolean(ClipboardRetentionPolicy.SENSITIVE_EXTRA, true)" in clipdb, "sensitive extras preserved when copying")
require("SecureEndpointPolicy.isAllowed(endpointUrl)" in transcription, "TLS gate for dictation")
require("SecureEndpointPolicy.isAllowed(endpointUrl)" in rewrite, "TLS gate for rewrite")
require("SecureEndpointPolicy.isAllowed(endpointUrl)" in provider, "TLS gate for rewrite UI validation")
require("https" in source("app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/network/SecureEndpointPolicy.kt"), "secure provider policy")
require((ROOT / "app/src/test/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardEventGenerationTest.kt").exists(), "clipboard concurrency regression test")
require((ROOT / "app/src/test/kotlin/dev/patrickgold/florisboard/ime/text/network/SecureEndpointPolicyTest.kt").exists(), "HTTPS regression test")
# Lock down archive media restore and real-device privacy tests, not just strings
restore = source("app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/RestoreScreen.kt")
restore_policy = source("app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/RestoreCompatibilityPolicy.kt")
restore_importer = source("app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/ClipboardBackupMediaImporter.kt")
storage = source("app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardFileStorage.kt")
require("normalizeMediaReference" in restore_policy and "rawAuthority" in restore_policy, "archive media URI identity must be validated")
require('".beta"' in restore_policy, "Ownkey and Tsuzuri beta archives remain supported")
require("ClipboardBackupMediaImporter.validate(" in restore, "restore preflight before modifying data")
require("ClipboardBackupMediaImporter.prepare(" in restore, "restore via dedicated media importer")
require("restoreHistoryForRestore(" in restore and "clearFullHistoryForRestore()" in restore, "awaitable transactional history restore")
require("importMediaFromBackup" in storage and "destination.exists()" in storage, "restore media must never overwrite a colliding file ID")
require("ClipboardMediaProvider.registerRestored" in restore_importer, "restored content needs usable MIME metadata")
require("ClipboardRetentionPolicy.shouldPersist(item.isSensitive)" in restore_importer, "never import sensitive legacy media")
require((ROOT / "app/src/androidTest/kotlin/dev/patrickgold/florisboard/TsuzuriClipboardSecurityTest.kt").exists(), "Android clipboard privacy instrumentation")
require(":app:assembleDebugAndroidTest" in workflow, "compile Android instrumentation")
require("android-instrumentation:" in workflow and "reactivecircus/android-emulator-runner" in workflow, "execute Android emulator privacy tests")
require('test "$INSTRUMENTATION" = \'success\'' in workflow, "mandatory instrumentation status check")
for path in (".github/workflows/crowdin-upload.yml", ".github/workflows/validate-strings-no-translations.yml"):
    require(not (ROOT / path).exists(), f"remove inherited privileged workflow {path}")
if errors:
    for item in errors:
        print("FAIL: " + item)
    raise SystemExit(1)
print("PASS: Tsuzuri source/CI security contracts present.")
