/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Test-APK provider simulating a writable third-party image source.
 * Delete attempts are counted through a Binder-accessible call, so tests do
 * not rely on a counter shared with the provider process.
 */
class TsuzuriWritableTestProvider : ContentProvider() {
    private val deleteCount = AtomicInteger()
    private val readCount = AtomicInteger()

    override fun onCreate(): Boolean = true
    override fun getType(uri: Uri): String = "image/png"
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        deleteCount.incrementAndGet()
        return 1
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        readCount.incrementAndGet()
        val file = File(requireNotNull(context).cacheDir, "tsuzuri-third-party-fixture.png")
        file.writeBytes("fake-test-image-bytes".toByteArray())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        return when (method) {
            "reset" -> {
                deleteCount.set(0)
                readCount.set(0)
                Bundle()
            }
            "counts" -> Bundle().apply {
                putInt("delete", deleteCount.get())
                putInt("read", readCount.get())
            }
            else -> null
        }
    }
}
