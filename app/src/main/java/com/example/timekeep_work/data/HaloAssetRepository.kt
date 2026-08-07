package com.example.timekeep_work.data

import com.example.timekeep_work.Asset
import com.example.timekeep_work.AssetUpdate
import com.example.timekeep_work.network.AccessTokenProvider
import com.example.timekeep_work.network.HaloApi
import com.example.timekeep_work.network.dto.decodeAssetList
import com.example.timekeep_work.network.dto.decodeUpdatedAsset
import com.example.timekeep_work.network.dto.toAppModel
import com.example.timekeep_work.network.dto.toHaloDto
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException

class HaloAssetRepository(
    private val api: HaloApi,
    private val json: Json,
    private val accessTokenProvider: AccessTokenProvider,
) : AssetRepository {
    override suspend fun findByIdentifier(identifier: String): List<Asset> {
        val normalized = identifier.trim()
        if (normalized.isBlank()) {
            throw AssetRepositoryException(AssetDataError.ValidationFailure)
        }
        requireAuthenticatedSession()
        return translateFailures {
            val matchesById = linkedMapOf<Int, Asset>()
            val seenAssetIds = mutableSetOf<Int>()
            var pageNumber = 1
            while (pageNumber <= MAX_SEARCH_PAGES) {
                val page = json.decodeAssetList(
                    api.findAssets(
                        identifier = normalized,
                        pageSize = SEARCH_PAGE_SIZE,
                        pageNumber = pageNumber,
                    ),
                ).map { it.toAppModel() }
                val newAssetSeen = page.map { seenAssetIds.add(it.id) }.any { it }
                page.filter { it.matches(normalized) }
                    .forEach { asset -> matchesById[asset.id] = asset }

                if (page.size < SEARCH_PAGE_SIZE || !newAssetSeen) {
                    break
                }
                pageNumber += 1
            }
            matchesById.values.toList()
        }
    }

    override suspend fun updateAsset(request: AssetUpdate): Asset {
        if (!request.hasChanges) {
            throw AssetRepositoryException(AssetDataError.ValidationFailure)
        }
        requireAuthenticatedSession()
        return translateFailures {
            json.decodeUpdatedAsset(api.updateAsset(listOf(request.toHaloDto()))).toAppModel()
        }
    }

    private fun requireAuthenticatedSession() {
        if (accessTokenProvider.accessToken().isNullOrBlank()) {
            throw AssetRepositoryException(AssetDataError.Unauthenticated)
        }
    }

    private suspend fun <T> translateFailures(block: suspend () -> T): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: AssetRepositoryException) {
        throw error
    } catch (error: Throwable) {
        throw error.toRepositoryException()
    }

    private fun Asset.matches(identifier: String): Boolean =
        serialNumber.equals(identifier, ignoreCase = true) ||
            assetTag.equals(identifier, ignoreCase = true) ||
            hostname.equals(identifier, ignoreCase = true)

    private companion object {
        const val SEARCH_PAGE_SIZE = 50
        const val MAX_SEARCH_PAGES = 20
    }
}

private fun Throwable.toRepositoryException(): AssetRepositoryException {
    val category = when (this) {
        is SocketTimeoutException -> AssetDataError.Timeout
        is UnknownHostException,
        is ConnectException,
        is NoRouteToHostException,
        -> AssetDataError.NoNetwork

        is HttpException -> when (code()) {
            401 -> AssetDataError.Unauthenticated
            403 -> AssetDataError.Forbidden
            404 -> AssetDataError.NotFound
            408 -> AssetDataError.Timeout
            400, 409, 422 -> AssetDataError.ValidationFailure
            in 500..599 -> AssetDataError.ServerFailure
            else -> AssetDataError.Unexpected
        }

        is SerializationException -> AssetDataError.MalformedResponse
        is IOException -> AssetDataError.NoNetwork
        else -> AssetDataError.Unexpected
    }
    return AssetRepositoryException(category, this)
}
