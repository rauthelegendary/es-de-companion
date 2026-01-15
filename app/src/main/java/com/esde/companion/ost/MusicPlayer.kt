package com.esde.companion.ost

import android.content.Context
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

class MusicPlayer(
    private val repository: MusicRepository,
    private val player: ExoPlayer
) {
    suspend fun onGameFocused(gameTitle: String) {
        val file = repository.getMusicFile(gameTitle)
        player.stop()
        if(file != null && file.exists()) {
            playFile(file)
        }
    }

    private fun playFile(file: File) {
        player.setMediaItem(MediaItem.fromUri(file.toUri()))
        player.prepare()
        player.play()
    }

}