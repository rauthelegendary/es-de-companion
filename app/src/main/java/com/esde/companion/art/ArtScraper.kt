package com.esde.companion.art

interface ArtScraper {
    val sourceName: String
    suspend fun searchGame(query: String): ScraperResult
    suspend fun getAvailableMediaTypes(gameId: String): List<MediaCategory>

    suspend fun fetchImages(gameId: String, categoryKey: String, pageNumber: Int?): List<MediaSearchResult>
}

data class MediaCategory(
    val key: String,
    val label: String,
    val aspect: Float = 1f
)

data class GameSearchResult(
    val gameId: String,
    val title: String,
    val source: String,
    val thumbnail: String? = null,
    val platform: String = ""
)

data class MediaSearchResult(
    val id: String,
    val url: String,
    val thumb: String,
    val score: Int = 0,
)

sealed class ScraperResult {
    data class Success(val results: List<GameSearchResult>) : ScraperResult()
    data class Error(val message: String) : ScraperResult()
}