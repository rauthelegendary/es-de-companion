package com.esde.companion.managers

import android.R.attr.value
import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.esde.companion.AudioReferee
import com.esde.companion.VolumeFader
import com.esde.companion.data.AppConstants
import com.esde.companion.data.AppState
import com.esde.companion.data.MusicSource
import com.esde.companion.ost.GameMusicRepository
import com.esde.companion.ost.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * ═══════════════════════════════════════════════════════════
 * MUSIC MANAGER
 * ═══════════════════════════════════════════════════════════
 * Manages background music playback for ES-DE Companion.
 *
 * FEATURES:
 * - State-based music playback (system/game/screensaver)
 * - System-specific music support
 * - Cross-fade transitions between sources
 * - Video ducking/pausing
 * - Playlist management with looping
 *
 * ARCHITECTURE:
 * - Follows standard manager pattern
 * - Lifecycle: init -> use -> cleanup()
 * - Callbacks for UI updates (song changed, playback state)
 * - Activity visibility tracking (onActivityVisible/Invisible)
 * ═══════════════════════════════════════════════════════════
 */
class MusicManager(
    private val context: Context,
    private val repository: GameMusicRepository,
    private val lifecycleOwner: LifecycleOwner,
    private val prefsManager: PreferencesManager
): DefaultLifecycleObserver {

    companion object {
        private const val TAG = "MusicManager"

        // Supported audio formats
        private val AUDIO_EXTENSIONS = AppConstants.FileExtensions.AUDIO

        // Animation durations
        private const val CROSS_FADE_DURATION = AppConstants.Timing.MUSIC_CROSS_FADE_DURATION
        private const val DUCK_FADE_DURATION = AppConstants.Timing.MUSIC_DUCK_FADE_DURATION

        // Volume levels
        private const val NORMAL_VOLUME = AppConstants.UI.MUSIC_NORMAL_VOLUME
        private const val DUCKED_VOLUME = AppConstants.UI.MUSIC_DUCKED_VOLUME
    }

    // ========== PLAYBACK STATE ==========

    private var musicPlayer: ExoPlayer? = null
    private var enhancer: LoudnessEnhancer? = null
    private var volumeFader: VolumeFader? = null
    private var currentMusicSource: MusicSource? = null
    private var currentPlaylist: List<File> = emptyList()
    private var currentTrackIndex: Int = 0
    private var currentVolume: Float = NORMAL_VOLUME
    private var targetVolume: Float = NORMAL_VOLUME
    private var currentTrackMaxVolume: Float = NORMAL_VOLUME
    private var isCurrentlyScraping: Boolean = false
    // Video interaction state
    private var isMusicDucked: Boolean = false
    private var wasMusicPausedForVideo: Boolean = false

    // Handler for volume fades and track transitions
    private val handler = Handler(Looper.getMainLooper())
    private var volumeFadeRunnable: Runnable? = null

    // Track the last AppState to detect source changes
    private var lastState: AppState? = null
    // Track if music is actually playing (stopped during GamePlaying)
    private var isMusicPlaying: Boolean = false

    // Track if activity is visible (onStart/onStop lifecycle)
    private var isActivityVisible: Boolean = true
    // Track if music was playing before becoming invisible
    private var wasMusicPlayingBeforeInvisible: Boolean = false
    // Track if black overlay is shown
    private var isBlackOverlayShown: Boolean = false

    // Callback for song title updates
    private var onSongChanged: ((String, MusicSource) -> Unit)? = null
    // Callback for when music stops
    private var onMusicStopped: (() -> Unit)? = null
    // Callback for when playback state changes (playing/paused)
    private var onPlaybackStateChanged: ((Boolean) -> Unit)? = null

    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentLoadingJob: Job? = null

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    init {
        Log.d(TAG, "MusicManager initialized")
        Log.d(TAG, "Base music path: ${getMusicPath()}")
        musicPlayer = buildNewMusicPlayer()

        lifecycleOwner.lifecycle.addObserver(this)
        lifecycleOwner.lifecycleScope.launch {
            AudioReferee.currentPriority
                .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect {
                    onAudioPriorityChanged()
                }
        }
    }

    private fun buildNewMusicPlayer(): ExoPlayer {
        val player = ExoPlayer.Builder(context).build()
        player.setAudioAttributes(
            audioAttributes,
            true
        )
        volumeFader = VolumeFader(player)

        player.addListener(object : Player.Listener {
            @OptIn(UnstableApi::class)
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

            override fun onPlaybackStateChanged(state: Int) {
                // When the single file finishes, the state moves to ENDED
                if (state == Player.STATE_ENDED) {
                    Log.d(TAG, "Track ended, moving to next track")
                    playNextTrack()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "ExoPlayer error: ${error.message}")
                playNextTrack()
            }
        })
        return player
    }

    private fun initEnhancer(sessionId: Int) {
        try {
            enhancer?.release()
            enhancer = LoudnessEnhancer(sessionId).apply { enabled = true }
        } catch (e: Exception) {
            Log.e("MusicPlayer", "LoudnessEnhancer not supported: ${e.message}")
        }
    }

    // ========== PATH MANAGEMENT ==========

    /**
     * Get the music path from preferences, or use default.
     */
    private fun getMusicPath(): String {
        return prefsManager.musicPath
    }

    private fun onAudioPriorityChanged() {
        if(allowedToPlay()) {
            onPriorityGained()
        } else {
            onPriorityLost()
        }
    }

    // ========== PUBLIC API (MusicController Interface) ==========

    suspend fun onManualSelect(gameTitle: String, gameFilename: String?, system: String, state: AppState) : Boolean {
        val song: Song? = repository.getMusicFile(gameTitle, gameFilename!!, system)
        if (song != null && song.file.exists()) {
            val source: MusicSource.Game = getMusicSourceForState(state) as MusicSource.Game
            source.song = song
            crossFadeToSource(source)
            return true
        }
        return false
    }

    fun onStateChanged(newState: AppState) {
        Log.d(TAG, "━━━ STATE CHANGE ━━━")
        Log.d(TAG, "New state: $newState")

        currentLoadingJob?.cancel()
        currentLoadingJob = managerScope.launch {

            // CRITICAL: Don't play music if activity is not visible
            // This prevents music from playing when:
            // - Device is asleep
            // - Another app is on top (e.g., ES-DE when scrolling)
            // - User has switched away from companion
            if (!isActivityVisible) {
                Log.d(TAG, "Music blocked - activity not visible")
                stopMusic()
                lastState = newState
                return@launch
            }

            // CRITICAL: Don't play music if black overlay is shown
            // User has explicitly hidden the companion display
            if (isBlackOverlayShown) {
                Log.d(TAG, "Music blocked - black overlay shown")
                stopMusic()
                lastState = newState
                return@launch
            }

            // Check if music is globally enabled
            if (!isMusicEnabled()) {
                Log.d(TAG, "Music disabled globally - stopping playback")
                stopMusic()
                lastState = newState
                return@launch
            }

            // Determine if this state should play music
            val shouldPlay = shouldPlayMusicForState(newState)
            Log.d(TAG, "Should play music: $shouldPlay")

            if (!shouldPlay) {
                stopMusic()
                lastState = newState
                return@launch
            }

            // Get the music source for this state
            val requestedSource = getMusicSourceForState(newState)
            Log.d(TAG, "Requested music source: $requestedSource")

            if (requestedSource == null) {
                Log.d(TAG, "No music source available")
                stopMusic()
                lastState = newState
                return@launch
            }

            // Resolve the actual source (after fallback) to determine if cross-fade is needed
            val actualSource = resolveActualSource(requestedSource)
            Log.d(TAG, "Actual music source (after fallback): $actualSource")

            if (actualSource == null) {
                Log.d(TAG, "No music files available")
                stopMusic()
                lastState = newState
                return@launch
            }

            if(currentMusicSource != actualSource) {
                // Determine if we need to cross-fade or continue
                val oldSource = currentMusicSource
                val needsCrossFade = shouldCrossFade(oldSource, actualSource, lastState, newState)

                Log.d(TAG, "Old source: $oldSource")
                Log.d(TAG, "Needs cross-fade: $needsCrossFade")

                if (needsCrossFade) {
                    // Different source - cross-fade
                    crossFadeToSource(actualSource)
                } else if (!isMusicPlaying) {
                    // Music was stopped (even if source is the same) - start fresh
                    Log.d(TAG, "Starting music (was not playing)")
                    startMusic(actualSource)
                } else if (oldSource == null) {
                    // No music playing - start fresh
                    Log.d(TAG, "Starting music (no previous source)")
                    startMusic(actualSource)
                } else {
                    // Same source AND already playing - continue
                    Log.d(TAG, "Continuing current music (same source)")
                }
                lastState = newState
             } else if(musicPlayer?.isPlaying == false){
                 if(musicPlayer?.playbackState == Player.STATE_READY) {
                     musicPlayer?.play()
                 } else {
                     startMusic(actualSource)
                 }
            }
        }
    }

    fun onPriorityLost() {
        if (musicPlayer == null || !musicPlayer!!.isPlaying) {
            return
        }

        val behavior = prefsManager.musicVideoBehavior
        Log.d(TAG, "Video started - behavior: $behavior")

        if(AudioReferee.getMenuState()) {
            pauseMusicForVideo()
        } else {
            when (behavior) {
                "continue" -> {
                    // Do nothing - music stays at 100%
                    Log.d(TAG, "Continuing music at full volume")
                }

                "duck" -> {
                    duckMusic()
                }

                "pause" -> {
                    pauseMusicForVideo()
                }
            }
        }
    }

    fun onPriorityGained() {
        Log.d(TAG, "Video ended")

        // Don't restore music if we're in GamePlaying state
        // Music should stay stopped during gameplay
        if (lastState is AppState.GamePlaying) {
            Log.d(TAG, "Not restoring music - game is playing")
            // Reset video interaction flags
            isMusicDucked = false
            wasMusicPausedForVideo = false
            return
        }

        if (isMusicDucked) {
            restoreMusicVolume()
        } else if (wasMusicPausedForVideo) {
            resumeMusicFromVideo()
        }
    }

    fun onBlackOverlayChanged(isShown: Boolean) {
        Log.d(TAG, "━━━ BLACK OVERLAY ${if (isShown) "SHOWN" else "HIDDEN"} ━━━")
        isBlackOverlayShown = isShown

        if (isShown) {
            // Black overlay shown - pause music
            Log.d(TAG, "Pausing music (black overlay shown)")

            if (musicPlayer?.isPlaying == true) {
                wasMusicPausedForVideo = true

                // Fade out then pause
                volumeFader?.fadeTo(0f, CROSS_FADE_DURATION) {
                    musicPlayer?.pause()
                }
            }
        } else {
            // Black overlay hidden - resume music
            Log.d(TAG, "Resuming music (black overlay hidden)")

            // Resume if we paused it
            if (wasMusicPausedForVideo) {
                musicPlayer?.play()
                volumeFader?.fadeTo(getVolumeForPriority(), CROSS_FADE_DURATION)
                wasMusicPausedForVideo = false
            }

            // NOTE: MainActivity will call onStateChanged() after this to handle
            // the case where user scrolled to a different game while overlay was shown
        }
    }

    fun onActivityVisible() {
        Log.d(TAG, "━━━ ACTIVITY VISIBLE ━━━")
        isActivityVisible = true

        // Check if music is enabled before resuming
        val musicEnabled = prefsManager.musicEnabled
        if (!musicEnabled) {
            Log.d(TAG, "Music disabled - not resuming")
            wasMusicPlayingBeforeInvisible = false
            return
        }

        // Resume music if it was playing before visibility was lost
        if (wasMusicPlayingBeforeInvisible) {
            Log.d(TAG, "Resuming music (was playing before invisible)")

            // Restart the music source that was playing
            if (currentMusicSource != null) {
                Log.d(TAG, "Restarting music from source: $currentMusicSource")
                startMusic(currentMusicSource!!)
            } else {
                Log.d(TAG, "No music source to resume")
                isMusicPlaying = false
            }
        } else {
            Log.d(TAG, "Not resuming music (was not playing)")
        }
    }

    fun onActivityInvisible() {
        Log.d(TAG, "━━━ ACTIVITY INVISIBLE ━━━")
        isActivityVisible = false
        // Pause music if currently playing
        if (musicPlayer?.isPlaying == true) {
            Log.d(TAG, "Pausing music (activity not visible)")
            wasMusicPlayingBeforeInvisible = true
        } else {
            Log.d(TAG, "Music not playing - no pause needed")
            wasMusicPlayingBeforeInvisible = false
        }

        musicPlayer?.pause()
    }

    fun cleanup() {
        Log.d(TAG, "Cleaning up music resources")

        // Cancel any pending operations
        volumeFadeRunnable?.let(handler::removeCallbacks)
        managerScope.cancel()

        // Release player
        musicPlayer?.release()
        musicPlayer = null

        currentMusicSource = null
        currentPlaylist = emptyList()
        lastState = null
        isMusicPlaying = false
    }

    /**
     * Set callback for when song changes
     */
    fun setOnSongChangedListener(listener: (String, MusicSource) -> Unit) {
        onSongChanged = listener
    }

    /**
     * Set callback for when music stops
     */
    fun setOnMusicStoppedListener(listener: () -> Unit) {
        onMusicStopped = listener
    }

    /**
     * Set callback for when playback state changes (playing/paused).
     * @param listener Callback with Boolean parameter: true = playing, false = paused
     */
    fun setOnPlaybackStateChangedListener(listener: (Boolean) -> Unit) {
        onPlaybackStateChanged = listener
    }

    // ========== MUSIC CONTROL ==========

    /**
     * Start playing music from a new source.
     */
    private fun startMusic(source: MusicSource) {
        Log.d(TAG, "Starting music from source: $source")

        // Load playlist for this source
        val playlist = loadPlaylist(source)
        if (playlist.isEmpty()) {
            Log.d(TAG, "No music files found for source: $source")
            isMusicPlaying = false
            return
        }

        Log.d(TAG, "Loaded playlist with ${playlist.size} tracks")

        // Store new state
        currentMusicSource = source
        currentPlaylist = playlist
        currentTrackIndex = 0

        // Reset volume state for new playback
        targetVolume = NORMAL_VOLUME

        // Play first track
        playTrack(playlist[0])
        isMusicPlaying = true
    }

    /**
     * Stop all music playback.
     */
    private fun stopMusic() {

        if (musicPlayer == null) {
            return
        }

        Log.d(TAG, "Stopping music")

        // Notify listener that music stopped
        onMusicStopped?.invoke()

        // Fade out then stop
        volumeFader?.fadeTo(0f, CROSS_FADE_DURATION) {
            musicPlayer?.apply {
                stop()
                clearMediaItems()
            }
            currentMusicSource = null
            currentPlaylist = emptyList()
            isMusicDucked = false
            wasMusicPausedForVideo = false
            isMusicPlaying = false
            targetVolume = NORMAL_VOLUME
        }
    }

    /**
     * Cross-fade from current source to a new source.
     */
    private fun crossFadeToSource(newSource: MusicSource) {
        Log.d(TAG, "Cross-fading to source: $newSource")

        // Save reference to old player BEFORE changing musicPlayer
        val oldPlayer = musicPlayer
        val oldFader = volumeFader

        // Load new playlist
        val newPlaylist = loadPlaylist(newSource)
        if (newPlaylist.isEmpty()) {
            Log.d(TAG, "No music files found for new source")
            stopMusic()
            return
        }

        // Update state
        currentMusicSource = newSource
        currentPlaylist = newPlaylist
        currentTrackIndex = 0

        currentVolume = 0f
        targetVolume = NORMAL_VOLUME

        musicPlayer = buildNewMusicPlayer()
        playTrack(newPlaylist[0])
        isMusicPlaying = true

        if (oldPlayer != null && oldPlayer.isPlaying) {
            Log.d(TAG, "Fading out old player via captured reference")

            oldFader?.fadeTo(0f, CROSS_FADE_DURATION) {
                try {
                    oldPlayer.stop()
                    oldPlayer.release()
                    Log.d(TAG, "Old player released successfully after fade")
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing old player: ${e.message}")
                }
            }
        }
    }

    fun setNormalizedVolume(vol: Float, fadeImmediately: Boolean = true) {
        currentTrackMaxVolume = vol.coerceIn(0f, 2f)

        if (currentTrackMaxVolume <= 1.0f) {
            try { enhancer?.setTargetGain(0) } catch (e: Exception) {}
        } else {
            val boostDecibels = (currentTrackMaxVolume - 1.0f) * 8.0f // 0.0 to 8.0 dB
            val targetGainmB = (boostDecibels * 100).toInt()

            try {
                enhancer?.setTargetGain(targetGainmB)
            } catch (e: Exception) {
                Log.e(TAG, "Boost failed: ${e.message}")
            }
        }
        targetVolume = currentTrackMaxVolume.coerceAtMost(1.0f)

        if(fadeImmediately) {
            volumeFader?.fadeTo(getVolumeForPriority())
        }
    }

    /**
     * Play a specific audio track.
     */
    private fun playTrack(file: File) {
        Log.d(TAG, "Playing track: ${file.name}")

        // Notify listener of song change
        val songName = file.nameWithoutExtension
        onSongChanged?.invoke(songName, currentMusicSource!!)
        var loudness = 1.0f
        if(currentMusicSource is MusicSource.Game) {
            loudness = (currentMusicSource as MusicSource.Game).song?.loudnessDb?.toFloat() ?: 1.0f
        }

        setNormalizedVolume(loudness, false)

        try {
            // 1. Create the MediaItem from your file
            val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
            val playImmediately = allowedToPlay()
            musicPlayer?.apply {
                stop()
                clearMediaItems()
                setMediaItem(mediaItem)
                volume = 0f

                repeatMode = if (currentMusicSource is MusicSource.Game)
                    Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            Log.d(TAG, "ExoPlayer ready, starting fade")
                            onPlaybackStateChanged?.invoke(true)
                            volumeFader?.fadeTo(getVolumeForPriority(), CROSS_FADE_DURATION)
                            removeListener(this)
                        }
                    }
                })

                prepare()
                playWhenReady = playImmediately
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error playing track: ${file.name}", e)
            playNextTrack()
        }
    }

    private fun allowedToPlay(): Boolean {
        return (AudioReferee.currentPriority.value == AudioReferee.AudioSource.MUSIC
                || prefsManager.musicVideoBehavior != "pause")
                && !AudioReferee.getMenuState() && !isBlackOverlayShown
    }

    private fun getVolumeForPriority(): Float {
        var vol = targetVolume
        if(isMusicDucked) {
            vol *= DUCKED_VOLUME
        }
        return vol
    }

    /**
     * Play the next track in the playlist.
     */
    private fun playNextTrack() {
        if (currentPlaylist.isEmpty()) {
            Log.d(TAG, "No playlist loaded")
            return
        }

        // Move to next track (loop back to start if at end)
        currentTrackIndex = (currentTrackIndex + 1) % currentPlaylist.size
        Log.d(TAG, "Next track index: $currentTrackIndex / ${currentPlaylist.size}")

        playTrack(currentPlaylist[currentTrackIndex])
    }

    // ========== PUBLIC PLAYBACK CONTROLS START ==========
    /**
     * Pause music playback (called by UI controls).
     */
    fun pauseMusic() {
        musicPlayer?.let { player ->
            if (player.isPlaying) {
                Log.d(TAG, "Pausing music via user control")
                player.pause()
                onPlaybackStateChanged?.invoke(false)
            }
        }
    }

    /**
     * Resume music playback (called by UI controls).
     */
    fun resumeMusic() {
        musicPlayer?.let { player ->
            if (!player.isPlaying) {
                Log.d(TAG, "Resuming music via user control")
                player.play()
                onPlaybackStateChanged?.invoke(true)
            }
        }
    }

    /**
     * Skip to next track in playlist (called by UI controls).
     */
    fun skipToNextTrack() {
        Log.d(TAG, "Skipping to next track via user control")
        playNextTrack()
    }

    /**
     * Check if music is currently playing.
     */
    fun isPlaying(): Boolean {
        return musicPlayer?.isPlaying ?: false
    }

    /**
     * Check if music player exists and is paused (not playing, but not released).
     */
    fun isPaused(): Boolean {
        val player = musicPlayer ?: return false
        return try {
            // If player exists and is NOT playing, it's paused
            !player.isPlaying
        } catch (_: IllegalStateException) {
            // Player was released
            false
        }
    }
    // ========== PUBLIC PLAYBACK CONTROLS END ==========

    // ========== VOLUME CONTROL ==========

    /**
     * Duck music volume for video playback.
     */
    private fun duckMusic() {
        Log.d(TAG, "Ducking music to ${DUCKED_VOLUME * 100}%")
        isMusicDucked = true
        volumeFader?.fadeTo(DUCKED_VOLUME * targetVolume, DUCK_FADE_DURATION)
    }

    /**
     * Restore music volume after video ends.
     */
    private fun restoreMusicVolume() {
        Log.d(TAG, "Restoring music to ${NORMAL_VOLUME * 100}%")
        isMusicDucked = false
        volumeFader?.fadeTo(targetVolume, DUCK_FADE_DURATION)
    }

    /**
     * Pause music for video playback.
     */
    private fun pauseMusicForVideo() {
        Log.d(TAG, "Pausing music for video")
        wasMusicPausedForVideo = true

        // Fade out then pause
        volumeFader?.fadeTo(0f, DUCK_FADE_DURATION) {
            musicPlayer?.pause()
        }
    }

    /**
     * Resume music after video ends.
     */
    private fun resumeMusicFromVideo() {
        Log.d(TAG, "Resuming music after video")
        wasMusicPausedForVideo = false

        musicPlayer?.play()
        volumeFader?.fadeTo(getVolumeForPriority(), DUCK_FADE_DURATION)
    }

    // ========== PLAYLIST MANAGEMENT ==========

    /**
     * Load all audio files from a music source.
     * Scans recursively through all subdirectories.
     */
    private fun loadPlaylist(source: MusicSource): List<File> {
        if (source is MusicSource.Game) {
            return source.song?.file?.let { listOf(it) } ?: emptyList()
        }

        val musicBaseDir = File(getMusicPath())

        if (source is MusicSource.System) {
            val systemId = source.systemName

            //First check the music/systems/systemname directory
            val systemsSubDir = File(musicBaseDir, "${AppConstants.Paths.MUSIC_SYSTEMS_SUBDIR}/$systemId")
            val systemsFiles = scanAudioFilesRecursively(systemsSubDir)
            if (systemsFiles.isNotEmpty()) return shufflePlaylist(systemsFiles)

            //Secondly, check the game song directory music/systemname
            val directSystemDir = File(musicBaseDir, systemId)
            val directFiles = scanAudioFilesRecursively(directSystemDir)
            if (directFiles.isNotEmpty()) return shufflePlaylist(directFiles)

            //Lastly, pick up any loose music files in the music/ directory
            Log.d(TAG, "No system-specific music found for $systemId, falling back to generic.")
            return loadPlaylist(MusicSource.Generic)
        }

        if (source is MusicSource.Generic) {
            // Shallow Scan: Only files directly inside "music/", no subfolders.
            Log.d(TAG, "Loading generic loose files from: ${musicBaseDir.absolutePath}")
            return shufflePlaylist(scanFolderShallow(musicBaseDir))
        }
        return emptyList()
    }

    private fun shufflePlaylist(playlist: List<File>): List<File> {
        return playlist.shuffled()
    }

    private fun isAudioFile(file: File): Boolean {
        return AUDIO_EXTENSIONS.any { ext -> file.name.endsWith(".$ext", ignoreCase = true) }
    }

    /**
     * Shallow Scan: Finds only audio files directly in the provided directory.
     * Ignores all subdirectories.
     */
    private fun scanFolderShallow(directory: File): List<File> {
        if (!directory.exists() || !directory.isDirectory) return emptyList()

        return directory.listFiles()?.filter { file ->
            file.isFile && isAudioFile(file)
        } ?: emptyList()
    }

    /**
     * Recursively scan a directory for audio files.
     *
     * @param directory The directory to scan
     * @return List of audio files found
     */
    private fun scanAudioFilesRecursively(directory: File): List<File> {
        if (!directory.exists() || !directory.isDirectory) return emptyList()

        val audioFiles = mutableListOf<File>()
        val foldersToScan = mutableListOf(directory)

        while (foldersToScan.isNotEmpty()) {
            val currentDir = foldersToScan.removeAt(0)
            currentDir.listFiles()?.forEach { file ->
                when {
                    file.isDirectory -> foldersToScan.add(file)
                    file.isFile && isAudioFile(file) -> audioFiles.add(file)
                }
            }
        }
        return audioFiles
    }

    // ========== STATE LOGIC ==========

    /**
     * Check if music is globally enabled.
     */
    private fun isMusicEnabled(): Boolean {
        return prefsManager.musicEnabled
    }

    /**
     * Determine if music should play for a given state.
     */
    private fun shouldPlayMusicForState(state: AppState): Boolean {
        return when (state) {
            is AppState.SystemBrowsing -> {
                prefsManager.musicSystemEnabled
            }
            is AppState.GameBrowsing -> {
                prefsManager.musicGameEnabled
            }
            is AppState.GamePlaying -> {
                false // Never play music during gameplay
            }
            is AppState.Screensaver -> {
                prefsManager.musicScreensaverEnabled
            }
        }
    }

    /**
     * Get the music source for a given state.
     */
    private fun getMusicSourceForState(state: AppState): MusicSource? {
        return when (state) {
            is AppState.SystemBrowsing -> {
                if (state.systemName.isNotEmpty()) {
                    MusicSource.System(state.systemName)
                } else {
                    MusicSource.Generic
                }
            }
            is AppState.GameBrowsing -> {
                if (state.gameName?.isNotEmpty() == true && prefsManager.musicScrapeGameEnabled) {
                    MusicSource.Game(state.systemName, state.gameName, state.gameFilename)
                } else if (state.systemName.isNotEmpty()) {
                    MusicSource.System(state.systemName)
                } else {
                    MusicSource.Generic
                }
            }
            is AppState.GamePlaying -> {
                null // No music during gameplay
            }
            is AppState.Screensaver -> {
                MusicSource.Generic // Always use generic for screensaver
            }
        }
    }

    /**
     * Resolve the actual music source after fallback logic.
     *
     * This determines what source will ACTUALLY be used after the loadPlaylist()
     * fallback logic is applied. This prevents unnecessary cross-fades when
     * multiple systems fall back to the same Generic source.
     *
     * @param requestedSource The initially requested source
     * @return The actual source that will be used, or null if no music available
     */
    private suspend fun resolveActualSource(requestedSource: MusicSource): MusicSource? {
        val baseMusicPath = getMusicPath()

        val sourcePath = requestedSource.getPath(baseMusicPath)
        // For Generic source, check if it exists
        if (requestedSource is MusicSource.Generic) {
            return if (hasAudioFiles(sourcePath)) requestedSource else null
        }

        // For System source, check if system folder exists with audio files
        else if (requestedSource is MusicSource.System) {
            // If system folder has audio files, use it
            //if (hasAudioFiles(sourcePath)) {
                return requestedSource
            //}

            // System folder doesn't exist/is empty - will fall back to Generic
            //Log.d(TAG, "System folder not found/empty, will use generic fallback")
            //val genericPath = MusicSource.Generic.getPath(baseMusicPath)
            //return if (hasAudioFiles(genericPath)) MusicSource.Generic else null
        }
        else if (requestedSource is MusicSource.Game) {
            requestedSource.song = repository.getMusicFile(
                requestedSource.gameName,
                requestedSource.gameFilename,
                requestedSource.systemName,
                prefsManager.musicScrapeGameEnabled
            ) {
                showScrapeNotification()
                pauseMusic()
            }
            return requestedSource
        }

        return null
    }

    private fun showScrapeNotification() {
        if (!isCurrentlyScraping) {
            isCurrentlyScraping = true

            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, "Searching for game music...", Toast.LENGTH_SHORT).show()

                // Basic debounce: don't show another toast for 3 seconds
                delay(3000)
                isCurrentlyScraping = false
            }
        }
    }

    /**
     * Check if a directory exists and contains audio files (recursively).
     */
    private fun hasAudioFiles(path: String): Boolean {
        val dir = File(path)

        if (!dir.exists() || !dir.isDirectory) {
            return false
        }

        val excludeSystems = !path.contains("/systems/") // Exclude systems folder only for generic path
        var audioFiles: List<File>
        if(excludeSystems) {
            audioFiles = scanFolderShallow(dir)
        } else {
            audioFiles = scanAudioFilesRecursively(dir)
        }
        return audioFiles.isNotEmpty()
    }

    /**
     * Determine if a state transition requires cross-fading.
     *
     * Cross-fade when:
     * - Source changes (e.g., Generic → System("snes"))
     * - System changes in SystemBrowsing (e.g., "snes" → "genesis")
     *
     * Continue without cross-fade when:
     * - SystemBrowsing → GameBrowsing (same system)
     * - GameBrowsing → SystemBrowsing (same system)
     */

    private fun shouldCrossFade(
        oldSource: MusicSource?,
        newSource: MusicSource,
        oldState: AppState?,
        newState: AppState
    ): Boolean {
        // If no old source, we're starting fresh (not a cross-fade)
        if (oldSource == null) {
            return false
        }

        // If sources are different, cross-fade
        if (oldSource != newSource) {
            return true
        }

        if(oldState is AppState.GameBrowsing && newState is AppState.GameBrowsing && oldState.gameFilename != newState.gameFilename) {
            return true
        }

        // Sources are the same - check state transitions
        // SystemBrowsing ↔ GameBrowsing should NOT cross-fade
        val isSystemToGame = oldState is AppState.SystemBrowsing && newState is AppState.GameBrowsing
        val isGameToSystem = oldState is AppState.GameBrowsing && newState is AppState.SystemBrowsing

        if (isSystemToGame || isGameToSystem) {
            // Same source, compatible states - continue playing
            //TODO: Maybe add a setting to turn this off again?
            return true
        }

        // Default: no cross-fade needed (same source)
        return false
    }
}