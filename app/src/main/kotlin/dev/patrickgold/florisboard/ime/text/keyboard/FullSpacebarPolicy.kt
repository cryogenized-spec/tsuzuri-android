/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.text.keyboard

import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.text.key.KeyCode

/**
 * Enlarge one uninterrupted spacebar on an ordinary characters keyboard.
 * Do not apply this policy to the split keyboard, special input modes, or
 * crowded/custom rows: their existing geometry is preserved.
 */
internal object FullSpacebarPolicy {
    const val PREFERRED_WIDTH_FACTOR = 4.6f

    data class Neighbor(val widthFactor: Float, val canShrink: Boolean)

    /**
     * Never force the space key so wide that neighboring punctuation, Enter, or
     * layout-switch keys need to collapse. Non-shrinkable keys retain their width;
     * shrinkable keys keep at least 75% of their width (or 0.75 key units).
     */
    fun safeWidthFactor(
        availableWidthUnits: Float,
        originalWidthFactor: Float,
        neighbors: List<Neighbor>,
    ): Float {
        if (!availableWidthUnits.isFinite() || availableWidthUnits <= 0f) return originalWidthFactor
        val reserved = neighbors.sumOf { neighbor ->
            val minWidth = if (neighbor.canShrink) {
                (neighbor.widthFactor * 0.75f).coerceAtLeast(0.75f).coerceAtMost(neighbor.widthFactor)
            } else {
                neighbor.widthFactor
            }
            minWidth.toDouble()
        }.toFloat()
        val safeMaximum = availableWidthUnits - reserved
        return if (safeMaximum < originalWidthFactor) originalWidthFactor
        else maxOf(originalWidthFactor, PREFERRED_WIDTH_FACTOR).coerceAtMost(safeMaximum)
    }

    fun shouldWiden(mode: KeyboardMode, isSplit: Boolean, activeKeyCodes: List<Int>): Boolean {
        if (mode != KeyboardMode.CHARACTERS || isSplit) return false
        if (activeKeyCodes.size !in 3..6) return false
        return activeKeyCodes.count { it == KeyCode.SPACE || it == KeyCode.CJK_SPACE } == 1
    }
}
