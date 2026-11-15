/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app.settings

import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.ime.window.ImeFormFactor
import dev.patrickgold.florisboard.ime.window.ImeInsets
import dev.patrickgold.florisboard.ime.window.ImeWindowConfig
import dev.patrickgold.florisboard.ime.window.ImeWindowConfigByType
import dev.patrickgold.florisboard.ime.window.ImeWindowConstraints
import dev.patrickgold.florisboard.ime.window.ImeWindowMode

/**
 * Persistent one-handed presets. Existing ImeWindowController observes the
 * keyboard.windowConfig preference and applies changes without an IME restart.
 * The compact sizes come from the same constraints used by the real keyboard.
 */
internal enum class QuickHandPreset {
    FULL,
    LEFT,
    RIGHT;

    companion object {
        fun formFactor(widthDp: Int, heightDp: Int): ImeFormFactor {
            val rectangle = DpRect(0.dp, 0.dp, widthDp.coerceAtLeast(1).dp, heightDp.coerceAtLeast(1).dp)
            return ImeFormFactor.of(rectangle)
        }

        fun of(config: ImeWindowConfig, root: ImeInsets.Root): QuickHandPreset {
            if (config.mode != ImeWindowMode.FIXED ||
                config.fixedMode != ImeWindowMode.Fixed.COMPACT
            ) return FULL
            val defaults = ImeWindowConstraints.Fixed.Compact(root).defaultProps
            val saved = config.fixedProps[ImeWindowMode.Fixed.COMPACT] ?: defaults
            return if (saved.paddingRight > saved.paddingLeft) LEFT else RIGHT
        }

        fun update(
            existing: ImeWindowConfigByType,
            widthDp: Int,
            heightDp: Int,
            preset: QuickHandPreset,
        ): ImeWindowConfigByType {
            val rect = DpRect(0.dp, 0.dp, widthDp.coerceAtLeast(1).dp, heightDp.coerceAtLeast(1).dp)
            val factor = ImeFormFactor.of(rect)
            val insets = ImeInsets.Root(rect, IntRect.Zero, factor)
            val before = existing[factor.typeGuess] ?: ImeWindowConfig.Default
            val config = if (preset == FULL) {
                before.copy(mode = ImeWindowMode.FIXED, fixedMode = ImeWindowMode.Fixed.NORMAL)
            } else {
                val defaults = ImeWindowConstraints.Fixed.Compact(insets).defaultProps
                val props = if (preset == LEFT) {
                    defaults.copy(paddingLeft = 0.dp, paddingRight = defaults.paddingLeft)
                } else {
                    defaults
                }
                before.copy(
                    mode = ImeWindowMode.FIXED,
                    fixedMode = ImeWindowMode.Fixed.COMPACT,
                    fixedProps = before.fixedProps + (ImeWindowMode.Fixed.COMPACT to props),
                )
            }
            return existing + (factor.typeGuess to config)
        }

        fun of(
            existing: ImeWindowConfigByType,
            widthDp: Int,
            heightDp: Int,
        ): QuickHandPreset {
            val rect = DpRect(0.dp, 0.dp, widthDp.coerceAtLeast(1).dp, heightDp.coerceAtLeast(1).dp)
            val factor = ImeFormFactor.of(rect)
            val insets = ImeInsets.Root(rect, IntRect.Zero, factor)
            return of(existing[factor.typeGuess] ?: ImeWindowConfig.Default, insets)
        }
    }
}
