package com.example.timekeep_work.data

import com.example.timekeep_work.BuildConfig
import com.example.timekeep_work.network.BearerTokenInterceptor
import com.example.timekeep_work.network.HaloApi
import com.example.timekeep_work.network.MutableAccessTokenProvider
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

interface AppContainer {
    val assetRepository: AssetRepository
    val accessTokenProvider: MutableAccessTokenProvider
}

class DefaultAppContainer(
    useFakeRepository: Boolean = BuildConfig.USE_FAKE_ASSET_REPOSITORY,
    resourceServerUrl: String = BuildConfig.HALO_RESOURCE_SERVER_URL,
) : AppContainer {
    override val accessTokenProvider = MutableAccessTokenProvider()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(BearerTokenInterceptor(accessTokenProvider))
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                redactHeader("Authorization")
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            },
        )
        .build()

    private val haloApi: HaloApi by lazy {
        Retrofit.Builder()
            .baseUrl(resourceServerUrl.normalizedBaseUrl())
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(HaloApi::class.java)
    }

    override val assetRepository: AssetRepository = if (useFakeRepository) {
        FakeAssetRepository()
    } else {
        HaloAssetRepository(haloApi, json, accessTokenProvider)
    }
}

object AppDependencies {
    val container: AppContainer by lazy { DefaultAppContainer() }

    val assetRepository: AssetRepository
        get() = container.assetRepository
}

private fun String.normalizedBaseUrl(): String = trim().trimEnd('/') + "/"
