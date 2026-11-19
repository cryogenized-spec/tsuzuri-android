/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.text.dictation

/** Presets are only shown as usable when the existing multipart client supports them. */
data class CloudTranscriptionPreset(
    val id: String,
    val name: String,
    val description: String,
    val endpointUrl: String,
    val model: String,
    val setupUrl: String,
    val priceInfoUrl: String,
    val deprecated: Boolean = false,
)

/**
 * Provider discovery is intentionally separate from authentication. Signing in to a provider
 * happens in the external browser; users paste THEIR key into the Keystore-backed local store.
 * Never imply Google Cloud OAuth/service-account credentials are a reusable bearer API key.
 */
object CloudTranscriptionPresets {
    const val OPENAI_KEYS = "https://platform.openai.com/api-keys"
    const val MISTRAL_KEYS = "https://console.mistral.ai/"
    const val GROQ_KEYS = "https://console.groq.com/keys"
    const val GROQ_LIMITS = "https://console.groq.com/docs/rate-limits"
    const val GOOGLE_CLOUD_SETUP = "https://docs.cloud.google.com/speech-to-text/docs/setup"
    const val GOOGLE_CLOUD_PRICING = "https://cloud.google.com/speech-to-text/pricing"
    const val GOOGLE_ANDROID_SPEECH = "https://developer.android.com/reference/android/speech/SpeechRecognizer"
    const val OPENAI_LIVE_GUIDE = "https://developers.openai.com/api/docs/guides/transcription"

    val presets: List<CloudTranscriptionPreset> = listOf(
        CloudTranscriptionPreset(
            id = "groq-whisper-turbo",
            name = "Groq Whisper Large V3 Turbo",
            description = "Free rate-limited tier · no payment card for free plan · recorded dictation",
            endpointUrl = "https://api.groq.com/openai/v1/audio/transcriptions",
            model = "whisper-large-v3-turbo",
            setupUrl = GROQ_KEYS,
            priceInfoUrl = "https://console.groq.com/docs/speech-to-text",
        ),
        CloudTranscriptionPreset(
            id = "openai-transcribe",
            name = "OpenAI GPT-Transcribe",
            description = "Recorded dictation · $0.0045/min · API key and credits required",
            endpointUrl = "https://api.openai.com/v1/audio/transcriptions",
            model = "gpt-transcribe",
            setupUrl = OPENAI_KEYS,
            priceInfoUrl = "https://developers.openai.com/api/docs/models/gpt-transcribe",
        ),
        CloudTranscriptionPreset(
            id = "mistral-mini-3",
            name = "Mistral Voxtral Mini Transcribe 3",
            description = "Recorded dictation · $0.0035/min · API key required",
            endpointUrl = "https://api.mistral.ai/v1/audio/transcriptions",
            model = "voxtral-mini-transcribe-3-0",
            setupUrl = MISTRAL_KEYS,
            priceInfoUrl = "https://docs.mistral.ai/models/voxtral-mini-transcribe-3-0",
        ),
        CloudTranscriptionPreset(
            id = "openai-mini-legacy",
            name = "OpenAI GPT-4o mini Transcribe (deprecated)",
            description = "Legacy lower-cost option · $0.003/min · shuts down 26 February 2027",
            endpointUrl = "https://api.openai.com/v1/audio/transcriptions",
            model = "gpt-4o-mini-transcribe",
            setupUrl = OPENAI_KEYS,
            priceInfoUrl = "https://developers.openai.com/api/docs/deprecations",
            deprecated = true,
        ),
    )

    fun matching(endpointUrl: String, model: String): CloudTranscriptionPreset? =
        presets.firstOrNull { it.endpointUrl == endpointUrl.trim() && it.model == model.trim() }

    /** Only approved known providers receive direct signup links; never pass user-provided URLs here. */
    fun setupUrlFor(endpointUrl: String): String? = when (endpointUrl.trim()) {
        "https://api.openai.com/v1/audio/transcriptions" -> OPENAI_KEYS
        "https://api.groq.com/openai/v1/audio/transcriptions" -> GROQ_KEYS
        "https://api.mistral.ai/v1/audio/transcriptions", "" -> MISTRAL_KEYS
        else -> null
    }

    /** A single encrypted key slot must never be forwarded to a different API host by accident. */
    fun mustClearApiKey(oldEndpoint: String, newEndpoint: String): Boolean =
        effectiveHost(oldEndpoint) != effectiveHost(newEndpoint)

    private fun effectiveHost(endpoint: String): String {
        val url = endpoint.trim().ifBlank { "https://api.mistral.ai/v1/audio/transcriptions" }
        return runCatching { java.net.URI(url).host?.lowercase() }.getOrNull().orEmpty()
    }
}
