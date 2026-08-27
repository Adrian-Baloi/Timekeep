package com.example.timekeep_work.network

import okhttp3.Interceptor
import okhttp3.Response

/** Synchronous access to the current in-memory bearer token for OkHttp. */
fun interface AccessTokenProvider {
    fun accessToken(): String?
}

/** Token source that may suspend while AppAuth refreshes the session. */
fun interface FreshAccessTokenProvider {
    suspend fun accessToken(forceRefresh: Boolean): String
}

/** Thread-visible token holder; persisted OAuth state remains the auth repository's concern. */
class MutableAccessTokenProvider : AccessTokenProvider {
    @Volatile
    private var token: String? = null

    override fun accessToken(): String? = token

    fun update(accessToken: String) {
        token = accessToken.trim().takeIf(String::isNotEmpty)
    }

    fun clear() {
        token = null
    }
}

/** Adds the latest bearer token without attempting refresh on OkHttp's synchronous thread. */
class BearerTokenInterceptor(
    private val tokenProvider: AccessTokenProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider.accessToken()
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }
}
