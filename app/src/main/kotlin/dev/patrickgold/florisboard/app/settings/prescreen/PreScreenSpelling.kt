/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app.settings.prescreen

/** Character offsets are UTF-16 indices, matching Android TextFieldValue. */
internal data class PreScreenWord(val start: Int, val end: Int, val text: String)

internal data class PreScreenSpellingIssue(
    val word: PreScreenWord,
    val suggestions: List<String>,
)

/** A bounded, deterministic tokenizer: spellchecks local prose, not URLs or inline code. */
internal object PreScreenWordScanner {
    private val words = Regex("""[\p{L}\p{M}]+(?:['’][\p{L}\p{M}]+)*""")
    private const val MAX_WORD_LENGTH = 38
    private const val MAX_SCANNED_WORDS = 48

    fun completed(text: String): List<PreScreenWord> {
        if (text.isEmpty()) return emptyList()
        val fenced = mutableListOf<IntRange>()
        var insideBackticks = false
        var runStart = 0
        // Simple Markdown-code exclusion: both inline and fenced backticks are excluded.
        for (i in text.indices) {
            if (text[i] == '`') {
                if (!insideBackticks) runStart = i
                else fenced.add(runStart..i)
                insideBackticks = !insideBackticks
            }
        }
        if (insideBackticks) fenced.add(runStart until text.length)
        return words.findAll(text).mapNotNull { match ->
            val start = match.range.first
            val end = match.range.last + 1
            val word = match.value
            if (end == text.length || word.length !in 2..MAX_WORD_LENGTH ||
                fenced.any { start in it || end - 1 in it } ||
                (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] == '@')) ||
                (end < text.length && text[end].isLetterOrDigit()) ||
                // Skip URLs, addresses, program symbols and technical identifiers.
                text.substring(
                    text.lastIndexOfAny(charArrayOf(' ', '\n', '\t'), start - 1).plus(1), end,
                ).let { it.contains("://") || it.contains('@') || it.contains('_') }
            ) null else PreScreenWord(start, end, word)
        }.toList().takeLast(MAX_SCANNED_WORDS)
    }

    fun atCursor(words: List<PreScreenWord>, cursor: Int): PreScreenWord? =
        words.firstOrNull { cursor in it.start..it.end }

    fun canReplace(text: String, current: PreScreenWord, selectionStart: Int, selectionEnd: Int): Boolean {
        if (current.start < 0 || current.end > text.length || current.start >= current.end) return false
        if (text.substring(current.start, current.end) != current.text) return false
        val low = minOf(selectionStart, selectionEnd)
        val high = maxOf(selectionStart, selectionEnd)
        return low >= current.start && high <= current.end
    }
}
