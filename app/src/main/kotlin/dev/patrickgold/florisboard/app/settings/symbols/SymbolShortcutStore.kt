/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app.settings.symbols

import android.content.Context
import java.text.Normalizer
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
internal enum class SymbolPopupPriority { ORIGINAL, ACCENTS_FIRST, CUSTOM_FIRST }

internal data class SymbolPopupOptions(
    val showAccents: Boolean = true,
    val priority: SymbolPopupPriority = SymbolPopupPriority.ORIGINAL,
)

internal object SymbolShortcutStore {
    private const val PREF_FILE = "tsuzuri_unicode_shortcuts_v1"
    private const val DELIMITER = "\u001F"
    const val MAX_SHORTCUTS_PER_KEY = 6
    private const val PREF_SHOW_ACCENTS = "option__show_accents"
    private const val PREF_POPUP_PRIORITY = "option__popup_priority"
    private const val MAX_CODEPOINTS_PER_SHORTCUT = 8

    val revision = MutableStateFlow(0)

    @Volatile private var cached: Map<String, List<String>> = emptyMap()
    @Volatile private var loaded = false
    @Volatile private var popupOptions = SymbolPopupOptions()
    private val storageMutex = Mutex()

    fun keyOf(text: String): String? {
        val value = text.trim().uppercase(Locale.ROOT)
        return value.takeIf { it.length == 1 && it[0] in 'A'..'Z' }
    }

    fun options(): SymbolPopupOptions = popupOptions

    /** Recognises Unicode Latin diacritic variants of the current letter. */
    fun isAccentVariant(key: String, candidate: String): Boolean {
        val base = keyOf(key)?.lowercase(Locale.ROOT) ?: return false
        val lower = candidate.lowercase(Locale.ROOT)
        if (lower == base || candidate.codePointCount(0, candidate.length) != 1) return false
        val stripped = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(Regex("[\\p{M}]"), "")
        if (stripped == base) return true
        return lower in when (base) {
            "a" -> setOf("æ", "ª")
            "d" -> setOf("ð")
            "i" -> setOf("ı")
            "l" -> setOf("ł")
            "o" -> setOf("ø", "œ", "º")
            "s" -> setOf("ß")
            "t" -> setOf("þ")
            else -> emptySet()
        }
    }

    fun isValidSymbol(text: String): Boolean {
        if (text.isEmpty() || text.length > 24) return false
        val count = text.codePointCount(0, text.length)
        return count in 1..MAX_CODEPOINTS_PER_SHORTCUT &&
            text.none { it == '\u001F' || Character.isISOControl(it) } &&
            text.codePoints().noneMatch {
                it in 0x202A..0x202E || it in 0x2066..0x2069 ||
                    it == 0x200B || it == 0xFEFF
            } &&
            text.codePoints().allMatch { Character.isValidCodePoint(it) &&
                (it < 0xD800 || it > 0xDFFF) }
    }

    /** Accepts a literal Unicode symbol or an explicit U+ hexadecimal code point. */
    fun decodeUserInput(input: String): String? {
        val trimmed = input.trim()
        val codes = trimmed.split(Regex("[,\\s]+")).filter { it.isNotEmpty() }
        val result = if (codes.isNotEmpty() && codes.all { it.matches(Regex("(?i)U\\+[0-9A-F]{1,6}")) }) {
            if (codes.size > MAX_CODEPOINTS_PER_SHORTCUT) return null
            buildString {
                for (code in codes) {
                    val point = code.substring(2).toIntOrNull(16) ?: return null
                    if (!Character.isValidCodePoint(point) || point in 0xD800..0xDFFF) return null
                    appendCodePoint(point)
                }
            }
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
        popupOptions = SymbolPopupOptions(
            showAccents = preferences.getBoolean(PREF_SHOW_ACCENTS, true),
            priority = runCatching {
                SymbolPopupPriority.valueOf(
                    preferences.getString(PREF_POPUP_PRIORITY, SymbolPopupPriority.ORIGINAL.name)
                        ?: SymbolPopupPriority.ORIGINAL.name,
                )
            }.getOrDefault(SymbolPopupPriority.ORIGINAL),
        )
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

    suspend fun setOptions(context: Context, updated: SymbolPopupOptions): Boolean =
        withContext(Dispatchers.IO) {
            storageMutex.withLock {
                loadLocked(context)
                val preferences = context.applicationContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                val saved = preferences.edit()
                    .putBoolean(PREF_SHOW_ACCENTS, updated.showAccents)
                    .putString(PREF_POPUP_PRIORITY, updated.priority.name)
                    .commit()
                if (saved) {
                    popupOptions = updated
                    revision.value += 1
                }
                saved
            }
        }

    /** Changes order without changing the bound Unicode characters. */
    suspend fun move(context: Context, rawKey: String, symbol: String, offset: Int): Boolean =
        withContext(Dispatchers.IO) {
            val key = keyOf(rawKey) ?: return@withContext false
            storageMutex.withLock {
                loadLocked(context)
                val current = cached[key].orEmpty()
                val from = current.indexOf(symbol)
                val to = from + offset
                if (from < 0 || to !in current.indices) false
                else writeLocked(context, key, current.toMutableList().apply {
                    add(to, removeAt(from))
                })
            }
        }

    /** Atomically apply a fully validated AI proposal, only after explicit user approval. */
    suspend fun replaceApproved(context: Context, changes: Map<String, List<String>>): Boolean =
        withContext(Dispatchers.IO) {
            if (changes.isEmpty() || changes.size > 26) return@withContext false
            val verified = LinkedHashMap<String, List<String>>()
            for ((rawKey, rawSymbols) in changes) {
                val key = keyOf(rawKey) ?: return@withContext false
                val symbols = rawSymbols.map { decodeUserInput(it) ?: return@withContext false }
                if (symbols.size > MAX_SHORTCUTS_PER_KEY ||
                    symbols.distinct().size != symbols.size || key in verified) return@withContext false
                verified[key] = symbols
            }
            storageMutex.withLock {
                loadLocked(context)
                val preferences = context.applicationContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
                val editor = preferences.edit()
                for ((key, symbols) in verified) {
                    if (symbols.isEmpty()) editor.remove(key)
                    else editor.putString(key, symbols.joinToString(DELIMITER))
                }
                if (!editor.commit()) return@withLock false
                cached = cached.toMutableMap().apply {
                    for ((key, symbols) in verified) {
                        if (symbols.isEmpty()) remove(key) else put(key, symbols.toList())
                    }
                }.toMap()
                revision.value += 1
                true
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
