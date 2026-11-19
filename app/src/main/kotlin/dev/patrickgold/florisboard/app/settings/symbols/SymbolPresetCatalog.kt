/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app.settings.symbols

/**
 * Explicitly selectable starter sets. They never run during app start or a
 * keyboard event, and never overwrite user choices without confirmation.
 * Every Latin letter receives exactly three Unicode alternatives.
 */
internal enum class SymbolPreset(val title: String, val description: String) {
    WORK("Functional", "Work, business, currency and mathematics"),
    FUNKY("Funky", "Social, playful and flashy symbols"),
    DEVELOPER("Developer", "Terminal, hacker and synth-style symbols");

    val assignments: Map<String, List<String>> get() = SymbolPresetCatalog.forPreset(this)
}

internal object SymbolPresetCatalog {
    private fun rows(vararg items: String): Map<String, List<String>> {
        require(items.size == 26)
        return items.associate { item ->
            val parts = item.split("|")
            require(parts.size == 4)
            val letter = parts[0]
            val symbols = parts.drop(1)
            require(SymbolShortcutStore.keyOf(letter) == letter &&
                SymbolShortcutStore.validatedApprovedSymbols(symbols) == symbols &&
                symbols.size == 3)
            letter to symbols
        }.also { require(it.size == 26) }
    }

    private val functional = rows(
        "Q|∑|√|∀",
        "W|≥|≤|≠",
        "E|€|∈|∃",
        "R|®|→|⇔",
        "T|™|∴|∵",
        "Y|¥|∝|⊥",
        "U|∪|∩|∅",
        "I|∞|∫|∮",
        "O|°|Ω|⊙",
        "P|π|±|‰",
        "A|≈|∠|∧",
        "S|§|∑|√",
        "D|∆|∂|÷",
        "F|ƒ|∀|≡",
        "G|≥|∇|γ",
        "H|ℏ|½|↔",
        "J|∝|⇐|⇒",
        "K|₭|κ|∴",
        "L|£|λ|≤",
        "Z|ℤ|∴|∵",
        "X|×|⨯|⊗",
        "C|©|¢|℃",
        "V|✓|∨|∩",
        "B|•|β|₿",
        "N|№|∉|≠",
        "M|µ|±|∓",
    )

    private val funky = rows(
        "Q|👑|✨|💫",
        "W|🌊|🪽|💎",
        "E|⚡|👁️|🎧",
        "R|🌹|🔥|🧨",
        "T|🪩|🎵|💃",
        "Y|💛|✨|☀️",
        "U|🦄|🌈|💜",
        "I|🧊|🪐|🌌",
        "O|🌙|💫|⭐",
        "P|💖|🎀|🎉",
        "A|💥|🎯|🚀",
        "S|🌟|🪄|🎮",
        "D|🖤|👽|💀",
        "F|🔥|🤘|🐉",
        "G|💚|🦋|👾",
        "H|❤️|🫶|🥂",
        "J|😂|😎|🥳",
        "K|💋|🐈‍⬛|👻",
        "L|💗|💌|🥰",
        "Z|⚡|🧟|🌀",
        "X|❌|☠️|🕷️",
        "C|✨|🌸|💞",
        "V|💜|🦇|🎆",
        "B|🦋|💙|🎈",
        "N|🌌|🌃|💜",
        "M|🌺|💎|🎶",
    )

    private val developer = rows(
        "Q|⌘|⌥|⇧",
        "W|⌃|⎋|⏎",
        "E|⌫|⇥|⌦",
        "R|⟲|⟳|↻",
        "T|⟡|⌗|⊞",
        "Y|λ|ψ|∴",
        "U|∪|∩|⊻",
        "I|∞|∑|⊨",
        "O|⊕|⊗|⊙",
        "P|π|¶|§",
        "A|∀|∃|⌘",
        "S|⟨|⟩|⌥",
        "D|⌗|∆|∂",
        "F|ƒ|⌥|⌘",
        "G|⎇|⚙|⟡",
        "H|⚡|⌘|⌬",
        "J|⟵|⟶|⇥",
        "K|⎋|⌥|⌘",
        "L|⟦|⟧|λ",
        "Z|⌁|⎈|⎊",
        "X|✕|☒|×",
        "C|©|☑|⌁",
        "V|✓|⌄|▾",
        "B|▣|▤|▥",
        "N|⌘|⊥|∇",
        "M|⌥|⌃|⏻",
    )

    fun forPreset(preset: SymbolPreset): Map<String, List<String>> = when (preset) {
        SymbolPreset.WORK -> functional
        SymbolPreset.FUNKY -> funky
        SymbolPreset.DEVELOPER -> developer
    }
}

/** Keep the letter picker in the same row/position order as the live IME. */
internal object SymbolKeyboardRows {
    fun fromLabels(layoutRows: List<List<String>>): List<List<String>> =
        layoutRows.map { row -> row.mapNotNull(SymbolShortcutStore::keyOf) }
            .filter { it.isNotEmpty() }
}
