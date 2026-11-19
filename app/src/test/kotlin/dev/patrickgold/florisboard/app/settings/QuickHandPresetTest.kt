/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app.settings

import dev.patrickgold.florisboard.ime.window.ImeFormFactor
import dev.patrickgold.florisboard.ime.window.ImeWindowConfigByType
import dev.patrickgold.florisboard.ime.window.ImeWindowMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class QuickHandPresetTest : FunSpec({
    val portraitWidth = 360
    val portraitHeight = 780

    test("one-handed left/right choices persist across window config reloads") {
        val empty: ImeWindowConfigByType = emptyMap()
        val left = QuickHandPreset.update(empty, portraitWidth, portraitHeight, QuickHandPreset.LEFT)
        QuickHandPreset.of(left, portraitWidth, portraitHeight) shouldBe QuickHandPreset.LEFT
        val right = QuickHandPreset.update(left, portraitWidth, portraitHeight, QuickHandPreset.RIGHT)
        QuickHandPreset.of(right, portraitWidth, portraitHeight) shouldBe QuickHandPreset.RIGHT
        val full = QuickHandPreset.update(right, portraitWidth, portraitHeight, QuickHandPreset.FULL)
        QuickHandPreset.of(full, portraitWidth, portraitHeight) shouldBe QuickHandPreset.FULL
    }

    test("compact mode uses the existing window controller and never alters other form factors") {
        val empty: ImeWindowConfigByType = emptyMap()
        val left = QuickHandPreset.update(empty, portraitWidth, portraitHeight, QuickHandPreset.LEFT)
        val portrait = ImeFormFactor.Type.PHONE_PORTRAIT
        left[portrait]?.mode shouldBe ImeWindowMode.FIXED
        left[portrait]?.fixedMode shouldBe ImeWindowMode.Fixed.COMPACT
        left.containsKey(ImeFormFactor.Type.TABLET_LANDSCAPE) shouldBe false
    }
})
