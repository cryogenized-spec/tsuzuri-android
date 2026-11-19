/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.ime.media.stickers

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * For a keyboard, merely checking the keyguard once is insufficient: the
 * screen may lock while its composition remains alive. Fail closed immediately
 * on SCREEN_OFF and refresh visibility only after an unlock event.
 */
@Composable
internal fun rememberStickerDeviceUnlocked(): Boolean {
    val context = LocalContext.current
    val keyguard = remember(context) { context.getSystemService(KeyguardManager::class.java) }
    fun unlockedNow() = keyguard != null &&
        !keyguard.isDeviceLocked && !keyguard.isKeyguardLocked
    var unlocked by remember(context) { mutableStateOf(unlockedNow()) }

    DisposableEffect(context, keyguard) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                unlocked = if (intent?.action == Intent.ACTION_SCREEN_OFF) false
                else unlockedNow()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_USER_UNLOCKED)
        }
        ContextCompat.registerReceiver(
            context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        unlocked = unlockedNow()
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
    return unlocked && unlockedNow()
}
