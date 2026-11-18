/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.clipboard

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipboardPreviewFrameTest {
    private val firstUri = "content://tsuzuri.provider.clipboard/clips/images/123"
    private val secondUri = "content://tsuzuri.provider.clipboard/clips/images/456"

    @Test
    fun recycledTileDoesNotShowPreviouslyLoadedImage() {
        val previouslyLoaded = ClipboardPreviewFrame(firstUri, "picture A")
        assertEquals("picture A", previouslyLoaded.visibleFor(firstUri))
        // Position-based history cells are reused when a clip is replaced.
        // A stale frame must be invisible even before the new decode finishes.
        assertNull(previouslyLoaded.visibleFor(secondUri))
        assertEquals("picture B", ClipboardPreviewFrame(secondUri, "picture B").visibleFor(secondUri))
    }

    @Test
    fun lateDecodeAfterReorderingCannotRevealUnrelatedMedia() = runTest {
        val delayedOldDecode = CompletableDeferred<ClipboardPreviewFrame<String>>()
        var visible: String? = null
        val currentItem = secondUri
        val oldJob = launch {
            visible = delayedOldDecode.await().visibleFor(currentItem)
        }
        // A cancelled producer should never commit its now-superseded result.
        oldJob.cancelAndJoin()
        delayedOldDecode.complete(ClipboardPreviewFrame(firstUri, "picture A"))
        runCurrent()
        assertNull(visible)

        // The URI check also protects against a result that races with the
        // cancellation boundary and reaches the tile after it has been reused.
        val lateOldResult = delayedOldDecode.await()
        assertNull(lateOldResult.visibleFor(currentItem))

        val newResult = ClipboardPreviewFrame(secondUri, "picture B")
        assertEquals("picture B", newResult.visibleFor(currentItem))
    }
}
