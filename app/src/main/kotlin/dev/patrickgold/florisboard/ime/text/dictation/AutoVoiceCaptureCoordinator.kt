/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.text.dictation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A testable focus-scoped activation gate. A slow cloud key lookup may complete after the
 * editor has changed, the keyboard has closed, or Stop was pressed. None may open the mic.
 * Only the current editor's explicit Auto preference can authorize capture.
 */
internal data class AutoVoiceCaptureContext(
    val enabled: Boolean,
    val backend: TranscriptionBackend,
    val editorSessionId: Long,
    val eligibleEditor: Boolean,
    val micPermission: Boolean,
    val backendReady: Boolean,
    val alreadyActive: Boolean,
)

internal class AutoVoiceCaptureCoordinator(
    private val scope: CoroutineScope,
    private val current: () -> AutoVoiceCaptureContext,
    private val configured: suspend (TranscriptionBackend) -> Boolean,
    private val start: (TranscriptionBackend) -> Unit,
) {
    private var visibleEligible = false
    private var stoppedByUser = false
    private var epoch = 0L
    private var pending: Job? = null

    fun onKeyboardFocus(eligible: Boolean) {
        visibleEligible = eligible
        if (!eligible) invalidatePending() else attempt()
    }

    /** The editor availability flow can lag the keyboard's synchronous focus callback. */
    fun onAvailabilityChanged(available: Boolean) {
        if (!available) invalidatePending() else attempt()
    }

    /** All Stop/Cancel entry points, including a recording-row stop, use this gate. */
    fun onUserStop() {
        stoppedByUser = true
        invalidatePending()
    }

    fun onSessionInvalidated(keyboardHidden: Boolean) {
        visibleEligible = false
        if (keyboardHidden) stoppedByUser = false
        invalidatePending()
    }

    private fun invalidatePending() {
        epoch++
        pending?.cancel()
        pending = null
    }

    private fun attempt() {
        if (!visibleEligible || stoppedByUser || pending?.isActive == true) return
        val before = current()
        if (before.editorSessionId <= 0L || !canStart(before, configured = before.backendReady)) return
        val requestEpoch = epoch
        pending = scope.launch {
            try {
                val isConfigured = configured(before.backend)
                val after = current()
                if (requestEpoch == epoch &&
                    visibleEligible &&
                    !stoppedByUser &&
                    before.editorSessionId == after.editorSessionId &&
                    before.backend == after.backend &&
                    canStart(after, configured = isConfigured)
                ) {
                    start(before.backend)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                // Failed credential/model preflight never starts recording.
            } finally {
                if (requestEpoch == epoch) pending = null
            }
        }
    }

    private fun canStart(context: AutoVoiceCaptureContext, configured: Boolean): Boolean =
        AutoVoiceCapturePolicy.shouldStart(
            explicitlyEnabled = context.enabled,
            backend = context.backend,
            eligibleEditor = context.eligibleEditor,
            hasMicPermission = context.micPermission,
            configured = configured && context.backendReady,
            alreadyActive = context.alreadyActive,
            stoppedByUser = stoppedByUser,
        )
}
