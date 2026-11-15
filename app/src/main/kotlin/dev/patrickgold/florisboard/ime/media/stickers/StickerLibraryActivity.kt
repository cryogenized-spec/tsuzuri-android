/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.media.stickers

import android.os.Bundle
import android.app.KeyguardManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Dedicated settings surface. The IME cannot own an ActivityResult launcher. */
class StickerLibraryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StickerLibraryScreen(onClose = { finish() })
                }
            }
        }
    }
}

@Composable
internal fun StickerThumbnail(item: StickerEntry, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, item.id) {
        value = withContext(Dispatchers.IO) {
            val path = StickerStore.fileFor(context, item)?.absolutePath
            path?.let {
                runCatching {
                    BitmapFactory.decodeFile(
                        it,
                        BitmapFactory.Options().apply { inSampleSize = 2 },
                    )?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    if (thumbnail != null) {
        Image(
            bitmap = requireNotNull(thumbnail),
            contentDescription = "Saved sticker image",
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    } else {
        Text("Image unavailable", modifier = modifier)
    }
}

@Composable
internal fun StickerLibraryScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val keyguard = remember { context.getSystemService(KeyguardManager::class.java) }
    if (keyguard == null || keyguard.isDeviceLocked || keyguard.isKeyguardLocked) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Unlock the device before managing private stickers.")
            TextButton(onClick = onClose) { Text("Close") }
        }
        return
    }
    val scope = rememberCoroutineScope()
    val revision by StickerStore.revision.collectAsState()
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        StickerStore.ensureLoaded(context)
        loaded = true
    }
    val packs = remember(revision) { StickerStore.packs() }
    var selectedPackId by rememberSaveable { mutableStateOf("default") }
    // The result can arrive after the manager is recreated. Preserve the
    // original destination independently of the current tab selection.
    var pendingImportPackId by rememberSaveable { mutableStateOf<String?>(null) }
    if (loaded && packs.none { it.id == selectedPackId }) selectedPackId = "default"
    val entries = remember(revision, selectedPackId) { StickerStore.stickers(selectedPackId) }
    var name by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<StickerEntry?>(null) }
    var pendingPackDelete by remember { mutableStateOf<StickerPack?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            val target = pendingImportPackId ?: selectedPackId
            importing = true
            scope.launch {
                try {
                    var success = 0
                    for (uri in uris.take(20)) {
                        if (StickerStore.importImage(context, target, uri)) success++
                    }
                    feedback = "Imported $success of ${uris.take(20).size} selected images. " +
                        "Supported formats: PNG, JPEG and WebP."
                } finally {
                    importing = false
                    pendingImportPackId = null
                }
            }
        }
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove sticker?") },
            text = { Text("This sticker will be deleted from your local library.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch { StickerStore.remove(context, entry.id) }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    pendingPackDelete?.let { pack ->
        AlertDialog(
            onDismissRequest = { pendingPackDelete = null },
            title = { Text("Delete ${pack.name}?") },
            text = { Text("This deletes the pack and all its local stickers.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingPackDelete = null
                    selectedPackId = "default"
                    scope.launch { StickerStore.deletePack(context, pack.id) }
                }) { Text("Delete pack") }
            },
            dismissButton = { TextButton(onClick = { pendingPackDelete = null }) { Text("Cancel") } },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("My stickers", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Done") }
        }
        Text(
            "Your images stay on this device. Import static PNG, JPEG or WebP; " +
                "Tsuzuri stores a 512 × 512 transparent-capable PNG copy. " +
                "Sending as a sticker depends on the messaging app.",
            style = MaterialTheme.typography.bodySmall,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(packs.size) { index ->
                val pack = packs[index]
                if (selectedPackId == pack.id) {
                    Button(onClick = { selectedPackId = pack.id }) { Text(pack.name) }
                } else {
                    OutlinedButton(onClick = { selectedPackId = pack.id }) { Text(pack.name) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(32) },
                singleLine = true,
                label = { Text("New pack name") },
                modifier = Modifier.weight(1f).testTag("sticker-new-pack-name"),
            )
            Button(
                enabled = StickerRules.validPackName(name.trim()) &&
                    packs.size < StickerRules.MAX_PACKS,
                onClick = {
                    val requested = name
                    scope.launch {
                        if (StickerStore.addPack(context, requested)) {
                            name = ""
                            feedback = "Pack created."
                        } else feedback = "Pack not created (duplicate or limit reached)."
                    }
                },
                modifier = Modifier.testTag("sticker-create-pack"),
            ) { Text("Create") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    pendingImportPackId = selectedPackId
                    picker.launch(arrayOf("image/png", "image/jpeg", "image/webp"))
                },
                enabled = !importing && StickerStore.count() < StickerRules.MAX_STICKERS,
                modifier = Modifier.testTag("sticker-import"),
            ) { Text(if (importing) "Importing…" else "Import images") }
            if (selectedPackId != "default") {
                OutlinedButton(
                    onClick = { pendingPackDelete = packs.firstOrNull { it.id == selectedPackId } },
                    enabled = !importing,
                ) { Text("Delete pack") }
            }
        }
        Text("${entries.size} in pack · ${StickerStore.count()} / ${StickerRules.MAX_STICKERS} total")
        if (feedback.isNotEmpty()) Text(feedback, style = MaterialTheme.typography.bodySmall)
        if (entries.isEmpty()) {
            Text("No stickers yet. Import a photo or transparent image to get started.")
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(entries, key = { it.id }) { item ->
                Column {
                    StickerThumbnail(item, Modifier.size(96.dp))
                    TextButton(onClick = { pendingDelete = item }) { Text("Remove") }
                }
            }
        }
    }
}
