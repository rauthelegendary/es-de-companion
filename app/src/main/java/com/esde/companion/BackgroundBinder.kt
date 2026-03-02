package com.esde.companion

import android.R.attr.data
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.OptIn
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
import androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
import androidx.media3.ui.PlayerView
import coil.dispose
import com.esde.companion.data.Widget.MediaSlot
import com.esde.companion.data.AppState
import com.esde.companion.data.getCurrentGameFilename
import com.esde.companion.data.getCurrentSystemName
import com.esde.companion.managers.ImageManager
import com.esde.companion.managers.MediaManager
import com.esde.companion.ui.AnimationHelper
import com.esde.companion.ui.ContentType
import com.esde.companion.ui.PageAnimation
import com.esde.companion.ui.PageContentType
import com.esde.companion.ui.ScaleType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val menuView: ComposeView,
    private val animationSettings: AnimationSettings,
    private val imageManager: ImageManager,
    private val mediaManager: MediaManager
): DefaultLifecycleObserver {
    private var wasPlayingOnPause: Boolean = false
    var isActivityVisible: Boolean = true
    private var currentPage: WidgetPage? = null
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
    private var previousMediaFile: Any? = null

    private var transitionJob: Job? = null
    private var pendingTransitionCallback: (() -> Unit)? = null

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
                    volumeFader.fadeTo(allowedVolume)
                }
        }
    }

    fun apply(page: WidgetPage, newState: AppState, mediaFile: Any?, widgetsLocked: Boolean, onTransitionReady: (() -> Unit)? = null, forcedRefresh: Boolean = false) {
        val systemName = newState.getCurrentSystemName()
        val gameName = newState.getCurrentGameFilename()

        switchedSystem = previousSystem != systemName
        switchedGame = (previousGame != gameName) && gameName != null
        this.widgetsLocked = widgetsLocked
        val isVideo = mediaFile != null && mediaManager.isVideo(mediaFile)

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
        if(page.backgroundType == PageContentType.VIDEO || isVideo) {
            updateVideoLayering(page.displayWidgetsOverVideo)
        } else {
            updateVideoLayering(true)
        }
        manualMuteInversion = false
        if(sameContent(forcedRefresh, gameName, page, mediaFile, isVideo)) {
            if(widgetsLocked && player != null && player!!.playbackState == Player.STATE_READY && !player!!.playWhenReady) {
                player?.play()
            }
            currentPage = page
            updateVolume()
        } else {
            videoDelayRunnable?.let { videoDelayHandler?.removeCallbacks(it) }
            currentPage = page
            state = newState
            val animationType = animationSettings.transitionTarget.value
            playAnimation =
                animationType == PageAnimation.PAGE || (animationType == PageAnimation.CONTEXT && (switchedGame || switchedSystem))

            previousSystem = systemName ?: ""
            previousGame = gameName ?: ""
            previousMediaFile = mediaFile

            if (page.backgroundType == PageContentType.SOLID_COLOR && page.solidColor != null) {
                showSolidColor(page.solidColor!!)
            } else if (page.backgroundType == PageContentType.CUSTOM_IMAGE && page.customPath != null) {
                showImage(page.customPath)
            } else if (isVideo) {
                showVideo(mediaFile)
            } else {
                showImage(mediaFile)
            }
        }

        if(onTransitionReady != null && page.transitionToPage && (state is AppState.GameBrowsing || state is AppState.SystemBrowsing)) {
            scheduleTransition(page, isVideo, onTransitionReady)
        }
    }

    private fun sameContent(forcedRefresh: Boolean, gameName: String?, page: WidgetPage, mediaFile: Any?, isVideo: Boolean): Boolean {
        val sameSystemOrGame = (!switchedSystem && ((previousGame.isEmpty() && gameName == null) || !switchedGame))
        val sameVisualSettings = (currentPage != null && page.hasSameVisualSettings(currentPage!!, isVideo))
        val mediaFileUnchanged = ((currentPage != null && page.slot == currentPage!!.slot) || (previousMediaFile == mediaFile && mediaFile != null && page.backgroundType != PageContentType.CUSTOM_IMAGE))
        return !forcedRefresh && (sameSystemOrGame && sameVisualSettings && mediaFileUnchanged)
    }

    private fun showSolidColor(color: Int) {
        stopVideoPlayer()
        imageManager.load(imageView, color, playAnimation, true, panZoom = false)
    }

    private fun showImage(data: Any?, stopVideo: Boolean = true) {
        if(stopVideo) {
            stopVideoPlayer()
        }

        imageView.setTag(R.id.tag_base_scale_applied, false)
        imageManager.load(
            imageView = imageView,
            data = data,
            playAnimation = playAnimation,
            isBackground = true,
            panZoom = currentPage?.panZoomAnimation == true && currentPage?.backgroundType != PageContentType.SCREENSHOT,
            scaleType = currentPage?.scaleType
        )
    }

    fun stopVideoPlayer() {
        videoView.visibility = View.GONE
        player?.stop()
        player?.clearMediaItems()
        player?.trackSelectionParameters = player!!.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .build()
        AudioReferee.updateBackgroundState(false)
    }

    private fun showVideo(file: Any?) {
        imageView.visibility = View.GONE
        videoView.visibility = View.VISIBLE

        if (state is AppState.GamePlaying) {
            Log.d("MainActivity", "Video blocked - game is playing (ES-DE event)")
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
                if(currentPage?.isVideoMuted == false) {
                    AudioReferee.updateBackgroundState(true)
                }

                if (videoDelayHandler == null) {
                    videoDelayHandler = Handler(Looper.getMainLooper())
                }

                videoDelayRunnable = Runnable {
                    val shouldAllowDelayedVideo =
                        isActivityVisible &&
                                (state is AppState.GameBrowsing || state is AppState.Screensaver) &&
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
        val isVideoMuted = currentPage?.isVideoMuted ?: false
        val vol = currentPage?.videoVolume ?: 1f
        val muted = if (!manualMuteInversion) isVideoMuted else !isVideoMuted
        return if (AudioReferee.currentPriority.value == AudioReferee.AudioSource.BACKGROUND && !muted) vol else 0f
    }

    private var videoFirstFrameListener: Player.Listener? = null

    @OptIn(UnstableApi::class)
    private fun loadVideo(videoFile: Any) {
        try {
            stopVideoPlayer()
            if (player == null) buildVideoPlayer()

            val transitionToPageAfterVideo = currentPage?.transitionToPage == true && currentPage?.transitionToPageAfterVideo == true && (state is AppState.GameBrowsing || state is AppState.SystemBrowsing)

            imageView.visibility = View.GONE
            var mediaItem: MediaItem? = null
            if(videoFile is Uri) {
                mediaItem = MediaItem.fromUri(videoFile)
            } else if (videoFile is File) {
                mediaItem = MediaItem.fromUri(videoFile.absolutePath)
            }

            if(currentPage?.scaleType == ScaleType.CROP) {
                videoView.resizeMode = RESIZE_MODE_ZOOM
            } else {
                videoView.resizeMode = RESIZE_MODE_FIT
            }

            if(mediaItem != null) {
                videoFirstFrameListener?.let { player?.removeListener(it) }

                videoFirstFrameListener = object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        videoView.post {
                            videoCover.visibility = View.GONE
                            if (playAnimation) {
                                AnimationHelper.applyAnimation(
                                    videoView,
                                    animationSettings.duration.value.toLong(),
                                    animationSettings.animationStyle.value
                                )
                            } else {
                                videoView.alpha = 1f
                                videoView.scaleX = 1f
                                videoView.scaleY = 1f
                            }
                        }

                        videoFirstFrameListener?.let { player?.removeListener(it) }
                        videoFirstFrameListener = null
                    }
                }

                player?.addListener(videoFirstFrameListener!!)
                player?.trackSelectionParameters = player!!.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .build()
                player?.apply {
                    setMediaItem(mediaItem)
                    volume = 0f
                    repeatMode = if (transitionToPageAfterVideo) {
                        Player.REPEAT_MODE_OFF
                    } else {
                        Player.REPEAT_MODE_ONE
                    }
                    prepare()
                    playWhenReady = true
                }

                if (transitionToPageAfterVideo) {
                    player?.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_ENDED) {
                                pendingTransitionCallback?.invoke()
                                pendingTransitionCallback = null
                                player?.removeListener(this)
                            }
                        }
                    })
                }

                updateVolume()

                val runLayoutLogic = {
                    //updateVideoLayering(currentPage?.displayWidgetsOverVideo == true)

                    videoView.visibility = View.VISIBLE
                    videoView.alpha = if (playAnimation) 0f else 1f

                    videoCover.animate().cancel()
                    if (playAnimation) {
                        videoCover.alpha = 1f
                        videoCover.visibility = View.VISIBLE
                    } else {
                        videoCover.visibility = View.GONE
                    }
                }

                if (videoView.isAttachedToWindow) {
                    runLayoutLogic()
                } else {
                    videoView.post { runLayoutLogic() }
                }
            }

        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading video: $videoFile", e)
            releasePlayer()
        }
    }

    private fun scheduleTransition(page: WidgetPage, isVideo: Boolean, callback: () -> Unit) {
        cancelTransition()
        pendingTransitionCallback = callback

        if ((!isVideo || !page.transitionToPageAfterVideo) && page.transitionDelay >= 0) {
            transitionJob = lifecycleOwner.lifecycleScope.launch {
                delay(page.transitionDelay * 1000L)
                callback()
                pendingTransitionCallback = null
            }
        }
    }

    fun cancelTransition() {
        transitionJob?.cancel()
        transitionJob = null
        pendingTransitionCallback = null
    }

    private fun updateVolume() {
        AudioReferee.updateBackgroundState(true)
        allowedVolume = this.getAllowedAudioLevel()
        volumeFader.fadeTo(allowedVolume)
        if(allowedVolume == 0f) {
            AudioReferee.updateBackgroundState(false)
        }
    }

    private fun updateVideoLayering(widgetsOverVideo: Boolean) {
        if (!widgetsOverVideo) {
            widgetContainer.translationZ = 0f
            videoView.translationZ = 10f
            dimmerView.translationZ = 20f
        } else {
            videoView.translationZ = 0f
            dimmerView.translationZ = 10f
            widgetContainer.translationZ = 20f
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
                PlayerDebug.released("backgroundbinder")
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

    /**
     * Get video delay in milliseconds
     */
    private fun getVideoDelay(): Long {
        return (currentPage!!.videoDelay * 1000L)
    }

    private fun resetVideoPlayer() {
        isActivityVisible = true
        releasePlayer()
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

    private fun buildVideoPlayer() {
        try {
            player = ExoPlayer.Builder(context).build()
            PlayerDebug.created("backgroundbinder")
            player?.setAudioAttributes(audioAttributes, false)
            player?.addListener(object : Player.Listener {
                @OptIn(UnstableApi::class)
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    val isAudioSinkError = error.cause is androidx.media3.exoplayer.audio.AudioSink.InitializationException
                            || error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
                    if (isAudioSinkError) {
                        Log.w("BackgroundBinder", "AudioTrack init failed, rebuilding player")
                        releasePlayer()
                        Handler(Looper.getMainLooper()).postDelayed({
                            buildVideoPlayer()
                        }, 1000)
                    }
                }
            })
            videoView.player = player
            volumeFader = VolumeFader(player)
        } catch (e: Exception) {
            // existing retry logic
        }
    }

    fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            if (player == null || player?.playerError != null) {
                resetVideoPlayer()
            } else if (!isActivityVisible) {
                // Just resume if we have a healthy player
                isActivityVisible = true
                if (wasPlayingOnPause) {
                    player?.play()
                    wasPlayingOnPause = false
                }
            }
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

    fun toggleMute(): Boolean {
        manualMuteInversion = !manualMuteInversion
        if(player?.isPlaying == true && videoHasAudioTrack()) {
            if (player?.volume == 0f) {
                AudioReferee.updateBackgroundState(true)
                player?.volume = getAllowedAudioLevel()
            } else {
                AudioReferee.updateBackgroundState(false)
                player?.volume = 0f
            }
            return true
        }
        return false
    }

    fun videoHasAudioTrack(): Boolean {
        val tracks = player?.currentTracks
        return tracks?.groups?.any { group ->
            group.type == C.TRACK_TYPE_AUDIO && group.length > 0
        } == true
    }
}