/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.text.dictation

/** OpenAI's new gpt-transcribe model uses a plural array rather than language=. */
object TranscriptionMultipartFields {
    fun languageField(endpointUrl: String, model: String): String {
        val host = runCatching { java.net.URI(endpointUrl).host?.lowercase() }.getOrNull()
        return if (host == "api.openai.com" && model == "gpt-transcribe") "languages[]" else "language"
    }
}
