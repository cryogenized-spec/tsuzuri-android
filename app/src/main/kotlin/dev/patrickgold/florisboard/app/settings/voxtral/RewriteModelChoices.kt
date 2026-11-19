/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app.settings.voxtral

import dev.patrickgold.florisboard.ime.text.rewrite.LlmRewriteProviderPreset
import dev.patrickgold.florisboard.ime.text.rewrite.LlmRewriteProviders

/**
 * Visible quick choices rather than forcing an API model ID to be typed.
 * These are convenience IDs, NOT a live model availability list.
 * An arbitrary provider-supported legacy or custom ID remains editable.
 */
internal object RewriteModelChoices {
    fun forProvider(preset: LlmRewriteProviderPreset): List<String> {
        val additional = when (preset.id) {
            LlmRewriteProviders.OpenAiResponses -> listOf(
                "gpt-5.4-mini", "gpt-5.4", "gpt-4.1-mini", "gpt-4.1", "gpt-4o-mini", "gpt-4o",
            )
            LlmRewriteProviders.OpenAiChatCompletions -> listOf(
                "gpt-5.4-mini", "gpt-4.1-mini", "gpt-4.1", "gpt-4o-mini", "gpt-4o",
            )
            LlmRewriteProviders.Mistral -> listOf(
                "mistral-small-latest", "mistral-medium-latest", "mistral-large-latest",
            )
            else -> emptyList()
        }
        return (listOf(preset.defaultModel) + additional)
            .filter { it.isNotBlank() }.distinct()
    }
}
