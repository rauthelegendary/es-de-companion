package com.esde.companion.art.igdb

import android.util.Log
import com.api.igdb.apicalypse.APICalypse
import com.api.igdb.request.IGDBWrapper
import com.api.igdb.request.games
import com.esde.companion.art.ArtScraper
import com.esde.companion.art.GameSearchResult
import com.esde.companion.art.MediaSearchResult
import com.esde.companion.art.MediaCategory
import com.esde.companion.art.ScraperResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IgdbArtScraper() : ArtScraper {
    override val sourceName: String = "IGDB"

    override suspend fun searchGame(query: String): ScraperResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return@withContext ScraperResult.Success(emptyList())
        try {
            val apiQuery = APICalypse()
                .search(cleanQuery)
                .fields("id, name, cover.image_id")
                .limit(10)

            val results = IGDBWrapper.games(apiQuery).map { game ->
                GameSearchResult(
                    gameId = game.id.toString(),
                    title = game.name,
                    source = sourceName,
                    thumbnail = game.cover?.let { "https://images.igdb.com/igdb/image/upload/t_thumb/${it.imageId}.jpg" }
                )
            }
            ScraperResult.Success(results)
        } catch (e: com.api.igdb.exceptions.RequestException) {
            Log.e("IGDB_ERROR", "HTTP Status: ${e.statusCode}")
            when (e.statusCode) {
                401, 403 -> ScraperResult.Error("Invalid IGDB credentials")
                else -> ScraperResult.Error("IGDB server error (${e.statusCode})")
            }
        } catch (e: java.net.UnknownHostException) {
            ScraperResult.Error("No internet connection")
        } catch (e: Exception) {
            Log.e("IGDB_SCRAPE", "Search failed for query: $query", e)
            ScraperResult.Error("Search failed")
        }
    }

    override suspend fun getAvailableMediaTypes(gameId: String): List<MediaCategory> = withContext(Dispatchers.IO) {
        try {
            val apiQuery = APICalypse()
                .fields("cover, screenshots, artworks, videos")
                .where("id = $gameId")

            val game = IGDBWrapper.games(apiQuery).firstOrNull() ?: return@withContext emptyList()
            val categories = mutableListOf<MediaCategory>()

            if (game.cover != null) categories.add(MediaCategory("cover", "Box Art", 0.75f))
            if (game.screenshotsList.isNotEmpty()) categories.add(
                MediaCategory(
                    "screenshots",
                    "Screenshots",
                    1.77f
                )
            )
            if (game.artworksList.isNotEmpty()) categories.add(
                MediaCategory(
                    "artworks",
                    "Artworks/Backgrounds",
                    1.77f
                )
            )
            if (game.videosList.isNotEmpty()) categories.add(
                MediaCategory(
                    "videos",
                    "Trailers & Videos",
                    1.77f
                )
            )

            categories
        } catch (e: Exception) {
            android.util.Log.e("IGDB_SCRAPE", "Retrieving available media types failed for game id: $gameId", e)
            emptyList()
        }
    }

    override suspend fun fetchImages(gameId: String, categoryKey: String, pageNumber: Int?): List<MediaSearchResult> = withContext(Dispatchers.IO) {
        try {
            val apiQuery = APICalypse()
                .fields("$categoryKey.*")
                .where("id = $gameId")

            val game = IGDBWrapper.games(apiQuery).firstOrNull() ?: return@withContext emptyList()

            // Map the selected category to your ImageSearchResult
            return@withContext when (categoryKey) {
                "cover" -> listOf(mapImage(game.cover.id.toInt(), game.cover.imageId))
                "screenshots" -> game.screenshotsList.map { mapImage(it.id.toInt(), it.imageId) }
                "artworks" -> game.artworksList.map { mapImage(it.id.toInt(), it.imageId) }
                "videos" -> game.videosList.map { video ->
                    MediaSearchResult(
                        id = video.id.toString(),
                        url = video.videoId, // We store the YouTube ID in the URL field for now
                        thumb = "https://img.youtube.com/vi/${video.videoId}/hqdefault.jpg",
                        score = 0
                    )
                }

                else -> emptyList()
            }
        }
        catch (e: Exception){
            android.util.Log.e("IGDB_SCRAPE", "Retrieving media failed for game id: $gameId in category: $categoryKey", e)
            emptyList()
        }
    }

    private fun mapImage(id: Int, imageHash: String) = MediaSearchResult(
        id = id.toString(),
        url = "https://images.igdb.com/igdb/image/upload/t_1080p/$imageHash.jpg",
        thumb = "https://images.igdb.com/igdb/image/upload/t_cover_big/$imageHash.jpg",
        score = 0
    )
}