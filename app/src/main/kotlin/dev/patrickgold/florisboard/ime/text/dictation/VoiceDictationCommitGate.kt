/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.text.dictation

/**
 * Capture editor session identity BEFORE recording. A voice result must not be
 * inserted into another application's input field after an asynchronous request.
 * Actual secure/incognito policies remain authoritative and are checked at commit.
 */
internal object VoiceDictationCommitGate {
    fun mayCommit(
        originalEditorSessionId: Long,
        currentEditorSessionId: Long,
        leaseCurrent: Boolean,
        aiAvailable: Boolean,
    ): Boolean =
        originalEditorSessionId > 0L &&
            originalEditorSessionId == currentEditorSessionId &&
            leaseCurrent && aiAvailable
}
