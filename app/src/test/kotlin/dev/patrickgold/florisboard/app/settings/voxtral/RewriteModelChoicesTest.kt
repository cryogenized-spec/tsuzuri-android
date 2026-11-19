/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app.settings.voxtral

import dev.patrickgold.florisboard.ime.text.rewrite.LlmRewriteProviders
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RewriteModelChoicesTest : FunSpec({
    test("OpenAI Chat shows legacy models without hiding the provider default") {
        val preset = LlmRewriteProviders.byId(LlmRewriteProviders.OpenAiChatCompletions)
        val choices = RewriteModelChoices.forProvider(preset)
        choices.first() shouldBe preset.defaultModel
        (choices.contains("gpt-4o-mini")) shouldBe true
        (choices.contains("gpt-4.1")) shouldBe true
        choices.distinct() shouldBe choices
    }

    test("Responses provider also offers older models") {
        val preset = LlmRewriteProviders.byId(LlmRewriteProviders.OpenAiResponses)
        RewriteModelChoices.forProvider(preset).contains("gpt-4o") shouldBe true
    }

    test("other providers never receive OpenAI-specific models") {
        listOf(
            LlmRewriteProviders.Anthropic,
            LlmRewriteProviders.Mistral,
            LlmRewriteProviders.OpenRouter,
            LlmRewriteProviders.Custom,
        ).forEach { id ->
            val preset = LlmRewriteProviders.byId(id)
            val choices = RewriteModelChoices.forProvider(preset)
            choices.none { it.startsWith("gpt-") } shouldBe true
            choices.distinct() shouldBe choices
        }
    }

    test("a custom provider retains manual model entry without a misleading default") {
        val preset = LlmRewriteProviders.byId(LlmRewriteProviders.Custom)
        RewriteModelChoices.forProvider(preset) shouldBe emptyList()
    }
})
