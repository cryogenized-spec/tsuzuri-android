/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app.settings.voxtral

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RewriteModelSheetTransitionTest : FunSpec({
    test("normal sheet dismissal clears the model picker") {
        onRewriteModelSheetDismiss(AiPanel.RewriteModel) shouldBe null
    }

    test("late dismissal never closes newly opened custom model dialog") {
        val now = AiPanel.RewriteCustomModel
        onRewriteModelSheetDismiss(now) shouldBe now
    }

    test("late dismissal does not close a newly selected provider dialog") {
        val now = AiPanel.RewriteProvider
        onRewriteModelSheetDismiss(now) shouldBe now
    }

    test("late dismissal after another panel is closed keeps it closed") {
        onRewriteModelSheetDismiss(null) shouldBe null
    }
})
