/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.text.dictation

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes key edits and provider switching. The only key store in this milestone
 * is shared across cloud dictation providers: a host switch must first commit removal
 * of the old key, and must also clear any still-present legacy plaintext migration key.
 */
/**
 * One process-wide lock protects BOTH the settings writer and the dictation session's
 * key + endpoint + model snapshot. This must not be a new Mutex per screen instance.
 */
object CloudVoiceProviderGuard {
    val mutex = Mutex()
}

class CloudVoiceProviderSwitcher(
    private val currentEndpoint: suspend () -> String,
    private val currentModel: suspend () -> String,
    private val durablyEraseKey: suspend () -> Boolean,
    private val clearLegacyKey: suspend () -> Unit,
    private val updateEndpoint: suspend (String) -> Unit,
    private val updateModel: suspend (String) -> Unit,
) {
    private val mutex = CloudVoiceProviderGuard.mutex

    sealed interface SwitchResult {
        data class Applied(val keyCleared: Boolean) : SwitchResult
        data object KeyDeletionFailed : SwitchResult
        data class SettingsUpdateFailed(
            val keyCleared: Boolean,
            val restoredPreviousSettings: Boolean,
        ) : SwitchResult
    }

    /**
     * Only sessions that take CloudVoiceProviderGuard.mutex observe the provider
     * as an atomic endpoint/model pair. The underlying preferences are separate,
     * so compensate for failed writes before releasing that lock.
     * Even after a failed cross-host update the previous key stays deleted.
     */
    suspend fun switchTo(endpoint: String, model: String): SwitchResult = mutex.withLock {
        val oldEndpoint: String
        val oldModel: String
        try {
            oldEndpoint = currentEndpoint()
            oldModel = currentModel()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            return@withLock SwitchResult.SettingsUpdateFailed(
                keyCleared = false, restoredPreviousSettings = false,
            )
        }
        val crossHost = CloudTranscriptionPresets.mustClearApiKey(oldEndpoint, endpoint)
        if (crossHost) {
            val erased = try {
                durablyEraseKey()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (!erased) return@withLock SwitchResult.KeyDeletionFailed
            try {
                clearLegacyKey()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Fail closed: never publish a new host if a legacy key might persist.
                return@withLock SwitchResult.KeyDeletionFailed
            }
        }
        try {
            // Publish the endpoint last; session snapshots cannot see partial
            // state because they share this process-wide mutex.
            updateModel(model)
            updateEndpoint(endpoint)
            SwitchResult.Applied(keyCleared = crossHost)
        } catch (failure: Exception) {
            // Roll back even if the caller was cancelled during the first write.
            // A persistence outage can defeat compensation; surface this rather
            // than claiming that the previous settings were restored.
            val restored = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                var ok = true
                try { updateModel(oldModel) } catch (_: Exception) { ok = false }
                try { updateEndpoint(oldEndpoint) } catch (_: Exception) { ok = false }
                ok
            }
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            SwitchResult.SettingsUpdateFailed(
                keyCleared = crossHost,
                restoredPreviousSettings = restored,
            )
        }
    }

    /** Prevent stale Key-dialog completions from depositing credentials into a new host. */
    suspend fun editKeyForHost(expectedEndpoint: String, edit: suspend () -> Boolean): Boolean = mutex.withLock {
        if (CloudTranscriptionPresets.mustClearApiKey(currentEndpoint(), expectedEndpoint)) {
            return@withLock false
        }
        edit()
    }
}
