/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.app.settings.advanced

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.cacheManager
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.clipboard.ClipboardRetentionPolicy
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFileStorage
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.ime.text.rewrite.RewritePromptPreferenceReader
import dev.patrickgold.florisboard.lib.cache.CacheManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.ime.text.dictation.dictionary.SpeechDictionaryDocument
import dev.patrickgold.florisboard.lib.ext.ExtensionManager
import dev.patrickgold.florisboard.speechDictionary
import org.florisboard.lib.android.stringRes
import dev.patrickgold.florisboard.lib.io.ZipUtils
import dev.patrickgold.jetpref.datastore.runtime.AndroidAppDataStorage
import dev.patrickgold.jetpref.datastore.runtime.FileBasedStorage
import dev.patrickgold.jetpref.datastore.runtime.ImportStrategy
import java.io.FileNotFoundException
import java.text.DateFormat
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.readToFile
import org.florisboard.lib.android.showLongToast
import org.florisboard.lib.android.showLongToastSync
import org.florisboard.lib.compose.FlorisButtonBar
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.kotlin.io.deleteContentsRecursively
import org.florisboard.lib.kotlin.io.readJson
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile
import androidx.compose.ui.platform.testTag
import dev.patrickgold.florisboard.app.ui.OwnkeyButton
import dev.patrickgold.florisboard.app.ui.OwnkeyButtonStyle
import dev.patrickgold.florisboard.app.ui.OwnkeyGroup
import dev.patrickgold.florisboard.app.ui.OwnkeyNotice
import dev.patrickgold.florisboard.app.ui.OwnkeyRow
import dev.patrickgold.florisboard.app.ui.OwnkeyTone
import dev.patrickgold.florisboard.app.ui.PreferenceGroup
import org.florisboard.lib.compose.OwnkeyTokens

object Restore {
    const val MIN_VERSION_CODE = 64
    const val PACKAGE_NAME = "dev.patrickgold.florisboard"
    const val BACKUP_ARCHIVE_FILE_NAME = "backup.zip"
}

@Composable
fun RestoreScreen() = FlorisScreen {
    title = stringRes(R.string.backup_and_restore__restore__title)
    previewFieldVisible = false

    val navController = LocalNavController.current
    val context = LocalContext.current
    val cacheManager by context.cacheManager()

    val restoreFilesSelector = remember { Backup.FilesSelector() }
    var importStrategy by remember { mutableStateOf(ImportStrategy.Merge) }
    // TODO: rememberCoroutineScope() is unusable because it provides the scope in a cancelled state, which does
    //  not make sense at all. I suspect that this is a bug and once it is resolved we can use it here again.
    val restoreScope = remember { CoroutineScope(Dispatchers.Main) }
    var restoreWorkspace by remember {
        mutableStateOf<CacheManager.BackupAndRestoreWorkspace?>(null)
    }

    val restoreDataFromFileSystemLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            runCatching {
                restoreWorkspace?.close()
                restoreWorkspace = null
                val workspace = cacheManager.backupAndRestore.new()
                workspace.zipFile = workspace.inputDir.subFile(Restore.BACKUP_ARCHIVE_FILE_NAME)
                context.contentResolver.readToFile(uri, workspace.zipFile)
                ZipUtils.unzip(workspace.zipFile, workspace.outputDir)
                workspace.metadata = try {
                    workspace.outputDir.subFile(Backup.METADATA_JSON_NAME).readJson()
                } catch (e: FileNotFoundException) {
                    error("Invalid archive: either backup_metadata.json is missing or file is not a ZIP archive.")
                }
                workspace.restoreWarningId = when {
                    workspace.metadata.versionCode != BuildConfig.VERSION_CODE -> {
                        R.string.backup_and_restore__restore__metadata_warn_different_version
                    }
                    !RestoreCompatibilityPolicy.isTsuzuriPackage(workspace.metadata.packageName) -> {
                        R.string.backup_and_restore__restore__metadata_warn_different_vendor
                    }
                    else -> null
                }
                workspace.restoreErrorId = when {
                    !RestoreCompatibilityPolicy.accepts(workspace.metadata.packageName, workspace.metadata.versionCode) -> {
                        R.string.backup_and_restore__restore__metadata_error_invalid_metadata
                    }
                    else -> null
                }
                restoreWorkspace = workspace
            }.onFailure { error ->
                context.showLongToastSync(
                    R.string.backup_and_restore__restore__failure,
                    "error_message" to error.localizedMessage,
                )
            }
        },
    )

    suspend fun performRestore() {
        val workspace = restoreWorkspace!!
        val shouldReset = importStrategy == ImportStrategy.Erase
        // Validate selected speech data before preferences, extension files or clipboard can change.
        // A missing section in an older archive intentionally leaves the current dictionary alone.
        val speechDocument = if (restoreFilesSelector.speechDictionary) {
            val file = workspace.outputDir.subDir(Backup.SPEECH_DIR_NAME).subFile(Backup.SPEECH_DICTIONARY_JSON_NAME)
            if (file.exists()) {
                val document = try {
                    SpeechDictionaryDocument.decode(file.readText(Charsets.UTF_8)).also {
                        context.speechDictionary().value.validateRestore(it)
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Do not retain decoder excerpts or IO paths, including through an exception cause.
                    throw IllegalStateException(context.stringRes(R.string.speech_dictionary__restore_invalid))
                }
                document
            } else null
        } else null
        // Parse and validate the selected clipboard archive BEFORE modifying any
        // preferences, dictionaries or existing clipboard files.
        val archiveClipboardDir = workspace.outputDir.subDir("clipboard")
        fun readArchiveItems(name: String): List<ClipboardItem> {
            val file = archiveClipboardDir.subFile(name)
            return if (file.exists()) file.readJson<List<ClipboardItem>>() else emptyList()
        }
        val textArchive = if (restoreFilesSelector.clipboardTextItems) {
            withContext(Dispatchers.IO) { readArchiveItems(Backup.CLIPBOARD_TEXT_ITEMS_JSON_NAME) }
        } else emptyList()
        val imageArchive = if (restoreFilesSelector.clipboardImageItems) {
            withContext(Dispatchers.IO) { readArchiveItems(Backup.CLIPBOARD_IMAGES_JSON_NAME) }
        } else emptyList()
        val videoArchive = if (restoreFilesSelector.clipboardVideoItems) {
            withContext(Dispatchers.IO) { readArchiveItems(Backup.CLIPBOARD_VIDEO_JSON_NAME) }
        } else emptyList()
        withContext(Dispatchers.IO) {
            val imageBytes = ClipboardBackupMediaImporter.validate(
                archiveClipboardDir, workspace.metadata.packageName,
                workspace.metadata.versionCode, imageArchive, ItemType.IMAGE,
            )
            val videoBytes = ClipboardBackupMediaImporter.validate(
                archiveClipboardDir, workspace.metadata.packageName,
                workspace.metadata.versionCode, videoArchive, ItemType.VIDEO,
            )
            require(imageBytes <= 256L * 1024L * 1024L - videoBytes) {
                "Backup clipboard media exceeds aggregate size budget"
            }
            val imageRows = imageArchive.count {
                it.type == ItemType.IMAGE && ClipboardRetentionPolicy.shouldPersist(it.isSensitive)
            }
            val videoRows = videoArchive.count {
                it.type == ItemType.VIDEO && ClipboardRetentionPolicy.shouldPersist(it.isSensitive)
            }
            require(imageRows + videoRows <= 999) { "Too many backup clipboard media rows" }
        }

        // Fail speech persistence before changing the other selected restore targets.
        if (speechDocument != null && !context.speechDictionary().value.restore(speechDocument, merge = !shouldReset)) {
            throw IllegalStateException(context.stringRes(R.string.speech_dictionary__save_error))
        }
        if (restoreFilesSelector.jetprefDatastore) {
            val file = workspace.outputDir
                .subDir(AndroidAppDataStorage.JETPREF_DIR_NAME)
                .subFile("${FlorisPreferenceModel.NAME}.${AndroidAppDataStorage.JETPREF_FILE_EXT}")
            if (file.exists()) {
                val fileBasedStorage = FileBasedStorage(file.path)
                FlorisPreferenceStore.import(importStrategy, RewritePromptPreferenceReader(fileBasedStorage)).getOrThrow()
            }
        }
        val workspaceFilesDir = workspace.outputDir.subDir("files")
        if (restoreFilesSelector.imeKeyboard) {
            val srcDir = workspaceFilesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH)
            val dstDir = context.filesDir.subDir(ExtensionManager.IME_KEYBOARD_PATH)
            if (shouldReset) {
                dstDir.deleteContentsRecursively()
            }
            if (srcDir.exists()) {
                srcDir.copyRecursively(dstDir, overwrite = true)
            }
        }
        if (restoreFilesSelector.imeTheme) {
            val srcDir = workspaceFilesDir.subDir(ExtensionManager.IME_THEME_PATH)
            val dstDir = context.filesDir.subDir(ExtensionManager.IME_THEME_PATH)
            if (shouldReset) {
                dstDir.deleteContentsRecursively()
            }
            if (srcDir.exists()) {
                srcDir.copyRecursively(dstDir, overwrite = true)
            }
        }
        val clipboardManager = context.clipboardManager().value
        if (shouldReset) {
            // Await deletion before copying imported media to the same directory.
            clipboardManager.clearFullHistoryForRestore()
            withContext(Dispatchers.IO) {
                ClipboardFileStorage.resetClipboardFileStorage(context)
            }
        }

        if (restoreFilesSelector.provideClipboardItems()) {
            val importedMedia = mutableListOf<ClipboardItem>()
            try {
                if (restoreFilesSelector.clipboardImageItems) {
                    importedMedia += ClipboardBackupMediaImporter.prepare(
                        context, archiveClipboardDir, workspace.metadata.packageName,
                        workspace.metadata.versionCode, imageArchive, ItemType.IMAGE,
                    )
                }
                if (restoreFilesSelector.clipboardVideoItems) {
                    importedMedia += ClipboardBackupMediaImporter.prepare(
                        context, archiveClipboardDir, workspace.metadata.packageName,
                        workspace.metadata.versionCode, videoArchive, ItemType.VIDEO,
                    )
                }
                // A single Room transaction makes history insertion awaitable,
                // and source URI identities have been rebound to our provider.
                clipboardManager.restoreHistoryForRestore(
                    textArchive.filter {
                        it.type == ItemType.TEXT &&
                            ClipboardRetentionPolicy.shouldPersist(it.isSensitive)
                    } + importedMedia,
                )
            } catch (error: Throwable) {
                // Avoid orphaning media files if another archive section fails.
                importedMedia.forEach { it.close(context) }
                throw error
            }
        }
    }

    bottomBar {
        FlorisButtonBar {
            ButtonBarSpacer()
            ButtonBarTextButton(
                onClick = {
                    restoreWorkspace?.close()
                    navController.navigateUp()
                },
                text = stringRes(R.string.action__cancel),
            )
            ButtonBarButton(
                onClick = {
                    restoreScope.launch(Dispatchers.Main) {
                        try {
                            performRestore()
                            context.showLongToast(R.string.backup_and_restore__restore__success)
                            navController.navigateUp()
                        } catch (e: Throwable) {
                            e.printStackTrace()
                            context.showLongToast(
                                R.string.backup_and_restore__restore__failure,
                                "error_message" to e.localizedMessage,
                            )
                        }
                    }
                },
                text = stringRes(R.string.action__restore),
                enabled = restoreWorkspace != null && restoreWorkspace?.restoreErrorId == null,
            )
        }
    }

    content {
        PreferenceGroup(title = stringRes(R.string.backup_and_restore__restore__mode)) {
            RadioListItem(
                onClick = {
                    importStrategy = ImportStrategy.Merge
                },
                selected = importStrategy == ImportStrategy.Merge,
                text = stringRes(R.string.backup_and_restore__restore__mode_merge),
                secondaryText = stringRes(R.string.settings__privacy__restore_merge_summary),
            )
            RadioListItem(
                onClick = {
                    importStrategy = ImportStrategy.Erase
                },
                selected = importStrategy == ImportStrategy.Erase,
                text = stringRes(R.string.backup_and_restore__restore__mode_erase_and_overwrite),
                secondaryText = stringRes(R.string.settings__privacy__restore_replace_summary),
            )
        }
        OwnkeyButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = OwnkeyTokens.GroupGap)
                .testTag("restore-select-file"),
            label = stringRes(R.string.action__select_file),
            style = OwnkeyButtonStyle.Secondary,
            onClick = {
                runCatching {
                    restoreDataFromFileSystemLauncher.launch("*/*")
                }.onFailure { error ->
                    context.showLongToastSync(
                        R.string.backup_and_restore__restore__failure,
                        "error_message" to error.localizedMessage,
                    )
                }
            },
        )
        val workspace = restoreWorkspace
        if (workspace == null) {
            OwnkeyNotice(text = stringRes(R.string.settings__privacy__restore_no_file))
        } else {
            OwnkeyGroup(title = stringRes(R.string.backup_and_restore__restore__metadata)) {
                OwnkeyRow(
                    title = stringRes(R.string.settings__privacy__restore_app),
                    value = workspace.metadata.packageName,
                )
                OwnkeyRow(
                    title = stringRes(R.string.settings__privacy__restore_version),
                    value = "${workspace.metadata.versionName} (${workspace.metadata.versionCode})",
                )
                OwnkeyRow(
                    title = stringRes(R.string.settings__privacy__restore_created),
                    value = remember(workspace.metadata.timestamp) {
                        val formatter = DateFormat.getDateTimeInstance()
                        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                        calendar.timeInMillis = workspace.metadata.timestamp
                        formatter.format(calendar.time)
                    },
                )
                if (workspace.restoreErrorId != null) {
                    OwnkeyNotice(
                        text = stringRes(workspace.restoreErrorId!!),
                        tone = OwnkeyTone.Error,
                    )
                } else if (workspace.restoreWarningId != null) {
                    OwnkeyNotice(
                        text = stringRes(workspace.restoreWarningId!!),
                        tone = OwnkeyTone.Warning,
                    )
                }
            }
            if (workspace.restoreErrorId == null) {
                BackupFilesSelector(
                    filesSelector = restoreFilesSelector,
                    title = stringRes(R.string.backup_and_restore__restore__files),
                )
            }
        }
    }
}
