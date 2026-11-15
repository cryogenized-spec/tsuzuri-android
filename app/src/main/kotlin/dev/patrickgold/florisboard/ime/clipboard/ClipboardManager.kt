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

package dev.patrickgold.florisboard.ime.clipboard

import android.content.ClipData
import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardHistoryDao
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardHistoryDatabase
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardMediaProvider
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.florisboard.lib.android.AndroidClipboardManager
import org.florisboard.lib.android.AndroidClipboardManager_OnPrimaryClipChangedListener
import org.florisboard.lib.android.clearPrimaryClipAnyApi
import org.florisboard.lib.android.setOrClearPrimaryClip
import org.florisboard.lib.android.showShortToastSync
import org.florisboard.lib.android.systemService
import org.florisboard.lib.kotlin.tryOrNull

/**
 * [ClipboardManager] manages the clipboard and clipboard history.
 *
 * Also just going to document how all the classes here work.
 *
 * [ClipboardManager] handles storage and retrieval of clipboard items. All manipulation of the
 * clipboard goes through here.
 */
class ClipboardManager(
    context: Context,
) : AndroidClipboardManager_OnPrimaryClipChangedListener, Closeable {
    companion object {
        /**
         * Taken from ClipboardDescription.java from the AOSP
         *
         * Helper to compare two MIME types, where one may be a pattern.
         * @param concreteType A fully-specified MIME type.
         * @param desiredType A desired MIME type that may be a pattern such as * / *.
         * @return Returns true if the two MIME types match.
         */
        fun compareMimeTypes(concreteType: String, desiredType: String): Boolean {
            val typeLength = desiredType.length
            if (typeLength == 3 && desiredType == "*/*") {
                return true
            }
            val slashpos = desiredType.indexOf('/')
            if (slashpos > 0) {
                if (typeLength == slashpos + 2 && desiredType[slashpos + 1] == '*') {
                    if (desiredType.regionMatches(0, concreteType, 0, slashpos + 1)) {
                        return true
                    }
                } else if (desiredType == concreteType) {
                    return true
                }
            }
            return false
        }
    }

    private val prefs by FlorisPreferenceStore
    private val appContext by context.appContext()
    private val editorInstance by context.editorInstance()
    private val systemClipboardManager = context.systemService(AndroidClipboardManager::class)

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cleanUpJob: Job
    private var clipHistoryDb: ClipboardHistoryDatabase? = null
    private val historyMutationMutex = Mutex()
    private val clipHistoryDao: ClipboardHistoryDao? get() = clipHistoryDb?.clipboardItemDao()

    val historyFlow: StateFlow<ClipboardHistory>
        field = MutableStateFlow(ClipboardHistory.EMPTY)
    val currentHistory: ClipboardHistory
        get() = historyFlow.value

    private val clipboardEventGeneration = ClipboardEventGeneration()
    val primaryClipFlow: StateFlow<ClipboardItem?>
        field = MutableStateFlow(null)
    inline var primaryClip
        get() = primaryClipFlow.value
        private set(v) {
            primaryClipFlow.value = v
        }

    init {
        systemClipboardManager.addPrimaryClipChangedListener(this)
        cleanUpJob = ioScope.launch {
            combine(
                historyFlow,
                prefs.clipboard.historyAutoCleanOldEnabled.asFlow(),
                prefs.clipboard.historyAutoCleanOldAfter.asFlow(),
                prefs.clipboard.historyAutoCleanSensitiveEnabled.asFlow(),
                prefs.clipboard.historyAutoCleanSensitiveAfter.asFlow(),
            ) { history, oldEnabled, oldAfterMinutes, sensitiveEnabled, sensitiveAfterSeconds ->
                ClipboardCleanupPlan(
                    history = history,
                    policy = ClipboardCleanupPolicy(
                        oldItemsEnabled = oldEnabled,
                        oldItemsAfterMinutes = oldAfterMinutes,
                        sensitiveItemsEnabled = sensitiveEnabled,
                        sensitiveItemsAfterSeconds = sensitiveAfterSeconds,
                    ),
                )
            }.collectLatest { plan ->
                var consecutiveRetries = 0
                while (true) {
                    val delayMs = ClipboardCleanupScheduler.nextDelayMs(
                        items = plan.history.all.map { it.toCleanupItem() },
                        policy = plan.policy,
                        nowMs = System.currentTimeMillis(),
                    ) ?: return@collectLatest
                    delay(delayMs)
                    val removedItems: Boolean? = try {
                        enforceExpiryDate(plan)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                    if (removedItems == true) return@collectLatest
                    if (removedItems == null || delayMs == 0L) {
                        consecutiveRetries = (consecutiveRetries + 1)
                            .coerceAtMost(ClipboardCleanupScheduler.MaxRapidRetries + 1)
                        delay(ClipboardCleanupScheduler.retryDelayMs(consecutiveRetries))
                    } else {
                        consecutiveRetries = 0
                    }
                }
            }
        }
    }

    private fun historyDatabase(): ClipboardHistoryDatabase = synchronized(this) {
        clipHistoryDb ?: ClipboardHistoryDatabase.new(appContext).also { clipHistoryDb = it }
    }

    fun initializeForContext(context: Context) {
        ioScope.launch {
            val database = historyDatabase()
            withContext(Dispatchers.Main) {
                database.clipboardItemDao().getAllAsFlow().collect { items ->
                    updateHistory(items)
                }
            }
        }
    }

    private fun updateHistory(items: List<ClipboardItem>) {
        val itemsSorted = items.sortedByDescending { it.creationTimestampMs }
        val clipHistory = ClipboardHistory(itemsSorted)
        enforceHistoryLimit(clipHistory)
        historyFlow.value = clipHistory
    }

    /**
     * Sets the current primary clip without updating the internal clipboard history.
     */
    fun updatePrimaryClip(item: ClipboardItem?) {
        // Internal updates supersede any pending external clipboard callback.
        clipboardEventGeneration.invalidatePending()
        primaryClip = item
        if (prefs.clipboard.useInternalClipboard.get()) {
            val syncBehavior = prefs.clipboard.syncToSystem.get()
            val clipData = item?.toClipData(appContext)
            if (clipData != null && syncBehavior.shouldSyncSet) {
                systemClipboardManager.setPrimaryClip(clipData)
            } else if (clipData == null && syncBehavior.shouldSyncClear) {
                systemClipboardManager.clearPrimaryClipAnyApi()
            }
        } else {
            systemClipboardManager.setOrClearPrimaryClip(item?.toClipData(appContext))
        }
    }

    /**
     * Called by system clipboard when the system primary clip has changed.
     */
    override fun onPrimaryClipChanged() {
        val callbackGeneration = clipboardEventGeneration.next()
        val syncBehavior = prefs.clipboard.syncToFloris.get()
        if (!prefs.clipboard.useInternalClipboard.get() || syncBehavior != ClipboardSyncBehavior.NO_EVENTS) {
            val systemPrimaryClip = systemClipboardManager.primaryClip
            ioScope.launch {
                if (!clipboardEventGeneration.isCurrent(callbackGeneration)) return@launch
                // Do not deduplicate until an event is actually applied. Otherwise
                // an identical later callback can invalidate unfinished work and be
                // mistaken for a duplicate, losing the clipboard update.
                val internalPrimaryClip = primaryClip

                if (systemPrimaryClip == null) {
                    if (syncBehavior.shouldSyncClear) {
                        clipboardEventGeneration.applyIfCurrent(callbackGeneration) { primaryClip = null }
                    }
                    return@launch
                }

                if (systemPrimaryClip.getItemAt(0).let { it.text == null && it.uri == null }) {
                    if (syncBehavior.shouldSyncClear) {
                        clipboardEventGeneration.applyIfCurrent(callbackGeneration) { primaryClip = null }
                    }
                    return@launch
                }

                if (!syncBehavior.shouldSyncSet) {
                    clipboardEventGeneration.applyIfCurrent(callbackGeneration) { /* no import */ }
                    return@launch
                }

                val incomingSensitive = systemPrimaryClip.description.extras
                    ?.getBoolean(ClipboardRetentionPolicy.SENSITIVE_EXTRA) ?: false
                val isEqual = internalPrimaryClip?.isEqualTo(systemPrimaryClip) == true &&
                    internalPrimaryClip.isSensitive == incomingSensitive
                if (!isEqual) {
                    val mediaSource = if (incomingSensitive) null else {
                        val incomingItem = systemPrimaryClip.getItemAt(0)
                        val formats = List(systemPrimaryClip.description.mimeTypeCount) {
                            systemPrimaryClip.description.getMimeType(it)
                        }
                        ClipboardCapturePolicy.externalMediaSource(
                            sourceUri = incomingItem.uri?.toString(),
                            sourceAuthority = incomingItem.uri?.authority,
                            timestampMs = systemPrimaryClip.description.timestamp,
                            mimeTypes = formats,
                            ownProviderAuthority = ClipboardMediaProvider.AUTHORITY,
                        )
                    }

                    // A callback can repeat before the first copy has been committed.
                    // Never suppress pending work: deduplicate only committed sources.
                    if (mediaSource != null &&
                        clipboardEventGeneration.wasAppliedExternalMediaSource(mediaSource)
                    ) return@launch

                    // IMPORTANT: cloning writes bytes to app-private disk before the
                    // history filter. Sensitive media must stay as a borrowed URI.
                    val item = ClipboardItem.fromClipData(
                        appContext, systemPrimaryClip, cloneUri = !incomingSensitive,
                    )
                    val applied = clipboardEventGeneration.applyIfCurrent(callbackGeneration, mediaSource) {
                        primaryClip = item
                        insertOrMoveBeginning(item)
                    }
                    if (!applied) {
                        // Borrowed Tsuzuri provider URIs may belong to pinned/history clips.
                        // Only close media newly copied from a different authority.
                        if (!incomingSensitive && ClipboardCapturePolicy.ownsNewClone(
                                isMedia = item.type == ItemType.IMAGE || item.type == ItemType.VIDEO,
                                originalAuthority = systemPrimaryClip.getItemAt(0).uri?.authority,
                                ownProviderAuthority = ClipboardMediaProvider.AUTHORITY,
                            )
                        ) {
                            item.close(appContext)
                        }
                    }
                } else {
                    clipboardEventGeneration.applyIfCurrent(callbackGeneration) { /* accepted current clip */ }
                }
            }
        }
    }

    /**
     * Change the current text on clipboard, update history (if enabled).
     */
    private fun addNewClip(item: ClipboardItem) {
        insertOrMoveBeginning(item)
        updatePrimaryClip(item)
    }

    /**
     * Wraps some plaintext in a ClipData and calls [addNewClip]
     */
    fun addNewPlaintext(newText: String) {
        val newData = ClipboardItem.text(newText)
        addNewClip(newData)
    }

    /**
     * Adds a new item to the clipboard history (if enabled).
     */
    private fun insertOrMoveBeginning(newItem: ClipboardItem) {
        if (!ClipboardRetentionPolicy.shouldPersist(newItem.isSensitive)) return
        if (prefs.clipboard.historyEnabled.get()) {
            val historyElement = currentHistory.all.firstOrNull { item ->
                item.type == ItemType.TEXT && item.text == newItem.text && item.isSensitive == newItem.isSensitive
            }
            if (historyElement != null) {
                moveToTheBeginning(
                    oldItem = historyElement,
                    newItem = if (historyElement.isPinned) {
                        newItem.copy(isPinned = true)
                    } else {
                        newItem
                    }
                )
            } else {
                insertClip(newItem)
            }
        }
    }

    private fun enforceHistoryLimit(clipHistory: ClipboardHistory) {
        if (prefs.clipboard.historySizeLimitEnabled.get()) {
            val nonPinnedItems = clipHistory.recent + clipHistory.other
            val nToRemove = nonPinnedItems.size - prefs.clipboard.historySizeLimit.get()
            if (nToRemove > 0) {
                val itemsToRemove = nonPinnedItems.asReversed().filterIndexed { n, _ -> n < nToRemove }
                ioScope.launch {
                    historyMutationMutex.withLock {
                        val db = historyDatabase()
                        val dao = db.clipboardItemDao()
                        // Use the current database, not a stale UI history snapshot:
                        // an item may have been pinned while this job was queued.
                        val currentUnpinned = dao.getAll()
                            .filterNot { it.isPinned }
                            .sortedByDescending { it.creationTimestampMs }
                        val expired = currentUnpinned.drop(prefs.clipboard.historySizeLimit.get())
                        if (expired.isNotEmpty()) {
                            db.runInTransaction { dao.delete(expired) }
                            disposeUnreferencedHistoryMedia(expired, db)
                        }
                    }
                }
            }
        }
    }

    /**
     * Release media only when no surviving history row or primary clipboard
     * item still refers to the same provider URI. Caller must hold
     * historyMutationMutex to prevent a write between check and deletion.
     */
    private fun disposeUnreferencedHistoryMedia(
        removed: Collection<ClipboardItem>,
        db: ClipboardHistoryDatabase,
    ) {
        val survivors = db.clipboardItemDao().getAll().mapNotNull { it.uri?.toString() }.toSet()
        val active = primaryClip?.uri?.toString()
        for (item in removed.distinctBy { it.uri?.toString() ?: "row:" + it.id }) {
            if (ClipboardMediaRetentionPolicy.mayDispose(item.uri?.toString(), survivors, active)) {
                item.close(appContext)
            }
        }
    }

    private suspend fun enforceExpiryDate(plan: ClipboardCleanupPlan): Boolean {
        val nowMs = System.currentTimeMillis()
        val itemsToRemove = plan.history.all.filter { item ->
            ClipboardCleanupScheduler.isExpired(
                item = item.toCleanupItem(),
                policy = plan.policy,
                nowMs = nowMs,
            )
        }
        if (itemsToRemove.isEmpty()) return false
        return historyMutationMutex.withLock {
            val db = historyDatabase()
            val dao = db.clipboardItemDao()
            val now = System.currentTimeMillis()
            // Re-evaluate expiry after acquiring the write mutex. Do not
            // destroy items pinned or updated after the original plan.
            val eligible = dao.getAll().filter { item ->
                ClipboardCleanupScheduler.isExpired(item.toCleanupItem(), plan.policy, now)
            }
            if (eligible.isEmpty()) return@withLock false
            db.runInTransaction { dao.delete(eligible) }
            disposeUnreferencedHistoryMedia(eligible, db)
            true
        }
    }

    private fun ClipboardItem.toCleanupItem() = ClipboardCleanupItem(
        creationTimestampMs = creationTimestampMs,
        isPinned = isPinned,
        isSensitive = isSensitive,
    )

    private fun moveToTheBeginning(oldItem: ClipboardItem, newItem: ClipboardItem) {
        ioScope.launch {
            historyMutationMutex.withLock {
                val db = historyDatabase()
                db.runInTransaction {
                    db.clipboardItemDao().delete(oldItem.id)
                    db.clipboardItemDao().insert(newItem)
                }
            }
        }
    }

    fun insertClip(item: ClipboardItem) {
        if (!ClipboardRetentionPolicy.shouldPersist(item.isSensitive)) return
        ioScope.launch {
            historyMutationMutex.withLock {
                val id = historyDatabase().clipboardItemDao().insert(item)
                item.id = id
            }
        }
    }

    fun clearExactHistory(items: List<ClipboardItem>) {
        ioScope.launch {
            historyMutationMutex.withLock {
                val db = historyDatabase()
                val ids = items.map { it.id }.toSet()
                val removed = db.runInTransaction<List<ClipboardItem>> {
                    val dao = db.clipboardItemDao()
                    val existing = dao.getAll().filter { it.id in ids }
                    if (existing.isNotEmpty()) dao.delete(existing)
                    existing
                }
                disposeUnreferencedHistoryMedia(removed, db)
            }
        }
    }

    /**
     * Clears all unpinned items from the clipboard history
     */
    fun clearHistory() {
        ioScope.launch {
            historyMutationMutex.withLock {
                val db = historyDatabase()
                val removed = db.runInTransaction<List<ClipboardItem>> {
                    val dao = db.clipboardItemDao()
                    val old = dao.getAll().filterNot { it.isPinned }
                    dao.deleteAllUnpinned()
                    old
                }
                disposeUnreferencedHistoryMedia(removed, db)
            }
        }
    }

    /**
     * Clears the full clipboard history
     */
    fun clearFullHistory() {
        ioScope.launch { clearFullHistoryForRestore() }
    }


    /**
     * Restore the clipboard history from a [List]
     *
     * @param items the [ClipboardItem] list with the new items
     */
    /**
     * Restore uses awaiting, transaction-backed writes. The UI must never
     * report success before its history rows have actually been committed.
     */
    suspend fun restoreHistoryForRestore(items: List<ClipboardItem>) {
        withContext(Dispatchers.IO) {
            historyMutationMutex.withLock {
                val db = historyDatabase()
                db.runInTransaction {
                    val dao = db.clipboardItemDao()
                    val existing = dao.getAll().map { it.copy(id = 0) }.toMutableSet()
                    for (item in items) {
                        if (!ClipboardRetentionPolicy.shouldPersist(item.isSensitive)) continue
                        val candidate = item.copy(id = 0)
                        if (existing.add(candidate)) dao.insert(candidate)
                    }
                }
            }
        }
    }

    /**
     * Clear all history before restoring an archive. Unlike clearFullHistory(),
     * this function waits for the database transaction and file cleanup.
     */
    suspend fun clearFullHistoryForRestore() {
        withContext(Dispatchers.IO) {
            historyMutationMutex.withLock {
                val db = historyDatabase()
                val oldItems = db.runInTransaction<List<ClipboardItem>> {
                    val dao = db.clipboardItemDao()
                    val old = dao.getAll()
                    dao.deleteAll()
                    old
                }
                disposeUnreferencedHistoryMedia(oldItems, db)
            }
        }
    }

    fun restoreHistory(items: List<ClipboardItem>) {
        ioScope.launch { restoreHistoryForRestore(items) }
    }

    suspend fun deleteClipAwait(item: ClipboardItem, onlyIfUnpinned: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            historyMutationMutex.withLock {
                val db = historyDatabase()
                val deleted = db.runInTransaction<ClipboardItem?> {
                    val dao = db.clipboardItemDao()
                    val existing = dao.getAll().firstOrNull { it.id == item.id }
                    if (existing == null || (onlyIfUnpinned && existing.isPinned)) {
                        null
                    } else {
                        dao.delete(existing.id)
                        existing
                    }
                }
                // Only delete media if a database row was actually removed.
                deleted?.let { disposeUnreferencedHistoryMedia(listOf(it), db) }
                deleted != null
            }
        }
    }

    fun deleteClip(item: ClipboardItem, onlyIfUnpinned: Boolean) {
        ioScope.launch { deleteClipAwait(item, onlyIfUnpinned) }
    }

    fun pinClip(item: ClipboardItem) {
        ioScope.launch {
            historyMutationMutex.withLock {
                historyDatabase().clipboardItemDao().update(item.copy(isPinned = true))
            }
        }
    }

    fun unpinClip(item: ClipboardItem) {
        ioScope.launch {
            historyMutationMutex.withLock {
                historyDatabase().clipboardItemDao().update(item.copy(isPinned = false))
            }
        }
    }

    fun pasteItem(item: ClipboardItem) {
        val editorInstance by appContext.editorInstance()
        editorInstance.commitClipboardItem(item).also { result ->
            if (!result) {
                appContext.showShortToastSync("Failed to paste item.")
            }
        }
    }

    /**
     * Returns true if the editor can accept the clip item, else false.
     */
    fun canBePasted(clipItem: ClipboardItem?): Boolean {
        if (clipItem == null) return false

        return clipItem.mimeTypes.contains("text/plain") || editorInstance.activeInfo.contentMimeTypes.any { editorType ->
            clipItem.mimeTypes.any { clipType ->
                compareMimeTypes(clipType, editorType)
            }
        }
    }

    /**
     * Cleans up.
     *
     * Unregisters the system clipboard listener, cancels clipboard clean ups.
     */
    override fun close() {
        systemClipboardManager.removePrimaryClipChangedListener(this)
        cleanUpJob.cancel()
    }
}
