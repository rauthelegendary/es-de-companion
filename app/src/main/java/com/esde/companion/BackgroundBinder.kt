package com.esde.companion

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.ui.platform.ComposeView
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
import com.esde.companion.ui.PageAnimation
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
    private val widgetContainer: ViewGroup,
    private val rootContainer: ViewGroup,
    private val videoCover: View,
    private val dimmerView: View,
    private var videoDelayHandler: Handler = Handler(Looper.getMainLooper()),
    private var musicStop: () -> Unit,
    private var widgetHide: () -> Unit,
    private val pathResolver: WidgetPathResolver,
    private val menuView: ComposeView
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
    private var playAnimation = false
    private var switchedGame = false
    private var switchedSystem = false
    private var widgetsLocked = false
    private var manualMuteInversion = false

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
                .collect {
                    allowedVolume = getAllowedAudioLevel()
                    player?.volume = allowedVolume//volumeFader.fadeTo(allowedVolume)
                }
        }
    }

    fun apply(page: WidgetPage, newState: AppState, mediaFile: File?, widgetsLocked: Boolean, forcedRefresh: Boolean = false) {
        val systemName = newState.getCurrentSystemName()
        val gameName = newState.getCurrentGameFilename()

        switchedSystem = previousSystem != systemName
        switchedGame = previousGame != gameName
        this.widgetsLocked = widgetsLocked

        if(!forcedRefresh && ((!switchedSystem && gameName == null && previousGame == "" || !switchedGame) && page.hasSameVisualSettings(currentPage))) {
            if(widgetsLocked && player != null && player!!.playbackState == Player.STATE_READY && !player!!.playWhenReady) {
                player?.play()
            }
            return
        }
        manualMuteInversion = false
        videoDelayRunnable?.let { videoDelayHandler?.removeCallbacks(it) }
        currentPage = page
        playAnimation = currentPage.animation == PageAnimation.PAGE || (currentPage.animation == PageAnimation.CONTEXT && (switchedGame || switchedSystem))
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
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
            imageView.animate().cancel()
            imageView.setTag(R.id.tag_base_scale_applied, false)

            imageView.load(file) {
                listener(
                    onStart = {
                        if (playAnimation) {
                            imageView.alpha = 0f
                        }
                    },
                    onSuccess = { _, _ ->
                        val shouldAnimate = currentPage.panZoomAnimation && currentPage.backgroundType != PageContentType.SCREENSHOT
                        if (playAnimation) {
                            imageView.animate()
                                .alpha(1f)
                                .setDuration(currentPage.animationDuration.toLong())
                                .withStartAction {
                                    if (shouldAnimate) {
                                        imageView.post {
                                            PanZoomAnimator.applyBaseScaleOnce(imageView)
                                        }
                                    }
                                }
                                .withEndAction {
                                    if (shouldAnimate) {
                                        PanZoomAnimator.startAnimation(imageView)
                                    }
                                }
                                .start()
                        } else {
                            imageView.alpha = 1f
                            if (shouldAnimate) {
                                PanZoomAnimator.applyBaseScaleOnce(imageView)
                                PanZoomAnimator.startAnimation(imageView)
                            }
                        }
                    },
                    onError = { _, _ ->
                        imageView.alpha = 1f
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
        imageView.visibility = View.GONE
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

        if (!widgetsLocked) {
            Log.d("MainActivity", "Video blocked - widget edit mode active")
            player?.pause()
            videoView.visibility = View.GONE
            return
        }

        if (file != null) {
            val delay = getVideoDelay()

            if (delay == 0L) {
                loadVideo(file)
            } else {
                stopVideoPlayer()
                if(!currentPage.isVideoMuted) {
                    AudioReferee.updateBackgroundState(true)
                }

                if (videoDelayHandler == null) {
                    videoDelayHandler = Handler(Looper.getMainLooper())
                }

                videoDelayRunnable = Runnable {
                    val shouldAllowDelayedVideo =
                        isActivityVisible &&
                                state is AppState.GameBrowsing &&
                                widgetsLocked

                    if (shouldAllowDelayedVideo) {
                        loadVideo(file)
                    } else {
                        AudioReferee.updateBackgroundState(false)
                    }
                }
                val tempImagePath = pathResolver.resolvePageMediaPath(PageContentType.FANART, previousSystem, previousGame, MediaSlot.Default, false)
                showImage(tempImagePath, false)
                videoDelayHandler.postDelayed(videoDelayRunnable!!, delay)
            }
        } else {
            stopVideoPlayer()
        }
    }

    private fun getAllowedAudioLevel(): Float {
        val muted = if (!manualMuteInversion) currentPage.isVideoMuted else !currentPage.isVideoMuted
        val audiolevel = if (AudioReferee.currentPriority.value == AudioReferee.AudioSource.BACKGROUND && !muted) 1f else 0f
        return audiolevel
    }


    private fun loadVideo(videoFile: File) {
        try {
            stopVideoPlayer()
            if(player == null) {
                buildVideoPlayer()
            }

            imageView.visibility = View.GONE
            val mediaItem = MediaItem.fromUri(videoFile.absolutePath)
            videoView.post {
                updateVideoLayering(currentPage.displayWidgetsOverVideo)
                videoCover.animate().cancel()
                if (playAnimation) {
                    videoCover.alpha = 1f
                    videoCover.visibility = View.VISIBLE
                } else {
                    videoCover.visibility = View.GONE
                }

                player?.addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        if (playAnimation) {
                            videoCover.animate()
                                .alpha(0f)
                                .setDuration(currentPage.animationDuration.toLong())
                                .withEndAction {
                                    videoCover.visibility = View.GONE
                                }
                                .start()
                        } else {
                            videoCover.visibility = View.GONE
                        }
                        player?.removeListener(this)
                    }
                })

                player?.setMediaItem(mediaItem)
                player?.volume = if (currentPage.isVideoMuted) 0f else allowedVolume
                player?.prepare()
                player?.playWhenReady = true
                player?.repeatMode = Player.REPEAT_MODE_ONE

                if(!currentPage.isVideoMuted) {
                    AudioReferee.updateBackgroundState(true)
                }
                videoView.visibility = View.VISIBLE
                videoView.alpha = 1f

                currentVideoPath = videoFile.absolutePath
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading video: ${videoFile.absolutePath}", e)
            releasePlayer()
        }
    }

    fun updateVideoLayering(widgetsOverVideo: Boolean) {
        if (!widgetsOverVideo) {
            videoView.bringToFront()
            menuView.bringToFront()
        } else {
           // videoView.translationZ = -1f
            dimmerView.bringToFront()
            widgetContainer.bringToFront()
            menuView.bringToFront()
        }

        rootContainer.requestLayout()
        rootContainer.invalidate()
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
        player?.volume = getAllowedAudioLevel()
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

    fun toggleMute() {
        manualMuteInversion = !manualMuteInversion
        if(currentPage.backgroundType == PageContentType.VIDEO && player?.isPlaying == true) {
            if (player?.volume == 0f) {
                AudioReferee.updateBackgroundState(true)
                player?.volume = getAllowedAudioLevel()
            } else {
                AudioReferee.updateBackgroundState(false)
                player?.volume = 0f
            }
        }
    }
}