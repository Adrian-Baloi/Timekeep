package com.example.timekeep_work

import com.example.timekeep_work.data.AssetDataError
import com.example.timekeep_work.data.AssetRepositoryException
import com.example.timekeep_work.data.HaloAssetRepository
import com.example.timekeep_work.network.AccessTokenProvider
import com.example.timekeep_work.network.HaloApi
import com.example.timekeep_work.network.dto.HaloAssetUpdateDto
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.test.runTest
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
                  "assets": [
                    {
                      "id": 42,
                      "hostname": "TOR-LT-42",
                      "serial_number": "SERIAL-42",
                      "inventory_number": "TAG-42",
                      "manufacturer_name": "Lenovo",
                      "model_name": "ThinkPad",
                      "username": "Technician",
                      "status_name": "In use",
                      "site_name": "Toronto"
                    }
                  ]
                }
                """,
            ),
        )

        val assets = repository(api).findByIdentifier(" SERIAL-42 ")

        assertEquals("SERIAL-42", api.lastIdentifier)
        assertEquals(
            Asset(42, "TOR-LT-42", "SERIAL-42", "TAG-42", "Lenovo", "ThinkPad", "Technician", "In use", "Toronto"),
            assets.single(),
        )
    }

    @Test
    fun `update sends a DTO and returns an app model`() = runTest {
        val api = StubHaloApi(
            updatePayload = payload(
                """
                [
                  {
                    "id": 42,
                    "hostname": "TOR-LT-42",
                    "serial_number": "SERIAL-42",
                    "status_name": "Repair"
                  }
                ]
                """,
            ),
        )

        val updated = repository(api).updateAsset(
            AssetUpdate(assetId = 42, statusName = "Repair"),
        )

        assertEquals(listOf(HaloAssetUpdateDto(id = 42, statusName = "Repair")), api.lastUpdate)
        assertEquals("Repair", updated.status)
    }

    @Test
    fun `HTTP errors become app-level error categories`() = runTest {
        val cases = listOf(
            401 to AssetDataError.Unauthenticated,
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
        var updatePayload: JsonElement = JsonPrimitive("unused"),
        var findFailure: Throwable? = null,
    ) : HaloApi {
        var lastIdentifier: String? = null
        var lastUpdate: List<HaloAssetUpdateDto>? = null

        override suspend fun findAssets(
            identifier: String,
            paginate: Boolean,
            pageSize: Int,
            pageNumber: Int,
        ): JsonElement {
            lastIdentifier = identifier
            findFailure?.let { throw it }
            return findPayload
        }

        override suspend fun updateAsset(request: List<HaloAssetUpdateDto>): JsonElement {
            lastUpdate = request
            return updatePayload
        }
    }
}
