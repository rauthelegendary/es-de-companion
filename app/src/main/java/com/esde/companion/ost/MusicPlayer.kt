package com.esde.companion.ost

import com.esde.companion.ost.loudness.TARGET_LOUDNESS_DB
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.pow

class MusicPlayer(
    private val repository: MusicRepository,
    private val player: ExoPlayer
) {
    private var fadeDuration: Long = 1000

    suspend fun onGameFocused(gameTitle: String, gameFileName: String?, system: String) {
        stopPlaying()
        val song: Song? = repository.getMusicFile(gameTitle, gameFileName!!, system)
        if(song != null && song.file.exists()) {
            playFile(song)
        }
    }

    private fun playFile(song: Song) {
        Log.d("CoroutineDebug", "PLAYING MUSIC: " + song.toString())
        player.setMediaItem(MediaItem.fromUri(song.file.toUri()))
        setNormalizedVolume(song.loudnessDb)
        player.prepare()
        player.repeatMode = ExoPlayer.REPEAT_MODE_ONE
        player.play()
        Log.d("MusicDebug", "Player: ${System.identityHashCode(player)}")
    }

    fun stopPlaying() {
        player.stop()
        player.clearMediaItems()
    }

    fun setVolume(newVolume: Float) {
        player.volume = newVolume
        Log.d("MusicDebug", "Setting volume on player: ${System.identityHashCode(player)} to $newVolume")
    }

    private fun setNormalizedVolume(songDb: Double) {
        val targetDb = TARGET_LOUDNESS_DB
        val difference = targetDb - songDb

        //limit gain to avoid distortion
        val safeAdjustment = difference.coerceAtMost(6.0)

        //player values need to be between 0-1
        val linearVolume = 10.0.pow(safeAdjustment / 20.0).toFloat()
        player.volume = linearVolume.coerceIn(0f, 1f)
    }

}