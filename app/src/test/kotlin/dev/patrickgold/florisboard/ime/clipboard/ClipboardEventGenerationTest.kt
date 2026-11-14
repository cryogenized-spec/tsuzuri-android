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

    test("sensitive flag key applies across Android API versions") {
        ClipboardRetentionPolicy.SENSITIVE_EXTRA shouldBe "android.content.extra.IS_SENSITIVE"
    }

    test("sensitive data is not eligible for persistent clipboard history") {
        ClipboardRetentionPolicy.shouldPersist(isSensitive = true) shouldBe false
        ClipboardRetentionPolicy.shouldPersist(isSensitive = false) shouldBe true
    }
})
