/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.text.dictation

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CloudTranscriptionPresetsTest : FunSpec({
    test("all selectable presets use known HTTPS multipart transcription endpoints") {
        CloudTranscriptionPresets.presets.all {
            it.endpointUrl in setOf(
                "https://api.openai.com/v1/audio/transcriptions",
                "https://api.mistral.ai/v1/audio/transcriptions",
            ) && it.model.isNotBlank() && it.setupUrl.startsWith("https://")
        } shouldBe true
    }
    test("OpenAI current model recommended and mini legacy marked deprecated") {
        CloudTranscriptionPresets.presets.first().model shouldBe "gpt-transcribe"
        CloudTranscriptionPresets.presets.first { it.model == "gpt-4o-mini-transcribe" }.deprecated shouldBe true
    }
    test("switching provider hosts clears single encrypted key slot") {
        CloudTranscriptionPresets.mustClearApiKey("", "https://api.openai.com/v1/audio/transcriptions") shouldBe true
        CloudTranscriptionPresets.mustClearApiKey(
            "https://api.openai.com/v1/audio/transcriptions",
            "https://api.openai.com/v1/audio/transcriptions",
        ) shouldBe false
        CloudTranscriptionPresets.mustClearApiKey(
            "https://api.openai.com/v1/audio/transcriptions",
            "https://api.mistral.ai/v1/audio/transcriptions",
        ) shouldBe true
        CloudTranscriptionPresets.mustClearApiKey(
            "https://api.mistral.ai/v1/audio/transcriptions",
            "https://api.mistral.ai/v1/audio/transcriptions",
        ) shouldBe false
    }
    test("only known endpoints have trusted account links") {
        CloudTranscriptionPresets.setupUrlFor("https://api.openai.com/v1/audio/transcriptions") shouldBe
            CloudTranscriptionPresets.OPENAI_KEYS
        CloudTranscriptionPresets.setupUrlFor("https://api.evil.test/v1/audio/transcriptions") shouldBe null
    }
})
