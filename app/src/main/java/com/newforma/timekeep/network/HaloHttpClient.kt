package com.newforma.timekeep.network

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class HaloHttpClient(
    private val json: Json,
    private val okHttpClient: OkHttpClient,
) {
    suspend fun getJson(
        url: String,
        bearerToken: String? = null,
    ): JsonElement {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply {
                if (!bearerToken.isNullOrBlank()) {
                    header("Authorization", "Bearer $bearerToken")
                }
            }
            .build()
        return executeJson(request)
    }

    suspend fun postForm(
        url: String,
        form: Map<String, String>,
        bearerToken: String? = null,
    ): JsonObject {
        val requestBody = FormBody.Builder().apply {
            form.forEach { (key, value) ->
                if (value.isNotBlank()) {
                    add(key, value)
                }
            }
        }.build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .post(requestBody)
            .apply {
                if (!bearerToken.isNullOrBlank()) {
                    header("Authorization", "Bearer $bearerToken")
                }
            }
            .build()

        return executeJson(request).jsonObject
    }

    private suspend fun executeJson(request: Request): JsonElement = withContext(Dispatchers.IO) {
        okHttpClient.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiException(response.code, rawBody)
            }
            if (rawBody.isBlank()) {
                JsonObject(emptyMap())
            } else {
                json.parseToJsonElement(rawBody)
            }
        }
    }
}

class ApiException(
    val statusCode: Int,
    val responseBody: String?,
) : IOException("Halo returned HTTP $statusCode")
