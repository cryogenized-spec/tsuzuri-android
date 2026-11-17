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
 * unusually crowded/custom rows: their existing geometry is preserved.
 */
internal object FullSpacebarPolicy {
    const val PREFERRED_WIDTH_FACTOR = 4.6f

    fun shouldWiden(mode: KeyboardMode, isSplit: Boolean, activeKeyCodes: List<Int>): Boolean {
        if (mode != KeyboardMode.CHARACTERS || isSplit) return false
        if (activeKeyCodes.size !in 3..8) return false
        return activeKeyCodes.count { it == KeyCode.SPACE || it == KeyCode.CJK_SPACE } == 1
    }
}
