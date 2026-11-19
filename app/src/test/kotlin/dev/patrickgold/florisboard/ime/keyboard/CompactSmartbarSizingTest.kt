/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.keyboard

import androidx.compose.ui.unit.dp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CompactSmartbarSizingTest : FunSpec({
    test("themed 3dp vertical margins leave a 48dp tappable row") {
        compactSecondaryActionRowHeight(65.dp) shouldBe 54.dp
        compactSecondaryActionRowHeight(60.dp) shouldBe 54.dp
        // Content after Signal/glass themes subtract 3dp at top and bottom.
        (compactSecondaryActionRowHeight(65.dp) - 6.dp) shouldBe SecondaryToolbarContentTouchHeight
        // At the supported 200% toolbar icon scale, 24dp icons use 48dp.
        SecondaryToolbarContentTouchHeight shouldBe 24.dp * 2
    }

    test("shorter keyboard layouts are not inflated by the toolbar") {
        compactSecondaryActionRowHeight(45.dp) shouldBe 45.dp
        compactSecondaryActionRowHeight(40.dp) shouldBe 40.dp
    }

    test("tall keyboard and orientation adjustments do not make shortcut bar grow") {
        compactSecondaryActionRowHeight(90.dp) shouldBe 54.dp
    }
})
