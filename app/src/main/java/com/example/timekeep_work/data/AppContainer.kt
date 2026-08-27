package com.example.timekeep_work.data

import android.content.Context
import com.example.timekeep_work.BuildConfig
import com.example.timekeep_work.auth.HaloAuthConfig
import com.example.timekeep_work.auth.HaloAuthRepository
import com.example.timekeep_work.auth.SecureAuthStateStore
import com.example.timekeep_work.network.BearerTokenInterceptor
import com.example.timekeep_work.network.FreshAccessTokenProvider
import com.example.timekeep_work.network.HaloApi
import com.example.timekeep_work.network.MutableAccessTokenProvider
import com.example.timekeep_work.network.PrivateFailureDiagnosticStore
import com.example.timekeep_work.network.SafeNetworkLoggingInterceptor
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Application-level object graph shared by the UI and repositories. */
interface AppContainer {
    val assetRepository: AssetRepository
    val accessTokenProvider: MutableAccessTokenProvider
    val authRepository: HaloAuthRepository
    val haloApi: HaloApi
}

/** Builds the production networking, authentication, and repository dependencies. */
class DefaultAppContainer(
    context: Context,
    useFakeRepository: Boolean = BuildConfig.USE_FAKE_ASSET_REPOSITORY,
    resourceServerUrl: String = BuildConfig.HALO_RESOURCE_SERVER_URL,
) : AppContainer {
    override val accessTokenProvider = MutableAccessTokenProvider()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    // The resource API and OAuth discovery share timeouts and privacy-safe diagnostics.
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(BearerTokenInterceptor(accessTokenProvider))
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    SafeNetworkLoggingInterceptor(
                        diagnosticSink = PrivateFailureDiagnosticStore(context.applicationContext),
                    ),
                )
            }
        }
        .build()

    override val haloApi: HaloApi by lazy {
        Retrofit.Builder()
            .baseUrl(resourceServerUrl.normalizedBaseUrl())
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(HaloApi::class.java)
    }

    override val authRepository = HaloAuthRepository(
        context = context.applicationContext,
        config = HaloAuthConfig.fromBuildConfig(),
        stateStore = SecureAuthStateStore(context.applicationContext),
        json = json,
        okHttpClient = okHttpClient,
    )

    private val freshAccessTokenProvider = FreshAccessTokenProvider { forceRefresh ->
        authRepository.freshAccessToken(forceRefresh).also(accessTokenProvider::update)
    }

    // The build-time switch keeps demos and local UI work independent from a Halo tenant.
    override val assetRepository: AssetRepository = if (useFakeRepository) {
        FakeAssetRepository()
    } else {
        HaloAssetRepository(
            api = haloApi,
            json = json,
            accessTokenProvider = accessTokenProvider,
            freshAccessTokenProvider = freshAccessTokenProvider,
        )
    }
}

/**
 * Minimal service locator used by Android-created entry points.
 * [initialize] must run from the application entry point before a ViewModel requests a dependency.
 */
object AppDependencies {
    @Volatile
    private var configuredContainer: AppContainer? = null

    val container: AppContainer
        get() = checkNotNull(configuredContainer) { "AppDependencies.initialize must run first." }

    fun initialize(context: Context) {
        if (configuredContainer != null) return
        // Double-checked locking keeps repeated Activity creation inexpensive and thread-safe.
        synchronized(this) {
            if (configuredContainer == null) {
                configuredContainer = DefaultAppContainer(context.applicationContext)
            }
        }
    }

    val assetRepository: AssetRepository
        get() = container.assetRepository
}

private fun String.normalizedBaseUrl(): String = trim().trimEnd('/') + "/"
