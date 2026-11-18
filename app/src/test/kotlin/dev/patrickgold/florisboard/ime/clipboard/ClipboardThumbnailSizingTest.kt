/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardThumbnailSizingTest {
    @Test
    fun smallImagesDoNotNeedSampling() {
        assertEquals(1, ClipboardThumbnailSizing.sampleSize(200, 100))
        assertEquals(1, ClipboardThumbnailSizing.sampleSize(384, 384))
    }

    @Test
    fun wideAndTallPhotosAreBoundedEqually() {
        assertEquals(16, ClipboardThumbnailSizing.sampleSize(4032, 3024))
        assertEquals(16, ClipboardThumbnailSizing.sampleSize(3024, 4032))
        assertEquals(2, ClipboardThumbnailSizing.sampleSize(385, 200))
    }

    @Test
    fun invalidImageBoundsDoNotCauseDivisionErrors() {
        assertEquals(1, ClipboardThumbnailSizing.sampleSize(-1, -1))
        assertEquals(1, ClipboardThumbnailSizing.sampleSize(0, 4000))
    }

    @Test
    fun extremeDimensionsCannotOverflowSampleSize() {
        val sample = ClipboardThumbnailSizing.sampleSize(Int.MAX_VALUE, Int.MAX_VALUE)
        assertTrue(sample > 0)
        assertTrue(Int.MAX_VALUE / sample <= ClipboardThumbnailSizing.MAX_DIMENSION_PX)
    }
}
