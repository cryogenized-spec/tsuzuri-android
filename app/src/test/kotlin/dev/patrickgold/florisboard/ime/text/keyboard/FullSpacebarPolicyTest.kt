/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.text.keyboard

import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FullSpacebarPolicyTest : FunSpec({
    val normalRow = listOf(KeyCode.VIEW_SYMBOLS, 44, KeyCode.SPACE, 46, KeyCode.ENTER)

    test("standard character row uses one full spacebar") {
        FullSpacebarPolicy.shouldWiden(KeyboardMode.CHARACTERS, false, normalRow) shouldBe true
        FullSpacebarPolicy.PREFERRED_WIDTH_FACTOR shouldBe 4.6f
    }

    test("split keyboard preserves both independent thumb space keys") {
        FullSpacebarPolicy.shouldWiden(KeyboardMode.CHARACTERS, true, normalRow) shouldBe false
        FullSpacebarPolicy.shouldWiden(
            KeyboardMode.CHARACTERS, false,
            listOf(KeyCode.VIEW_SYMBOLS, KeyCode.SPACE, KeyCode.SPACE, KeyCode.ENTER),
        ) shouldBe false
    }

    test("numeric and symbol keyboards retain existing geometry") {
        FullSpacebarPolicy.shouldWiden(KeyboardMode.SYMBOLS, false, normalRow) shouldBe false
        FullSpacebarPolicy.shouldWiden(KeyboardMode.NUMERIC, false, normalRow) shouldBe false
    }

    test("custom crowded or degenerate rows retain existing geometry") {
        FullSpacebarPolicy.shouldWiden(
            KeyboardMode.CHARACTERS, false, List(7) { if (it == 3) KeyCode.SPACE else 65 + it },
        ) shouldBe false
        FullSpacebarPolicy.shouldWiden(
            KeyboardMode.CHARACTERS, false, listOf(KeyCode.SPACE, KeyCode.ENTER),
        ) shouldBe false
    }

    test("CJK space can remain a single full-width key") {
        FullSpacebarPolicy.shouldWiden(
            KeyboardMode.CHARACTERS, false,
            listOf(KeyCode.VIEW_SYMBOLS, KeyCode.CJK_SPACE, KeyCode.ENTER),
        ) shouldBe true
    }
})
