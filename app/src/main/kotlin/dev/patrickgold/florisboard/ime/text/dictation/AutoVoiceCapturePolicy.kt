/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.text.dictation

/**
 * Automatic microphone capture is a separate opt-in, never inferred from microphone permission
 * or from enabling dictation. A user-initiated stop blocks auto-restart until the keyboard hides.
 * Neither unsupported system voice input nor the mock backend may start without a key press.
 */
internal object AutoVoiceCapturePolicy {
    fun shouldStart(
        explicitlyEnabled: Boolean,
        backend: TranscriptionBackend,
        eligibleEditor: Boolean,
        hasMicPermission: Boolean,
        configured: Boolean,
        alreadyActive: Boolean,
        stoppedByUser: Boolean,
    ): Boolean = explicitlyEnabled &&
        backend in setOf(TranscriptionBackend.ORUKEET, TranscriptionBackend.CLOUD) &&
        eligibleEditor &&
        hasMicPermission &&
        configured &&
        !alreadyActive &&
        !stoppedByUser
}
