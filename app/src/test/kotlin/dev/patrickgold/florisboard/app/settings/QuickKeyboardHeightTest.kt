/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app.settings

import dev.patrickgold.florisboard.ime.window.ImeFormFactor
import dev.patrickgold.florisboard.ime.window.ImeWindowConfigByType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class QuickKeyboardHeightTest : FunSpec({
    test("default height is 100 percent and can be adjusted without affecting other form factors") {
        val empty: ImeWindowConfigByType = emptyMap()
        QuickKeyboardHeight.shown(empty, 393, 852) shouldBe 100
        val tall = QuickKeyboardHeight.update(empty, 393, 852, 120)
        tall.containsKey(ImeFormFactor.Type.PHONE_PORTRAIT) shouldBe true
        tall.containsKey(ImeFormFactor.Type.TABLET_PORTRAIT) shouldBe false
        (QuickKeyboardHeight.shown(tall, 393, 852) >= 100) shouldBe true
    }
    test("out-of-range input is clamped to allowed slider bounds") {
        val empty: ImeWindowConfigByType = emptyMap()
        val short = QuickKeyboardHeight.update(empty, 393, 852, -250)
        val tooTall = QuickKeyboardHeight.update(empty, 393, 852, 300)
        QuickKeyboardHeight.shown(short, 393, 852) shouldBe QuickKeyboardHeight.MIN_PERCENT
        (QuickKeyboardHeight.shown(tooTall, 393, 852) <= QuickKeyboardHeight.MAX_PERCENT) shouldBe true
    }
})
