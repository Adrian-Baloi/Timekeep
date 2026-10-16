package com.example.timekeep_work.screen

import com.example.timekeep_work.Asset
import com.example.timekeep_work.AssignableUser
import com.example.timekeep_work.AssetManagementAction
import com.example.timekeep_work.AssetSite

/** Mutually exclusive states for the scan-and-lookup portion of the workflow. */
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

/** Form state for applying a management action to one selected asset. */
data class ManagementUiState(
    val assetId: Int? = null,
    val selectedAction: AssetManagementAction? = null,
    val userQuery: String = "",
    val userResults: List<AssignableUser> = emptyList(),
    val selectedUser: AssignableUser? = null,
    val userSearching: Boolean = false,
    val sites: List<AssetSite> = emptyList(),
    val selectedSite: AssetSite? = null,
    val sitesLoading: Boolean = false,
    val optionsFailure: String? = null,
    val phase: ManagementPhase = ManagementPhase.Editing,
    val failure: AssetUiState.Failure? = null,
) {
    val canSubmit: Boolean
        get() = selectedSite != null && when (selectedAction) {
            AssetManagementAction.Deploying -> selectedUser != null
            AssetManagementAction.ReturningToInventory -> true
            null -> false
        }
}

/** Stages of the management form and update. */
enum class ManagementPhase {
    Editing,
    Updating,
    Success,
}
