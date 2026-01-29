package com.esde.companion.art.SGDB

import android.util.Log
import com.esde.companion.NetworkClientManager
import com.esde.companion.art.ArtScraper
import com.esde.companion.art.GameSearchResult
import com.esde.companion.art.MediaSearchResult
import com.esde.companion.art.MediaCategory
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SGDBScraper (apiKey: String) : ArtScraper {
    override val sourceName = "SteamGridDB"
    private val BASE_URL = "https://www.steamgriddb.com/api/v2/"

    private val categories = listOf(MediaCategory("heroes", "Heroes (Wide images)"), MediaCategory("grid", "Grids (Boxart)"), MediaCategory("logos", "Logos (Marquee)"))

    private val service: SteamGridDBService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(NetworkClientManager.getSteamGridClient(apiKey))
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SteamGridDBService::class.java)

    override suspend fun searchGame(query: String): List<GameSearchResult> {
        val response = service.searchGame(query)
        return if (response.success) {
            response.data.map {
                GameSearchResult(it.id, it.name, sourceName)
            }
        } else emptyList()
    }

    override suspend fun getAvailableMediaTypes(gameId: String): List<MediaCategory> {
        return categories
    }

    override suspend fun fetchImages(gameId: String, categoryKey: String): List<MediaSearchResult> {
        return try {
            var response: SGDBResponse<List<SGDBImage>>? = null
            when(categoryKey) {
                "heroes" -> {
                    response = service.getHeroes(gameId)
                }
                "grid" -> {
                    response = service.getGrids(gameId)
                }
                "logos" -> {
                    response = service.getLogos(gameId)
                }
            }

            //convert result to generic data objects and order by score (votes)
            return if (response != null && response.success) {
                response.data.map {
                    MediaSearchResult(it.id, it.url, it.thumb, it.score)
                }.sortedByDescending { it.score }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("SteamGrid", "Failed to fetch SGDB images of type $categoryKey", e)
            emptyList()
        }
    }
}