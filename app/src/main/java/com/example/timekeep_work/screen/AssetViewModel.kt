package com.example.timekeep_work.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timekeep_work.Asset
import com.example.timekeep_work.AssignableUser
import com.example.timekeep_work.AssetManagementAction
import com.example.timekeep_work.AssetSite
import com.example.timekeep_work.AssetUpdate
import com.example.timekeep_work.data.AppDependencies
import com.example.timekeep_work.data.AssetRepositoryException
import com.example.timekeep_work.data.AssetRepository
import com.example.timekeep_work.scanner.IdentifierNormalizationResult
import com.example.timekeep_work.scanner.IdentifierNormalizer
import com.example.timekeep_work.scanner.InvalidIdentifierReason
import com.example.timekeep_work.scanner.NormalizedIdentifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Coordinates lookup and asset-management state for the Compose workflow.
 *
 * The ViewModel exposes immutable flows to the UI and keeps navigation-recovery details, such
 * as the last identifier and candidate list, private.
 */
class AssetViewModel(
    private val repository: AssetRepository = AppDependencies.assetRepository,
    private val identifierNormalizer: IdentifierNormalizer = IdentifierNormalizer(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<AssetUiState>(AssetUiState.Ready)
    val uiState: StateFlow<AssetUiState> = _uiState.asStateFlow()

    private val _serialInput = MutableStateFlow(DEMO_SCANNED_IDENTIFIER)
    val serialInput: StateFlow<String> = _serialInput.asStateFlow()

    private val _managementUiState = MutableStateFlow(ManagementUiState())
    val managementUiState: StateFlow<ManagementUiState> = _managementUiState.asStateFlow()

    private var lookupJob: Job? = null
    private var updateJob: Job? = null
    private var userSearchJob: Job? = null
    private var siteLoadJob: Job? = null
    // Snapshots used to rebuild a screen after navigation or retry the exact failed operation.
    private var lastIdentifier: String? = null
    private var lastCandidates: List<Asset>? = null
    private var lastRequestedAssetId: Int? = null
    private var failedOperation: FailedOperation? = null

    fun onSignedIn() {
        _uiState.value = AssetUiState.Ready
    }

    /** Cancels work and removes all user/session-specific workflow state. */
    fun onSignOut() {
        lookupJob?.cancel()
        updateJob?.cancel()
        userSearchJob?.cancel()
        siteLoadJob?.cancel()
        lastIdentifier = null
        lastCandidates = null
        lastRequestedAssetId = null
        failedOperation = null
        _managementUiState.value = ManagementUiState()
        _uiState.value = AssetUiState.Ready
    }

    fun onScanRequested() {
        if (_uiState.value == AssetUiState.Scanning) return
        lookupJob?.cancel()
        _uiState.value = AssetUiState.Scanning
    }

    fun onScanCancelled() {
        lookupJob?.cancel()
        _uiState.value = AssetUiState.Ready
    }

    fun onSerialChanged(identifier: String) {
        _serialInput.value = identifier
    }

    fun onSerialSubmitted(identifier: String = _serialInput.value): IdentifierNormalizationResult {
        val result = identifierNormalizer.normalize(identifier)
        when (result) {
            is IdentifierNormalizationResult.Success -> onIdentifierSubmitted(result.identifier)
            is IdentifierNormalizationResult.Invalid -> {
                failedOperation = null
                _uiState.value = AssetUiState.Failure(
                    message = result.reason.validationMessage(),
                    retryable = false,
                )
            }
        }
        return result
    }

    fun onIdentifierSubmitted(identifier: NormalizedIdentifier) {
        _serialInput.value = identifier.value
        search(identifier.value)
    }

    /** Reuses cached candidates when Navigation Compose recreates the same destination. */
    fun onCandidatesShown(identifier: String) {
        val result = identifierNormalizer.normalize(identifier)
        if (result !is IdentifierNormalizationResult.Success) {
            failedOperation = null
            _uiState.value = AssetUiState.Failure(
                message = (result as IdentifierNormalizationResult.Invalid).reason.validationMessage(),
                retryable = false,
            )
            return
        }
        val normalized = result.identifier.value
        if (normalized == lastIdentifier) {
            lastCandidates?.let {
                publishCandidates(normalized, it)
                return
            }
            if (lookupJob?.isActive == true) return
        }
        onIdentifierSubmitted(result.identifier)
    }

    fun onAssetSelected(assetId: Int) {
        val currentAsset = (_uiState.value as? AssetUiState.MatchFound)?.asset
        if (currentAsset?.id == assetId) {
            prepareManagement(currentAsset)
            return
        }

        lastCandidates?.firstOrNull { it.id == assetId }?.let {
            selectAsset(it)
            return
        }

        loadAsset(assetId)
    }

    fun onManagementActionRequested() {
        val state = _managementUiState.value
        _managementUiState.value = if (state.phase == ManagementPhase.Success) {
            ManagementUiState(
                assetId = state.assetId,
                sites = state.sites,
            )
        } else {
            state.copy(
                phase = ManagementPhase.Editing,
                failure = null,
            )
        }
        loadSitesIfNeeded()
    }

    fun onActionSelected(action: AssetManagementAction) {
        userSearchJob?.cancel()
        _managementUiState.value = _managementUiState.value.copy(
            selectedAction = action,
            userQuery = "",
            userResults = emptyList(),
            selectedUser = null,
            userSearching = false,
            optionsFailure = null,
            phase = ManagementPhase.Editing,
            failure = null,
        )
    }

    fun onUserQueryChanged(query: String) {
        userSearchJob?.cancel()
        val normalized = query.trim()
        val shouldSearch =
            _managementUiState.value.selectedAction == AssetManagementAction.Deploying &&
                normalized.length >= MIN_USER_QUERY_LENGTH
        _managementUiState.value = _managementUiState.value.copy(
            userQuery = query,
            userResults = emptyList(),
            selectedUser = null,
            userSearching = shouldSearch,
            optionsFailure = null,
            phase = ManagementPhase.Editing,
            failure = null,
        )
        if (!shouldSearch) return

        userSearchJob = viewModelScope.launch {
            // Debouncing prevents a Halo request for every keystroke while the user is typing.
            delay(USER_SEARCH_DEBOUNCE_MS)
            runCatching { repository.searchAssignableUsers(normalized) }
                .onSuccess { users ->
                    _managementUiState.value = _managementUiState.value.copy(
                        userResults = users,
                        userSearching = false,
                        optionsFailure = null,
                    )
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _managementUiState.value = _managementUiState.value.copy(
                        userResults = emptyList(),
                        userSearching = false,
                        optionsFailure = error.toFailure("Users could not be searched.").message,
                    )
                }
        }
    }

    fun onUserSelected(user: AssignableUser) {
        _managementUiState.value = _managementUiState.value.copy(
            userQuery = user.name,
            userResults = emptyList(),
            selectedUser = user,
            userSearching = false,
            optionsFailure = null,
            phase = ManagementPhase.Editing,
            failure = null,
        )
    }

    fun onSiteSelected(site: AssetSite) {
        _managementUiState.value = _managementUiState.value.copy(
            selectedSite = site,
            optionsFailure = null,
            phase = ManagementPhase.Editing,
            failure = null,
        )
    }

    /** Submits one update as soon as the required fields are filled. */
    fun onSubmitUpdate() {
        val state = _managementUiState.value
        val assetId = state.assetId ?: return
        val site = state.selectedSite ?: return
        val update = when (state.selectedAction) {
            AssetManagementAction.Deploying -> AssetUpdate.Deploy(
                assetId = assetId,
                site = site,
                user = state.selectedUser ?: return,
            )

            AssetManagementAction.ReturningToInventory -> AssetUpdate.ReturnToInventory(
                assetId = assetId,
                site = site,
            )

            null -> return
        }
        val currentAsset = (_uiState.value as? AssetUiState.MatchFound)?.asset
            ?.takeIf { it.id == assetId }
            ?: return
        if (!state.canSubmit || state.phase != ManagementPhase.Editing || updateJob?.isActive == true) return

        _managementUiState.value = state.copy(
            phase = ManagementPhase.Updating,
            failure = null,
        )
        updateJob = viewModelScope.launch {
            runCatching { repository.updateAsset(update) }
                .onSuccess {
                    // Halo accepted the write; update the local snapshot without an immediate refetch.
                    val updatedAsset = currentAsset.applyAcceptedUpdate(update)
                    failedOperation = null
                    _uiState.value = AssetUiState.MatchFound(updatedAsset)
                    lastCandidates = lastCandidates?.map { candidate ->
                        if (candidate.id == updatedAsset.id) updatedAsset else candidate
                    }
                    _managementUiState.value = _managementUiState.value.copy(
                        phase = ManagementPhase.Success,
                        failure = null,
                    )
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    failedOperation = FailedOperation.Update
                    _managementUiState.value = _managementUiState.value.copy(
                        phase = ManagementPhase.Editing,
                        failure = error.toFailure("The asset could not be updated."),
                    )
                }
        }
    }

    /** Replays only the operation that produced the current failure. */
    fun onRetry() {
        when (failedOperation) {
            FailedOperation.Search -> lastIdentifier?.let(::search)
            FailedOperation.LoadAsset -> lastRequestedAssetId?.let(::loadAsset)
            FailedOperation.Update -> onSubmitUpdate()
            null -> Unit
        }
    }

    fun onRetryLookup() {
        lastIdentifier?.let(::search)
    }

    fun onStartAnotherScan() {
        clearLookup()
        _uiState.value = AssetUiState.Scanning
    }

    fun onReturnToScanner() {
        clearLookup()
        _uiState.value = AssetUiState.Ready
    }

    private fun clearLookup() {
        lookupJob?.cancel()
        updateJob?.cancel()
        userSearchJob?.cancel()
        siteLoadJob?.cancel()
        lastIdentifier = null
        lastCandidates = null
        lastRequestedAssetId = null
        failedOperation = null
        _managementUiState.value = ManagementUiState()
    }

    private fun search(identifier: String) {
        lookupJob?.cancel()
        lastIdentifier = identifier
        lastCandidates = null
        failedOperation = null
        _uiState.value = AssetUiState.Searching
        lookupJob = viewModelScope.launch {
            runCatching { repository.findByIdentifier(identifier).toList() }
                .onSuccess { assets ->
                    lastCandidates = assets
                    failedOperation = null
                    publishCandidates(identifier, assets)
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    failedOperation = FailedOperation.Search
                    _uiState.value = error.toFailure("The asset search failed.")
                }
        }
    }

    /**
     * Restores a selected asset by repeating the last identifier lookup, then selecting by id.
     * The repository intentionally exposes identifier lookup rather than a separate get-by-id API.
     */
    private fun loadAsset(assetId: Int) {
        lookupJob?.cancel()
        lastRequestedAssetId = assetId
        failedOperation = null
        val identifier = lastIdentifier
        if (identifier == null) {
            _uiState.value = AssetUiState.Failure(
                message = "The asset could not be loaded. Scan or search for it again.",
                retryable = false,
            )
            return
        }
        _uiState.value = AssetUiState.Searching
        lookupJob = viewModelScope.launch {
            runCatching {
                repository.findByIdentifier(identifier).firstOrNull { it.id == assetId }
            }
                .onSuccess { asset ->
                    if (asset == null) {
                        failedOperation = FailedOperation.LoadAsset
                        _uiState.value = AssetUiState.Failure(
                            message = "Asset $assetId was not found.",
                            retryable = false,
                        )
                    } else {
                        failedOperation = null
                        selectAsset(asset)
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    failedOperation = FailedOperation.LoadAsset
                    _uiState.value = error.toFailure("Asset $assetId could not be loaded.")
                }
        }
    }

    /** Collapses a repository result into the three lookup outcomes understood by the UI. */
    private fun publishCandidates(identifier: String, assets: List<Asset>) {
        _uiState.value = when (assets.size) {
            0 -> AssetUiState.NoMatch(identifier)
            1 -> AssetUiState.MatchFound(assets.single())
            else -> AssetUiState.MultipleMatches(assets.toList())
        }
    }

    private fun selectAsset(asset: Asset) {
        lastRequestedAssetId = asset.id
        _uiState.value = AssetUiState.MatchFound(asset)
        prepareManagement(asset)
    }

    private fun prepareManagement(asset: Asset) {
        if (_managementUiState.value.assetId == asset.id) return
        _managementUiState.value = ManagementUiState(assetId = asset.id)
    }

    private fun loadSitesIfNeeded() {
        val state = _managementUiState.value
        if (state.assetId == null || state.sites.isNotEmpty() || siteLoadJob?.isActive == true) return
        _managementUiState.value = state.copy(sitesLoading = true, optionsFailure = null)
        siteLoadJob = viewModelScope.launch {
            runCatching { repository.getActiveSites() }
                .onSuccess { sites ->
                    _managementUiState.value = _managementUiState.value.copy(
                        sites = sites,
                        sitesLoading = false,
                        optionsFailure = if (sites.isEmpty()) "Halo returned no active sites." else null,
                    )
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _managementUiState.value = _managementUiState.value.copy(
                        sitesLoading = false,
                        optionsFailure = error.toFailure("Sites could not be loaded.").message,
                    )
                }
        }
    }

    private fun Throwable.toFailure(fallbackMessage: String): AssetUiState.Failure {
        val appError = (this as? AssetRepositoryException)?.error
        return AssetUiState.Failure(
            message = appError?.technicianMessage ?: fallbackMessage,
            retryable = appError?.retryable ?: false,
        )
    }

    private enum class FailedOperation {
        Search,
        LoadAsset,
        Update,
    }

    companion object {
        const val DEMO_SCANNED_IDENTIFIER = "WLT-488"
        const val MIN_USER_QUERY_LENGTH = 4
        const val USER_SEARCH_DEBOUNCE_MS = 350L
    }
}

private fun Asset.applyAcceptedUpdate(update: AssetUpdate): Asset = when (update) {
    is AssetUpdate.Deploy -> copy(
        assignedUser = update.user.name,
        location = update.site.name,
    )

    is AssetUpdate.ReturnToInventory -> copy(
        assignedUser = null,
        location = update.site.name,
    )
}

private fun InvalidIdentifierReason.validationMessage(): String = when (this) {
    InvalidIdentifierReason.Empty -> "Enter a serial number before searching."
    InvalidIdentifierReason.UnsupportedFormat ->
        "That value is not a supported serial-number format. Check the label or enter the serial manually."
}
