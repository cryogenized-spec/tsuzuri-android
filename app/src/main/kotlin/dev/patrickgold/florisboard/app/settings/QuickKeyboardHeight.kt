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
import kotlin.math.roundToInt

/**
 * Height slider for the active screen size and fixed keyboard mode.
 * Stores actual clamped keyboard height using the existing window controller,
 * not a second measurement system. Other orientations remain unchanged.
 */
internal object QuickKeyboardHeight {
    const val MIN_PERCENT = 70
    const val MAX_PERCENT = 130

    private fun insets(widthDp: Int, heightDp: Int): ImeInsets.Root {
        val rect = DpRect(0.dp, 0.dp, widthDp.coerceAtLeast(1).dp, heightDp.coerceAtLeast(1).dp)
        return ImeInsets.Root(rect, IntRect.Zero, ImeFormFactor.of(rect))
    }

    private fun constraints(
        root: ImeInsets.Root,
        mode: ImeWindowMode.Fixed,
    ): ImeWindowConstraints.Fixed = if (mode == ImeWindowMode.Fixed.COMPACT) {
        ImeWindowConstraints.Fixed.Compact(root)
    } else {
        ImeWindowConstraints.Fixed.Normal(root)
    }

    fun shown(
        configs: ImeWindowConfigByType,
        widthDp: Int,
        heightDp: Int,
    ): Int {
        val root = insets(widthDp, heightDp)
        val config = configs[root.formFactor.typeGuess] ?: ImeWindowConfig.Default
        val mode = if (config.fixedMode == ImeWindowMode.Fixed.COMPACT) {
            ImeWindowMode.Fixed.COMPACT
        } else {
            ImeWindowMode.Fixed.NORMAL
        }
        val geometry = constraints(root, mode)
        val baseline = geometry.defaultProps.keyboardHeight.value
        if (baseline <= 0f) return 100
        val chosen = config.fixedProps[mode]?.keyboardHeight?.value ?: baseline
        return (chosen / baseline * 100f).roundToInt().coerceIn(MIN_PERCENT, MAX_PERCENT)
    }

    fun update(
        configs: ImeWindowConfigByType,
        widthDp: Int,
        heightDp: Int,
        percent: Int,
    ): ImeWindowConfigByType {
        val root = insets(widthDp, heightDp)
        val factor = root.formFactor.typeGuess
        val config = configs[factor] ?: ImeWindowConfig.Default
        val mode = if (config.fixedMode == ImeWindowMode.Fixed.COMPACT) {
            ImeWindowMode.Fixed.COMPACT
        } else {
            ImeWindowMode.Fixed.NORMAL
        }
        val geometry = constraints(root, mode)
        val original = config.fixedProps[mode] ?: geometry.defaultProps
        val chosen = (geometry.defaultProps.keyboardHeight *
            (percent.coerceIn(MIN_PERCENT, MAX_PERCENT) / 100f))
            .coerceIn(geometry.minKeyboardHeight, geometry.maxKeyboardHeight)
        val updated = config.copy(
            fixedProps = config.fixedProps + (mode to original.copy(keyboardHeight = chosen)),
        )
        return configs + (factor to updated)
    }
}
