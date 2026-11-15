/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app.settings.symbols

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SymbolAssignmentContractTest : FunSpec({
    val good = """
        {
          "schema_version":1,
          "summary":"New mathematical symbol on Q and skull on X",
          "changes":{"Q":["∑","§"],"X":["U+2620 U+FE0F"]},
          "recommendations":[{"symbol":"♜","label":"Chess rook"}]
        }
    """.trimIndent()

    test("accept valid multi-key proposal without applying it") {
        val result = SymbolAssignmentContract.parse(good).getOrThrow()
        result.changes["Q"] shouldBe listOf("∑", "§")
        result.changes["X"] shouldBe listOf("☠️")
        result.recommendations.single().symbol shouldBe "♜"
    }
    test("allow recommendation-only requests") {
        SymbolAssignmentContract.parse(
            good.replace(
                "\"changes\":{\"Q\":[\"∑\",\"§\"],\"X\":[\"U+2620 U+FE0F\"]}",
                "\"changes\":{}",
            )
        ).isSuccess shouldBe true
    }
    test("reject model invented actions and extra metadata") {
        SymbolAssignmentContract.parse(
            good.replace("\"summary\":", "\"execute\":\"delete\",\"summary\":"),
        ).isFailure shouldBe true
        SymbolAssignmentContract.parse(good.replace("\"Q\"", "\"Alt\"")).isFailure shouldBe true
    }
    test("reject too many slots, empty response and illegal Unicode") {
        val overflowing = good.replace(
            "[\"∑\",\"§\"]", "[\"∑\",\"§\",\"€\",\"£\",\"¥\",\"✓\",\"✕\"]"
        )
        SymbolAssignmentContract.parse(overflowing).isFailure shouldBe true
        SymbolAssignmentContract.parse(good.replace("∑", "U+D800")).isFailure shouldBe true
        SymbolAssignmentContract.parse(
            """{"schema_version":1,"summary":"Empty","changes":{},"recommendations":[]}"""
        ).isFailure shouldBe true
    }
    test("reject Markdown fenced or concatenated model responses") {
        SymbolAssignmentContract.parse("```json\n$good\n```").isFailure shouldBe true
        SymbolAssignmentContract.parse(good + "{}").isFailure shouldBe true
    }
    test("safe JSON response preserves the original keyboard until explicit approved write") {
        val before = SymbolShortcutStore.forKey("Q")
        SymbolAssignmentContract.parse(good).getOrThrow()
        SymbolShortcutStore.forKey("Q") shouldBe before
    }
})
