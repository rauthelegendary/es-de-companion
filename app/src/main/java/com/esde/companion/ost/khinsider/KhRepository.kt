package com.esde.companion.ost.khinsider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KhRepository {

    suspend fun search(query: String, type: Int = 1): List<KhAlbum> = withContext(Dispatchers.IO) {
        return@withContext KhScraper.searchAlbums(query, type)
    }

    suspend fun getTracks(album: KhAlbum): List<KhSong> = withContext(Dispatchers.IO) {
        return@withContext KhScraper.getAlbumTracks(album.url)
    }

    suspend fun getStreamableUrl(song: KhSong): String? = withContext(Dispatchers.IO) {
        val playable = KhScraper.resolveAudioLink(song.detailUrl)
        return@withContext playable?.directAudioUrl
    }
}