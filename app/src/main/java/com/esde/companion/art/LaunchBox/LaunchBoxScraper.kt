package com.esde.companion.art.LaunchBox

import com.esde.companion.art.ArtScraper
import com.esde.companion.art.GameSearchResult
import com.esde.companion.art.MediaCategory
import com.esde.companion.art.MediaSearchResult
import com.esde.companion.ui.ContentType

class LaunchBoxScraper(
    private val dao: LaunchBoxDao
) : ArtScraper {
    override val sourceName = "LaunchBox"
    private val BASE_URL = ""

    override suspend fun searchGame(query: String): List<GameSearchResult> {
        val dbResults = dao.searchGames("%${query.replace(" ", "%")}%")
        return dbResults.map {
            GameSearchResult(
                gameId = it.databaseId,
                title = it.name,
                platform = it.platform,
                source = sourceName
            )
        }
    }

    override suspend fun getAvailableMediaTypes(gameId: String): List<MediaCategory> {
        val images = dao.getImagesForGame(gameId)

        return images.distinctBy { it.type }.map {
            MediaCategory(
                key = it.type,
                label = it.type
            )
        }
    }

    override suspend fun fetchImages(gameId: String, categoryKey: String): List<MediaSearchResult> {
        val images = dao.getImagesForGame(gameId).filter { it.type == categoryKey }

        return images.map { img ->
            val fullUrl = "https://images.launchbox-app.com/${img.fileName}"

            MediaSearchResult(
                url = fullUrl,
                id = img.id.toString(),
                thumb = fullUrl
            )
        }
    }

    private fun mapToContentType(lbType: String): ContentType {
        return when {
            lbType.contains("Front", ignoreCase = true) -> ContentType.BOX_2D
            lbType.contains("3D", ignoreCase = true) -> ContentType.BOX_3D
            lbType.contains("Back", ignoreCase = true) -> ContentType.BACK_COVER
            lbType.contains("Screenshot", ignoreCase = true) -> ContentType.SCREENSHOT
            lbType.contains("Fanart", ignoreCase = true) -> ContentType.FANART
            lbType.contains("Clear Logo", ignoreCase = true) -> ContentType.MARQUEE
            else -> ContentType.FANART
        }
    }
}