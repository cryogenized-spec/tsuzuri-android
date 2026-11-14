/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard

import android.content.ContentUris
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.patrickgold.florisboard.app.settings.advanced.ClipboardBackupMediaImporter
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
    fun externalMediaCloseNeverDeletesAnUnownedContentUri() {
        val external = oldImage(70017L, sensitive = true)
        // Must not attempt ContentResolver.delete() on a borrowed external URI.
        external.close(context)
        assertEquals(oldPackage + ".provider.clipboard", external.uri?.authority)
    }
}
