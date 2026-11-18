/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.text.dictation

import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteOperation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class VoiceAutoCleanupTest : FunSpec({
    test("automatic voice cleanup remains off by default and with unknown preferences") {
        VoiceAutoCleanupMode.fromPreference("") shouldBe VoiceAutoCleanupMode.OFF
        VoiceAutoCleanupMode.fromPreference("unknown") shouldBe VoiceAutoCleanupMode.OFF
    }
    test("off does not call text-model provider") {
        var calls = 0
        val processor = VoiceAutoCleanupProcessor(object : VoiceRewriteOperation {
            override suspend fun rewrite(sourceText: String, instruction: String): Result<String> {
                calls++
                return Result.success("changed")
            }
        })
        processor.process("My exact words", VoiceAutoCleanupMode.OFF).getOrThrow() shouldBe "My exact words"
        calls shouldBe 0
    }
    test("natural mode sends instructions to preserve the speaker's uncertainty") {
        var capturedInstruction = ""
        val processor = VoiceAutoCleanupProcessor(object : VoiceRewriteOperation {
            override suspend fun rewrite(sourceText: String, instruction: String): Result<String> {
                capturedInstruction = instruction
                return Result.success("I'll probably call a taxi home.")
            }
        })
        processor.process("and uh I'll probably call a taxi home", VoiceAutoCleanupMode.NATURAL).getOrThrow() shouldBe
            "I'll probably call a taxi home."
        capturedInstruction.contains("uncertainty") shouldBe true
        capturedInstruction.contains("natural tone") shouldBe true
    }
    test("concise mode preserves raw input when model returns empty text") {
        val processor = VoiceAutoCleanupProcessor(object : VoiceRewriteOperation {
            override suspend fun rewrite(sourceText: String, instruction: String): Result<String> =
                Result.success("  ")
        })
        processor.process("This should stay", VoiceAutoCleanupMode.CONCISE).getOrThrow() shouldBe "This should stay"
    }
    test("rewrite failure does not turn raw transcript into a fabricated success") {
        val processor = VoiceAutoCleanupProcessor(object : VoiceRewriteOperation {
            override suspend fun rewrite(sourceText: String, instruction: String): Result<String> =
                Result.failure(IllegalStateException("HTTP 429"))
        })
        processor.process("Original text", VoiceAutoCleanupMode.NATURAL).isFailure shouldBe true
    }
    test("oversized raw text never reaches the provider") {
        var called = false
        val processor = VoiceAutoCleanupProcessor(object : VoiceRewriteOperation {
            override suspend fun rewrite(sourceText: String, instruction: String): Result<String> {
                called = true
                return Result.success("oops")
            }
        })
        processor.process("x".repeat(VoiceAutoCleanupPolicy.MAX_CHARS + 1), VoiceAutoCleanupMode.CONCISE).isFailure shouldBe true
        called shouldBe false
    }
})
