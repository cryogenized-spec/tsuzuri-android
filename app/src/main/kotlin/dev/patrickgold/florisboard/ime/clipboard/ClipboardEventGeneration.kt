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

    fun next(): Long = latest.incrementAndGet()
    fun isCurrent(epoch: Long): Boolean = latest.get() == epoch
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
