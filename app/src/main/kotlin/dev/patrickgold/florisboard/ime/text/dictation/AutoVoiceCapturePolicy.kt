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
    /**
     * The legacy empty preference selects cloud dictation when an encrypted cloud key
     * already exists. Do not read Keystore on the keyboard thread: the coordinator
     * verifies that key asynchronously before any microphone capture.
     */
    fun backendForAuto(preference: String): TranscriptionBackend = when (preference) {
        TranscriptionBackend.CLOUD.preference, "" -> TranscriptionBackend.CLOUD
        TranscriptionBackend.ORUKEET.preference -> TranscriptionBackend.ORUKEET
        else -> TranscriptionBackend.UNAVAILABLE
    }

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
