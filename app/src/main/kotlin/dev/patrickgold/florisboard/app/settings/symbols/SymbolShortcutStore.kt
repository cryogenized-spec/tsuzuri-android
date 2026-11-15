/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app.settings.symbols

import android.content.Context
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * User-editable long-press Unicode alternatives for A..Z. These are ordinary
 * non-sensitive preferences, not the Lockbox. The IME only reads an immutable
 * in-memory snapshot while computing keys, never storage on a key event.
 */
internal object SymbolShortcutStore {
    private const val PREF_FILE = "tsuzuri_unicode_shortcuts_v1"
    private const val DELIMITER = "\u001F"
    const val MAX_SHORTCUTS_PER_KEY = 4
    private const val MAX_CODEPOINTS_PER_SHORTCUT = 8

    val revision = MutableStateFlow(0)

    @Volatile private var cached: Map<String, List<String>> = emptyMap()
    @Volatile private var loaded = false
    private val storageMutex = Mutex()

    fun keyOf(text: String): String? {
        val value = text.trim().uppercase(Locale.ROOT)
        return value.takeIf { it.length == 1 && it[0] in 'A'..'Z' }
    }

    fun isValidSymbol(text: String): Boolean {
        if (text.isEmpty() || text.length > 24) return false
        val count = text.codePointCount(0, text.length)
        return count in 1..MAX_CODEPOINTS_PER_SHORTCUT &&
            text.none { it == '\u001F' || Character.isISOControl(it) } &&
            text.codePoints().allMatch { Character.isValidCodePoint(it) &&
                (it < 0xD800 || it > 0xDFFF) }
    }

    /** Accepts a literal Unicode symbol or an explicit U+ hexadecimal code point. */
    fun decodeUserInput(input: String): String? {
        val trimmed = input.trim()
        val result = if (trimmed.matches(Regex("(?i)U\\+[0-9A-F]{1,6}"))) {
            val codepoint = trimmed.substring(2).toIntOrNull(16) ?: return null
            if (!Character.isValidCodePoint(codepoint) ||
                codepoint in 0xD800..0xDFFF
            ) return null
            String(Character.toChars(codepoint))
        } else {
            trimmed
        }
        return result.takeIf(::isValidSymbol)
    }

    /**
     * All SharedPreferences reads and commits run under the same coroutine
     * mutex on Dispatchers.IO. The key computation path only reads cached.
     */
    private fun loadLocked(context: Context) {
        if (loaded) return
        val preferences = context.applicationContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        val values = mutableMapOf<String, List<String>>()
        for ((rawKey, anyValue) in preferences.all) {
            val key = keyOf(rawKey) ?: continue
            val value = anyValue as? String ?: continue
            val symbols = value.split(DELIMITER)
                .filter(::isValidSymbol).distinct().take(MAX_SHORTCUTS_PER_KEY)
            if (symbols.isNotEmpty()) values[key] = symbols
        }
        cached = values.toMap()
        loaded = true
        revision.value += 1
    }

    suspend fun ensureLoaded(context: Context) = withContext(Dispatchers.IO) {
        storageMutex.withLock { loadLocked(context) }
    }

    fun forKey(label: String): List<String> {
        val key = keyOf(label) ?: return emptyList()
        return cached[key].orEmpty()
    }

    suspend fun add(context: Context, rawKey: String, rawSymbol: String): Boolean =
        withContext(Dispatchers.IO) {
            val key = keyOf(rawKey) ?: return@withContext false
            val symbol = decodeUserInput(rawSymbol) ?: return@withContext false
            storageMutex.withLock {
                loadLocked(context)
                val current = cached[key].orEmpty()
                if (symbol in current || current.size >= MAX_SHORTCUTS_PER_KEY) {
                    false
                } else {
                    writeLocked(context, key, current + symbol)
                }
            }
        }

    suspend fun remove(context: Context, rawKey: String, symbol: String): Boolean =
        withContext(Dispatchers.IO) {
            val key = keyOf(rawKey) ?: return@withContext false
            storageMutex.withLock {
                loadLocked(context)
                val current = cached[key].orEmpty()
                if (symbol !in current) false else writeLocked(context, key, current - symbol)
            }
        }

    /** Requires storageMutex on Dispatchers.IO; publish only after commit. */
    private fun writeLocked(context: Context, key: String, symbols: List<String>): Boolean {
        val preferences = context.applicationContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        val editor = preferences.edit()
        if (symbols.isEmpty()) editor.remove(key)
        else editor.putString(key, symbols.joinToString(DELIMITER))
        if (!editor.commit()) return false
        cached = cached.toMutableMap().apply {
            if (symbols.isEmpty()) remove(key) else put(key, symbols.toList())
        }.toMap()
        revision.value += 1
        return true
    }
}

/** A compact, offline symbol catalogue. Users can also paste any Unicode symbol. */
internal enum class SymbolCategory(val title: String, val symbols: List<String>) {
    ACCENTS("Accents", listOf("é", "è", "ê", "ë", "ē", "ė", "ę", "á", "à", "â", "ä", "ñ", "ç", "ø")),
    MATHEMATICS("Math", listOf("±", "×", "÷", "≠", "≤", "≥", "≈", "∞", "∑", "√", "π", "∆", "∫", "∂")),
    CURRENCY("Money", listOf("€", "£", "¥", "₹", "₩", "₿", "¢", "₽", "₴", "₪", "₱", "₦")),
    ARROWS("Arrows", listOf("←", "→", "↑", "↓", "↔", "↕", "↗", "↘", "⇐", "⇒", "⇔", "⤴")),
    DECORATION("Symbols", listOf("©", "®", "™", "§", "¶", "•", "★", "☆", "♥", "♦", "♠", "♣", "✓", "✕")),
    EMOJI("Emoji", listOf("🐻", "🐈", "🐕", "🌹", "✨", "🔥", "❤️", "🌙", "🌻", "🍀", "🙂", "😂")),
}
