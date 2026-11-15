/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.text.dictation

import dev.patrickgold.florisboard.ime.text.rewrite.VoiceRewriteOperation

/**
 * AI cleanup is an independent second request after transcription, never part of recording.
 * Off is the only default. The user explicitly opts in after reading its privacy/cost notice.
 */
enum class VoiceAutoCleanupMode(val preference: String, val label: String) {
    OFF("off", "Off — exact transcription"),
    NATURAL("natural", "Natural — preserve my tone"),
    CONCISE("concise", "Concise — get to the point");

    companion object {
        fun fromPreference(value: String): VoiceAutoCleanupMode =
            entries.firstOrNull { it.preference == value } ?: OFF
    }

    val instruction: String
        get() = when (this) {
            OFF -> ""
            NATURAL ->
                "Clean up this spoken transcript into readable text. Remove meaningless filler words, " +
                "stutters, false starts and unnecessary repetition; repair punctuation and sentence boundaries. " +
                "Keep the speaker's natural tone, deliberate colloquialisms and mixed-language phrases. " +
                "Do not change names, prices, dates, numbers, commitments, negation, uncertainty or meaning. " +
                "Do not invent information, add tasks, send messages or explain your edits. Return only the cleaned text."
            CONCISE ->
                "Turn this spoken transcript into concise, fluent prose in the speaker's own natural tone. " +
                "Remove needless digressions and repetition but preserve every meaningful fact, qualification, " +
                "name, price, date, number, commitment, negation and uncertainty. " +
                "Do not infer intentions, change the language, invent tasks or provide explanations. " +
                "Return only the revised text."
        }
}

/** Keeps the raw text if a separate text-model call fails, times out or produces an empty result. */
object VoiceAutoCleanupPolicy {
    const val MAX_CHARS = 8_192

    fun acceptedText(original: String, rewritten: String?): String {
        val candidate = rewritten?.trim().orEmpty()
        return if (candidate.isBlank() || candidate.length > MAX_CHARS) original else candidate
    }
}

class VoiceAutoCleanupProcessor(private val rewriter: VoiceRewriteOperation) {
    suspend fun process(transcript: String, mode: VoiceAutoCleanupMode): Result<String> {
        if (mode == VoiceAutoCleanupMode.OFF || transcript.isBlank()) return Result.success(transcript)
        if (transcript.length > VoiceAutoCleanupPolicy.MAX_CHARS) {
            return Result.failure(IllegalArgumentException("Dictation exceeds the automatic cleanup limit"))
        }
        return rewriter.rewrite(transcript, mode.instruction).map { rewritten ->
            VoiceAutoCleanupPolicy.acceptedText(transcript, rewritten)
        }
    }
}
