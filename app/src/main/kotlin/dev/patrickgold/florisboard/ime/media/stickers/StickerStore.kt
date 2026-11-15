/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.media.stickers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import dev.patrickgold.florisboard.BuildConfig
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** EXIF transform encoded separately so every mirrored case can be unit tested. */
internal data class StickerTransform(
    val rotation: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
) {
    val needed get() = rotation != 0 || flipHorizontal || flipVertical
}

/** Limits apply to untrusted gallery images as well as pack metadata. */
internal object StickerRules {
    /** EXIF orientation codes 1–8; transverse is reflection plus quarter-turn. */
    fun exifTransform(orientation: Int): StickerTransform = when (orientation) {
        2 -> StickerTransform(flipHorizontal = true)
        3 -> StickerTransform(rotation = 180)
        4 -> StickerTransform(flipVertical = true)
        5 -> StickerTransform(rotation = 90, flipHorizontal = true)
        6 -> StickerTransform(rotation = 90)
        7 -> StickerTransform(rotation = -90, flipHorizontal = true)
        8 -> StickerTransform(rotation = -90)
        else -> StickerTransform()
    }

    const val MAX_PACKS = 12
    const val MAX_STICKERS = 200
    const val MAX_BYTES = 8 * 1024 * 1024
    const val TARGET_SIZE = 512
    const val MAX_SOURCE_DIMENSION = 12_000

    fun validPackName(name: String): Boolean =
        name.isNotBlank() && name.length <= 32 &&
            name.none { it.isISOControl() }

    fun validId(id: String): Boolean =
        id == "default" || (id.length == 32 && id.all { it in '0'..'9' || it in 'a'..'f' })

    fun supportedMime(mime: String?): Boolean =
        mime?.lowercase() in setOf("image/png", "image/jpeg", "image/webp")
}

@Serializable
internal data class StickerPack(val id: String, val name: String)

@Serializable
internal data class StickerEntry(val id: String, val packId: String)

@Serializable
private data class StickerIndex(
    val version: Int = 1,
    val packs: List<StickerPack> = listOf(StickerPack("default", "My stickers")),
    val entries: List<StickerEntry> = emptyList(),
)

/**
 * Offline stickers are fully local. The picker reads a user-approved URI once
 * and saves a sanitised, 512px PNG copy. Source URI permissions are never
 * retained, and no original URI or user content is logged.
 */
internal object StickerStore {
    private const val PREFERENCES = "tsuzuri_sticker_index_v1"
    private const val INDEX_KEY = "index"
    private val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }
    private val mutex = Mutex()
    private val defaultIndex = StickerIndex()

    @Volatile private var loaded = false
    @Volatile private var snapshot = defaultIndex
    val revision = MutableStateFlow(0L)

    private fun folder(context: Context): File = File(context.applicationContext.filesDir, "stickers")
        .also { check(it.isDirectory || it.mkdirs()) }

    private fun stickerFile(context: Context, id: String): File {
        require(StickerRules.validId(id) && id != "default")
        return File(folder(context), "$id.png")
    }

    fun fileFor(context: Context, item: StickerEntry): File? {
        if (!StickerRules.validId(item.id) || item.id == "default") return null
        return stickerFile(context, item.id).takeIf { it.isFile }
    }

    fun uriFor(context: Context, item: StickerEntry): Uri? {
        val file = fileFor(context, item) ?: return null
        return FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider.file", file)
    }

    fun packs(): List<StickerPack> = snapshot.packs
    fun stickers(packId: String): List<StickerEntry> = snapshot.entries.filter { it.packId == packId }
    fun count(): Int = snapshot.entries.size
    fun find(id: String): StickerEntry? = snapshot.entries.firstOrNull { it.id == id }

    private fun load(context: Context) {
        if (loaded) return
        val raw = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(INDEX_KEY, null)
        val parsed = raw?.let { runCatching { json.decodeFromString<StickerIndex>(it) }.getOrNull() }
        snapshot = parsed?.takeIf {
            it.version == 1 && it.packs.isNotEmpty() &&
                it.packs.size <= StickerRules.MAX_PACKS &&
                it.entries.size <= StickerRules.MAX_STICKERS &&
                it.packs.distinctBy(StickerPack::id).size == it.packs.size &&
                it.packs.all { pack -> StickerRules.validId(pack.id) && StickerRules.validPackName(pack.name) } &&
                it.entries.distinctBy(StickerEntry::id).size == it.entries.size &&
                it.entries.all { entry ->
                    entry.id != "default" && StickerRules.validId(entry.id) &&
                        it.packs.any { pack -> pack.id == entry.packId }
                }
        } ?: defaultIndex
        loaded = true
        revision.value++
    }

    suspend fun ensureLoaded(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock { load(context) }
    }

    private fun commit(context: Context, next: StickerIndex): Boolean {
        val stored = context.applicationContext
            .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString(INDEX_KEY, json.encodeToString(next)).commit()
        if (stored) {
            snapshot = next
            revision.value++
        }
        return stored
    }

    suspend fun addPack(context: Context, rawName: String): Boolean = withContext(Dispatchers.IO) {
        val name = rawName.trim()
        if (!StickerRules.validPackName(name)) return@withContext false
        mutex.withLock {
            load(context)
            if (snapshot.packs.size >= StickerRules.MAX_PACKS ||
                snapshot.packs.any { it.name.equals(name, ignoreCase = true) }) false
            else commit(
                context,
                snapshot.copy(packs = snapshot.packs + StickerPack(newId(), name)),
            )
        }
    }

    suspend fun deletePack(context: Context, packId: String): Boolean = withContext(Dispatchers.IO) {
        if (packId == "default") return@withContext false
        mutex.withLock {
            load(context)
            if (snapshot.packs.none { it.id == packId }) return@withLock false
            val removed = snapshot.entries.filter { it.packId == packId }
            val next = snapshot.copy(
                packs = snapshot.packs.filterNot { it.id == packId },
                entries = snapshot.entries.filterNot { it.packId == packId },
            )
            if (!commit(context, next)) false
            else {
                removed.forEach { fileFor(context, it)?.delete() }
                true
            }
        }
    }

    suspend fun remove(context: Context, id: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            load(context)
            val item = snapshot.entries.firstOrNull { it.id == id } ?: return@withLock false
            if (!commit(context, snapshot.copy(entries = snapshot.entries.filterNot { it.id == id }))) {
                return@withLock false
            }
            fileFor(context, item)?.delete()
            true
        }
    }

    /**
     * A failed or oversized image never reaches the persistent index.
     * All image decoding, encoding and disk access runs off the UI thread.
     */
    suspend fun importImage(context: Context, packId: String, source: Uri): Boolean =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                load(context)
                if (snapshot.entries.size >= StickerRules.MAX_STICKERS ||
                    snapshot.packs.none { it.id == packId } ||
                    source.scheme != "content") return@withLock false

                val mime = try {
                    context.contentResolver.getType(source)
                } catch (_: Exception) {
                    null
                }
                if (!StickerRules.supportedMime(mime)) return@withLock false
                val bytes = try {
                    context.contentResolver.openInputStream(source)?.use { input ->
                        val data = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            if (data.size() + n > StickerRules.MAX_BYTES) return@use null
                            data.write(buffer, 0, n)
                        }
                        data.toByteArray()
                    }
                } catch (_: Exception) {
                    null
                } ?: return@withLock false

                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                val width = options.outWidth
                val height = options.outHeight
                if (width !in 1..StickerRules.MAX_SOURCE_DIMENSION ||
                    height !in 1..StickerRules.MAX_SOURCE_DIMENSION) return@withLock false
                var sample = 1
                while ((width / sample) > 1024 || (height / sample) > 1024) sample *= 2
                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
                val decoded = try {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                } catch (_: Exception) {
                    null
                } ?: return@withLock false

                val sticker = try {
                    val exifOrientation = try {
                        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL,
                        )
                    } catch (_: Exception) { ExifInterface.ORIENTATION_NORMAL }
                    val transform = StickerRules.exifTransform(exifOrientation)
                    val rotated = if (transform.needed) {
                        Bitmap.createBitmap(
                            decoded, 0, 0, decoded.width, decoded.height,
                            Matrix().apply {
                                setRotate(transform.rotation.toFloat())
                                postScale(
                                    if (transform.flipHorizontal) -1f else 1f,
                                    if (transform.flipVertical) -1f else 1f,
                                )
                            }, true,
                        )
                    } else decoded
                    val output = Bitmap.createBitmap(
                        StickerRules.TARGET_SIZE, StickerRules.TARGET_SIZE, Bitmap.Config.ARGB_8888,
                    )
                    val canvas = Canvas(output)
                    val scale = minOf(
                        StickerRules.TARGET_SIZE.toFloat() / rotated.width,
                        StickerRules.TARGET_SIZE.toFloat() / rotated.height,
                    )
                    val w = rotated.width * scale
                    val h = rotated.height * scale
                    canvas.drawBitmap(
                        rotated,
                        null,
                        android.graphics.RectF(
                            (StickerRules.TARGET_SIZE - w) / 2f,
                            (StickerRules.TARGET_SIZE - h) / 2f,
                            (StickerRules.TARGET_SIZE + w) / 2f,
                            (StickerRules.TARGET_SIZE + h) / 2f,
                        ),
                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                    )
                    if (rotated !== decoded) rotated.recycle()
                    output
                } catch (_: Exception) {
                    return@withLock false
                } finally {
                    decoded.recycle()
                }

                val id = newId()
                val file = stickerFile(context, id)
                val temp = File(folder(context), "$id.part")
                val written = try {
                    FileOutputStream(temp).use { sticker.compress(Bitmap.CompressFormat.PNG, 100, it) } &&
                        temp.isFile && temp.length() > 0 && temp.renameTo(file)
                } catch (_: Exception) {
                    false
                } finally {
                    sticker.recycle()
                    temp.delete()
                }
                if (!written) return@withLock false
                val updated = snapshot.copy(entries = snapshot.entries + StickerEntry(id, packId))
                if (!commit(context, updated)) {
                    file.delete()
                    return@withLock false
                }
                true
            }
        }

    private fun newId(): String = UUID.randomUUID().toString().replace("-", "")
}
