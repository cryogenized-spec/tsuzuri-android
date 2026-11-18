/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.clipboard

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class ClipboardPreviewFrameTest : FunSpec({
    val firstUri = "content://tsuzuri.provider.clipboard/clips/images/123"
    val secondUri = "content://tsuzuri.provider.clipboard/clips/images/456"

    test("recycled tile never displays the previous image") {
        val previous = ClipboardPreviewFrame(firstUri, "picture A")
        previous.visibleFor(firstUri) shouldBe "picture A"
        previous.visibleFor(secondUri) shouldBe null
        ClipboardPreviewFrame(secondUri, "picture B").visibleFor(secondUri) shouldBe "picture B"
    }

    test("late decode and cancellation never reveal unrelated media") {
        runTest {
            val delayedOldDecode = CompletableDeferred<ClipboardPreviewFrame<String>>()
            var visible: String? = null
            val currentItem = secondUri
            val oldJob = launch {
                visible = delayedOldDecode.await().visibleFor(currentItem)
            }
            oldJob.cancelAndJoin()
            delayedOldDecode.complete(ClipboardPreviewFrame(firstUri, "picture A"))
            visible shouldBe null
            // If an old result races with cancellation, the URI check hides it.
            delayedOldDecode.await().visibleFor(currentItem) shouldBe null
            ClipboardPreviewFrame(secondUri, "picture B").visibleFor(currentItem) shouldBe "picture B"
        }
    }
})
