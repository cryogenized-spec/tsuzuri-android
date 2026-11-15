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


    test("six slots and Unicode sequences, with no hidden bidirectional controls") {
        SymbolShortcutStore.MAX_SHORTCUTS_PER_KEY shouldBe 6
        SymbolShortcutStore.decodeUserInput("U+2764 U+FE0F") shouldBe "❤️"
        SymbolShortcutStore.decodeUserInput("U+1F468 U+200D U+1F4BB") shouldBe "👨‍💻"
        SymbolShortcutStore.decodeUserInput("a\u202E") shouldBe null
        SymbolShortcutStore.decodeUserInput("a\u200B") shouldBe null
    }

    test("accent toggles do not delete user-assigned accented Unicode") {
        val base = listOf("é", "è", "£", "1")
        val custom = listOf("∑", "é")
        SymbolPopupArrangement.arrange(
            "E", base, custom, SymbolPopupOptions(showAccents = false), { it },
        ) shouldBe listOf("£", "1", "∑", "é")
    }

    test("custom-first and accent-first are deterministically ordered") {
        val base = listOf("1", "é", "£", "è")
        val custom = listOf("∑", "🐻")
        SymbolPopupArrangement.arrange(
            "E", base, custom,
            SymbolPopupOptions(priority = SymbolPopupPriority.CUSTOM_FIRST), { it },
        ) shouldBe listOf("∑", "🐻", "1", "é", "£", "è")
        SymbolPopupArrangement.arrange(
            "E", base, custom,
            SymbolPopupOptions(priority = SymbolPopupPriority.ACCENTS_FIRST), { it },
        ) shouldBe listOf("é", "è", "1", "£", "∑", "🐻")
    }

    test("approved U+ shaped text is stored verbatim, never decoded a second time") {
        val rendered = SymbolShortcutStore.decodeUserInput("U+0055 U+002B U+0034 U+0031")
        rendered shouldBe "U+41"
        SymbolShortcutStore.validatedApprovedSymbols(listOf(rendered.orEmpty())) shouldBe listOf("U+41")
        SymbolShortcutStore.validatedApprovedSymbols(listOf("hello\nworld")) shouldBe null
    }

    test("default preserves inherited popup ordering") {
        SymbolPopupArrangement.arrange(
            "Q", listOf("1", "@"), listOf("☠️"),
            SymbolPopupOptions(), { it },
        ) shouldBe listOf("1", "@", "☠️")
    }

    test("the picker offers a small offline catalogue in clearly named groups") {
        (SymbolCategory.entries.size >= 5) shouldBe true
        SymbolCategory.EMOJI.symbols.contains("🐻") shouldBe true
        SymbolCategory.MATHEMATICS.symbols.contains("∑") shouldBe true
    }
})
