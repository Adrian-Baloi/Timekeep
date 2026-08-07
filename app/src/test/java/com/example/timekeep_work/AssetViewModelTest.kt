package com.example.timekeep_work

import com.example.timekeep_work.data.AssetRepository
import com.example.timekeep_work.data.AssetDataError
import com.example.timekeep_work.data.AssetRepositoryException
import com.example.timekeep_work.screen.AssetUiState
import com.example.timekeep_work.screen.AssetViewModel
import com.example.timekeep_work.screen.ManagementPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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

        viewModel.onAssetSelected(assets.last().id)
        assertEquals(AssetUiState.MatchFound(assets.last()), viewModel.uiState.value)
        assertEquals(assets.last().id, viewModel.managementUiState.value.assetId)
    }

    @Test
    fun `confirm update delegates to repository and publishes immutable success state`() = runTest {
        val repository = RecordingAssetRepository(searchResults = listOf(assets.first()))
        val viewModel = AssetViewModel(repository)

        viewModel.onSerialSubmitted(assets.first().serialNumber)
        advanceUntilIdle()
        viewModel.onAssetSelected(assets.first().id)
        viewModel.onAssetReviewed()
        viewModel.onStatusSelected("Repair")
        viewModel.onReviewUpdateRequested()

        assertEquals(ManagementPhase.Confirming, viewModel.managementUiState.value.phase)

        viewModel.onConfirmUpdate()
        advanceUntilIdle()

        assertEquals(assets.first().id, repository.lastUpdate?.assetId)
        assertEquals("Repair", repository.lastUpdate?.statusName)
        assertEquals(ManagementPhase.Success, viewModel.managementUiState.value.phase)
        val state = viewModel.uiState.value
        assertTrue(state is AssetUiState.MatchFound)
        state as AssetUiState.MatchFound
        assertEquals("Repair", state.asset.status)
    }

    @Test
    fun `retry repeats a retryable failed lookup`() = runTest {
        val repository = RecordingAssetRepository(
            searchResults = listOf(assets.first()),
            failNextSearch = true,
        )
        val viewModel = AssetViewModel(repository)

        viewModel.onSerialSubmitted(assets.first().serialNumber)
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

    private class RecordingAssetRepository(
        var searchResults: List<Asset> = emptyList(),
        var failNextSearch: Boolean = false,
        var searchFailure: Throwable? = null,
    ) : AssetRepository {
        var searchCount = 0
        var lastUpdate: AssetUpdate? = null

        override suspend fun findByIdentifier(identifier: String): List<Asset> {
            searchCount += 1
            searchFailure?.let { throw it }
            if (failNextSearch) {
                failNextSearch = false
                throw AssetRepositoryException(AssetDataError.NoNetwork)
            }
            return searchResults
        }

        override suspend fun updateAsset(request: AssetUpdate): Asset {
            lastUpdate = request
            return assets.first { it.id == request.assetId }.copy(
                status = request.statusName,
            )
        }
    }

    private companion object {
        val assets = listOf(
            Asset(1, "ONE", "SERIAL", "TAG-1", "Maker", "Model", "Agent", "In use", "Toronto"),
            Asset(2, "TWO", "SERIAL", "TAG-2", "Maker", "Model", null, "In stock", "Toronto"),
        )
    }
}
