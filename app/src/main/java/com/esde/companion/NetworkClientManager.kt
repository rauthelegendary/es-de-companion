package com.esde.companion

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkClientManager {
    val baseClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    fun getSteamGridClient(apiKey: String): OkHttpClient {
        return baseClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()
                chain.proceed(request)
            }
            .build()
    }
}