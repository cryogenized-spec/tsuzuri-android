/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.text.prescreen

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PreScreenContractTest : FunSpec({
    val good = """
        {
          "schema_version":1,
          "revised_text":"Hello **world**",
          "follow_up":"Do you want a warmer version?",
          "suggestions":[
            {"id":"warm","label":"Make it warmer","instruction":"Use a warmer tone","kind":"tone"},
            {"id":"short","label":"Make concise","instruction":"Shorten the message","kind":"length"},
            {"id":"titles","label":"Format headings","instruction":"Format headings in Markdown","kind":"format"}
          ]
        }
    """.trimIndent()

    test("accept a complete versioned data-only response") {
        val result = PreScreenContract.parse(good, 3).getOrThrow()
        result.revisedText shouldBe "Hello **world**"
        result.suggestions.size shouldBe 3
        result.suggestions[0].kind shouldBe PreScreenKind.TONE
    }
    test("reject AI-created UI descriptions and unexpected fields") {
        PreScreenContract.parse(good.replace(
            "\"follow_up\":", "\"button_callback\":\"execute\",\"follow_up\":"
        ), 3).isFailure shouldBe true
    }
    test("reject wrong version, suggestion count and duplicate ids") {
        PreScreenContract.parse(good.replace("\"schema_version\":1", "\"schema_version\":2"), 3)
            .isFailure shouldBe true
        PreScreenContract.parse(good, 5).isFailure shouldBe true
        PreScreenContract.parse(good.replace("\"short\"", "\"warm\""), 3).isFailure shouldBe true
    }
    test("reject fabricated kinds, oversized model text and formatting fences") {
        PreScreenContract.parse(good.replace("\"tone\"", "\"run_script\""), 3)
            .isFailure shouldBe true
        PreScreenContract.parse("```json\n$good\n```", 3).isFailure shouldBe true
        PreScreenContract.parse(good + " ".repeat(15_001), 3).isFailure shouldBe true
    }
    test("never store message text as a learned preference") {
        val raw = PreScreenPreferenceMemory.approved("{}", PreScreenKind.TONE)
        raw.contains("tone") shouldBe true
        raw.contains("Hello") shouldBe false
        PreScreenPreferenceMemory.decode(raw)[PreScreenKind.TONE] shouldBe 1
        val doubled = PreScreenPreferenceMemory.approved(raw, PreScreenKind.TONE)
        PreScreenPreferenceMemory.ranked(doubled).first() shouldBe PreScreenKind.TONE
    }
    test("learned preferences rank existing options; model cannot add controls") {
        val proposals = PreScreenContract.parse(good, 3).getOrThrow()
        val prefs = PreScreenPreferenceMemory.approved("{}", PreScreenKind.FORMAT)
        PreScreenPreferenceMemory.rankActions(proposals.suggestions, prefs).first().id shouldBe "titles"
    }
    test("Markdown selection and WhatsApp selection use different output adapters") {
        PreScreenFormatting.apply(
            "write hello today", 6, 11, PreScreenFormat.BOLD, PreScreenSyntax.MARKDOWN
        )?.text shouldBe "write **hello** today"
        PreScreenFormatting.apply(
            "write hello today", 6, 11, PreScreenFormat.BOLD, PreScreenSyntax.WHATSAPP
        )?.text shouldBe "write *hello* today"
    }
    test("cursor under a word and code fence escaping are handled locally") {
        PreScreenFormatting.apply(
            "hello world", 2, 2, PreScreenFormat.INLINE_CODE, PreScreenSyntax.MARKDOWN
        )?.text shouldBe "`hello` world"
        val result = PreScreenFormatting.apply(
            "a ``` b", 2, 5, PreScreenFormat.CODE_BLOCK, PreScreenSyntax.MARKDOWN
        )?.text.orEmpty()
        result.contains("````") shouldBe true
    }
    test("invalid or empty selections never alter the draft") {
        PreScreenFormatting.apply(
            "hello", -1, 2, PreScreenFormat.BOLD, PreScreenSyntax.MARKDOWN
        ) shouldBe null
        PreScreenFormatting.apply(
            "   ", 1, 1, PreScreenFormat.BOLD, PreScreenSyntax.MARKDOWN
        ) shouldBe null
    }
    test("emoji are diffed by code point, never by half a surrogate pair") {
        val diff = PreScreenDiff.between("Hi 🐻 now", "Hi 🐱 now")
        diff.map { it.kind } shouldBe listOf(
            PreScreenDiffKind.UNCHANGED, PreScreenDiffKind.REMOVED,
            PreScreenDiffKind.ADDED, PreScreenDiffKind.UNCHANGED,
        )
        diff[1].text shouldBe "🐻"
        diff[2].text shouldBe "🐱"
    }
})
