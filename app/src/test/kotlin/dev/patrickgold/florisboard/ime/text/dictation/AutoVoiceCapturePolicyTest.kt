/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.text.dictation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AutoVoiceCapturePolicyTest : FunSpec({
    fun allowed(
        enabled: Boolean = false,
        backend: TranscriptionBackend = TranscriptionBackend.CLOUD,
        eligible: Boolean = true,
        permission: Boolean = true,
        configured: Boolean = true,
        active: Boolean = false,
        stopped: Boolean = false,
    ) = AutoVoiceCapturePolicy.shouldStart(
        explicitlyEnabled = enabled,
        backend = backend,
        eligibleEditor = eligible,
        hasMicPermission = permission,
        configured = configured,
        alreadyActive = active,
        stoppedByUser = stopped,
    )

    test("ordinary dictation never starts itself in the default Manual mode") {
        allowed() shouldBe false
        allowed(backend = TranscriptionBackend.ORUKEET) shouldBe false
    }

    test("Auto starts only after explicit activation with a configured local or cloud backend") {
        allowed(enabled = true, backend = TranscriptionBackend.CLOUD) shouldBe true
        allowed(enabled = true, backend = TranscriptionBackend.ORUKEET) shouldBe true
    }

    test("system input, mock and unavailable backends never auto-activate") {
        listOf(TranscriptionBackend.EXTERNAL_IME, TranscriptionBackend.MOCK, TranscriptionBackend.UNAVAILABLE).forEach {
            allowed(enabled = true, backend = it) shouldBe false
        }
    }

    test("secure or private editor, missing microphone permission, or unconfigured provider fails closed") {
        allowed(enabled = true, eligible = false) shouldBe false
        allowed(enabled = true, permission = false) shouldBe false
        allowed(enabled = true, configured = false) shouldBe false
    }

    test("an active session or explicit Stop blocks automatic reactivation") {
        allowed(enabled = true, active = true) shouldBe false
        allowed(enabled = true, stopped = true) shouldBe false
    }
})
