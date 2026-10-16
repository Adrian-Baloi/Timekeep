package com.example.timekeep_work.data

import com.example.timekeep_work.Asset
import com.example.timekeep_work.AssignableUser
import com.example.timekeep_work.AssetSite
import com.example.timekeep_work.AssetManagementSites
import com.example.timekeep_work.AssetUpdate
import com.example.timekeep_work.auth.AuthenticationRequiredException
import com.example.timekeep_work.network.AccessTokenProvider
import com.example.timekeep_work.network.FreshAccessTokenProvider
import com.example.timekeep_work.network.HaloApi
import com.example.timekeep_work.network.dto.decodeAssetPage
import com.example.timekeep_work.network.dto.decodeSitePage
import com.example.timekeep_work.network.dto.decodeUserPage
import com.example.timekeep_work.network.dto.matchesIdentifier
import com.example.timekeep_work.network.dto.toAssetSite
import com.example.timekeep_work.network.dto.toAssignableUser
import com.example.timekeep_work.network.dto.toAppModel
import com.example.timekeep_work.network.dto.toHaloTicketDtos
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * Halo-backed implementation of [AssetRepository].
 *
 * List operations page defensively, deduplicate by Halo id, and stop if Halo repeats a page.
 * All transport and schema failures are translated into the small error vocabulary used by
 * the UI.
 */
class HaloAssetRepository(
    private val api: HaloApi,
    private val json: Json,
    private val accessTokenProvider: AccessTokenProvider,
    private val freshAccessTokenProvider: FreshAccessTokenProvider = FreshAccessTokenProvider {
        accessTokenProvider.accessToken() ?: throw AuthenticationRequiredException()
    },
) : AssetRepository {
    override suspend fun findByIdentifier(identifier: String): List<Asset> {
        val normalized = identifier.trim()
        if (normalized.isBlank()) {
            throw AssetRepositoryException(AssetDataError.ValidationFailure)
        }
        return translateFailures {
            authenticatedCall {
                // Halo's search is broad; exact matching here avoids showing loosely related assets.
                val matchesById = linkedMapOf<Int, Asset>()
                val seenAssetIds = mutableSetOf<Int>()
                var pageNumber = 1
                while (pageNumber <= MAX_SEARCH_PAGES) {
                    val page = json.decodeAssetPage(
                        api.findAssets(
                            identifier = normalized,
                            pageSize = SEARCH_PAGE_SIZE,
                            pageNumber = pageNumber,
                        ),
                    )
                    val pageAssetIds = page.assets.map { dto ->
                        dto.id?.takeIf { it > 0 }
                            ?: throw SerializationException("Halo asset response has no valid id.")
                    }
                    val newAssetSeen = pageAssetIds.map(seenAssetIds::add).any { it }
                    page.assets
                        .filter { it.matchesIdentifier(normalized) }
                        .map { it.toAppModel(matchedIdentifier = normalized) }
                        .forEach { asset -> matchesById[asset.id] = asset }

                    val reachedReportedEnd = pageNumber.toLong() * SEARCH_PAGE_SIZE >= page.recordCount
                    if (page.assets.size < SEARCH_PAGE_SIZE || reachedReportedEnd || !newAssetSeen) {
                        break
                    }
                    pageNumber += 1
                }
                matchesById.values.toList()
            }
        }
    }

    override suspend fun searchAssignableUsers(query: String): List<AssignableUser> {
        val normalized = query.trim()
        if (normalized.length < MIN_USER_QUERY_LENGTH) {
            throw AssetRepositoryException(AssetDataError.ValidationFailure)
        }
        return translateFailures {
            authenticatedCall {
                val usersById = linkedMapOf<Int, AssignableUser>()
                val seenUserIds = mutableSetOf<Int>()
                var pageNumber = 1
                while (pageNumber <= MAX_USER_SEARCH_PAGES) {
                    val page = json.decodeUserPage(
                        api.searchAssignableUsers(
                            query = normalized,
                            pageSize = USER_SEARCH_PAGE_SIZE,
                            pageNumber = pageNumber,
                        ),
                    )
                    val newUserSeen = page.users.mapNotNull { it.id }.map(seenUserIds::add).any { it }
                    page.users.mapNotNull { it.toAssignableUser() }.forEach { user -> usersById[user.id] = user }

                    val reachedReportedEnd = pageNumber.toLong() * USER_SEARCH_PAGE_SIZE >= page.recordCount
                    if (page.users.size < USER_SEARCH_PAGE_SIZE || reachedReportedEnd || !newUserSeen) break
                    pageNumber += 1
                }
                usersById.values.toList()
            }
        }
    }

    override suspend fun getActiveSites(): List<AssetSite> = translateFailures {
        authenticatedCall {
            val sitesById = linkedMapOf<Int, AssetSite>()
            val seenSiteIds = mutableSetOf<Int>()
            var pageNumber = 1
            while (pageNumber <= MAX_SITE_PAGES) {
                val page = json.decodeSitePage(
                    api.getActiveSites(
                        pageSize = SITE_PAGE_SIZE,
                        pageNumber = pageNumber,
                    ),
                )
                val newSiteSeen = page.sites.mapNotNull { it.id }.map(seenSiteIds::add).any { it }
                page.sites.mapNotNull { it.toAssetSite() }.forEach { site -> sitesById[site.id] = site }

                val reachedReportedEnd = pageNumber.toLong() * SITE_PAGE_SIZE >= page.recordCount
                if (page.sites.size < SITE_PAGE_SIZE || reachedReportedEnd || !newSiteSeen) break
                pageNumber += 1
            }
            AssetManagementSites.restrict(sitesById.values)
        }
    }

    override suspend fun updateAsset(request: AssetUpdate) {
        translateFailures {
            authenticatedCall {
                api.createAssetChangeTicket(request.toHaloTicketDtos())
            }
        }
    }

    /** Runs a protected call and retries it once after a forced token refresh on HTTP 401. */
    private suspend fun <T> authenticatedCall(block: suspend () -> T): T {
        freshAccessTokenProvider.accessToken(false)
        return try {
            block()
        } catch (error: HttpException) {
            if (error.code() != 401) throw error
            freshAccessTokenProvider.accessToken(true)
            try {
                block()
            } catch (retryError: HttpException) {
                if (retryError.code() == 401) {
                    throw AssetRepositoryException(AssetDataError.AuthorizationRejected, retryError)
                }
                throw retryError
            }
        }
    }

    /** Preserves coroutine cancellation while converting operational failures for the UI. */
    private suspend fun <T> translateFailures(block: suspend () -> T): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: AssetRepositoryException) {
        throw error
    } catch (error: Throwable) {
        throw error.toRepositoryException()
    }

    private companion object {
        const val SEARCH_PAGE_SIZE = 50
        const val MAX_SEARCH_PAGES = 20
        const val MIN_USER_QUERY_LENGTH = 4
        const val USER_SEARCH_PAGE_SIZE = 25
        const val MAX_USER_SEARCH_PAGES = 20
        const val SITE_PAGE_SIZE = 100
        const val MAX_SITE_PAGES = 20
    }
}

/** Maps implementation-specific failures to the repository's stable error contract. */
private fun Throwable.toRepositoryException(): AssetRepositoryException {
    val category = when (this) {
        is AuthenticationRequiredException -> AssetDataError.Unauthenticated
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
