package com.example.timekeep_work.screen

import com.example.timekeep_work.Asset

sealed interface AssetUiState {
    data object Ready : AssetUiState
    data object Scanning : AssetUiState
    data object Searching : AssetUiState
    data class NoMatch(val identifier: String) : AssetUiState
    data class MultipleMatches(val assets: List<Asset>) : AssetUiState
    data class MatchFound(val asset: Asset) : AssetUiState
    data class Failure(
        val message: String,
        val retryable: Boolean,
    ) : AssetUiState
}

data class ManagementUiState(
    val assetId: Int? = null,
    val selectedStatus: String = "In use",
    val reviewed: Boolean = false,
    val phase: ManagementPhase = ManagementPhase.Editing,
    val failure: AssetUiState.Failure? = null,
)

enum class ManagementPhase {
    Editing,
    Confirming,
    Updating,
    Success,
}
