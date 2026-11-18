/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.clipboard

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ClipboardThumbnailSizingTest : FunSpec({
    test("small images do not need sampling") {
        ClipboardThumbnailSizing.sampleSize(200, 100) shouldBe 1
        ClipboardThumbnailSizing.sampleSize(384, 384) shouldBe 1
    }

    test("wide and tall photos are bounded equally") {
        ClipboardThumbnailSizing.sampleSize(4032, 3024) shouldBe 16
        ClipboardThumbnailSizing.sampleSize(3024, 4032) shouldBe 16
        ClipboardThumbnailSizing.sampleSize(385, 200) shouldBe 2
    }

    test("invalid image bounds do not cause division errors") {
        ClipboardThumbnailSizing.sampleSize(-1, -1) shouldBe 1
        ClipboardThumbnailSizing.sampleSize(0, 4000) shouldBe 1
    }

    test("extreme dimensions do not overflow sample size") {
        val sample = ClipboardThumbnailSizing.sampleSize(Int.MAX_VALUE, Int.MAX_VALUE)
        (sample > 0) shouldBe true
        (Int.MAX_VALUE / sample <= ClipboardThumbnailSizing.MAX_DIMENSION_PX) shouldBe true
    }
})
