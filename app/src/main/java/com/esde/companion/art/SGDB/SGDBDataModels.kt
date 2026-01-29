package com.esde.companion.art.SGDB

import com.esde.companion.art.GameSearchResult
import com.esde.companion.art.MediaSearchResult

data class SGDBResponse<T>(
    val success: Boolean,
    val data: T
)

data class SGDBGame(
    val id: String,
    val name: String,
)

data class SGDBImage(
    val id: String,
    val url: String,
    val thumb: String, // Best for your companion app's gallery
    val score: Int,    // Use this to sort by popularity
    val author: SGDBAuthor?
)

data class SGDBAuthor(val name: String)

fun SGDBGame.toSearchResult(): GameSearchResult {
    return GameSearchResult(
        gameId = this.id,
        title = this.name,
        source = "SteamGridDB",
        thumbnail = null
    )
}

fun SGDBImage.toImageSearchResult(): MediaSearchResult {
    return MediaSearchResult(
        id = this.id,
        url = this.url,
        thumb = this.thumb,
        score = this.score
    )
}