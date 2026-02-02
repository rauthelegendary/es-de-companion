package com.esde.companion.ost

import android.os.Environment
import com.esde.companion.ost.khinsider.KhAlbum
import com.esde.companion.ost.khinsider.KhRepository
import com.esde.companion.ost.khinsider.KhSong
import com.esde.companion.ost.loudness.LoudnessService
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File

class MusicRepository(
    private val downloader: YoutubeMediaService,
    private val loudnessService: LoudnessService,
    private val khRepository: KhRepository
) {
    private val musicDir: File? = File("${Environment.getExternalStorageDirectory()}/ES-DE Companion/music/")
    private val supportedExtensions = listOf("m4a", "mp3", "ogg", "opus", "wav")

    init {
        if (musicDir != null && !musicDir.exists()) {
            musicDir.mkdirs()
        }
    }
    private fun findExistingMusicFile(gameTitle: String, musicDirectory: File?): File? {
        for (ext in supportedExtensions) {
            val file = File(musicDirectory, "$gameTitle.$ext")
            if (file.exists()) return file
        }
        return null
    }

    private fun getSystemFolder(system: String): File? {
        val systemDir = musicDir?.resolve(system)

        if (systemDir != null && !systemDir.exists()) {
            systemDir.mkdirs()
        }
        return systemDir
    }

    suspend fun getMusicFile(gameTitle: String, gameFileName: String, system: String): Song? {
        val systemDir: File? = getSystemFolder(system)
        var file = findExistingMusicFile(gameFileName, systemDir)

        if (file == null || !file.exists()) {
            file = downloader.downloadGameMusic(gameTitle, gameFileName, system, systemDir)
        }

        if(file != null) {
            return Song(file, loudnessService.getOrComputeLoudness(file, system))
        }
        return null
    }

    suspend fun getAllPotentialResults(query: String, gameTitle: String, system: String): List<StreamInfoItem> {
        return downloader.getYoutubeSearchResultsFiltered(query, gameTitle, system, false)
    }

    suspend fun manualSelection(gameFilename: String, system: String, url: String, onProgress: (Float)-> Unit) {
        val systemDir: File? = getSystemFolder(system)
        deleteExistingFile(gameFilename, systemDir)

        downloader.downloadGameMusicWithUrl( gameFilename, systemDir, url, onProgress)
    }

    private fun deleteExistingFile(gameFilename: String, systemDir: File?) {
        findExistingMusicFile(gameFilename, systemDir)?.delete()
    }

    //album type is 1 for soundtrack, 2 for gamerip
    suspend fun getAlbums(query: String, albumType: Int = 1 ): List<KhAlbum> {
        return khRepository.search(query, albumType)
    }

    suspend fun getSongsFromAlbum(album: KhAlbum): List<KhSong> {
        return khRepository.getTracks(album)
    }

    suspend fun getPlayableUrlForSong(song: KhSong): String {
        return khRepository.getStreamableUrl(song) ?: ""
    }

    suspend fun manualKhSelection(
        gameFilenameSanitized: String,
        systemName: String,
        url: String,
        onProgress: (Float) -> Unit
    ) {
        val systemDir: File? = getSystemFolder(systemName)
        deleteExistingFile(gameFilenameSanitized, systemDir)
        downloader.downloadToLocal( url, gameFilenameSanitized, systemDir, onProgress, false)
    }
}