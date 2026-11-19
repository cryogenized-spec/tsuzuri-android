/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.text.dictation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Deterministic lifecycle tests: delayed cloud credential lookup is an injected deferred,
 * and recorder-start is counted without requiring Android hardware or wall clock sleeps.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoVoiceCaptureCoordinatorTest : FunSpec({
    fun base() = AutoVoiceCaptureContext(
        enabled = true,
        backend = TranscriptionBackend.CLOUD,
        editorSessionId = 42L,
        eligibleEditor = true,
        micPermission = true,
        backendReady = true,
        alreadyActive = false,
    )

    test("Manual default never opens a recorder even after availability notification") {
        runTest {
            var context = base().copy(enabled = false)
            var starts = 0
            val gate = AutoVoiceCaptureCoordinator(
                scope = this,
                current = { context },
                configured = { true },
                start = { starts++ },
            )
            gate.onKeyboardFocus(true)
            gate.onAvailabilityChanged(true)
            runCurrent()
            starts shouldBe 0
            context = context.copy(enabled = true)
            gate.onAvailabilityChanged(true)
            runCurrent()
            starts shouldBe 1
        }
    }

    test("slow credential completion after keyboard hide never opens the microphone") {
        runTest {
            val credential = CompletableDeferred<Boolean>()
            val started = mutableListOf<TranscriptionBackend>()
            val gate = AutoVoiceCaptureCoordinator(
                scope = this,
                current = ::base,
                configured = { credential.await() },
                start = { started += it },
            )
            gate.onKeyboardFocus(true)
            runCurrent()
            gate.onSessionInvalidated(keyboardHidden = true)
            credential.complete(true)
            runCurrent()
            started shouldBe emptyList()
        }
    }

    test("Stop from any UI prevents restart and cancels pending preflight until keyboard hide") {
        runTest {
            var context = base()
            val credential = CompletableDeferred<Boolean>()
            var starts = 0
            val gate = AutoVoiceCaptureCoordinator(
                scope = this,
                current = { context },
                configured = { credential.await() },
                start = { starts++ },
            )
            gate.onKeyboardFocus(true)
            runCurrent()
            gate.onUserStop()
            credential.complete(true)
            gate.onAvailabilityChanged(true)
            runCurrent()
            starts shouldBe 0

            gate.onSessionInvalidated(keyboardHidden = true)
            context = context.copy(editorSessionId = 43L)
            gate.onKeyboardFocus(true)
            runCurrent()
            starts shouldBe 1
        }
    }

    test("an editor switch or provider change invalidates delayed permission and credential results") {
        runTest {
            var context = base()
            val credential = CompletableDeferred<Boolean>()
            var starts = 0
            val gate = AutoVoiceCaptureCoordinator(
                scope = this,
                current = { context },
                configured = { credential.await() },
                start = { starts++ },
            )
            gate.onKeyboardFocus(true)
            runCurrent()
            context = context.copy(editorSessionId = 99L)
            credential.complete(true)
            runCurrent()
            starts shouldBe 0

            // Even with the same editor, switching the vendor must invalidate the old preflight.
            val another = CompletableDeferred<Boolean>()
            context = base()
            val anotherGate = AutoVoiceCaptureCoordinator(
                scope = this,
                current = { context },
                configured = { another.await() },
                start = { starts++ },
            )
            anotherGate.onKeyboardFocus(true)
            runCurrent()
            context = context.copy(backend = TranscriptionBackend.ORUKEET)
            another.complete(true)
            runCurrent()
            starts shouldBe 0
        }
    }

    test("availability flow catches up after focus, then retries exactly once") {
        runTest {
            var context = base().copy(eligibleEditor = false)
            var starts = 0
            val gate = AutoVoiceCaptureCoordinator(
                scope = this,
                current = { context },
                configured = { true },
                start = { starts++ },
            )
            gate.onKeyboardFocus(true)
            runCurrent()
            starts shouldBe 0
            context = context.copy(eligibleEditor = true)
            gate.onAvailabilityChanged(true)
            runCurrent()
            starts shouldBe 1
        }
    }

    test("a slow result cannot bypass revoked permission, secure editor or disabled Auto") {
        runTest {
            for (changed in listOf(
                base().copy(micPermission = false),
                base().copy(eligibleEditor = false),
                base().copy(enabled = false),
                base().copy(alreadyActive = true),
            )) {
                var context = base()
                val credential = CompletableDeferred<Boolean>()
                var starts = 0
                val gate = AutoVoiceCaptureCoordinator(
                    scope = this,
                    current = { context },
                    configured = { credential.await() },
                    start = { starts++ },
                )
                gate.onKeyboardFocus(true)
                runCurrent()
                context = changed
                credential.complete(true)
                runCurrent()
                starts shouldBe 0
            }
        }
    }
    test("Auto waits for local model reconciliation and starts only if focus is still current") {
        runTest {
            val modelReady = CompletableDeferred<Boolean>()
            val started = mutableListOf<TranscriptionBackend>()
            val gate = AutoVoiceCaptureCoordinator(
                scope = this,
                current = { base().copy(backend = TranscriptionBackend.ORUKEET) },
                configured = { modelReady.await() },
                start = { started += it },
            )
            gate.onKeyboardFocus(true)
            runCurrent()
            started shouldBe emptyList()
            modelReady.complete(true)
            runCurrent()
            started shouldBe listOf(TranscriptionBackend.ORUKEET)
        }
    }

    test("hiding during local model reconciliation must not start even when model becomes ready") {
        runTest {
            val modelReady = CompletableDeferred<Boolean>()
            var starts = 0
            val gate = AutoVoiceCaptureCoordinator(
                scope = this,
                current = { base().copy(backend = TranscriptionBackend.ORUKEET) },
                configured = { modelReady.await() },
                start = { starts++ },
            )
            gate.onKeyboardFocus(true)
            runCurrent()
            gate.onSessionInvalidated(keyboardHidden = true)
            modelReady.complete(true)
            runCurrent()
            starts shouldBe 0
        }
    }
})
