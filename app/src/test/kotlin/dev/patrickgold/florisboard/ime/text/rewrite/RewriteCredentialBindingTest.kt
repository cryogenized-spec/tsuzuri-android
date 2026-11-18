/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.text.rewrite

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RewriteCredentialBindingTest : FunSpec({
    val openai = "https://api.openai.com/v1/responses"
    val anthropic = "https://api.anthropic.com/v1/messages"

    test("a rewrite credential is never available to a different provider host") {
        val saved = RewriteCredentialBinding.bind(openai, "openai-private-key")
        RewriteCredentialBinding.keyFor(saved, openai) shouldBe "openai-private-key"
        RewriteCredentialBinding.keyFor(saved, anthropic) shouldBe ""
        RewriteCredentialBinding.keyFor(saved, "https://api.mistral.ai/v1/chat/completions") shouldBe ""
    }

    test("same origin with different path is safe, different port is not") {
        val saved = RewriteCredentialBinding.bind("https://api.openai.com:443/v1/responses", "key")
        RewriteCredentialBinding.keyFor(saved, "https://API.OPENAI.COM/v1/chat/completions") shouldBe "key"
        RewriteCredentialBinding.keyFor(saved, "https://api.openai.com:8443/v1/responses") shouldBe ""
    }

    test("unbound legacy keys and malformed records never authorize cleanup") {
        RewriteCredentialBinding.keyFor("old-unbound-key", openai) shouldBe ""
        RewriteCredentialBinding.keyFor(null, openai) shouldBe ""
        RewriteCredentialBinding.keyFor("https://api.openai.com:443\nkey", "http://api.openai.com/v1") shouldBe ""
        RewriteCredentialBinding.keyFor("https://api.openai.com:443\nkey", "https://someone@api.openai.com/v1") shouldBe ""
    }

    test("insecure or incomplete endpoints cannot bind credentials") {
        RewriteCredentialBinding.bind("http://api.openai.com/v1", "key") shouldBe null
        RewriteCredentialBinding.bind("not-a-url", "key") shouldBe null
        RewriteCredentialBinding.bind(openai, "  ") shouldBe null
    }

    test("cleanup never opens a provider request when its key belongs to another origin") {
        val saved = RewriteCredentialBinding.bind(openai, "do-not-leak")
        val client = LlmRewriteClient(
            apiKeyProvider = { error("Unbound credential fallback must never be used") },
            apiKeyForEndpointProvider = { endpoint -> RewriteCredentialBinding.keyFor(saved, endpoint) },
            endpointUrlProvider = { anthropic },
            modelProvider = { "claude-test" },
            providerIdProvider = { LlmRewriteProviders.Anthropic },
        )
        val result = client.rewriteWithVoiceInstruction("original transcript", "Clean up punctuation")
        result.isFailure shouldBe true
        result.exceptionOrNull()?.message?.contains("bound to this rewrite provider") shouldBe true
    }
})
