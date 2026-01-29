package com.esde.companion.art.IGDB

import android.util.Log
import com.esde.companion.BuildConfig
import com.esde.companion.NetworkClientManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TwitchAuth {
    suspend fun getTwitchAccessToken(): String? = withContext(Dispatchers.IO) {
        val url = "https://id.twitch.tv/oauth2/token" +
                "?client_id=${BuildConfig.IGDB_CLIENT_ID}" +
                "&client_secret=${BuildConfig.IGDB_CLIENT_SECRET}" +
                "&grant_type=client_credentials"

        val request = okhttp3.Request.Builder()
            .url(url)
            .post(okhttp3.RequestBody.create(null, ""))
            .build()

        try {
            NetworkClientManager.baseClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                val tokenResponse = Gson().fromJson(body, TwitchTokenResponse::class.java)
                return@withContext tokenResponse.access_token
            }
        } catch (e: Exception) {
            Log.e("IGDB_AUTH", "Failed to get Twitch token", e)
            null
        }
    }
}

data class TwitchTokenResponse(
    val access_token: String,
    val expires_in: Long,
    val token_type: String
)