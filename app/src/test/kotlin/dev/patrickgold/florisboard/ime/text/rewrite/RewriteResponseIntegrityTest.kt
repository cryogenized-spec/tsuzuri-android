/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.text.rewrite

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class RewriteResponseIntegrityTest : FunSpec({
    test("Anthropic max_tokens indicates unsafe truncated rewrite") {
        RewriteResponseIntegrity.isComplete(
            Json.parseToJsonElement("""{"stop_reason":"max_tokens","content":[{"type":"text","text":"only beginning"}]}""").jsonObject
        ) shouldBe false
    }
    test("OpenAI chat completions finish_reason length indicates truncation") {
        RewriteResponseIntegrity.isComplete(
            Json.parseToJsonElement("""{"choices":[{"finish_reason":"length","message":{"content":"only beginning"}}]}""").jsonObject
        ) shouldBe false
    }
    test("OpenAI Responses incomplete status indicates truncation") {
        RewriteResponseIntegrity.isComplete(
            Json.parseToJsonElement("""{"status":"incomplete","incomplete_details":{"reason":"max_output_tokens"}}""").jsonObject
        ) shouldBe false
    }
    test("complete responses remain accepted") {
        RewriteResponseIntegrity.isComplete(
            Json.parseToJsonElement("""{"stop_reason":"end_turn","content":[{"type":"text","text":"all the text"}]}""").jsonObject
        ) shouldBe true
        RewriteResponseIntegrity.isComplete(
            Json.parseToJsonElement("""{"choices":[{"finish_reason":"stop","message":{"content":"text"}}]}""").jsonObject
        ) shouldBe true
    }
})
