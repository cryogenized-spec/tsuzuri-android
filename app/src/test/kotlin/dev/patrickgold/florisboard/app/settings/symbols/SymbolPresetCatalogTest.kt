/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app.settings.symbols

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SymbolPresetCatalogTest : FunSpec({
    test("all presets cover all 26 letters with exactly three valid distinct choices") {
        val letters = ('A'..'Z').map(Char::toString).toSet()
        SymbolPreset.entries.forEach { preset ->
            val assignments = preset.assignments
            assignments.keys shouldBe letters
            assignments.values.forEach { values ->
                values.size shouldBe 3
                values.distinct().size shouldBe 3
                values.forEach { SymbolShortcutStore.isValidSymbol(it) shouldBe true }
            }
        }
    }

    test("built-in QWERTY letter rows retain native ordering") {
        SymbolKeyboardRows.fromLabels(
            listOf(
                listOf("1", "q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                listOf("shift", "z", "x", "c", "v", "b", "n", "m", "enter"),
            ),
        ) shouldBe listOf(
            listOf("Q","W","E","R","T","Y","U","I","O","P"),
            listOf("A","S","D","F","G","H","J","K","L"),
            listOf("Z","X","C","V","B","N","M"),
        )
    }

    test("AZERTY and Dvorak are never silently rearranged into QWERTY") {
        SymbolKeyboardRows.fromLabels(listOf(listOf("a", "z", "e"), listOf("q", "s", "d"))) shouldBe
            listOf(listOf("A", "Z", "E"), listOf("Q", "S", "D"))
        SymbolKeyboardRows.fromLabels(listOf(listOf("a", "o", "e", "u"))) shouldBe
            listOf(listOf("A", "O", "E", "U"))
    }

    test("symbol presets do not alter the A X C V action-key assignment policy") {
        SymbolPreset.entries.forEach { preset ->
            listOf("A","X","C","V").forEach { key ->
                preset.assignments[key]?.size shouldBe 3
            }
        }
    }
})
