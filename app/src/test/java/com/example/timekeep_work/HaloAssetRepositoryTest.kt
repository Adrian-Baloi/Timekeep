package com.example.timekeep_work

import com.example.timekeep_work.data.AssetDataError
import com.example.timekeep_work.data.AssetRepositoryException
import com.example.timekeep_work.data.HaloAssetRepository
import com.example.timekeep_work.network.AccessTokenProvider
import com.example.timekeep_work.network.FreshAccessTokenProvider
import com.example.timekeep_work.network.HaloApi
import com.example.timekeep_work.network.dto.HaloAssetChangeTicketDto
import com.example.timekeep_work.network.dto.HaloTicketAssetDto
import com.example.timekeep_work.network.dto.HaloTicketCustomFieldDto
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class HaloAssetRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `find maps Halo DTOs to app models`() = runTest {
        val api = StubHaloApi(
            findPayload = payload(
                """
                {
                  "page_no": 1,
                  "page_size": 50,
                  "record_count": 1,
                  "assets": [
                    {
                      "id": 42,
                      "inventory_number": "TAG-42",
                      "key_field": "SERIAL-42",
                      "key_field_name": "Serial number",
                      "key_field2": "TOR-LT-42",
                      "key_field2_name": "Hostname",
                      "key_field3": "ThinkPad T14 Gen 4",
                      "key_field3_name": "Model",
                      "manufacturer_name": "Lenovo",
                      "username": "Technician",
                      "status_name": "In use",
                      "site_name": "Toronto",
                      "changehistory_last_updated": "2026-08-10T15:30:00Z"
                    }
                  ]
                }
                """,
            ),
        )

        val assets = repository(api).findByIdentifier(" SERIAL-42 ")

        assertEquals("SERIAL-42", api.lastIdentifier)
        assertEquals(true, api.lastIncludeUser)
        assertEquals(true, api.lastIncludeLastUpdated)
        assertEquals(
            Asset(
                42,
                "TOR-LT-42",
                "SERIAL-42",
                "TAG-42",
                "Lenovo",
                "ThinkPad T14 Gen 4",
                "Technician",
                "In use",
                "Toronto",
                "2026-08-10T15:30:00Z",
            ),
            assets.single(),
        )
    }

    @Test
    fun `broad Halo search results are filtered to exact identifier matches`() = runTest {
        val api = StubHaloApi(
            findPayload = payload(
                """
                {
                  "page_no": 1,
                  "page_size": 50,
                  "record_count": 3,
                  "assets": [
                    {"id":1,"inventory_number":"SERIAL-42"},
                    {"id":2,"key_field":"SERIAL-420","key_field_name":"Serial number"},
                    {"id":3,"key_field2":"serial-42","key_field2_name":"Serial number"}
                  ]
                }
                """,
            ),
        )

        val matches = repository(api).findByIdentifier("SERIAL-42")

        assertEquals(listOf(1, 3), matches.map(Asset::id))
        assertTrue(matches.all { it.matchedIdentifier == "SERIAL-42" })
    }

    @Test
    fun `empty Device View is a valid no-match result`() = runTest {
        val api = StubHaloApi(
            findPayload = payload(
                """
                {"page_no":1,"page_size":50,"record_count":0,"assets":[]}
                """,
            ),
        )

        assertTrue(repository(api).findByIdentifier("UNKNOWN-42").isEmpty())
    }

    @Test
    fun `deploy and return create one ticket with a related asset`() = runTest {
        val api = StubHaloApi(updatePayload = payload("""{"id":42}"""))
        val repository = repository(api)
        val site = AssetSite(id = 7, name = "Toronto", clientName = "Newforma", clientId = 12)
        val user = AssignableUser(9, "Maya Chen")

        repository.updateAsset(AssetUpdate.Deploy(assetId = 42, site = site, user = user))
        val deployRequest = requireNotNull(api.lastUpdate)
        assertEquals(
            listOf(
                HaloAssetChangeTicketDto(
                    ticketTypeId = 79,
                    assetNumber = 42,
                    assets = listOf(HaloTicketAssetDto(id = 42)),
                    clientId = 12,
                    siteId = 7,
                    customFields = listOf(
                        HaloTicketCustomFieldDto(id = 564, value = "1"),
                        HaloTicketCustomFieldDto(id = 289, value = "9"),
                        HaloTicketCustomFieldDto(id = 404, value = "7"),
                    ),
                ),
            ),
            deployRequest,
        )
        assertEquals(
            payload(
                """[{"tickettype_id":79,"asset_number":42,"assets":[{"id":42}],"client_id":12,"site_id":7,"customfields":[{"id":564,"value":"1"},{"id":289,"value":"9"},{"id":404,"value":"7"}]}]""",
            ),
            payload(json.encodeToString(deployRequest)),
        )
        assertEquals(0, api.findCallCount)

        repository.updateAsset(AssetUpdate.ReturnToInventory(assetId = 42, site = site))
        assertEquals(
            listOf(
                HaloAssetChangeTicketDto(
                    ticketTypeId = 79,
                    assetNumber = 42,
                    assets = listOf(HaloTicketAssetDto(id = 42)),
                    clientId = 12,
                    siteId = 7,
                    customFields = listOf(
                        HaloTicketCustomFieldDto(id = 564, value = "2"),
                        HaloTicketCustomFieldDto(id = 404, value = "7"),
                    ),
                ),
            ),
            api.lastUpdate,
        )
        val returnRequests = api.lastUpdate.orEmpty()
        assertTrue(returnRequests.single().customFields.none { it.id == 289 })
        assertEquals(
            payload(
                """[{"tickettype_id":79,"asset_number":42,"assets":[{"id":42}],"client_id":12,"site_id":7,"customfields":[{"id":564,"value":"2"},{"id":404,"value":"7"}]}]""",
            ),
            payload(json.encodeToString(returnRequests)),
        )
        assertEquals(0, api.findCallCount)
    }

    @Test
    fun `user search includes active end users and linked agents`() = runTest {
        val api = StubHaloApi(
            userPayload = payload(
                """
                {
                  "page_no":1,
                  "page_size":25,
                  "record_count":3,
                  "users":[
                    {"id":1,"name":"Maya Chen","emailaddress":"maya@example.com","inactive":false},
                    {"id":2,"name":"Jordan Lee","linked_agent_id":99,"inactive":false},
                    {"id":3,"name":"Inactive User","inactive":true}
                  ]
                }
                """,
            ),
        )

        val users = repository(api).searchAssignableUsers("maya")

        assertEquals("maya", api.lastUserQuery)
        assertEquals(false, api.lastExcludeAgents)
        assertEquals(false, api.lastIncludeInactiveUsers)
        assertEquals(listOf(1, 2), users.map(AssignableUser::id))
        assertEquals(false, users.first().isAgent)
        assertEquals(true, users.last().isAgent)
    }

    @Test
    fun `site list paginates and excludes inactive records`() = runTest {
        val api = StubHaloApi(
            sitePayload = payload(
                """
                {
                  "page_no":1,
                  "page_size":100,
                  "record_count":4,
                  "sites":[
                    {"id":7,"name":"remote | us","inactive":false},
                    {"id":8,"name":"Boston Office","client_id":"12","client_name":"Newforma","inactive":false},
                    {"id":9,"name":"Toronto Office","inactive":false},
                    {"id":10,"name":"Closed","inactive":true}
                  ]
                }
                """,
            ),
        )

        val sites = repository(api).getActiveSites()

        assertEquals(listOf("Boston Office", "Remote | US"), sites.map(AssetSite::name))
        assertEquals(12, sites.first().clientId)
        assertEquals(false, api.lastIncludeInactiveSites)
    }

    @Test
    fun `HTTP errors become app-level error categories`() = runTest {
        val cases = listOf(
            401 to AssetDataError.AuthorizationRejected,
            403 to AssetDataError.Forbidden,
            404 to AssetDataError.NotFound,
            408 to AssetDataError.Timeout,
            422 to AssetDataError.ValidationFailure,
            500 to AssetDataError.ServerFailure,
            418 to AssetDataError.Unexpected,
        )

        cases.forEach { (status, expected) ->
            val api = StubHaloApi(findFailure = httpError(status))
            assertEquals(expected, repository(api).findError())
        }
    }

    @Test
    fun `401 forces one token refresh and retries the read once`() = runTest {
        val refreshRequests = mutableListOf<Boolean>()
        val api = StubHaloApi(
            findPayload = payload(
                """
                {"page_no":1,"page_size":50,"record_count":1,"assets":[{"id":42,"key_field":"SERIAL-42","key_field_name":"Serial number"}]}
                """,
            ),
            findFailure = httpError(401),
            findFailuresBeforeSuccess = 1,
        )
        val repository = HaloAssetRepository(
            api = api,
            json = json,
            accessTokenProvider = AccessTokenProvider { "test-access-token" },
            freshAccessTokenProvider = FreshAccessTokenProvider { forceRefresh ->
                refreshRequests += forceRefresh
                "test-access-token"
            },
        )

        assertEquals(42, repository.findByIdentifier("SERIAL-42").single().id)
        assertEquals(listOf(false, true), refreshRequests)
    }

    @Test
    fun `API rejection after a successful refresh is reported as authorization rejection`() = runTest {
        val refreshRequests = mutableListOf<Boolean>()
        val api = StubHaloApi(findFailure = httpError(401))
        val repository = HaloAssetRepository(
            api = api,
            json = json,
            accessTokenProvider = AccessTokenProvider { "expired-access-token" },
            freshAccessTokenProvider = FreshAccessTokenProvider { forceRefresh ->
                refreshRequests += forceRefresh
                "refreshed-access-token"
            },
        )

        assertEquals(AssetDataError.AuthorizationRejected, repository.findError())
        assertEquals(listOf(false, true), refreshRequests)
    }

    @Test
    fun `transport and malformed failures become app-level errors`() = runTest {
        assertEquals(
            AssetDataError.NoNetwork,
            repository(StubHaloApi(findFailure = UnknownHostException())).findError(),
        )
        assertEquals(
            AssetDataError.Timeout,
            repository(StubHaloApi(findFailure = SocketTimeoutException())).findError(),
        )
        assertEquals(
            AssetDataError.NoNetwork,
            repository(StubHaloApi(findFailure = IOException("connection lost during lookup"))).findError(),
        )
        assertEquals(
            AssetDataError.MalformedResponse,
            repository(StubHaloApi(findPayload = JsonPrimitive("not an asset response"))).findError(),
        )
        assertEquals(
            AssetDataError.Unexpected,
            repository(StubHaloApi(findFailure = IllegalStateException("raw server detail"))).findError(),
        )
    }

    @Test
    fun `missing token fails before calling Halo`() = runTest {
        val api = StubHaloApi()
        val repository = HaloAssetRepository(api, json, AccessTokenProvider { null })

        assertEquals(AssetDataError.Unauthenticated, repository.findError())
        assertTrue(api.lastIdentifier == null)
    }

    private fun repository(api: HaloApi) = HaloAssetRepository(
        api = api,
        json = json,
        accessTokenProvider = AccessTokenProvider { "test-access-token" },
    )

    private suspend fun HaloAssetRepository.findError(): AssetDataError {
        return try {
            findByIdentifier("SERIAL")
            fail("Expected AssetRepositoryException")
            AssetDataError.Unexpected
        } catch (error: AssetRepositoryException) {
            error.error
        }
    }

    private fun payload(value: String): JsonElement = json.parseToJsonElement(value)

    private fun httpError(status: Int): HttpException = HttpException(
        Response.error<JsonElement>(
            status,
            "{\"internal\":\"must not reach UI\"}".toResponseBody("application/json".toMediaType()),
        ),
    )

    private class StubHaloApi(
        var findPayload: JsonElement = JsonPrimitive("unused"),
        var userPayload: JsonElement = JsonPrimitive("unused"),
        var sitePayload: JsonElement = JsonPrimitive("unused"),
        var updatePayload: JsonElement = JsonPrimitive("unused"),
        var findFailure: Throwable? = null,
        findFailuresBeforeSuccess: Int = Int.MAX_VALUE,
    ) : HaloApi {
        private var remainingFindFailures = findFailuresBeforeSuccess
        var lastIdentifier: String? = null
        var lastIncludeUser: Boolean? = null
        var lastIncludeLastUpdated: Boolean? = null
        var findCallCount: Int = 0
        var lastUserQuery: String? = null
        var lastExcludeAgents: Boolean? = null
        var lastIncludeInactiveUsers: Boolean? = null
        var lastIncludeInactiveSites: Boolean? = null
        var lastUpdate: List<HaloAssetChangeTicketDto>? = null

        override suspend fun getCurrentAgent(): JsonElement = JsonPrimitive("unused")

        override suspend fun findAssets(
            identifier: String,
            paginate: Boolean,
            pageSize: Int,
            pageNumber: Int,
            includeUser: Boolean,
            includeLastUpdated: Boolean,
        ): JsonElement {
            findCallCount += 1
            lastIdentifier = identifier
            lastIncludeUser = includeUser
            lastIncludeLastUpdated = includeLastUpdated
            findFailure?.let {
                if (remainingFindFailures > 0) {
                    remainingFindFailures -= 1
                    throw it
                }
            }
            return findPayload
        }

        override suspend fun searchAssignableUsers(
            query: String,
            paginate: Boolean,
            pageSize: Int,
            pageNumber: Int,
            includeActive: Boolean,
            includeInactive: Boolean,
            excludeAgents: Boolean,
            listAgentUserFirst: Boolean,
        ): JsonElement {
            lastUserQuery = query
            lastExcludeAgents = excludeAgents
            lastIncludeInactiveUsers = includeInactive
            return userPayload
        }

        override suspend fun getActiveSites(
            paginate: Boolean,
            pageSize: Int,
            pageNumber: Int,
            includeActive: Boolean,
            includeInactive: Boolean,
            order: String,
        ): JsonElement {
            lastIncludeInactiveSites = includeInactive
            return sitePayload
        }

        override suspend fun createAssetChangeTicket(request: List<HaloAssetChangeTicketDto>): JsonElement {
            lastUpdate = request
            return updatePayload
        }
    }
}
