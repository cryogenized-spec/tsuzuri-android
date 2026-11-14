/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.text.network

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SecureEndpointPolicyTest : FunSpec({
    test("valid HTTPS endpoints are accepted") {
        SecureEndpointPolicy.isAllowed("https://api.openai.com/v1/audio/transcriptions") shouldBe true
        SecureEndpointPolicy.isAllowed(" HTTPS://example.org:443/v1/rewrite ") shouldBe true
        SecureEndpointPolicy.isAllowed("https://[::1]:8443/api") shouldBe true
    }
    test("cleartext, scheme confusion and credential-bearing endpoints are rejected") {
        for (url in listOf(
            "http://api.openai.com/v1/audio/transcriptions",
            "http://localhost:8000/v1/chat",
            "ftp://example.org/api",
            "api.example.org/v1/audio",
            "https://user:pass@example.org/api",
            "https://example.org/#fragment",
            "https://",
            ""
        )) {
            SecureEndpointPolicy.isAllowed(url) shouldBe false
        }
    }
})
