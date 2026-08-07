package com.example.timekeep_work.network

import okhttp3.Interceptor
import okhttp3.Response

fun interface AccessTokenProvider {
    fun accessToken(): String?
}

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
