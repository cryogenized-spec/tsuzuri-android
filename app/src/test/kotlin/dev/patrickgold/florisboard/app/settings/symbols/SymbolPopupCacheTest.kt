/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app.settings.symbols

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SymbolPopupCacheTest : FunSpec({
    test("precomputed Unicode keys preserve single and multi-codepoint glyphs") {
        val input = mapOf(
            "A" to listOf("≈", "❤️", "🐈‍⬛"),
            "V" to listOf("✓", "∨", "⇥"),
        )
        val prepared = SymbolShortcutStore.buildPopupCache(input)
        prepared.keys shouldBe input.keys
        prepared["A"]!!.map { it.label } shouldBe input["A"]
        prepared["V"]!!.map { it.label } shouldBe input["V"]
    }

    test("existing accent ordering and A X C V action logic remain unaffected") {
        SymbolShortcutStore.isAccentVariant("E", "é") shouldBe true
        SymbolShortcutStore.isAccentVariant("X", "×") shouldBe false
        val prepared = SymbolShortcutStore.buildPopupCache(mapOf("C" to listOf("©")))
        prepared["C"]!!.first().label shouldBe "©"
    }
})
