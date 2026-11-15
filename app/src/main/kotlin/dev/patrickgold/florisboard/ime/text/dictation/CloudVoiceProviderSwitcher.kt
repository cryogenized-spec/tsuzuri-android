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
    private val durablyEraseKey: suspend () -> Boolean,
    private val clearLegacyKey: suspend () -> Unit,
    private val updateEndpoint: suspend (String) -> Unit,
    private val updateModel: suspend (String) -> Unit,
) {
    private val mutex = CloudVoiceProviderGuard.mutex

    sealed interface SwitchResult {
        data class Applied(val keyCleared: Boolean) : SwitchResult
        data object KeyDeletionFailed : SwitchResult
    }

    suspend fun switchTo(endpoint: String, model: String): SwitchResult = mutex.withLock {
        val oldEndpoint = currentEndpoint()
        val crossHost = CloudTranscriptionPresets.mustClearApiKey(oldEndpoint, endpoint)
        if (crossHost) {
            // Failure must leave the previous endpoint untouched.
            if (!durablyEraseKey()) return@withLock SwitchResult.KeyDeletionFailed
            clearLegacyKey()
        }
        updateEndpoint(endpoint)
        updateModel(model)
        SwitchResult.Applied(keyCleared = crossHost)
    }

    /** Prevent stale Key-dialog completions from depositing credentials into a new host. */
    suspend fun editKeyForHost(expectedEndpoint: String, edit: suspend () -> Boolean): Boolean = mutex.withLock {
        if (CloudTranscriptionPresets.mustClearApiKey(currentEndpoint(), expectedEndpoint)) {
            return@withLock false
        }
        edit()
    }
}
