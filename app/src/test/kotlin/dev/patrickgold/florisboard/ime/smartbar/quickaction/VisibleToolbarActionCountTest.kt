/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.smartbar.quickaction

import androidx.compose.ui.unit.dp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class VisibleToolbarActionCountTest : FunSpec({
    test("393dp glass keyboard retains seven 48dp action slots after theme margins") {
        // Glass/Signal themes: 10dp outer margin + 16dp padding per side.
        val contentWidth = 393.dp - (10.dp + 16.dp) * 2
        contentWidth shouldBe 341.dp
        val actions = visibleToolbarActionCount(contentWidth, 48.dp, true, 6)
        actions shouldBe 6
        // Six tools plus overflow consume 336dp; the last button never shrinks to 17dp.
        (contentWidth - 48.dp * (actions + 1)) shouldBe 5.dp
    }

    test("360dp narrow phone reserves a complete overflow slot and reduces only visible actions") {
        val contentWidth = 360.dp - (10.dp + 16.dp) * 2
        visibleToolbarActionCount(contentWidth, 48.dp, true, 6) shouldBe 5
        (contentWidth - 48.dp * 6) shouldBe 20.dp
    }

    test("extra large screens retain a six-shortcut clutter limit") {
        visibleToolbarActionCount(1000.dp, 48.dp, true, 6) shouldBe 6
    }

    test("shared row without overflow may fill its full content width") {
        visibleToolbarActionCount(250.dp, 50.dp, false, Int.MAX_VALUE) shouldBe 5
    }

    test("nonpositive bounds fail closed, without division by zero") {
        visibleToolbarActionCount(0.dp, 48.dp, true, 6) shouldBe 0
        visibleToolbarActionCount(100.dp, 0.dp, true, 6) shouldBe 0
        visibleToolbarActionCount(12.dp, 48.dp, true, 6) shouldBe 0
    }
})
