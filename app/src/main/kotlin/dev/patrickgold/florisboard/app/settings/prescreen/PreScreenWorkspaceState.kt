/*
 * Copyright (C) 2026 Tsuzuri contributors
 * Licensed under the Apache License, Version 2.0.
 */
package dev.patrickgold.florisboard.app.settings.prescreen

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import dev.patrickgold.florisboard.ime.text.prescreen.PreScreenKind
import dev.patrickgold.florisboard.ime.text.prescreen.PreScreenProposal
import dev.patrickgold.florisboard.ime.text.prescreen.PreScreenSyntax
import kotlinx.coroutines.Job

/**
 * Navigation-entry-scoped, memory-only editing state. No SavedStateHandle,
 * Parcelable draft, disk backup, autosave, cloud sync or lifecycle persistence.
 * A rotated Activity or an AI Settings round-trip keeps the same ViewModel.
 * Removing the destination clears the draft and cancels any outstanding job.
 */
class PreScreenWorkspaceState : ViewModel() {
    val syntax = mutableStateOf(PreScreenSyntax.MARKDOWN)
    val draft = mutableStateOf(TextFieldValue(""))
    val instruction = mutableStateOf("")
    val proposal = mutableStateOf<PreScreenProposal?>(null)
    val proposalSource = mutableStateOf<String?>(null)
    val selectedCategory = mutableStateOf<PreScreenKind?>(null)
    val approvedText = mutableStateOf<String?>(null)
    val busy = mutableStateOf(false)
    val activeJob = mutableStateOf<Job?>(null)
    val requestGeneration = mutableStateOf(0)
    val feedback = mutableStateOf<String?>(null)

    override fun onCleared() {
        requestGeneration.value += 1
        activeJob.value?.cancel()
        activeJob.value = null
        draft.value = TextFieldValue("")
        instruction.value = ""
        proposal.value = null
        proposalSource.value = null
        selectedCategory.value = null
        approvedText.value = null
        feedback.value = null
        super.onCleared()
    }
}
