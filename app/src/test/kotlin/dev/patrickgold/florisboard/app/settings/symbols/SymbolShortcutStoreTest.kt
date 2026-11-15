/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app.settings.symbols

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SymbolShortcutStoreTest : FunSpec({
    test("only one Latin character key is accepted") {
        SymbolShortcutStore.keyOf("e") shouldBe "E"
        SymbolShortcutStore.keyOf(" Q ") shouldBe "Q"
        SymbolShortcutStore.keyOf("Enter") shouldBe null
        SymbolShortcutStore.keyOf("1") shouldBe null
        SymbolShortcutStore.keyOf("") shouldBe null
    }

    test("accept literal unicode, mathematical and supplementary emoji") {
        SymbolShortcutStore.decodeUserInput("é") shouldBe "é"
        SymbolShortcutStore.decodeUserInput("∑") shouldBe "∑"
        SymbolShortcutStore.decodeUserInput("🐻") shouldBe "🐻"
        SymbolShortcutStore.decodeUserInput("U+1F43B") shouldBe "🐻"
        SymbolShortcutStore.decodeUserInput("u+03A3") shouldBe "Σ"
    }

    test("reject forbidden control characters and invalid code points") {
        SymbolShortcutStore.decodeUserInput("U+D800") shouldBe null
        SymbolShortcutStore.decodeUserInput("U+110000") shouldBe null
        SymbolShortcutStore.decodeUserInput("hello\nworld") shouldBe null
        SymbolShortcutStore.decodeUserInput("") shouldBe null
        SymbolShortcutStore.decodeUserInput("U+ZZZZ") shouldBe "U+ZZZZ"
    }

    test("the picker offers a small offline catalogue in clearly named groups") {
        (SymbolCategory.entries.size >= 5) shouldBe true
        SymbolCategory.EMOJI.symbols.contains("🐻") shouldBe true
        SymbolCategory.MATHEMATICS.symbols.contains("∑") shouldBe true
    }
})
