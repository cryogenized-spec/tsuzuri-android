/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard

import android.content.ContentUris
import android.content.ClipData
import android.os.PersistableBundle
import dev.patrickgold.florisboard.ime.clipboard.ClipboardRetentionPolicy
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.patrickgold.florisboard.app.settings.advanced.ClipboardBackupMediaImporter
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardHistoryDatabase
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFileStorage
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardMediaProvider
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import kotlinx.coroutines.runBlocking
import org.florisboard.lib.kotlin.io.FsFile
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Android content-provider and filesystem assertions; no network or real
 * user clipboard is involved. Run on emulator/device before approving PR.
 */
@RunWith(AndroidJUnit4::class)
class TsuzuriClipboardSecurityTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val oldPackage = "nl.bartvandermeeren.ownkey"

    private fun oldImage(archiveId: Long, sensitive: Boolean = false) = ClipboardItem(
        type = ItemType.IMAGE,
        text = null,
        uri = Uri.parse("content://" + oldPackage + ".provider.clipboard/clips/images/" + archiveId),
        creationTimestampMs = System.currentTimeMillis(),
        isPinned = false,
        mimeTypes = listOf("image/png"),
        isSensitive = sensitive,
    )

    @Test
    fun importingLegacyMediaAllocatesNewUriWithoutOverwritingExistingFiles() = runBlocking {
        val sourceDir = FsFile(context.cacheDir, "tsuzuri-security-test-" + System.nanoTime())
        val archiveFiles = sourceDir.subDir(ClipboardFileStorage.CLIPBOARD_FILES_PATH)
        archiveFiles.mkdirs()
        var oldId = System.nanoTime() and Long.MAX_VALUE
        while (oldId == 0L || ClipboardFileStorage.getFileForId(context, oldId).exists()) {
            oldId = System.nanoTime() and Long.MAX_VALUE
        }
        val preservedLocalFile = ClipboardFileStorage.getFileForId(context, oldId)
        val sourcePayload = "restored-image-fixture".toByteArray()
        archiveFiles.subFile(oldId.toString()).writeBytes(sourcePayload)
        preservedLocalFile.writeText("existing-local-file")
        var imported: ClipboardItem? = null
        try {
            val importedItems = ClipboardBackupMediaImporter.prepare(
                context, sourceDir, oldPackage, 127, listOf(oldImage(oldId)), ItemType.IMAGE,
            )
            assertEquals(1, importedItems.size)
            imported = importedItems.single()
            val uri = requireNotNull(imported.uri)
            assertEquals(ClipboardMediaProvider.AUTHORITY, uri.authority)
            val newId = ContentUris.parseId(uri)
            assertNotEquals(oldId, newId)
            assertEquals("existing-local-file", preservedLocalFile.readText())
            assertArrayEquals(sourcePayload, ClipboardFileStorage.getFileForId(context, newId).readBytes())
            assertEquals("image/png", context.contentResolver.getType(uri))
            val servedPayload = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            assertArrayEquals(sourcePayload, servedPayload)
        } finally {
            imported?.close(context)
            preservedLocalFile.delete()
            sourceDir.deleteRecursively()
        }
    }

    @Test
    fun duplicateArchiveReferencesShareOneRestoredMediaFile() = runBlocking {
        val sourceDir = FsFile(context.cacheDir, "tsuzuri-duplicate-test-" + System.nanoTime())
        val folder = sourceDir.subDir(ClipboardFileStorage.CLIPBOARD_FILES_PATH)
        folder.mkdirs()
        val id = (System.nanoTime() and Long.MAX_VALUE).coerceAtLeast(1L)
        folder.subFile(id.toString()).writeText("shared-media-test")
        var copied: List<ClipboardItem> = emptyList()
        try {
            copied = ClipboardBackupMediaImporter.prepare(
                context, sourceDir, oldPackage, 127,
                listOf(
                    oldImage(id),
                    oldImage(id).copy(creationTimestampMs = System.currentTimeMillis() + 1),
                ),
                ItemType.IMAGE,
            )
            assertEquals(2, copied.size)
            assertEquals(copied[0].uri, copied[1].uri)
            assertArrayEquals(
                "shared-media-test".toByteArray(),
                ClipboardFileStorage.getFileForId(
                    context, ContentUris.parseId(requireNotNull(copied[0].uri)),
                ).readBytes(),
            )
        } finally {
            copied.firstOrNull()?.close(context)
            sourceDir.deleteRecursively()
        }
    }

    @Test
    fun hostileArchiveWithTooManyMediaRowsIsRejectedBeforeDiskCopy() {
        val sourceDir = FsFile(context.cacheDir, "tsuzuri-cap-test-" + System.nanoTime())
        val folder = sourceDir.subDir(ClipboardFileStorage.CLIPBOARD_FILES_PATH)
        folder.mkdirs()
        val sourceId = (System.nanoTime() and Long.MAX_VALUE).coerceAtLeast(1L)
        folder.subFile(sourceId.toString()).writeText("same-image")
        try {
            val repeated = List(1000) { oldImage(sourceId) }
            val result = runCatching {
                ClipboardBackupMediaImporter.validate(
                    sourceDir, oldPackage, 127, repeated, ItemType.IMAGE,
                )
            }
            assertTrue(result.isFailure)
            assertEquals(
                "same-image",
                folder.subFile(sourceId.toString()).readText(),
            )
        } finally {
            sourceDir.deleteRecursively()
        }
    }

    @Test
    fun sensitiveLegacyImagesAreNotImportedOrPersisted() = runBlocking {
        val sourceDir = FsFile(context.cacheDir, "tsuzuri-sensitive-test-" + System.nanoTime())
        val archiveFiles = sourceDir.subDir(ClipboardFileStorage.CLIPBOARD_FILES_PATH)
        archiveFiles.mkdirs()
        val sourceId = System.nanoTime() and Long.MAX_VALUE
        val source = archiveFiles.subFile(sourceId.toString())
        source.writeText("sensitive-test-fixture")
        val localDir = FsFile(context.noBackupFilesDir, ClipboardFileStorage.CLIPBOARD_FILES_PATH)
        val filesBefore = localDir.listFiles()?.map { it.name }?.toSet().orEmpty()
        try {
            val result = ClipboardBackupMediaImporter.prepare(
                context, sourceDir, oldPackage, 127, listOf(oldImage(sourceId, sensitive = true)),
                ItemType.IMAGE,
            )
            assertTrue(result.isEmpty())
            assertTrue(source.exists())
            val filesAfter = localDir.listFiles()?.map { it.name }?.toSet().orEmpty()
            assertEquals(filesBefore, filesAfter)
        } finally {
            sourceDir.deleteRecursively()
        }
    }

    @Test
    fun conditionalDeletionRetainsPinnedImageFileUntilExplicitlyDeleted() = runBlocking {
        val sourceDir = FsFile(context.cacheDir, "tsuzuri-pinned-test-" + System.nanoTime())
        val archiveFiles = sourceDir.subDir(ClipboardFileStorage.CLIPBOARD_FILES_PATH)
        archiveFiles.mkdirs()
        val archiveId = (System.nanoTime() and Long.MAX_VALUE).coerceAtLeast(1L)
        archiveFiles.subFile(archiveId.toString()).writeText("pinned-image-fixture")
        val db = ClipboardHistoryDatabase.new(context)
        var item: ClipboardItem? = null
        try {
            item = ClipboardBackupMediaImporter.prepare(
                context, sourceDir, oldPackage, 127, listOf(oldImage(archiveId)), ItemType.IMAGE,
            ).single()
            val media = requireNotNull(item)
            val storedFile = ClipboardFileStorage.getFileForId(
                context, ContentUris.parseId(requireNotNull(media.uri)),
            )
            val manager = context.clipboardManager().value
            manager.restoreHistoryForRestore(listOf(media.copy(isPinned = true)))
            val saved = db.clipboardItemDao().getAll().first { it.uri == media.uri }
            assertTrue(saved.isPinned)
            assertFalse(manager.deleteClipAwait(saved, onlyIfUnpinned = true))
            assertTrue(storedFile.isFile)
            assertTrue(manager.deleteClipAwait(saved, onlyIfUnpinned = false))
            assertFalse(storedFile.exists())
        } finally {
            item?.close(context)
            db.close()
            sourceDir.deleteRecursively()
        }
    }

    @Test
    fun sensitiveBorrowedMediaNeverDeletesOrReadsItsOriginalSource() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val testContext = instrumentation.context
        val providerUri = Uri.parse(
            "content://" + testContext.packageName + ".test-source/picture/non-numeric",
        )
        testContext.contentResolver.call(providerUri, "reset", null, null)
        val clip = ClipData.newUri(testContext.contentResolver, "private test media", providerUri)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipboardRetentionPolicy.SENSITIVE_EXTRA, true)
        }
        val item = ClipboardItem.fromClipData(context, clip, cloneUri = false)
        assertTrue(item.isSensitive)
        assertEquals(providerUri, item.uri)
        // The production close() path is only allowed to delete own-provider URIs.
        item.close(context)
        val counts = testContext.contentResolver.call(providerUri, "counts", null, null)
        assertEquals(0, counts?.getInt("delete"))
        assertEquals(0, counts?.getInt("read"))
    }

    @Test
    fun externalMediaCloseNeverDeletesAnUnownedContentUri() {
        val external = oldImage(70017L, sensitive = true)
        // Must not attempt ContentResolver.delete() on a borrowed external URI.
        external.close(context)
        assertEquals(oldPackage + ".provider.clipboard", external.uri?.authority)
    }
}
