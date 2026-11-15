/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.text.keyboard

import dev.patrickgold.florisboard.ime.text.key.KeyVariation

/**
 * Regular-language editing shortcuts, offered after the locale's accents.
 * No shortcuts in passwords, email addresses or URLs. Disabled by preference
 * or when the target isn't a text editor.
 */
internal object TextEditingShortcuts {
    fun dataFor(
        letter: String,
        variation: KeyVariation,
        isRichTextEditor: Boolean,
        enabled: Boolean,
        hasSelection: Boolean,
    ): TextKeyData? {
        if (!enabled || !isRichTextEditor || variation != KeyVariation.NORMAL) return null
        // Cut and Copy must not be offered on a collapsed cursor. Select All
        // and Paste remain available even without a current selection.
        if (!hasSelection && letter.lowercase() in setOf("x", "c")) return null
        return when (letter.lowercase()) {
            "a" -> TextKeyData.CLIPBOARD_SELECT_ALL
            "x" -> TextKeyData.CLIPBOARD_CUT
            "c" -> TextKeyData.CLIPBOARD_COPY
            "v" -> TextKeyData.CLIPBOARD_PASTE
            else -> null
        }
    }
}
