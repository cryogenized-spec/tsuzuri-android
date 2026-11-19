/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.keyboard

import androidx.compose.ui.unit.dp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CompactSmartbarSizingTest : FunSpec({
    test("extended toolbar has a 48dp touch row instead of a full 65dp key row") {
        compactSecondaryActionRowHeight(65.dp) shouldBe 48.dp
        compactSecondaryActionRowHeight(60.dp) shouldBe 48.dp
    }

    test("shorter keyboard layouts are not inflated by the toolbar") {
        compactSecondaryActionRowHeight(45.dp) shouldBe 45.dp
        compactSecondaryActionRowHeight(40.dp) shouldBe 40.dp
    }

    test("tall keyboard and orientation adjustments do not make shortcut bar grow") {
        compactSecondaryActionRowHeight(90.dp) shouldBe 48.dp
    }
})
