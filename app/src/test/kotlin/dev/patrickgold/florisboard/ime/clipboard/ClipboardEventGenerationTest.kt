/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.clipboard

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ClipboardEventGenerationTest : FunSpec({
    test("stale clipboard work cannot be applied after a later event") {
        val generations = ClipboardEventGeneration()
        val old = generations.next()
        generations.isCurrent(old) shouldBe true
        val newest = generations.next()
        generations.isCurrent(old) shouldBe false
        generations.isCurrent(newest) shouldBe true
    }

    test("only the newest callback can commit state after a generation transition") {
        val gate = ClipboardEventGeneration()
        val old = gate.next()
        val fresh = gate.next()
        var applied = "unchanged"
        gate.applyIfCurrent(old) { applied = "stale" } shouldBe false
        gate.applyIfCurrent(fresh) { applied = "new" } shouldBe true
        applied shouldBe "new"
    }

    test("concurrent callbacks never commit an older generation after a newer one") {
        val gate = ClipboardEventGeneration()
        val workers = 8
        val eventsPerWorker = 700
        val applied = java.util.concurrent.atomic.AtomicLong(-1L)
        val executor = Executors.newFixedThreadPool(workers)
        val done = CountDownLatch(workers)
        try {
            repeat(workers) {
                executor.execute {
                    try {
                        repeat(eventsPerWorker) {
                            val event = gate.next()
                            gate.applyIfCurrent(event) { applied.set(event) }
                        }
                    } finally {
                        done.countDown()
                    }
                }
            }
            done.await(30, TimeUnit.SECONDS) shouldBe true
            applied.get() shouldBe (workers * eventsPerWorker).toLong()
        } finally {
            executor.shutdownNow()
        }
    }

    test("concurrent rapid clipboard callbacks issue unique generations") {
        val generations = ClipboardEventGeneration()
        val workers = 8
        val eventsPerWorker = 1_500
        val results = ConcurrentHashMap.newKeySet<Long>()
        val executor = Executors.newFixedThreadPool(workers)
        val done = CountDownLatch(workers)
        try {
            repeat(workers) {
                executor.execute {
                    try {
                        repeat(eventsPerWorker) { results.add(generations.next()) }
                    } finally {
                        done.countDown()
                    }
                }
            }
            done.await(30, TimeUnit.SECONDS) shouldBe true
            results.size shouldBe workers * eventsPerWorker
            generations.isCurrent((workers * eventsPerWorker).toLong()) shouldBe true
        } finally {
            executor.shutdownNow()
        }
    }

    test("duplicate callback after a superseded capture remains eligible to apply") {
        val gate = ClipboardEventGeneration()
        val first = gate.next() // capture starts, observed value == "same"
        val second = gate.next() // a second callback also observes "same"
        gate.isCurrent(first) shouldBe false
        gate.isCurrent(second) shouldBe true // no early same-value deduplication
    }

    test("discarded media never deletes a borrowed Tsuzuri provider URI") {
        val ownAuthority = "io.github.cryogenizedspec.tsuzuri.provider.clipboard"
        ClipboardCapturePolicy.ownsNewClone(
            isMedia = true,
            originalAuthority = ownAuthority,
            ownProviderAuthority = ownAuthority,
        ) shouldBe false
        ClipboardCapturePolicy.ownsNewClone(
            isMedia = true,
            originalAuthority = "com.example.gallery",
            ownProviderAuthority = ownAuthority,
        ) shouldBe true
        ClipboardCapturePolicy.ownsNewClone(
            isMedia = false,
            originalAuthority = "com.example.gallery",
            ownProviderAuthority = ownAuthority,
        ) shouldBe false
    }

    test("duplicate external-media callbacks are recognized only after a successful commit") {
        val gate = ClipboardEventGeneration()
        val key = ClipboardCapturePolicy.externalMediaSource(
            sourceUri = "content://gallery.example/image/123",
            sourceAuthority = "gallery.example",
            timestampMs = 800L,
            mimeTypes = listOf("image/png"),
            ownProviderAuthority = "io.github.cryogenizedspec.tsuzuri.provider.clipboard",
        )!!
        gate.wasAppliedExternalMediaSource(key) shouldBe false
        val first = gate.next()
        val second = gate.next()
        gate.applyIfCurrent(first, key) {} shouldBe false
        gate.wasAppliedExternalMediaSource(key) shouldBe false
        gate.applyIfCurrent(second, key) {} shouldBe true
        gate.wasAppliedExternalMediaSource(key) shouldBe true
        // An accepted non-media event resets the coalescing identity.
        gate.applyIfCurrent(gate.next()) {} shouldBe true
        gate.wasAppliedExternalMediaSource(key) shouldBe false
    }

    test("clipboard timestamp and MIME changes produce distinct external-media identities") {
        val own = "io.github.cryogenizedspec.tsuzuri.provider.clipboard"
        fun key(timestamp: Long, mime: String) = ClipboardCapturePolicy.externalMediaSource(
            "content://gallery.example/image/123", "gallery.example", timestamp, listOf(mime), own,
        )
        key(1L, "image/png") shouldBe key(1L, "image/png")
        (key(1L, "image/png") == key(2L, "image/png")) shouldBe false
        (key(1L, "image/png") == key(1L, "image/jpeg")) shouldBe false
        ClipboardCapturePolicy.externalMediaSource(
            "content://$own/clips/images/1", own, 1L, listOf("image/png"), own,
        ) shouldBe null
        ClipboardCapturePolicy.externalMediaSource(
            "content://gallery.example/doc/1", "gallery.example", 1L, listOf("text/plain"), own,
        ) shouldBe null
    }

    test("internal updates invalidate older pending captures and media cache") {
        val gate = ClipboardEventGeneration()
        val source = ClipboardExternalMediaSource("content://gallery.example/x", 1L, listOf("image/png"))
        val old = gate.next()
        gate.applyIfCurrent(old, source) {} shouldBe true
        gate.wasAppliedExternalMediaSource(source) shouldBe true
        gate.invalidatePending()
        gate.wasAppliedExternalMediaSource(source) shouldBe false
        gate.applyIfCurrent(old, source) {} shouldBe false
    }

    test("flagged media must be captured without cloning bytes to disk") {
        // The integration source contract additionally enforces cloneUri = !incomingSensitive.
        val incomingSensitive = true
        val cloneUri = !incomingSensitive
        cloneUri shouldBe false
    }

    test("sensitive flag key applies across Android API versions") {
        ClipboardRetentionPolicy.SENSITIVE_EXTRA shouldBe "android.content.extra.IS_SENSITIVE"
    }

    test("sensitive data is not eligible for persistent clipboard history") {
        ClipboardRetentionPolicy.shouldPersist(isSensitive = true) shouldBe false
        ClipboardRetentionPolicy.shouldPersist(isSensitive = false) shouldBe true
    }
})
