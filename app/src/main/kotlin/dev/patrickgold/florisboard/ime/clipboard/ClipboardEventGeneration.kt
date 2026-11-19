/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.clipboard

import java.util.concurrent.atomic.AtomicLong

/**
 * Monotonic epoch for clipboard listener callbacks.
 *
 * The source clipboard may change while a coroutine prepares a rich-content URI.
 * Work created for an older epoch must not overwrite newer clipboard content.
 * Database mutations still need a separate transactional/serialized path.
 */
internal class ClipboardEventGeneration {
    private val latest = AtomicLong(0L)
    private var lastAppliedExternalMedia: ClipboardExternalMediaSource? = null

    fun next(): Long = synchronized(this) { latest.incrementAndGet() }
    fun isCurrent(epoch: Long): Boolean = latest.get() == epoch

    /** An explicit internal clipboard update supersedes any pending external callback. */
    fun invalidatePending() = synchronized(this) {
        latest.incrementAndGet()
        lastAppliedExternalMedia = null
    }

    /** Cache identity only after successful application, never while work is pending. */
    fun wasAppliedExternalMediaSource(key: ClipboardExternalMediaSource): Boolean =
        synchronized(this) { lastAppliedExternalMedia == key }

    /** Atomically compare, commit and mark applied source. */
    fun applyIfCurrent(
        epoch: Long,
        mediaSource: ClipboardExternalMediaSource? = null,
        update: () -> Unit,
    ): Boolean = synchronized(this) {
        if (latest.get() != epoch) false else {
            update()
            lastAppliedExternalMedia = mediaSource
            true
        }
    }
}

/**
 * Sensitive clips are permitted as ephemeral primary clipboard content, but
 * they must never be inserted into ordinary persistent clipboard history.
 * The rule applies even when a user disables expiry or pins a clip.
 */
internal object ClipboardRetentionPolicy {
    const val SENSITIVE_EXTRA = "android.content.extra.IS_SENSITIVE"

    fun shouldPersist(isSensitive: Boolean): Boolean = !isSensitive
}

/**
 * Only URI media newly cloned from an external content provider may be
 * discarded after the capture callback is superseded.
 */
internal object ClipboardCapturePolicy {
    fun ownsNewClone(isMedia: Boolean, originalAuthority: String?, ownProviderAuthority: String): Boolean =
        isMedia && originalAuthority != null && originalAuthority != ownProviderAuthority

    fun externalMediaSource(
        sourceUri: String?,
        sourceAuthority: String?,
        timestampMs: Long,
        mimeTypes: List<String>,
        ownProviderAuthority: String,
    ): ClipboardExternalMediaSource? {
        if (sourceUri.isNullOrBlank() || sourceAuthority == ownProviderAuthority) return null
        if (mimeTypes.none { it.startsWith("image/") || it.startsWith("video/") }) return null
        return ClipboardExternalMediaSource(sourceUri, timestampMs, mimeTypes)
    }
}

/**
 * Source URI, MIME and setPrimaryClip timestamp uniquely identify one external
 * media clipboard event without retaining the media bytes or text.
 */
internal data class ClipboardExternalMediaSource(
    val uri: String,
    val timestampMs: Long,
    val mimeTypes: List<String>,
)
