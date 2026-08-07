package com.example.timekeep_work.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timekeep_work.Asset
import com.example.timekeep_work.AssetUpdate
import com.example.timekeep_work.data.AppDependencies
import com.example.timekeep_work.data.AssetRepositoryException
import com.example.timekeep_work.data.AssetRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AssetViewModel(
    private val repository: AssetRepository = AppDependencies.assetRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<AssetUiState>(AssetUiState.Ready)
    val uiState: StateFlow<AssetUiState> = _uiState.asStateFlow()

    private val _serialInput = MutableStateFlow(DEMO_SCANNED_IDENTIFIER)
    val serialInput: StateFlow<String> = _serialInput.asStateFlow()

    private val _managementUiState = MutableStateFlow(ManagementUiState())
    val managementUiState: StateFlow<ManagementUiState> = _managementUiState.asStateFlow()

    private var lookupJob: Job? = null
    private var updateJob: Job? = null
    private var lastIdentifier: String? = null
    private var lastCandidates: List<Asset>? = null
    private var lastRequestedAssetId: Int? = null
    private var failedOperation: FailedOperation? = null

    fun onSignedIn() {
        _uiState.value = AssetUiState.Ready
    }

    fun onSignOut() {
        lookupJob?.cancel()
        updateJob?.cancel()
        lastIdentifier = null
        lastCandidates = null
        lastRequestedAssetId = null
        failedOperation = null
        _managementUiState.value = ManagementUiState()
        _uiState.value = AssetUiState.Ready
    }

    fun onScanRequested() {
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

    fun onSerialSubmitted(identifier: String = _serialInput.value) {
        val normalized = identifier.trim()
        _serialInput.value = normalized
        if (normalized.isBlank()) {
            failedOperation = null
            _uiState.value = AssetUiState.Failure(
                message = "Enter a serial number before searching.",
                retryable = false,
            )
            return
        }
        search(normalized)
    }

    fun onCandidatesShown(identifier: String) {
        val normalized = identifier.trim()
        if (normalized == lastIdentifier) {
            lastCandidates?.let {
                publishCandidates(normalized, it)
                return
            }
            if (lookupJob?.isActive == true) return
        }
        onSerialSubmitted(normalized)
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

    fun onReturnToCandidates() {
        val identifier = lastIdentifier ?: return
        onCandidatesShown(identifier)
    }

    fun onAssetReviewed() {
        _managementUiState.value = _managementUiState.value.copy(reviewed = true)
    }

    fun onManagementActionRequested() {
        _managementUiState.value = _managementUiState.value.copy(
            phase = ManagementPhase.Editing,
            failure = null,
        )
    }

    fun onStatusSelected(status: String) {
        _managementUiState.value = _managementUiState.value.copy(
            selectedStatus = status,
            phase = ManagementPhase.Editing,
            failure = null,
        )
    }

    fun onReviewedChanged(reviewed: Boolean) {
        _managementUiState.value = _managementUiState.value.copy(
            reviewed = reviewed,
            phase = ManagementPhase.Editing,
            failure = null,
        )
    }

    fun onReviewUpdateRequested() {
        val state = _managementUiState.value
        if (state.assetId != null && state.reviewed) {
            _managementUiState.value = state.copy(
                phase = ManagementPhase.Confirming,
                failure = null,
            )
        }
    }

    fun onConfirmationDismissed() {
        _managementUiState.value = _managementUiState.value.copy(
            phase = ManagementPhase.Editing,
        )
    }

    fun onConfirmUpdate() {
        val state = _managementUiState.value
        val assetId = state.assetId ?: return
        if (!state.reviewed || updateJob?.isActive == true) return

        _managementUiState.value = state.copy(
            phase = ManagementPhase.Updating,
            failure = null,
        )
        updateJob = viewModelScope.launch {
            runCatching {
                repository.updateAsset(
                    AssetUpdate(
                        assetId = assetId,
                        statusName = state.selectedStatus,
                    ),
                )
            }
                .onSuccess { updatedAsset ->
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

    fun onRetry() {
        when (failedOperation) {
            FailedOperation.Search -> lastIdentifier?.let(::search)
            FailedOperation.LoadAsset -> lastRequestedAssetId?.let(::loadAsset)
            FailedOperation.Update -> onConfirmUpdate()
            null -> Unit
        }
    }

    fun onStartAnotherScan() {
        lookupJob?.cancel()
        updateJob?.cancel()
        lastIdentifier = null
        lastCandidates = null
        lastRequestedAssetId = null
        failedOperation = null
        _managementUiState.value = ManagementUiState()
        _uiState.value = AssetUiState.Scanning
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
        _managementUiState.value = ManagementUiState(
            assetId = asset.id,
            selectedStatus = asset.status ?: "In use",
        )
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
        const val DEMO_SCANNED_IDENTIFIER = "PF4X91Q2"
    }
}
