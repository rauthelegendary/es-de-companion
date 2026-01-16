package com.esde.companion.ost

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

class MusicRepository(
    private val context: Context,
    private val downloader: MusicDownloader
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

    suspend fun getMusicFile(gameTitle: String, gameFileName: String, system: String): File? {
        val systemDir: File? = getSystemFolder(system)
        var file = findExistingMusicFile(gameFileName, systemDir)

        if (file == null || !file.exists()) {
            Log.d("CoroutineDebug", "Music file doesnt exist, going to download")
            file = downloader.downloadGameMusic(gameTitle, gameFileName, system, systemDir)
        }

        Log.d("CoroutineDebug", "Music file exists!")
        return file
    }
}