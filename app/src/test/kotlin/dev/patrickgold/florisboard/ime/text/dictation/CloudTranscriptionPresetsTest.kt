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
                "https://api.groq.com/openai/v1/audio/transcriptions",
                "https://api.mistral.ai/v1/audio/transcriptions",
            ) && it.model.isNotBlank() && it.setupUrl.startsWith("https://")
        } shouldBe true
    }
    test("OpenAI current model recommended and mini legacy marked deprecated") {
        CloudTranscriptionPresets.presets.first { it.id == "openai-transcribe" }.model shouldBe "gpt-transcribe"
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
    test("Groq free-tier Whisper Turbo uses its actual recorded audio endpoint") {
        val groq = CloudTranscriptionPresets.presets.first { it.id == "groq-whisper-turbo" }
        groq.model shouldBe "whisper-large-v3-turbo"
        groq.endpointUrl shouldBe "https://api.groq.com/openai/v1/audio/transcriptions"
        groq.setupUrl shouldBe CloudTranscriptionPresets.GROQ_KEYS
        CloudTranscriptionPresets.mustClearApiKey(
            "https://api.openai.com/v1/audio/transcriptions", groq.endpointUrl,
        ) shouldBe true
        TranscriptionMultipartFields.languageField(groq.endpointUrl, groq.model) shouldBe "language"
    }
    test("new OpenAI model uses languages array and legacy, Groq, Mistral use singular") {
        val openai = "https://api.openai.com/v1/audio/transcriptions"
        TranscriptionMultipartFields.languageField(openai, "gpt-transcribe") shouldBe "languages[]"
        TranscriptionMultipartFields.languageField(openai, "gpt-4o-mini-transcribe") shouldBe "language"
        TranscriptionMultipartFields.languageField(
            "https://api.mistral.ai/v1/audio/transcriptions", "voxtral-mini-transcribe-3-0",
        ) shouldBe "language"
    }
    test("only known endpoints have trusted account links") {
        CloudTranscriptionPresets.setupUrlFor("https://api.openai.com/v1/audio/transcriptions") shouldBe
            CloudTranscriptionPresets.OPENAI_KEYS
        CloudTranscriptionPresets.setupUrlFor("https://api.evil.test/v1/audio/transcriptions") shouldBe null
    }
})
