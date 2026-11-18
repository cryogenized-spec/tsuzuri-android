/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.text.dictation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock

class CloudVoiceProviderSwitcherTest : FunSpec({
    val mistral = "https://api.mistral.ai/v1/audio/transcriptions"
    val groq = "https://api.groq.com/openai/v1/audio/transcriptions"
    val openai = "https://api.openai.com/v1/audio/transcriptions"

    test("failure to commit encrypted key deletion blocks all provider settings changes") {
        var active = mistral
        var model = "old"
        var legacyKey = "legacy-secret"
        val switcher = CloudVoiceProviderSwitcher(
            currentEndpoint = { active },
            currentModel = { "previous-model" },
            durablyEraseKey = { false },
            clearLegacyKey = { legacyKey = "" },
            updateEndpoint = { active = it },
            updateModel = { model = it },
        )
        switcher.switchTo(groq, "whisper-large-v3-turbo") shouldBe
            CloudVoiceProviderSwitcher.SwitchResult.KeyDeletionFailed
        active shouldBe mistral
        model shouldBe "old"
        legacyKey shouldBe "legacy-secret"
    }

    test("successful switch clears legacy key before target endpoint becomes visible") {
        var active = mistral
        var legacyKey = "legacy-secret"
        var durableDeletion = false
        val switcher = CloudVoiceProviderSwitcher(
            currentEndpoint = { active },
            currentModel = { "previous-model" },
            durablyEraseKey = { durableDeletion = true; true },
            clearLegacyKey = { legacyKey = "" },
            updateEndpoint = {
                durableDeletion shouldBe true
                legacyKey shouldBe ""
                active = it
            },
            updateModel = { },
        )
        switcher.switchTo(groq, "whisper-large-v3-turbo") shouldBe
            CloudVoiceProviderSwitcher.SwitchResult.Applied(keyCleared = true)
        active shouldBe groq
    }

    test("rapid consecutive provider choices finish in request order despite delayed deletion") {
        runBlocking {
            var active = mistral
            val entered = CompletableDeferred<Unit>()
            val unblock = CompletableDeferred<Unit>()
            val switcher = CloudVoiceProviderSwitcher(
                currentEndpoint = { active },
                currentModel = { "previous-model" },
                durablyEraseKey = {
                    entered.complete(Unit)
                    unblock.await()
                    true
                },
                clearLegacyKey = {},
                updateEndpoint = { active = it },
                updateModel = {},
            )
            val first = async { switcher.switchTo(openai, "gpt-transcribe") }
            entered.await()
            val second = async { switcher.switchTo(groq, "whisper-large-v3-turbo") }
            unblock.complete(Unit)
            first.await() shouldBe CloudVoiceProviderSwitcher.SwitchResult.Applied(true)
            second.await() shouldBe CloudVoiceProviderSwitcher.SwitchResult.Applied(true)
            active shouldBe groq
        }
    }

    test("key writes for the old provider cannot land after a provider host switch") {
        var active = mistral
        var edited = false
        val switcher = CloudVoiceProviderSwitcher(
            currentEndpoint = { active },
            currentModel = { "previous-model" },
            durablyEraseKey = { true },
            clearLegacyKey = {},
            updateEndpoint = { active = it },
            updateModel = {},
        )
        switcher.switchTo(groq, "whisper-large-v3-turbo")
        switcher.editKeyForHost(mistral) { edited = true; true } shouldBe false
        edited shouldBe false
        switcher.editKeyForHost(groq) { edited = true; true } shouldBe true
        edited shouldBe true
    }

    test("session cannot pair an old provider key with the new host during switching") {
        runBlocking {
            var active = mistral
            var key = "mistral-secret"
            val started = CompletableDeferred<Unit>()
            val finishErase = CompletableDeferred<Unit>()
            val switcher = CloudVoiceProviderSwitcher(
                currentEndpoint = { active },
                currentModel = { "previous-model" },
                durablyEraseKey = {
                    started.complete(Unit)
                    finishErase.await()
                    key = ""
                    true
                },
                clearLegacyKey = {},
                updateEndpoint = { active = it },
                updateModel = {},
            )
            val switching = async { switcher.switchTo(groq, "whisper-large-v3-turbo") }
            started.await()
            val snapshot = async {
                CloudVoiceProviderGuard.mutex.withLock { key to active }
            }
            finishErase.complete(Unit)
            switching.await() shouldBe CloudVoiceProviderSwitcher.SwitchResult.Applied(true)
            snapshot.await() shouldBe ("" to groq)
        }
    }

    test("dialog host pinned before suspension rejects old key save after a host switch") {
        runBlocking {
            var active = mistral
            val expectedKeyHost = active
            val waiting = CompletableDeferred<Unit>()
            val resume = CompletableDeferred<Unit>()
            var saved = false
            val switcher = CloudVoiceProviderSwitcher(
                currentEndpoint = { active },
                currentModel = { "previous-model" },
                durablyEraseKey = { true },
                clearLegacyKey = {},
                updateEndpoint = { active = it },
                updateModel = {},
            )
            val delayed = async {
                waiting.complete(Unit)
                resume.await()
                switcher.editKeyForHost(expectedKeyHost) { saved = true; true }
            }
            waiting.await()
            switcher.switchTo(groq, "whisper-large-v3-turbo")
            resume.complete(Unit)
            delayed.await() shouldBe false
            saved shouldBe false
        }
    }
    test("model write failure leaves the old host and model selected") {
        var active = mistral
        var model = "old-model"
        var keyCleared = false
        val switcher = CloudVoiceProviderSwitcher(
            currentEndpoint = { active }, currentModel = { model },
            durablyEraseKey = { keyCleared = true; true },
            clearLegacyKey = {},
            updateEndpoint = { active = it },
            updateModel = {
                if (it == "whisper-large-v3-turbo") throw IllegalStateException("disk write failed")
                model = it
            },
        )
        switcher.switchTo(groq, "whisper-large-v3-turbo") shouldBe
            CloudVoiceProviderSwitcher.SwitchResult.SettingsUpdateFailed(
                keyCleared = true, restoredPreviousSettings = true,
            )
        keyCleared shouldBe true
        active shouldBe mistral
        model shouldBe "old-model"
    }

    test("endpoint failure after partial commit compensates both writes under shared lock") {
        var active = mistral
        var model = "old-model"
        val switcher = CloudVoiceProviderSwitcher(
            currentEndpoint = { active }, currentModel = { model },
            durablyEraseKey = { true }, clearLegacyKey = {},
            updateEndpoint = {
                active = it
                if (it == groq) throw IllegalStateException("endpoint write reported failure")
            },
            updateModel = { model = it },
        )
        switcher.switchTo(groq, "whisper-large-v3-turbo") shouldBe
            CloudVoiceProviderSwitcher.SwitchResult.SettingsUpdateFailed(
                keyCleared = true, restoredPreviousSettings = true,
            )
        active shouldBe mistral
        model shouldBe "old-model"
    }

    test("a failed rollback is reported instead of claiming settings were restored") {
        var active = mistral
        var model = "old-model"
        val switcher = CloudVoiceProviderSwitcher(
            currentEndpoint = { active }, currentModel = { model },
            durablyEraseKey = { true }, clearLegacyKey = {},
            updateEndpoint = { active = it; if (it == groq) error("endpoint unavailable") },
            updateModel = {
                if (it == "old-model") error("rollback unavailable")
                model = it
            },
        )
        switcher.switchTo(groq, "whisper-large-v3-turbo") shouldBe
            CloudVoiceProviderSwitcher.SwitchResult.SettingsUpdateFailed(
                keyCleared = true, restoredPreviousSettings = false,
            )
        active shouldBe mistral
        model shouldBe "whisper-large-v3-turbo"
    }

    test("legacy key deletion failure never publishes a new provider host") {
        var active = mistral
        var model = "old-model"
        val switcher = CloudVoiceProviderSwitcher(
            currentEndpoint = { active }, currentModel = { model },
            durablyEraseKey = { true },
            clearLegacyKey = { throw IllegalStateException("legacy write failure") },
            updateEndpoint = { active = it },
            updateModel = { model = it },
        )
        switcher.switchTo(groq, "whisper-large-v3-turbo") shouldBe
            CloudVoiceProviderSwitcher.SwitchResult.KeyDeletionFailed
        active shouldBe mistral
        model shouldBe "old-model"
    }
})
