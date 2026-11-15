/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.media.stickers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.editorInstance

internal fun openStickerLibrary(context: Context) {
    context.startActivity(
        Intent(context, StickerLibraryActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/** Sticker tab in the IME. No filesystem operations occur while handling a tap. */
@Composable
internal fun StickerPalette(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val editor by context.editorInstance()
    val revision by StickerStore.revision.collectAsState()
    LaunchedEffect(Unit) { StickerStore.ensureLoaded(context) }
    val packs = remember(revision) { StickerStore.packs() }
    var selectedPack by remember { mutableStateOf("default") }
    if (packs.none { it.id == selectedPack }) selectedPack = "default"
    val entries = remember(revision, selectedPack) { StickerStore.stickers(selectedPack) }
    var feedback by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(packs.size) { index ->
                val pack = packs[index]
                if (selectedPack == pack.id) {
                    Button(onClick = { selectedPack = pack.id }) { Text(pack.name) }
                } else {
                    OutlinedButton(onClick = { selectedPack = pack.id }) { Text(pack.name) }
                }
            }
        }
        if (entries.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                Text("No stickers in this pack.")
                TextButton(onClick = { openStickerLibrary(context) }) {
                    Text("Import images and manage packs")
                }
            }
        } else {
            if (feedback.isNotBlank()) {
                Text(
                    feedback,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(88.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items(entries, key = { it.id }) { item ->
                    Column {
                        Box(
                            modifier = Modifier
                                .size(82.dp)
                                .testTag("sticker-send-${item.id}")
                                .clickable {
                                    feedback = if (editor.commitSticker(item.id)) {
                                        "Sent as an image."
                                    } else {
                                        "This app did not accept the image. Try Copy."
                                    }
                                },
                        ) {
                            StickerThumbnail(item, Modifier.fillMaxSize())
                        }
                        TextButton(
                            onClick = {
                                val uri = StickerStore.uriFor(context, item)
                                if (uri == null) {
                                    feedback = "Sticker file is unavailable."
                                } else {
                                    val manager = context.getSystemService(
                                        Context.CLIPBOARD_SERVICE,
                                    ) as ClipboardManager
                                    try {
                                        manager.setPrimaryClip(
                                            ClipData.newUri(
                                                context.contentResolver,
                                                "Tsuzuri sticker",
                                                uri,
                                            ),
                                        )
                                        feedback = "Image copied; paste it into the target app."
                                    } catch (_: Exception) {
                                        feedback = "Could not copy this image."
                                    }
                                }
                            },
                            modifier = Modifier.testTag("sticker-copy-${item.id}"),
                        ) { Text("Copy") }
                    }
                }
            }
        }
    }
}
