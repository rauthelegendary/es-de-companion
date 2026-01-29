package com.esde.companion.art

interface ArtScraper {
    val sourceName: String
    suspend fun searchGame(query: String): List<GameSearchResult>
    // 1. New: Ask the scraper what categories are available for a specific game
    suspend fun getAvailableMediaTypes(gameId: String): List<MediaCategory>

    // 2. Updated: Fetch images based on a category ID/Key
    suspend fun fetchImages(gameId: String, categoryKey: String): List<MediaSearchResult>
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
    val thumbnail: String? = null
)

data class MediaSearchResult(
    val id: String,
    val url: String,
    val thumb: String,
    val score: Int = 0,
)