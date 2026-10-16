package com.example.timekeep_work

import com.example.timekeep_work.data.AssetRepository
import com.example.timekeep_work.data.AssetDataError
import com.example.timekeep_work.data.AssetRepositoryException
import com.example.timekeep_work.scanner.IdentifierNormalizationResult
import com.example.timekeep_work.scanner.InvalidIdentifierReason
import com.example.timekeep_work.screen.AssetUiState
import com.example.timekeep_work.screen.AssetViewModel
import com.example.timekeep_work.screen.AssetViewModel.Companion.MIN_USER_QUERY_LENGTH
import com.example.timekeep_work.screen.ManagementPhase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssetViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `lookup publishes no match, multiple matches, and selected asset states`() = runTest {
        val repository = RecordingAssetRepository()
        val viewModel = AssetViewModel(repository)

        repository.searchResults = emptyList()
        viewModel.onSerialSubmitted("NONE")
        advanceUntilIdle()
        assertEquals(AssetUiState.NoMatch("NONE"), viewModel.uiState.value)

        repository.searchResults = assets
        viewModel.onSerialSubmitted("MULTI")
        advanceUntilIdle()
        assertEquals(AssetUiState.MultipleMatches(assets), viewModel.uiState.value)
        assertEquals(null, viewModel.managementUiState.value.assetId)

        viewModel.onAssetSelected(assets.last().id)
        assertEquals(AssetUiState.MatchFound(assets.last()), viewModel.uiState.value)
        assertEquals(assets.last().id, viewModel.managementUiState.value.assetId)
    }

    @Test
    fun `one exact result is the only case that publishes match found`() = runTest {
        val repository = RecordingAssetRepository(searchResults = listOf(assets.first()))
        val viewModel = AssetViewModel(repository)

        viewModel.onSerialSubmitted("SERIAL")
        advanceUntilIdle()

        assertEquals(AssetUiState.MatchFound(assets.first()), viewModel.uiState.value)
    }

    @Test
    fun `slow lookup remains visibly searching until the repository completes`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = RecordingAssetRepository(
            searchResults = listOf(assets.first()),
            searchGate = gate,
        )
        val viewModel = AssetViewModel(repository)

        viewModel.onSerialSubmitted("SERIAL")
        runCurrent()

        assertEquals(AssetUiState.Searching, viewModel.uiState.value)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(AssetUiState.MatchFound(assets.first()), viewModel.uiState.value)
    }

    @Test
    fun `deploy requires user and site then updates local state without another asset fetch`() = runTest {
        val repository = RecordingAssetRepository(searchResults = listOf(assets.first()))
        val viewModel = AssetViewModel(repository)

        viewModel.onSerialSubmitted(assets.first().matchedIdentifier)
        advanceUntilIdle()
        viewModel.onAssetSelected(assets.first().id)
        viewModel.onManagementActionRequested()
        advanceUntilIdle()
        viewModel.onActionSelected(AssetManagementAction.Deploying)
        viewModel.onUserSelected(users.first())
        viewModel.onSiteSelected(sites.first())
        viewModel.onReviewedChanged(true)
        viewModel.onReviewUpdateRequested()

        assertEquals(ManagementPhase.Confirming, viewModel.managementUiState.value.phase)

        viewModel.onConfirmUpdate()
        advanceUntilIdle()

        assertEquals(
            AssetUpdate.Deploy(assets.first().id, sites.first(), users.first()),
            repository.lastUpdate,
        )
        assertEquals(1, repository.searchCount)
        assertEquals(ManagementPhase.Success, viewModel.managementUiState.value.phase)
        val state = viewModel.uiState.value
        assertTrue(state is AssetUiState.MatchFound)
        state as AssetUiState.MatchFound
        assertEquals(users.first().name, state.asset.assignedUser)
        assertEquals(sites.first().name, state.asset.location)
    }

    @Test
    fun `return to inventory clears user and changes only the selected site`() = runTest {
        val repository = RecordingAssetRepository(searchResults = listOf(assets.first()))
        val viewModel = AssetViewModel(repository)

        viewModel.onSerialSubmitted(assets.first().matchedIdentifier)
        advanceUntilIdle()
        viewModel.onAssetSelected(assets.first().id)
        viewModel.onManagementActionRequested()
        advanceUntilIdle()
        viewModel.onActionSelected(AssetManagementAction.ReturningToInventory)
        viewModel.onSiteSelected(sites.last())
        viewModel.onReviewedChanged(true)
        viewModel.onReviewUpdateRequested()
        viewModel.onConfirmUpdate()
        advanceUntilIdle()

        assertEquals(
            AssetUpdate.ReturnToInventory(assets.first().id, sites.last()),
            repository.lastUpdate,
        )
        val updated = (viewModel.uiState.value as AssetUiState.MatchFound).asset
        assertEquals(null, updated.assignedUser)
        assertEquals(sites.last().name, updated.location)
        assertEquals(assets.first().status, updated.status)
    }

    @Test
    fun `user search waits for four characters and uses server results`() = runTest {
        val repository = RecordingAssetRepository()
        val viewModel = AssetViewModel(repository)

        viewModel.onActionSelected(AssetManagementAction.Deploying)
        viewModel.onUserQueryChanged("may")
        advanceUntilIdle()
        assertEquals(0, repository.userSearchCount)

        viewModel.onUserQueryChanged("maya".take(MIN_USER_QUERY_LENGTH))
        advanceUntilIdle()

        assertEquals(1, repository.userSearchCount)
        assertEquals("maya", repository.lastUserQuery)
        assertEquals(users, viewModel.managementUiState.value.userResults)
    }

    @Test
    fun `retry repeats a retryable failed lookup`() = runTest {
        val repository = RecordingAssetRepository(
            searchResults = listOf(assets.first()),
            failNextSearch = true,
        )
        val viewModel = AssetViewModel(repository)

        viewModel.onSerialSubmitted(assets.first().matchedIdentifier)
        advanceUntilIdle()

        val failure = viewModel.uiState.value
        assertTrue(failure is AssetUiState.Failure)
        failure as AssetUiState.Failure
        assertTrue(failure.retryable)
        assertEquals(1, repository.searchCount)

        viewModel.onRetry()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AssetUiState.MatchFound)
        assertEquals(2, repository.searchCount)
        assertFalse(repository.failNextSearch)
    }

    @Test
    fun `unexpected repository details are not shown to technicians`() = runTest {
        val repository = RecordingAssetRepository(
            searchFailure = IllegalStateException("server stack trace and database details"),
        )
        val viewModel = AssetViewModel(repository)

        viewModel.onSerialSubmitted("SERIAL")
        advanceUntilIdle()

        assertEquals(
            AssetUiState.Failure(
                message = "The asset search failed.",
                retryable = false,
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `manual identifier is normalized before lookup`() = runTest {
        val repository = RecordingAssetRepository()
        val viewModel = AssetViewModel(repository)

        val result = viewModel.onSerialSubmitted("  pf4x91q2  ")
        advanceUntilIdle()

        assertTrue(result is IdentifierNormalizationResult.Success)
        result as IdentifierNormalizationResult.Success
        assertEquals("PF4X91Q2", result.identifier.value)
        assertEquals("PF4X91Q2", repository.lastSearchIdentifier)
        assertEquals(AssetUiState.NoMatch("PF4X91Q2"), viewModel.uiState.value)
    }

    @Test
    fun `invalid payload never starts an asset lookup`() = runTest {
        val repository = RecordingAssetRepository()
        val viewModel = AssetViewModel(repository)

        val result = viewModel.onSerialSubmitted("https://unapproved.example/asset/PF4X91Q2")
        advanceUntilIdle()

        assertEquals(
            IdentifierNormalizationResult.Invalid(InvalidIdentifierReason.UnsupportedFormat),
            result,
        )
        assertEquals(0, repository.searchCount)
        assertTrue(viewModel.uiState.value is AssetUiState.Failure)
    }

    private class RecordingAssetRepository(
        var searchResults: List<Asset> = emptyList(),
        var failNextSearch: Boolean = false,
        var searchFailure: Throwable? = null,
        var searchGate: CompletableDeferred<Unit>? = null,
    ) : AssetRepository {
        var searchCount = 0
        var lastSearchIdentifier: String? = null
        var lastUpdate: AssetUpdate? = null
        var userSearchCount = 0
        var lastUserQuery: String? = null

        override suspend fun findByIdentifier(identifier: String): List<Asset> {
            searchCount += 1
            lastSearchIdentifier = identifier
            searchGate?.await()
            searchFailure?.let { throw it }
            if (failNextSearch) {
                failNextSearch = false
                throw AssetRepositoryException(AssetDataError.NoNetwork)
            }
            return searchResults
        }

        override suspend fun searchAssignableUsers(query: String): List<AssignableUser> {
            userSearchCount += 1
            lastUserQuery = query
            return users
        }

        override suspend fun getActiveSites(): List<AssetSite> = sites

        override suspend fun updateAsset(request: AssetUpdate) {
            lastUpdate = request
        }
    }

    private companion object {
        val assets = listOf(
            Asset(1, "ONE", "SERIAL", "TAG-1", "Maker", "Model", "Agent", "In use", "Toronto"),
            Asset(2, "TWO", "SERIAL", "TAG-2", "Maker", "Model", null, "In stock", "Toronto"),
        )
        val users = listOf(
            AssignableUser(10, "Maya Chen", "maya@example.com"),
            AssignableUser(11, "Jordan Lee", "jordan@example.com", isAgent = true),
        )
        val sites = listOf(
            AssetSite(20, "Toronto - 14th floor"),
            AssetSite(21, "Toronto - IT storage"),
        )
    }
}
