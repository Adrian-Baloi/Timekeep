package com.newforma.timekeep

import android.content.Context
import com.newforma.timekeep.auth.AuthRepository
import com.newforma.timekeep.auth.HaloEnvironmentConfig
import com.newforma.timekeep.auth.SecureSessionStore
import com.newforma.timekeep.network.HaloApiClient
import com.newforma.timekeep.network.HaloHttpClient
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    val config: HaloEnvironmentConfig = HaloEnvironmentConfig.fromBuildConfig()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .apply {
            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }
            addInterceptor(logging)
        }
        .build()

    val sessionStore = SecureSessionStore(appContext, json)
    val httpClient = HaloHttpClient(json, okHttpClient)
    val authRepository = AuthRepository(config, sessionStore, httpClient, json)
    val haloApiClient = HaloApiClient(config, httpClient)
}
