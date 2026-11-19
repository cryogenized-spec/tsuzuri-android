/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app.settings

import dev.patrickgold.florisboard.app.settings.theme.SignalThemes
import dev.patrickgold.florisboard.app.settings.theme.ThemeWrites
import dev.patrickgold.florisboard.ime.keyboard.extCoreLayout
import dev.patrickgold.florisboard.ime.theme.ThemeGlassPreset
import dev.patrickgold.florisboard.ime.theme.ThemeMode
import dev.patrickgold.florisboard.ime.theme.extMyTheme
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName

/**
 * Five approachable looks. These point only at themes already bundled with
 * Ownkey/FlorisBoard; no downloads, external theme files, or JSON are required.
 */
internal enum class QuickThemePreset {
    SYSTEM,
    GRAPHITE,
    BLACK,
    BONE,
    GLASS;

    fun writes(): ThemeWrites = when (this) {
        SYSTEM -> ThemeWrites(
            mode = ThemeMode.FOLLOW_SYSTEM,
            dayThemeId = SignalThemes.Bone,
            nightThemeId = SignalThemes.Graphite,
            glassPreset = ThemeGlassPreset.SIGNAL,
        )
        GRAPHITE -> ThemeWrites(
            mode = ThemeMode.ALWAYS_NIGHT,
            nightThemeId = SignalThemes.Graphite,
            glassPreset = ThemeGlassPreset.SIGNAL,
        )
        BLACK -> ThemeWrites(
            mode = ThemeMode.ALWAYS_NIGHT,
            nightThemeId = SignalThemes.Black,
            glassPreset = ThemeGlassPreset.SIGNAL_BLACK,
        )
        BONE -> ThemeWrites(
            mode = ThemeMode.ALWAYS_DAY,
            dayThemeId = SignalThemes.Bone,
        )
        GLASS -> ThemeWrites(
            mode = ThemeMode.ALWAYS_NIGHT,
            nightThemeId = extMyTheme("ownkey_glass_amoled_bordered_medium"),
        )
    }

    companion object {
        fun of(
            mode: ThemeMode,
            day: ExtensionComponentName,
            night: ExtensionComponentName,
        ): QuickThemePreset? = when {
            mode == ThemeMode.FOLLOW_SYSTEM &&
                day == SignalThemes.Bone && night == SignalThemes.Graphite -> SYSTEM
            mode == ThemeMode.ALWAYS_NIGHT && night == SignalThemes.Graphite -> GRAPHITE
            mode == ThemeMode.ALWAYS_NIGHT && night == SignalThemes.Black -> BLACK
            mode == ThemeMode.ALWAYS_DAY && day == SignalThemes.Bone -> BONE
            mode == ThemeMode.ALWAYS_NIGHT &&
                night == extMyTheme("ownkey_glass_amoled_bordered_medium") -> GLASS
            else -> null
        }
    }
}

/** Safe built-in character layouts; the full catalogue stays in Languages. */
internal enum class QuickLayoutPreset(val layoutId: String) {
    QWERTY("qwerty"),
    QWERTZ("qwertz"),
    AZERTY("azerty"),
    DVORAK("dvorak"),
    COLEMAK("colemak"),
    BEPO("bepo");

    val componentId: ExtensionComponentName get() = extCoreLayout(layoutId)

    companion object {
        fun of(id: ExtensionComponentName): QuickLayoutPreset? =
            entries.firstOrNull { it.componentId == id }
    }
}

internal enum class QuickInputDemo { TEXT, NUMBER, PHONE }
