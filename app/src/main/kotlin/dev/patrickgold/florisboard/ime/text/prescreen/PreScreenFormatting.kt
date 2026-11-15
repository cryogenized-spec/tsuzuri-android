/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.text.prescreen

/** Markdown syntax depends on the receiving application, not the keyboard. */
enum class PreScreenSyntax { MARKDOWN, WHATSAPP }

enum class PreScreenFormat(val label: String) {
    BOLD("Bold"), ITALIC("Italic"), STRIKE("Strike"),
    INLINE_CODE("Inline code"), CODE_BLOCK("Code block"),
    HEADING("Heading"), QUOTE("Quote")
}

data class PreScreenFormatResult(val text: String, val selectionStart: Int, val selectionEnd: Int)

object PreScreenFormatting {
    /** Formats the selected range, or the word under a collapsed cursor. Never touches the host editor. */
    fun apply(
        source: String,
        selectionStart: Int,
        selectionEnd: Int,
        format: PreScreenFormat,
        syntax: PreScreenSyntax,
    ): PreScreenFormatResult? {
        if (source.length > PreScreenContract.MAX_SOURCE_CHARS ||
            selectionStart !in 0..source.length ||
            selectionEnd !in 0..source.length
        ) return null
        var start = minOf(selectionStart, selectionEnd)
        var end = maxOf(selectionStart, selectionEnd)
        if (start == end) {
            // No selection: only format the word beneath the cursor.
            while (start > 0 && source[start - 1].isLetterOrDigit()) start--
            while (end < source.length && source[end].isLetterOrDigit()) end++
        }
        if (start == end) return null
        // Heading, quote and fenced code are block syntax. Grow a selected
        // word/fragment to whole line boundaries so markers never end up
        // inside an existing line (e.g. "hello # world").
        if (format == PreScreenFormat.HEADING ||
            format == PreScreenFormat.QUOTE || format == PreScreenFormat.CODE_BLOCK
        ) {
            start = source.lastIndexOf('\n', start - 1) + 1
            val lastCovered = if (end > start && source[end - 1] == '\n') end - 1 else end
            val nextNewline = source.indexOf('\n', lastCovered)
            end = if (nextNewline < 0) source.length else nextNewline
        }
        // Avoid cutting an emoji surrogate pair in half.
        if (start > 0 && start < source.length &&
            Character.isHighSurrogate(source[start - 1]) &&
            Character.isLowSurrogate(source[start])) return null
        if (end > 0 && end < source.length &&
            Character.isHighSurrogate(source[end - 1]) &&
            Character.isLowSurrogate(source[end])) return null

        val selected = source.substring(start, end)
        val replacement = when (format) {
            PreScreenFormat.BOLD -> selected.wrap(if (syntax == PreScreenSyntax.WHATSAPP) "*" else "**")
            PreScreenFormat.ITALIC -> selected.wrap(if (syntax == PreScreenSyntax.WHATSAPP) "_" else "*")
            PreScreenFormat.STRIKE -> selected.wrap(if (syntax == PreScreenSyntax.WHATSAPP) "~" else "~~")
            PreScreenFormat.INLINE_CODE -> selected.wrap(backtickFence(selected, 1))
            PreScreenFormat.CODE_BLOCK -> {
                val fence = backtickFence(selected, 3)
                if (syntax == PreScreenSyntax.WHATSAPP) "$fence$selected$fence"
                else "$fence\n$selected\n$fence"
            }
            PreScreenFormat.HEADING -> if (syntax == PreScreenSyntax.WHATSAPP) selected.wrap("*")
                else selected.linePrefix("# ")
            PreScreenFormat.QUOTE -> selected.linePrefix("> ")
        }
        val newText = source.replaceRange(start, end, replacement)
        if (newText.length > PreScreenContract.MAX_SOURCE_CHARS) return null
        return PreScreenFormatResult(newText, start, start + replacement.length)
    }

    private fun String.wrap(mark: String): String = "$mark$this$mark"

    private fun String.linePrefix(prefix: String): String =
        lineSequence().joinToString("\n") { "$prefix$it" }

    /** A backtick run inside the text must not prematurely close its surrounding fence. */
    private fun backtickFence(text: String, minimum: Int): String {
        val longestRun = Regex("`+").findAll(text).maxOfOrNull { it.value.length } ?: 0
        return "`".repeat(maxOf(minimum, longestRun + 1))
    }
}

enum class PreScreenDiffKind { UNCHANGED, ADDED, REMOVED }
data class PreScreenDiffSegment(val text: String, val kind: PreScreenDiffKind)

/** A linear-time, Unicode-safe coarse diff with unchanged text shown in the regular colour. */
object PreScreenDiff {
    fun between(original: String, proposed: String): List<PreScreenDiffSegment> {
        if (original == proposed) return listOf(PreScreenDiffSegment(original, PreScreenDiffKind.UNCHANGED))
        val before = original.codePoints().toArray()
        val after = proposed.codePoints().toArray()
        var prefix = 0
        while (prefix < before.size && prefix < after.size && before[prefix] == after[prefix]) prefix++
        var suffix = 0
        while (suffix < before.size - prefix && suffix < after.size - prefix &&
            before[before.lastIndex - suffix] == after[after.lastIndex - suffix]
        ) suffix++
        fun slice(cps: IntArray, begin: Int, end: Int): String =
            String(cps, begin, end - begin)
        return buildList {
            if (prefix > 0) add(PreScreenDiffSegment(slice(before, 0, prefix), PreScreenDiffKind.UNCHANGED))
            val removed = slice(before, prefix, before.size - suffix)
            val added = slice(after, prefix, after.size - suffix)
            if (removed.isNotEmpty()) add(PreScreenDiffSegment(removed, PreScreenDiffKind.REMOVED))
            if (added.isNotEmpty()) add(PreScreenDiffSegment(added, PreScreenDiffKind.ADDED))
            if (suffix > 0) add(PreScreenDiffSegment(slice(after, after.size - suffix, after.size), PreScreenDiffKind.UNCHANGED))
        }
    }
}
