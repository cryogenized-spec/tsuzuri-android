/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.media.stickers

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class StickerRulesTest : FunSpec({
    test("accept only static formats handled by the local sticker converter") {
        StickerRules.supportedMime("image/png") shouldBe true
        StickerRules.supportedMime("IMAGE/JPEG") shouldBe true
        StickerRules.supportedMime("image/webp") shouldBe true
        StickerRules.supportedMime("image/gif") shouldBe false
        StickerRules.supportedMime("image/svg+xml") shouldBe false
        StickerRules.supportedMime("application/octet-stream") shouldBe false
        StickerRules.supportedMime(null) shouldBe false
    }

    test("private sticker IDs can never contain path traversal segments") {
        StickerRules.validId("default") shouldBe true
        StickerRules.validId("0123456789abcdef0123456789abcdef") shouldBe true
        StickerRules.validId("../other") shouldBe false
        StickerRules.validId("../../backup") shouldBe false
        StickerRules.validId("a".repeat(33)) shouldBe false
        StickerRules.validId("ABCDEF0123456789abcdef0123456789") shouldBe false
    }

    test("pack names are short printable labels") {
        StickerRules.validPackName("Cats") shouldBe true
        StickerRules.validPackName("Special symbols ✨") shouldBe true
        StickerRules.validPackName("   ") shouldBe false
        StickerRules.validPackName("x".repeat(33)) shouldBe false
        StickerRules.validPackName("cats\nprivate") shouldBe false
    }

    test("all eight EXIF orientations including reflections are normalized") {
        StickerRules.exifTransform(1) shouldBe StickerTransform()
        StickerRules.exifTransform(2) shouldBe StickerTransform(flipHorizontal = true)
        StickerRules.exifTransform(3) shouldBe StickerTransform(rotation = 180)
        StickerRules.exifTransform(4) shouldBe StickerTransform(flipVertical = true)
        StickerRules.exifTransform(5) shouldBe StickerTransform(
            rotation = 90, flipHorizontal = true,
        )
        StickerRules.exifTransform(6) shouldBe StickerTransform(rotation = 90)
        StickerRules.exifTransform(7) shouldBe StickerTransform(
            rotation = -90, flipHorizontal = true,
        )
        StickerRules.exifTransform(8) shouldBe StickerTransform(rotation = -90)
        StickerRules.exifTransform(100) shouldBe StickerTransform()
    }

    test("import and storage caps remain explicitly bounded") {
        StickerRules.MAX_BYTES shouldBe 8 * 1024 * 1024
        StickerRules.TARGET_SIZE shouldBe 512
        (StickerRules.MAX_PACKS <= 20) shouldBe true
        (StickerRules.MAX_STICKERS <= 500) shouldBe true
    }
})
