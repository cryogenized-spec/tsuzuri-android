/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.popup

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PopupHitIndexTest : FunSpec({
    test("single-row popup selects visible candidate from either edge") {
        popupHitIndex(3f, 72f, 0f, 40f, 40f, 40f, 4, 0, false, 140f) shouldBe 0
        popupHitIndex(158f, 72f, 0f, 40f, 40f, 40f, 4, 0, false, 140f) shouldBe 3
    }

    test("clamped popup uses its actual shifted origin rather than the key's old anchor") {
        popupHitIndex(120f, 72f, 80f, 40f, 40f, 40f, 4, 0, false, 140f) shouldBe 1
        popupHitIndex(238f, 72f, 80f, 40f, 40f, 40f, 4, 0, true, 140f) shouldBe 3
    }

    test("upper short row stays right-aligned when anchored on right") {
        popupHitIndex(95f, 25f, 0f, 20f, 40f, 40f, 3, 2, true, 140f) shouldBe 1
        popupHitIndex(45f, 25f, 0f, 20f, 40f, 40f, 3, 2, true, 140f) shouldBe 0
        popupHitIndex(95f, 75f, 0f, 20f, 40f, 40f, 3, 2, true, 140f) shouldBe 4
    }

    test("two-row left anchor selects top then bottom and preserves finger-down selection") {
        popupHitIndex(10f, 25f, 0f, 20f, 40f, 40f, 3, 2, false, 140f) shouldBe 0
        popupHitIndex(10f, 75f, 0f, 20f, 40f, 40f, 3, 2, false, 140f) shouldBe 2
        popupHitIndex(10f, 126f, 0f, 20f, 40f, 40f, 3, 2, false, 140f) shouldBe 2
    }

    test("pointer outside popup interaction corridor cancels selection") {
        popupHitIndex(-50f, 60f, 0f, 40f, 40f, 40f, 3, 0, false, 140f) shouldBe null
        popupHitIndex(10f, -15f, 0f, 40f, 40f, 40f, 3, 0, false, 140f) shouldBe null
        popupHitIndex(10f, 200f, 0f, 40f, 40f, 40f, 3, 0, false, 140f) shouldBe null
    }

    test("empty or degenerate grids never divide by zero or return a key") {
        popupHitIndex(10f, 70f, 0f, 40f, 0f, 40f, 3, 0, false, 140f) shouldBe null
        popupHitIndex(10f, 70f, 0f, 40f, 40f, 40f, 0, 0, false, 140f) shouldBe null
    }
})
