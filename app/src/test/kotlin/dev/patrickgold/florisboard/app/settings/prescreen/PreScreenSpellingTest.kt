/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app.settings.prescreen

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PreScreenSpellingTest : FunSpec({
    test("only completed words are offered to the offline checker") {
        PreScreenWordScanner.completed("definately ") shouldBe
            listOf(PreScreenWord(0, 10, "definately"))
        PreScreenWordScanner.completed("definately") shouldBe emptyList()
    }
    test("apostrophes and accented letters keep UTF-16 offsets") {
        val parsed = PreScreenWordScanner.completed("🐻 naïve don't ")
        parsed.map { it.text } shouldBe listOf("naïve", "don't")
        parsed[0].start shouldBe 3
        parsed[1].start shouldBe 9
    }
    test("do not spellcheck URL tokens or inline code") {
        val parsed = PreScreenWordScanner.completed("visit https://example.com `teh` please ")
        parsed.map { it.text }.contains("example") shouldBe false
        parsed.map { it.text }.contains("teh") shouldBe false
        parsed.map { it.text }.contains("please") shouldBe true
    }
    test("cursor must still target exactly the same word before accepting correction") {
        val word = PreScreenWord(6, 16, "definately")
        PreScreenWordScanner.canReplace("Hello definately now", word, 9, 9) shouldBe true
        PreScreenWordScanner.canReplace("Hello definately now", word, 17, 17) shouldBe false
        PreScreenWordScanner.canReplace("Hello definitely now", word, 9, 9) shouldBe false
        PreScreenWordScanner.canReplace("Hello definately now", word, 6, 16) shouldBe true
    }
    test("scan is bounded for large pasted drafts") {
        val many = (1..100).joinToString(" ") { "hello" } + " "
        PreScreenWordScanner.completed(many).size shouldBe 48
    }
})
