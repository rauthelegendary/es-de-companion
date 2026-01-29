package com.esde.companion.ost

import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.esde.companion.ost.loudness.TARGET_LOUDNESS_DB
import kotlin.math.pow

class MusicPlayer(
    private val repository: MusicRepository,
    private val player: ExoPlayer
) {
    var targetVolume: Float = 1f
    private var hasBeenPlayed = false

    suspend fun onGameFocused(gameTitle: String, gameFileName: String?, system: String): Boolean {
        stopPlaying()
        hasBeenPlayed = false
        val song: Song? = repository.getMusicFile(gameTitle, gameFileName!!, system)
        if(song != null && song.file.exists()) {
            playFile(song)
            return true
        }
        return false
    }

    private fun playFile(song: Song) {
        Log.d("CoroutineDebug", "PLAYING MUSIC: " + song.toString())
        player.playWhenReady = false
        player.setMediaItem(MediaItem.fromUri(song.file.toUri()))
        setNormalizedVolume(song.loudnessDb)
        player.prepare()
        player.repeatMode = ExoPlayer.REPEAT_MODE_ONE
        Log.d("MusicDebug", "Player: ${System.identityHashCode(player)}")
    }

    fun setVolume(vol: Float) {
        player.volume = vol
    }

    fun pause() {
        player.pause()
    }

    fun play() {
        hasBeenPlayed = true
        player.play()
    }

    fun isPlaying(): Boolean {
        return player.isPlaying
    }

    fun stopPlaying() {
        player.pause()
        //player.stop()
        //player.clearMediaItems()
    }

    private fun setNormalizedVolume(songDb: Double) {
        val targetDb = TARGET_LOUDNESS_DB
        val difference = targetDb - songDb

        //limit gain to avoid distortion
        val safeAdjustment = difference.coerceAtMost(6.0)

        //player values need to be between 0-1
        val linearVolume = 10.0.pow(safeAdjustment / 20.0).toFloat()
        targetVolume = linearVolume.coerceIn(0f, 1f)
    }

    fun hasBeenPlayed(): Boolean {
        return hasBeenPlayed
    }

}