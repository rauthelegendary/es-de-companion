package com.esde.companion

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.dispose
import coil.load
import com.esde.companion.OverlayWidget.MediaSlot
import com.esde.companion.animators.PanZoomAnimator
import com.esde.companion.ui.PageContentType
import com.esde.companion.ui.WidgetContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File


class BackgroundBinder(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val imageView: ImageView,
    private val videoView: PlayerView,
    private val dimmerView: View,
    private var videoDelayHandler: Handler = Handler(Looper.getMainLooper()),
    private var widgetsLocked: Boolean,
    private var musicStop: () -> Unit,
    private var widgetHide: () -> Unit,
    private val pathResolver: WidgetPathResolver
): DefaultLifecycleObserver {
    private var wasPlayingOnPause: Boolean = false
    var isActivityVisible: Boolean = true
    private lateinit var currentPage: WidgetPage
    private var player: ExoPlayer? = null
    private var volumeFader: VolumeFader = VolumeFader(player)
    private lateinit var state: AppState
    private var videoDelayRunnable: Runnable? = null
    private var currentVideoPath: String = ""

    private var previousSystem : String = ""
    private var previousGame: String = ""
    private var allowedVolume: Float = 1f

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()

    init {
        lifecycleOwner.lifecycle.addObserver(this)
        lifecycleOwner.lifecycleScope.launch {
            AudioReferee.currentPriority
                .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .distinctUntilChanged()
                .collect { priority ->
                    allowedVolume = getAllowedAudioLevel(priority)
                    player?.volume = allowedVolume//volumeFader.fadeTo(allowedVolume)
                }
        }
    }

    fun apply(page: WidgetPage, newState: AppState, mediaFile: File?, forcedRefresh: Boolean = false) {
        val systemName = newState.getCurrentSystemName()
        val gameName = newState.getCurrentGameFilename()

        if(!forcedRefresh && ((previousSystem == systemName && gameName == null && previousGame == "" || previousGame == gameName) && page.hasSameVisualSettings(currentPage))) {
            if(widgetsLocked && player != null && player!!.playbackState == Player.STATE_READY && !player!!.playWhenReady) {
                player?.play()
            }
            return
        }
        videoDelayRunnable?.let { videoDelayHandler?.removeCallbacks(it) }
        currentPage = page
        state = newState

        previousSystem = systemName ?: ""
        previousGame = gameName ?: ""

        //if not solid color or no system
        if ((page.backgroundType != PageContentType.SOLID_COLOR || page.solidColor == null) && systemName != null) {
            if(mediaFile != null) {
                if (page.backgroundType != PageContentType.CUSTOM_IMAGE && state.toWidgetContext() == WidgetContext.GAME) {
                    if (page.backgroundType == PageContentType.VIDEO) {
                        showVideo(mediaFile)
                    } else {
                        showImage(mediaFile)
                    }
                } else {
                    //If we're in system page
                    showImage(mediaFile)
                }
            }
        } else {
            imageView.setImageDrawable(page.solidColor!!.toDrawable())
            AudioReferee.updateBackgroundState(false)
        }

        dimmerView.alpha = 1.0f - page.backgroundOpacity

        if (page.blurRadius > 0) {
            val blurEffect = RenderEffect.createBlurEffect(
                page.blurRadius,
                page.blurRadius,
                Shader.TileMode.CLAMP
            )
            imageView.setRenderEffect(blurEffect)
        } else {
            imageView.setRenderEffect(null)
        }
    }

    private fun showImage(file: File?, stopVideo: Boolean = true) {
        if(file != null && file.exists()) {
            if(stopVideo) {
                stopVideoPlayer()
            }
            imageView.visibility = View.VISIBLE
            PanZoomAnimator.stopPanZoom(imageView)
            imageView.animate().cancel()
            imageView.setTag(R.id.tag_base_scale_applied, false)

            imageView.load(file) {
                if (currentPage.swapAnimation) {
                    crossfade(currentPage.animationDuration)
                } else {
                    crossfade(0)
                }

                listener(
                    onSuccess = { _, _ ->
                        if (currentPage.panZoomAnimation) {
                            PanZoomAnimator.applyBaseScaleOnce(imageView)
                            imageView.post {
                                PanZoomAnimator.startAnimation(imageView)
                            }
                        }
                    }
                )
            }
        }
    }


    fun stopVideoPlayer() {
        videoView.visibility = View.GONE
        player?.stop()
        player?.clearMediaItems()
        AudioReferee.updateBackgroundState(false)
    }

    private fun showVideo(file: File?) {
        //if (currentVideoPath == file?.absolutePath && (player?.isPlaying == true)) {
        //    return
        //}
        imageView.visibility = View.GONE
        //should I clear image here or something?
        videoView.visibility = View.VISIBLE

        if (state is AppState.GamePlaying) {
            Log.d("MainActivity", "Video blocked - game is playing (ES-DE event)")
            releasePlayer()
            return
        }

        if (state is AppState.Screensaver) {
            Log.d("MainActivity", "Video blocked - screensaver active")
            releasePlayer()
            return
        }

        // Block videos during widget edit mode
        if (!widgetsLocked) {
            Log.d("MainActivity", "Video blocked - widget edit mode active")
            player?.pause()
            //stopVideoPlayer()
            return
        }

        if (file != null) {
            val delay = getVideoDelay()

            if (delay == 0L) {
                // Instant - load video immediately
                loadVideo(file)
            } else {
                // Delayed - show image first, then video
                stopVideoPlayer() // Stop any current video
                if(!currentPage.isVideoMuted) {
                    AudioReferee.updateBackgroundState(true)
                }

                if (videoDelayHandler == null) {
                    videoDelayHandler = Handler(Looper.getMainLooper())
                }

                videoDelayRunnable = Runnable {
                    // Check if conditions are still valid for playing video
                    // Only check reliable signals
                    val shouldAllowDelayedVideo =
                        isActivityVisible &&        // Still visible (window-level, not app state)
                                state is AppState.GameBrowsing &&                 // Still browsing (not playing or screensaver)
                                widgetsLocked                                     // Widget edit mode OFF

                    if (shouldAllowDelayedVideo) {
                        loadVideo(file)
                    } else {
                        AudioReferee.updateBackgroundState(false)
                    }
                }
                val tempImagePath = pathResolver.resolvePageMediaPath(PageContentType.FANART, previousSystem, previousGame, MediaSlot.Default)
                showImage(tempImagePath, false)
                videoDelayHandler.postDelayed(videoDelayRunnable!!, delay)
            }
        } else {
            stopVideoPlayer()
        }
    }

    private fun getAllowedAudioLevel(priority: AudioReferee.AudioSource): Float {
        return if (priority == AudioReferee.AudioSource.BACKGROUND && !currentPage.isVideoMuted) 1f else 0f
    }


    private fun loadVideo(videoFile: File) {
        try {
            stopVideoPlayer()
            if(player == null) {
                buildVideoPlayer()
            }
            // Set volume based on system volume
            //updateVideoVolume()

            // Create media item
            imageView.visibility = View.GONE
            val mediaItem = MediaItem.fromUri(videoFile.absolutePath)
            videoView.post {
                player?.setMediaItem(mediaItem)
                player?.volume = if (currentPage.isVideoMuted) 0f else allowedVolume
                player?.prepare()
                player?.playWhenReady = true
                player?.repeatMode = Player.REPEAT_MODE_ONE
                //player?.play()

                //Hide widgets when video plays
                //widgetHide()

                if(!currentPage.isVideoMuted) {
                    AudioReferee.updateBackgroundState(true)
                }

                // Show video view with animation
                videoView.visibility = View.VISIBLE
                if(currentPage.swapAnimation) {
                    videoView.alpha = 0f
                    videoView.scaleX = 1f
                    videoView.scaleY = 1f
                    videoView.animate()
                    .alpha(1f)
                    .setDuration(currentPage.animationDuration.toLong())
                    .setInterpolator(DecelerateInterpolator())
                    .start()
                }
                currentVideoPath = videoFile.absolutePath
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading video: ${videoFile.absolutePath}", e)
            releasePlayer()
        }
    }

    /**
     * Release video player
     */
    fun releasePlayer() {
        player?.let { p ->
            try {
                p.stop()
                p.release()
            } catch (e: IllegalStateException) {
                Log.e("BackgroundBinder", "Player already dead, skipping release: ${e.message}")
            } finally {
                player = null
            }
        }
        videoDelayRunnable?.let { videoDelayHandler?.removeCallbacks(it) }
        AudioReferee.updateBackgroundState(false)
    }

    fun onBlackscreen() {
        imageView.dispose()
        imageView.setImageDrawable(null)
        imageView.visibility = View.GONE
        videoView.visibility = View.GONE
        releasePlayer()
        AudioReferee.updateBackgroundState(false)
        musicStop()
    }

    fun loadBuiltInFallbackBackground() {
       /** try {
            //TODO: How to access this better
            val assetPath = "fallback/default_background.webp"
            // Copy asset to cache for loadImageWithAnimation
            val fallbackFile = File(File.cacheDir, "default_background.webp")
            if (!fallbackFile.exists()) {
                assets.open(assetPath).use { input ->
                    fallbackFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            // Use loadImageWithAnimation for consistent behavior
            loadImageWithAnimation(fallbackFile, gameImageView) {
                android.util.Log.d("MainActivity", "Loaded built-in fallback image from assets")
            }
        } catch (e: Exception) {
            android.util.Log.w(
                "MainActivity",
                "Failed to load built-in fallback image, using solid color",
                e
            )
            // Final fallback: solid color (no animation possible)
            imageView.setBackgroundColor(android.graphics.Color.parseColor("#1A1A1A"))
            imageView.setImageDrawable(null)
        }*/
    }

    /**
     * Check if video is currently playing
     */
    private fun isVideoPlaying(): Boolean {
        return player?.isPlaying == true
    }

    /**
     * Get video delay in milliseconds
     */
    private fun getVideoDelay(): Long {
        return (currentPage.videoDelay * 1000L)
    }

    private fun resetVideoPlayer() {
        isActivityVisible = true
        videoView.player = null
        buildVideoPlayer()

        videoView.player = player
        if (player?.mediaItemCount == 0 && !currentVideoPath.isEmpty()) {
            val mediaItem = MediaItem.fromUri(currentVideoPath)
            player?.setMediaItem(mediaItem)
        }
        val currentPos = player?.currentPosition ?: 0L
        player?.seekTo(currentPos)
        player?.prepare()
        if(wasPlayingOnPause) {
            player?.play()
            wasPlayingOnPause = false
        }
        //volumeFader.fadeTo(getAllowedAudioLevel(AudioReferee.currentPriority.value))
        player?.volume = getAllowedAudioLevel(AudioReferee.currentPriority.value)
    }

    override fun onStart(owner: LifecycleOwner) {
        resetVideoPlayer()
    }

    private fun buildVideoPlayer() {
        player = ExoPlayer.Builder(context).build()
        player?.setAudioAttributes(audioAttributes, false)
        videoView.player = player
        volumeFader = VolumeFader(player)
    }

    fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            // The user just returned from Recents or pulled down the notification shade
            Log.d("AppFocus", "Window regained focus - Recovering Background")
            resetVideoPlayer()
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        isActivityVisible = false
        videoDelayRunnable?.let { videoDelayHandler?.removeCallbacks(it) }
        if(player?.isPlaying == true) {
            player?.pause()
            wasPlayingOnPause = true
        }
    }
}