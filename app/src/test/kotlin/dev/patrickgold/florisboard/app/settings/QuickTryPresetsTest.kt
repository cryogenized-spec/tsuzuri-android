/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app.settings

import dev.patrickgold.florisboard.app.settings.theme.SignalThemes
import dev.patrickgold.florisboard.ime.keyboard.extCoreLayout
import dev.patrickgold.florisboard.ime.theme.ThemeMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class QuickTryPresetsTest : FunSpec({
    test("every quick theme preset chooses an actual bundled theme and mode") {
        for (preset in QuickThemePreset.entries) {
            val writes = preset.writes()
            (writes.mode != null) shouldBe true
            (writes.dayThemeId != null || writes.nightThemeId != null) shouldBe true
        }
    }
    test("quick theme restores and detects the stock Graphite and Bone themes") {
        QuickThemePreset.of(
            ThemeMode.FOLLOW_SYSTEM, SignalThemes.Bone, SignalThemes.Graphite,
        ) shouldBe QuickThemePreset.SYSTEM
        QuickThemePreset.of(
            ThemeMode.ALWAYS_DAY, SignalThemes.Bone, SignalThemes.Graphite,
        ) shouldBe QuickThemePreset.BONE
        QuickThemePreset.of(
            ThemeMode.ALWAYS_NIGHT, SignalThemes.Bone, SignalThemes.Black,
        ) shouldBe QuickThemePreset.BLACK
    }
    test("every quick layout uses the corresponding bundled Latin arrangement") {
        QuickLayoutPreset.entries.map { it.layoutId }.distinct().size shouldBe QuickLayoutPreset.entries.size
        for (preset in QuickLayoutPreset.entries) {
            QuickLayoutPreset.of(extCoreLayout(preset.layoutId)) shouldBe preset
        }
        QuickLayoutPreset.of(extCoreLayout("ipa")) shouldBe null
    }
})
