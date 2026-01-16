package com.esde.companion.ost

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

class MusicPlayer(
    private val repository: MusicRepository,
    private val player: ExoPlayer
) {
    suspend fun onGameFocused(gameTitle: String, gameFileName: String, system: String) {
        val file = repository.getMusicFile(gameTitle, gameFileName, system)
        player.stop()

        Log.d("CoroutineDebug", "Music file retrieved: " + file.toString())
        if(file != null && file.exists()) {
            playFile(file)
        }
    }

    private fun playFile(file: File) {
        Log.d("CoroutineDebug", "PLAYING MUSIC: " + file.toString())
        player.setMediaItem(MediaItem.fromUri(file.toUri()))
        player.prepare()
        player.play()
    }

    public fun stopPlaying() {
        player.stop()
    }
}