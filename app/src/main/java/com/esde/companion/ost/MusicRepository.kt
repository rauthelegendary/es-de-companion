package com.esde.companion.ost

import android.content.Context
import java.io.File

class MusicRepository(
    private val context: Context,
    private val downloader: MusicDownloader
) {
    private val musicDir: File? = context.getExternalFilesDir("/Music")
    private val supportedExtensions = listOf("m4a", "mp3", "ogg", "opus", "wav")
    private fun findExistingMusicFile(gameTitle: String): File? {
        for (ext in supportedExtensions) {
            val file = File(musicDir, "$gameTitle.$ext")
            if (file.exists()) return file
        }
        return null
    }

    suspend fun getMusicFile(gameTitle: String): File? {
        val sanitizedTitle = gameTitle.replace(Regex("[^a-zA-Z0-9]"), "_")
        var file = findExistingMusicFile(sanitizedTitle)

        if (file != null && (!file.exists() || file.length() < 1024)) {
            file = downloader.downloadGameMusic(gameTitle, context)
        }
        return file
    }
}