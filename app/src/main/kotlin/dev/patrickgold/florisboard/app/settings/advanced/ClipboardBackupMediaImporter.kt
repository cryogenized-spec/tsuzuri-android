/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app.settings.advanced

import android.content.ContentUris
import android.content.Context
import dev.patrickgold.florisboard.ime.clipboard.ClipboardRetentionPolicy
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFileInfo
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFileStorage
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFilesDatabase
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardMediaProvider
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.florisboard.lib.kotlin.io.FsFile
import org.florisboard.lib.kotlin.io.subFile

/**
 * Translates trusted Ownkey/Floris/Tsuzuri backup media into NEW, app-owned IDs.
 * Never copies sensitive media, trusts arbitrary content URIs, or overwrites an
 * existing clipboard file. Caller must commit the returned history items before
 * reporting restore success.
 */
internal object ClipboardBackupMediaImporter {
    /**
     * Preflight before resetting preferences or clipboard history. Never trust
     * an archive path derived from an unvalidated URI, even when restoring only
     * a subset of the backup's clipboard content.
     */
    fun validate(
        archiveClipboardDir: FsFile,
        archivePackage: String,
        archiveVersion: Int,
        items: List<ClipboardItem>,
        type: ItemType,
    ) {
        val segment = when (type) {
            ItemType.IMAGE -> "images"
            ItemType.VIDEO -> "videos"
            else -> throw IllegalArgumentException("Invalid backup media type")
        }
        val prefix = if (type == ItemType.IMAGE) "image/" else "video/"
        for (item in items) {
            if (!ClipboardRetentionPolicy.shouldPersist(item.isSensitive) || item.type != type) continue
            val reference = RestoreCompatibilityPolicy.normalizeMediaReference(
                item.uri?.toString(),
                archivePackage,
                archiveVersion,
                segment,
                ClipboardMediaProvider.AUTHORITY,
            ) ?: throw IllegalArgumentException("Invalid archive clipboard media reference")
            val source = archiveClipboardDir.subFile(
                ClipboardFileStorage.CLIPBOARD_FILES_PATH + "/" + reference.archiveId,
            )
            require(source.isFile && source.length() in 1..ClipboardFileStorage.MAX_BACKUP_MEDIA_BYTES) {
                "Archive clipboard media is missing or exceeds permitted size"
            }
            require(item.mimeTypes.any { it.startsWith(prefix) }) {
                "Archive clipboard media MIME type is invalid"
            }
        }
    }

    suspend fun prepare(
        context: Context,
        archiveClipboardDir: FsFile,
        archivePackage: String,
        archiveVersion: Int,
        items: List<ClipboardItem>,
        type: ItemType,
    ): List<ClipboardItem> = withContext(Dispatchers.IO) {
        val segment = when (type) {
            ItemType.IMAGE -> "images"
            ItemType.VIDEO -> "videos"
            else -> throw IllegalArgumentException("Cannot import non-media as media")
        }
        val mediaPrefix = if (type == ItemType.IMAGE) "image/" else "video/"
        val baseUri = if (type == ItemType.IMAGE) {
            ClipboardMediaProvider.IMAGE_CLIPS_URI
        } else {
            ClipboardMediaProvider.VIDEO_CLIPS_URI
        }
        val database = ClipboardFilesDatabase.new(context.applicationContext)
        val importedIds = mutableListOf<Long>()
        try {
            val prepared = mutableListOf<ClipboardItem>()
            for (item in items) {
                if (!ClipboardRetentionPolicy.shouldPersist(item.isSensitive)) continue
                if (item.type != type) continue

                val reference = RestoreCompatibilityPolicy.normalizeMediaReference(
                    source = item.uri?.toString(),
                    archivePackage = archivePackage,
                    archiveVersion = archiveVersion,
                    kind = segment,
                    targetProviderAuthority = ClipboardMediaProvider.AUTHORITY,
                ) ?: throw IllegalArgumentException("Invalid archive clipboard media reference")

                // Only a decimal validated ID is ever used as a child file name.
                val sourceFile = archiveClipboardDir.subFile(
                    ClipboardFileStorage.CLIPBOARD_FILES_PATH + "/" + reference.archiveId,
                )
                require(sourceFile.isFile) { "Archive clipboard media payload is missing" }
                val mimeTypes = item.mimeTypes.filter { it.startsWith(mediaPrefix) }
                require(mimeTypes.isNotEmpty()) { "Archive clipboard media MIME type is invalid" }

                val newId = ClipboardFileStorage.importMediaFromBackup(context, sourceFile)
                importedIds.add(newId)
                val info = ClipboardFileInfo(
                    id = newId,
                    displayName = "Restored clipboard " + segment,
                    size = sourceFile.length(),
                    orientation = 0,
                    mimeTypes = mimeTypes,
                )
                database.clipboardFilesDao().insert(info)
                ClipboardMediaProvider.registerRestored(info)
                prepared.add(
                    item.copy(
                        id = 0,
                        uri = ContentUris.withAppendedId(baseUri, newId),
                        mimeTypes = mimeTypes,
                    ),
                )
            }
            prepared
        } catch (error: Throwable) {
            for (id in importedIds) {
                ClipboardFileStorage.deleteById(context, id)
                runCatching { database.clipboardFilesDao().delete(id) }
                ClipboardMediaProvider.forgetRestored(id)
            }
            throw error
        } finally {
            database.close()
        }
    }
}
