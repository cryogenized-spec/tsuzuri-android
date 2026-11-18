/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Instrumentation APK provider for a writable third-party image source.
 *
 * Deliberately Java-only: the test APK runs in its own process and must not
 * require Kotlin stdlib classes that may live only in the target app's APK.
 * The provider counts deletes and reads, allowing privacy tests to verify
 * that the keyboard does not damage borrowed external content.
 */
public final class TsuzuriWritableTestProvider extends ContentProvider {
    private final AtomicInteger deleteCount = new AtomicInteger();
    private final AtomicInteger readCount = new AtomicInteger();

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) { return "image/png"; }

    @Override public Cursor query(
        Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder
    ) { return null; }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }

    @Override public int update(
        Uri uri, ContentValues values, String selection, String[] selectionArgs
    ) { return 0; }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        deleteCount.incrementAndGet();
        return 1;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode)
        throws FileNotFoundException {
        readCount.incrementAndGet();
        final Context context = getContext();
        if (context == null) {
            throw new FileNotFoundException("Test provider context unavailable");
        }
        final File file = new File(context.getCacheDir(), "tsuzuri-third-party-fixture.png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write("fake-test-image-bytes".getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new FileNotFoundException("Unable to create synthetic media fixture");
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        if ("reset".equals(method)) {
            deleteCount.set(0);
            readCount.set(0);
            return new Bundle();
        }
        if ("counts".equals(method)) {
            Bundle result = new Bundle();
            result.putInt("delete", deleteCount.get());
            result.putInt("read", readCount.get());
            return result;
        }
        return null;
    }
}
