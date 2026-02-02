package com.esde.companion.ost

import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.esde.companion.ost.loudness.TARGET_LOUDNESS_DB
import kotlin.math.pow

class MusicPlayer @OptIn(UnstableApi::class) constructor
    (
    private val repository: MusicRepository,
    private val player: ExoPlayer
) {
    private var enhancer: LoudnessEnhancer? = null

    // The "Natural" volume determined by the song's DB
    private var normalizedTarget: Float = 1f

    // The "User" volume (e.g., from a settings slider or mute toggle)
    private var userMasterVolume: Float = 1f

    private var hasBeenPlayed = false

    init {
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                // Check if the player is ready or a new track started
                if (events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                    if (player.playbackState == Player.STATE_READY) {
                        val exoPlayer = player as? ExoPlayer
                        val sessionId = exoPlayer?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET

                        if (sessionId != C.AUDIO_SESSION_ID_UNSET) {
                            initEnhancer(sessionId)
                        }
                    }
                }
            }
        })
    }

    fun setMasterVolume(vol: Float) {
        userMasterVolume = vol.coerceIn(0f, 1f)
        applyFinalVolume()
    }

    private fun initEnhancer(sessionId: Int) {
        try {
            enhancer?.release()
            enhancer = LoudnessEnhancer(sessionId).apply { enabled = true }
        } catch (e: Exception) {
            Log.e("MusicPlayer", "LoudnessEnhancer not supported: ${e.message}")
        }
    }

    suspend fun onGameFocused(gameTitle: String, gameFileName: String?, system: String): Boolean {
        stopPlaying()
        hasBeenPlayed = false
        val song: Song? = repository.getMusicFile(gameTitle, gameFileName!!, system)
        if (song != null && song.file.exists()) {
            playFile(song)
            return true
        }
        return false
    }

    private fun playFile(song: Song) {
        player.playWhenReady = false
        player.setMediaItem(MediaItem.fromUri(song.file.toUri()))

        calculateNormalization(song.loudnessDb)

        player.prepare()
        player.repeatMode = ExoPlayer.REPEAT_MODE_ONE
    }

    private fun calculateNormalization(songDb: Double) {
        val targetDb = TARGET_LOUDNESS_DB
        val difference = targetDb - songDb

        if (difference <= 0) {
            normalizedTarget = 10.0.pow(difference / 20.0).toFloat().coerceIn(0f, 1f)
            try { enhancer?.setTargetGain(0) } catch (e: Exception) {}
        } else {
            normalizedTarget = 1.0f
            val boost = difference.coerceAtMost(7.0)
            try {
                enhancer?.setTargetGain((boost * 100).toInt())
            } catch (e: Exception) {
                normalizedTarget = 10.0.pow(boost / 20.0).toFloat()
            }
        }
        applyFinalVolume()
    }

    private fun applyFinalVolume() {
        var norm = normalizedTarget
        var mas =userMasterVolume
        player.volume = norm * mas
    }

    fun play() {
        hasBeenPlayed = true
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun getMasterVolume(): Float {
        return userMasterVolume
    }

    fun isPlaying(): Boolean = player.isPlaying

    fun stopPlaying() {
        player.pause()
        // Optional: Reset enhancer gain when stopping
        try { enhancer?.setTargetGain(0) } catch (e: Exception) {}
    }

    /**
     * Required clean-up for the hardware effects
     */
    fun release() {
        enhancer?.release()
        enhancer = null
        player.release()
    }
}