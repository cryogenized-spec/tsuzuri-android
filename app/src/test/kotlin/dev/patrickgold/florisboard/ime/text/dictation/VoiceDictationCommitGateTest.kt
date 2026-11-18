/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.text.dictation

import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteOperation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking

class VoiceDictationCommitGateTest : FunSpec({
    fun valid(start: Long = 42, current: Long = 42, lease: Boolean = true, allowed: Boolean = true) =
        VoiceDictationCommitGate.mayCommit(start, current, lease, allowed)

    test("no active editor and insecure or stale state must fail closed") {
        valid(start = 0, current = 0) shouldBe false
        valid(current = 43) shouldBe false // different host app/editor
        valid(lease = false) shouldBe false // cancelled or replaced recording
        valid(allowed = false) shouldBe false // incognito or password
        valid() shouldBe true
    }

    test("host editor change during a delayed model request must prevent insertion") {
        runBlocking {
            val entered = CompletableDeferred<Unit>()
            val resume = CompletableDeferred<Unit>()
            var editor = 42L
            val processor = VoiceAutoCleanupProcessor(object : VoiceRewriteOperation {
                override suspend fun rewrite(sourceText: String, instruction: String): Result<String> {
                    entered.complete(Unit)
                    resume.await()
                    return Result.success("Cleaned transcript")
                }
            })
            val suspended = async {
                val result = processor.process("raw", VoiceAutoCleanupMode.NATURAL)
                result.getOrThrow() to valid(start = 42, current = editor)
            }
            entered.await()
            editor = 43
            resume.complete(Unit)
            suspended.await() shouldBe ("Cleaned transcript" to false)
        }
    }

    test("entering incognito while cleanup is suspended rejects the result") {
        runBlocking {
            val entered = CompletableDeferred<Unit>()
            val resume = CompletableDeferred<Unit>()
            var aiAllowed = true
            val processor = VoiceAutoCleanupProcessor(object : VoiceRewriteOperation {
                override suspend fun rewrite(sourceText: String, instruction: String): Result<String> {
                    entered.complete(Unit)
                    resume.await()
                    return Result.success("Cleaned")
                }
            })
            val suspended = async {
                processor.process("raw", VoiceAutoCleanupMode.CONCISE).getOrThrow()
                valid(allowed = aiAllowed)
            }
            entered.await()
            aiAllowed = false
            resume.complete(Unit)
            suspended.await() shouldBe false
        }
    }

    test("cancelled model call cannot later produce a committed text result") {
        runBlocking {
            val entered = CompletableDeferred<Unit>()
            val forever = CompletableDeferred<Unit>()
            val processor = VoiceAutoCleanupProcessor(object : VoiceRewriteOperation {
                override suspend fun rewrite(sourceText: String, instruction: String): Result<String> {
                    entered.complete(Unit)
                    forever.await()
                    return Result.success("Should never be inserted")
                }
            })
            val job = async { processor.process("raw", VoiceAutoCleanupMode.NATURAL) }
            entered.await()
            job.cancelAndJoin()
            job.isCancelled shouldBe true
        }
    }
})
