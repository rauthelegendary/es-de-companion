package com.esde.companion

import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.ColorStateList
import com.esde.companion.managers.ScriptManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.MimeTypeMap
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.edit
import androidx.core.view.GestureDetectorCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.annotation.ExperimentalCoilApi
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.imageLoader
import com.api.igdb.request.IGDBWrapper
import com.caverock.androidsvg.SVG
import com.esde.companion.MediaFileHelper.extractGameFilenameWithoutExtension
import com.esde.companion.MediaFileHelper.sanitizeGameFilename
import com.esde.companion.data.Widget.MediaSlot
import com.esde.companion.art.ArtRepository
import com.esde.companion.art.LaunchBox.LaunchBoxScraper
import com.esde.companion.art.MediaService
import com.esde.companion.art.SystemColorMapper
import com.esde.companion.art.igdb.IgdbArtScraper
import com.esde.companion.art.igdb.TwitchAuth
import com.esde.companion.art.mediaoverride.MediaOverride
import com.esde.companion.art.mediaoverride.MediaOverrideRepository
import com.esde.companion.art.steamgrid.SGDBScraper
import com.esde.companion.data.AppConstants
import com.esde.companion.data.AppState
import com.esde.companion.data.SavedBrowsingState
import com.esde.companion.data.ScreensaverGame
import com.esde.companion.data.Widget
import com.esde.companion.data.getCurrentGameFilename
import com.esde.companion.data.getCurrentSystemName
import com.esde.companion.data.toWidgetContext
import com.esde.companion.managers.AppLaunchManager
import com.esde.companion.managers.MusicManager
import com.esde.companion.managers.PreferencesManager
import com.esde.companion.metadata.GameListSyncManager
import com.esde.companion.metadata.GameRepository
import com.esde.companion.ost.GameMusicRepository
import com.esde.companion.ost.YoutubeMediaService
import com.esde.companion.ost.khinsider.KhRepository
import com.esde.companion.ost.khinsider.KhSong
import com.esde.companion.ost.loudness.LoudnessService
import com.esde.companion.ui.ContentType
import com.esde.companion.ui.PageContentType
import com.esde.companion.ui.WidgetContext
import com.esde.companion.ui.contextmenu.MainContextMenu
import com.esde.companion.ui.contextmenu.MenuUiState
import com.esde.companion.ui.contextmenu.WidgetActions
import com.esde.companion.ui.widget.WidgetSettingsOverlay
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.abs
import androidx.core.view.isVisible
import coil.Coil
import coil.decode.DecodeResult
import coil.memory.MemoryCache
import coil.request.ImageRequest
import com.esde.companion.art.ApiKeyManager
import com.esde.companion.art.ArtScraper
import com.esde.companion.art.LaunchBox.LaunchBoxDao
import com.esde.companion.art.ScraperType
import com.esde.companion.data.MusicSource
import com.esde.companion.data.ScraperCredentials
import com.esde.companion.managers.ImageManager
import com.esde.companion.managers.MediaManager
import com.esde.companion.ui.contextmenu.WidgetUiState
import com.esde.companion.ui.widget.WidgetControlMenu
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.schabi.newpipe.extractor.timeago.patterns.it
import java.util.EventListener


class ContextMenuStateHolder {
    fun isActive(): Boolean {
        return showMenu || widgetToEditState != null || widgetSelected.isVisible
    }

    var widgetToEditState by mutableStateOf<Widget?>(null)
    var showMenu by mutableStateOf(false)
    var widgetSelected by mutableStateOf(WidgetUiState())
}

class MainActivity : AppCompatActivity(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.3)
                    .build()
            }
            .okHttpClient {
                NetworkClientManager.baseClient
            }
            .components {
                add(SvgDecoder.Factory())
                add(ImageDecoderDecoder.Factory())
            }
            .build()
    }

    private var listeningToAudioRef: Boolean = false

    private lateinit var songTitleOverlay: LinearLayout
    private lateinit var songTitleText: TextView
    private lateinit var musicPlayPauseButton: ImageButton
    private lateinit var musicNextButton: ImageButton
    private var songTitleHandler: Handler? = null
    private var songTitleRunnable: Runnable? = null
    private lateinit var musicManager: MusicManager

    private lateinit var menuComposeView: ComposeView
    private lateinit var widgetToolbarMenuView: ComposeView
    private lateinit var artRepository: ArtRepository
    private lateinit var launchBoxDao: LaunchBoxDao
    private lateinit var mediaOverrideRepository: MediaOverrideRepository
    private lateinit var rootLayout: RelativeLayout
    private lateinit var appDrawer: View
    private lateinit var appRecyclerView: RecyclerView
    private lateinit var appSearchBar: EditText
    private lateinit var searchClearButton: ImageButton
    private lateinit var drawerBackButton: ImageButton
    private lateinit var settingsButton: ImageButton
    private lateinit var androidSettingsButton: ImageButton
    private lateinit var prefsManager: PreferencesManager
    private lateinit var appLaunchPrefs: AppLaunchPreferences
    private lateinit var mediaService: MediaService
    private lateinit var mediaManager: MediaManager
    private lateinit var blackOverlay: View
    private var isBlackOverlayShown = false

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var gestureDetector: GestureDetectorCompat

    // Widget system
    private lateinit var widgetContainer: ResizableWidgetContainer
    private lateinit var gameWidgetManager: WidgetManager
    private lateinit var systemWidgetManager: WidgetManager
    private lateinit var imageManager: ImageManager
    private lateinit var widgetPathResolver: WidgetPathResolver
    private lateinit var widgetViewBinder: WidgetViewBinder
    private lateinit var backgroundBinder: BackgroundBinder
    private var gridOverlayView: GridOverlayView? = null
    private var widgetsLocked by mutableStateOf(true)
    private var snapToGrid by mutableStateOf(true)
    private val gridSize = 40f
    private var showGrid by mutableStateOf(false)
    private var isInteractingWithWidget = false
    private var previousWidgetContext: WidgetContext? = null
    private var longPressHandler: Handler? = null
    private var longPressRunnable: Runnable? = null
    private var longPressTriggered = false
    private var isDrag by mutableStateOf(false)
    private var touchDownX = 0f
    private var touchDownY = 0f
    private val LONG_PRESS_TIMEOUT by lazy {
        ViewConfiguration.getLongPressTimeout().toLong()
    }
    private var widgetMenuShowing = false

    var currentGameVolume by mutableDoubleStateOf(1.0)
        private set


    // This tracks state alongside existing booleans during migration
    private var state: AppState = AppState.SystemBrowsing("")
        set(value) {
            val oldState = field
            field = value

            // Log state changes for debugging
            Log.d("MainActivity", "━━━ STATE CHANGE ━━━")
            Log.d("MainActivity", "FROM: $oldState")
            Log.d("MainActivity", "TO:   $value")
            Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━")


            musicManager.onStateChanged(value)

        }

    private var fileObserver: FileObserver? = null
    private var allApps = listOf<ResolveInfo>()  // Store all apps for search filtering
    private var hasWindowFocus = true  // Track if app has window focus (is on top)

    // Note: hasWindowFocus is window-level state, not app state
    private var isLaunchingFromScreensaver =
        false  // Track if we're launching game from screensaver
    private var screensaverInitialized = false  // Track if screensaver has loaded its first game

    // Video playback variables
    private var isActivityVisible = true  // Track onStart/onStop - most reliable signal

    // Flag to skip reload in onResume (used when returning from settings with no changes)
    private var skipNextReload = false

    // Double-tap detection variables
    private var tapCount = 0
    private var lastTapTime = 0L

    private var twoFingerTapCount = 0
    private var lastTwoFingerTapTime = 0L
    // Standard Android double-tap timeout (max time between taps)
    private val DOUBLE_TAP_TIMEOUT by lazy {
        ViewConfiguration.getDoubleTapTimeout().toLong() // Default: 300ms
    }
    // Custom minimum interval to prevent accidental activations (100ms)
    // This is intentionally higher than Android's internal 40ms hardware filter:
    // - 40ms filters touch controller artifacts (hardware-level)
    // - 100ms filters user errors like screen brushing (UX-level)
    // Still imperceptible to users while significantly reducing false positives
    private val MIN_TAP_INTERVAL = AppConstants.Timing.DOUBLE_TAP_MIN_INTERVAL

    // Scripts verification
    private var isWaitingForScriptVerification = false
    private var scriptVerificationHandler: Handler? = null
    private var scriptVerificationRunnable: Runnable? = null
    private var currentVerificationDialog: AlertDialog? = null

    private var autoTransitionJob: Job? = null
    private var currentErrorDialog: AlertDialog? = null
    private val SCRIPT_VERIFICATION_TIMEOUT = 15000L  // 15 seconds

    // Dynamic debouncing for fast scrolling - separate tracking for systems and games
    private val imageLoadHandler = Handler(Looper.getMainLooper())
    private var imageLoadRunnable: Runnable? = null
    private var musicSearchJob: Job? = null
    private var musicResults by mutableStateOf<List<StreamInfoItem>>(emptyList())
    private var isSearchingMusic by mutableStateOf(false)
    private var lastSystemScrollTime = 0L
    private var lastGameScrollTime = 0L
    private var gameInfoJob: Job? = null
    private var musicJob: Job? = null

    // System scrolling: Enable debouncing to reduce rapid updates
    private val SYSTEM_FAST_SCROLL_THRESHOLD = AppConstants.Timing.SYSTEM_FAST_SCROLL_THRESHOLD
    private val SYSTEM_FAST_SCROLL_DELAY = AppConstants.Timing.SYSTEM_FAST_SCROLL_DELAY
    private val SYSTEM_SLOW_SCROLL_DELAY = AppConstants.Timing.SYSTEM_SLOW_SCROLL_DELAY

    // Game scrolling: No debouncing for instant response
    private val GAME_FAST_SCROLL_THRESHOLD = AppConstants.Timing.GAME_FAST_SCROLL_THRESHOLD
    private val GAME_FAST_SCROLL_DELAY = AppConstants.Timing.GAME_FAST_SCROLL_DELAY
    private val GAME_SLOW_SCROLL_DELAY = AppConstants.Timing.GAME_SLOW_SCROLL_DELAY

    // Filter out game-select on game-start and game-end
    private var lastGameStartTime = 0L
    private var lastGameEndTime = 0L
    private val GAME_EVENT_DEBOUNCE = AppConstants.Timing.GAME_EVENT_DEBOUNCE

    private lateinit var loudnessService : LoudnessService
    private lateinit var gameMusicRepository: GameMusicRepository
    private var isNavigatingInternally = false

    private lateinit var animationSettings: AnimationSettings
    private var activeWidget: WidgetView? = null
    private var isTouchingWidgetToolbar: Boolean = false
    private val menuState = ContextMenuStateHolder()

    // Broadcast receiver for app install/uninstall events
    private val appChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REMOVED,
                Intent.ACTION_PACKAGE_REPLACED -> {
                    // Refresh app drawer when apps are installed/uninstalled
                    setupAppDrawer()
                }
            }
        }
    }

    // Settings launcher to handle when visual settings change
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val showWidgetTutorial =
                result.data?.getBooleanExtra("SHOW_WIDGET_TUTORIAL", false) ?: false
            if (showWidgetTutorial) {
                // Delay slightly to let UI settle after settings closes
                Handler(Looper.getMainLooper()).postDelayed({
                    showWidgetSystemTutorial(fromUpdate = false)
                }, AppConstants.Timing.WIZARD_DELAY)
            }
            val needsRecreate = result.data?.getBooleanExtra("NEEDS_RECREATE", false) ?: false
            val appsHiddenChanged =
                result.data?.getBooleanExtra("APPS_HIDDEN_CHANGED", false) ?: false
            val musicSettingsChanged = result.data?.getBooleanExtra("MUSIC_SETTINGS_CHANGED", false) ?: false
            val musicMasterToggleChanged = result.data?.getBooleanExtra("MUSIC_MASTER_TOGGLE_CHANGED", false) ?: false
            val closeDrawer = result.data?.getBooleanExtra("CLOSE_DRAWER", false) ?: false
            val videoSettingsChanged =
                result.data?.getBooleanExtra("VIDEO_SETTINGS_CHANGED", false) ?: false
            val logoSizeChanged = result.data?.getBooleanExtra("LOGO_SIZE_CHANGED", false) ?: false
            val mediaPathChanged =
                result.data?.getBooleanExtra("MEDIA_PATH_CHANGED", false) ?: false
            val imagePreferenceChanged =
                result.data?.getBooleanExtra("IMAGE_PREFERENCE_CHANGED", false) ?: false
            val logoTogglesChanged =
                result.data?.getBooleanExtra("LOGO_TOGGLES_CHANGED", false) ?: false
            val gameLaunchBehaviorChanged =
                result.data?.getBooleanExtra("GAME_LAUNCH_BEHAVIOR_CHANGED", false) ?: false
            val screensaverBehaviorChanged =
                result.data?.getBooleanExtra("SCREENSAVER_BEHAVIOR_CHANGED", false) ?: false
            val startVerification =
                result.data?.getBooleanExtra("START_SCRIPT_VERIFICATION", false) ?: false
            val customBackgroundChanged =
                result.data?.getBooleanExtra("CUSTOM_BACKGROUND_CHANGED", false) ?: false

            // Close drawer if requested (before recreate to avoid visual glitch)
            if (closeDrawer && ::bottomSheetBehavior.isInitialized) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }

            if (needsRecreate) {
                // Recreate the activity to apply visual changes (dimming, blur, drawer transparency)
                recreate()
            } else if (appsHiddenChanged) {
                // Refresh app drawer to apply hidden apps changes
                setupAppDrawer()
            } else if (gameLaunchBehaviorChanged && state is AppState.GamePlaying) {
                // Game launch behavior changed while game is playing - update display
                handleGameStart()
                // Skip reload in onResume to prevent override
                skipNextReload = true
            } else if (screensaverBehaviorChanged && state is AppState.Screensaver) {
                // Screensaver behavior changed while screensaver is active - update display
                refreshWidgets()
                // Skip reload in onResume to prevent override
                skipNextReload = true
            } else if (imagePreferenceChanged) {
                // Image preference changed - reload appropriate view
                if (state is AppState.GamePlaying) {
                    // Game is playing - update game launch display
                    Log.d("MainActivity", "Image preference changed during gameplay - reloading display")
                    handleGameStart()
                    skipNextReload = true
                } else if (state is AppState.SystemBrowsing) {
                    // In system view - reload system image with new preference
                    Log.d("MainActivity", "Image preference changed in system view - reloading system image")
                    loadSystemImage()
                    skipNextReload = true
                } else {
                    // In game browsing view - reload game image with new preference
                    Log.d("MainActivity", "Image preference changed in game view - reloading game image")
                    loadGameInfo()
                    skipNextReload = true
                }
            } else if (customBackgroundChanged) {
                // Custom background changed - reload to apply changes
                if (state is AppState.SystemBrowsing) {
                    loadSystemImage()
                } else if (state !is AppState.GamePlaying) {
                    // Only reload if not playing - if playing, customBackgroundChanged won't affect display
                    loadGameInfo()
                } else {
                    // Game is playing - skip reload since game launch behavior controls display
                    skipNextReload = true
                }
            } else if (videoSettingsChanged || logoSizeChanged || mediaPathChanged || logoTogglesChanged) {
                // Settings that affect displayed content changed - reload to apply changes
                if (state is AppState.SystemBrowsing) {
                    loadSystemImage()
                } else if (state !is AppState.GamePlaying) {
                    // Only reload if not playing - if playing, these settings don't affect game launch display
                    loadGameInfo()
                } else {
                    // Game is playing - skip reload
                    skipNextReload = true
                }
            } else if (musicMasterToggleChanged) {
                // Music MASTER TOGGLE changed - re-evaluate for both ON and OFF
                val musicEnabled = prefsManager.musicEnabled

                if (!musicEnabled) {
                    // Music was turned OFF - stop it
                    Log.d("MainActivity", "Music master toggle changed to OFF - stopping music")
                    hideSongTitle()
                    onStateChangedMusicHandler(state)
                } else {
                    // Music was turned ON - evaluate if it should play for current state
                    Log.d("MainActivity", "Music master toggle changed to ON - evaluating music state")
                    onStateChangedMusicHandler(state)
                }

                // CRITICAL FIX: If in GameBrowsing, reload to ensure UI is correct
                // (video might be playing and needs to be stopped, widgets hidden, etc.)
                if (state is AppState.GameBrowsing) {
                    Log.d("MainActivity", "Music master toggle changed in GameBrowsing - reloading game display")
                    loadGameInfo()
                    skipNextReload = true
                } else {
                    skipNextReload = true
                }
            } else if (musicSettingsChanged) {
                // Other music settings changed (not master toggle) - re-evaluate music for current state
                Log.d("MainActivity", "Music settings changed (not master) - re-evaluating music state")
                val songTitleEnabled = prefsManager.musicSongTitleEnabled

                // Check if song title display was toggled off
                if (!songTitleEnabled) {
                    hideSongTitle()
                }

                // Re-evaluate music for current state with new settings
                // This will start/stop music based on the new per-state toggles
                onStateChangedMusicHandler(state)

                // CRITICAL FIX: If in GameBrowsing, reload to ensure UI is correct
                // (video might be playing and needs to be stopped, widgets hidden, etc.)
                if (state is AppState.GameBrowsing) {
                    Log.d("MainActivity", "Music settings changed in GameBrowsing - reloading game display")
                    loadGameInfo()
                    skipNextReload = true
                } else {
                    skipNextReload = true
                }
            } else {
                // No settings changed that require reload
                // However, if we're in GameBrowsing state, we should still reload
                // to ensure video plays properly when returning from Settings
                if (state is AppState.GameBrowsing) {
                    Log.d("MainActivity", "No settings changed but in GameBrowsing - allowing reload for video")
                    skipNextReload = false
                } else {
                    Log.d("MainActivity", "No settings changed - skipping reload")
                    skipNextReload = true
                }
            }
            // Note: Video audio changes are handled automatically in onResume

            // Start script verification if requested
            if (startVerification) {
                // Delay slightly to let UI settle
                Handler(Looper.getMainLooper()).postDelayed({
                    startScriptVerification()
                }, AppConstants.Timing.WIZARD_DELAY)
            }
        }
    }

    private fun onStateChangedMusicHandler(state: AppState) {
        musicJob?.cancel()
        musicJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                musicManager.onStateChanged(state)
            } catch (e: Exception) {

            }
        }
    }

    private fun setupMusicCallbacks() {
        musicManager.setOnSongChangedListener { songName, source ->
            showSongTitle(songName, source)
        }

        musicManager.setOnMusicStoppedListener {
            hideSongTitle()
            updateMusicControls()
        }

        musicManager.setOnPlaybackStateChangedListener { isPlaying ->
            updateMusicControls()
        }
    }

    /**
     * Update music controls visibility and state based on MusicManager.
     */
    private fun updateMusicControls() {
        val hasActiveMusic = musicManager.isPlaying() || musicManager.isPaused()

        if (hasActiveMusic) {
            musicPlayPauseButton.setImageResource(
                if (musicManager.isPlaying()) R.drawable.ic_pause
                else R.drawable.ic_play
            )
        }

        Log.d("MainActivity", "Music controls updated: hasActiveMusic=$hasActiveMusic, isPlaying=${musicManager.isPlaying()}")
    }

    /**
     * Show song title overlay with current settings.
     */
    private fun showSongTitle(songName: String, source: MusicSource) {
        // Update text
        songTitleText.text = songName

        if (!prefsManager.musicSongTitleEnabled || (prefsManager.musicSongTitleSystemOnlyEnabled && source is MusicSource.Game)) {
            Log.d("MainActivity", "Song title display disabled in settings")
            return
        }

        // Show overlay with timeout
        showSongTitleOverlay()
    }

    /**
     * Show the song title overlay with configured timeout.
     */
    private fun showSongTitleOverlay() {
        // Cancel any pending hide
        songTitleRunnable?.let { songTitleHandler?.removeCallbacks(it) }

        if(!AudioReferee.getMenuState() && AudioReferee.currentPriority.value == AudioReferee.AudioSource.MUSIC) {
            // Apply background opacity setting
            val opacity = prefsManager.musicSongTitleOpacity
            val alpha = (opacity * 255 / 100).coerceIn(0, 255)
            val hexAlpha = String.format("%02x", alpha)
            val backgroundColor = Color.parseColor("#${hexAlpha}000000")
            songTitleOverlay.setBackgroundColor(backgroundColor)

            // Fade in
            songTitleOverlay.visibility = View.VISIBLE
            songTitleOverlay.animate()
                .alpha(1.0f)
                .setDuration(AppConstants.Timing.FADE_ANIMATION_DURATION)
                .start()

            // Get display duration
            val durationSetting = prefsManager.musicSongTitleDuration

            // If infinite (15), don't schedule fade out
            if (durationSetting == 15) {
                Log.d("MainActivity", "Song title set to infinite display")
                return
            }

            // Calculate duration: 0->2s, 1->4s, 2->6s, ... 14->30s
            val displayDuration =
                ((durationSetting + 1) * AppConstants.Timing.SONG_TITLE_STEP_SECONDS) * 1000L

            // Schedule fade out
            songTitleRunnable = Runnable {
                hideSongTitleOverlay()
            }
            songTitleHandler?.postDelayed(songTitleRunnable!!, displayDuration)

            Log.d("MainActivity", "Song title will auto-hide after ${displayDuration}ms")
        }
    }

    /**
     * Hide the song title overlay with fade animation.
     */
    private fun hideSongTitleOverlay() {
        songTitleRunnable?.let { songTitleHandler?.removeCallbacks(it) }

        if(songTitleOverlay.visibility == View.VISIBLE) {
            songTitleOverlay.animate()
                .alpha(0.0f)
                .setDuration(AppConstants.Timing.FADE_ANIMATION_DURATION)
                .withEndAction {
                    songTitleOverlay.visibility = View.GONE
                }
                .start()

            Log.d("MainActivity", "Song title overlay hidden")
        }
    }

    fun onSwapMedia(game: String, contentType: ContentType, system: String, originalSlot: MediaSlot, targetSlot: MediaSlot) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                mediaService.swapMedia(game, contentType, system, originalSlot, targetSlot)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Swapped successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //val prefs = this.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        //prefs.edit { putString("system_widgets_json", "") }
        //prefs.edit { putString("game_widgets_json", "") }

        Coil.setImageLoader(newImageLoader())
        setContentView(R.layout.activity_main)
        enableImmersiveMode()

        val database = AppDatabase.getDatabase(this)
        val loudnessDao = database.loudnessDao()
        val mediaOverrideDao = database.mediaOverrideDao()
        launchBoxDao = database.launchBoxDao()
        val apiKeyManager = ApiKeyManager.getInstance(this)
        mediaOverrideRepository = MediaOverrideRepository(mediaOverrideDao)

        lifecycleScope.launch(Dispatchers.IO) {
            mediaOverrideRepository.initialize()

            apiKeyManager.scraperCredentials.collect { creds ->
               updateScrapers(creds)
            }
        }

        val youtubeService = YoutubeMediaService(NetworkClientManager.baseClient)
        val khRepository = KhRepository()
        loudnessService = LoudnessService(loudnessDao)
        gameMusicRepository = GameMusicRepository(youtubeService,loudnessService, khRepository)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val musicExoPlayer = ExoPlayer.Builder(this).build()
        musicExoPlayer.setAudioAttributes(
            audioAttributes,
            true
        )
        val gameDao = database.gameDao()
        val gameRepository = GameRepository(gameDao)

        prefsManager = PreferencesManager(this)
        appLaunchPrefs = AppLaunchPreferences(this)
        mediaManager = MediaManager(prefsManager)
        mediaService = MediaService(mediaOverrideRepository, mediaManager)

        musicManager = MusicManager(this, gameMusicRepository, this, prefsManager)
        setupMusicCallbacks()

        // Check if we should show widget tutorial for updating users
        if (prefsManager.setupCompleted) {
            checkAndShowWidgetTutorialForUpdate()
        }

        rootLayout = findViewById(R.id.rootLayout)
        appDrawer = findViewById(R.id.appDrawer)
        appRecyclerView = findViewById(R.id.appRecyclerView)
        appSearchBar = findViewById(R.id.appSearchBar)
        searchClearButton = findViewById(R.id.searchClearButton)
        drawerBackButton = findViewById(R.id.drawerBackButton)
        settingsButton = findViewById(R.id.settingsButton)
        androidSettingsButton = findViewById(R.id.androidSettingsButton)
        blackOverlay = findViewById(R.id.blackOverlay)

        songTitleText = findViewById(R.id.songTitleText)
        songTitleOverlay = findViewById(R.id.songTitleOverlay)
        songTitleOverlay.translationZ = 25f
        musicPlayPauseButton = findViewById(R.id.musicPlayPauseButton)
        musicNextButton = findViewById(R.id.musicNextButton)
        songTitleHandler = Handler(Looper.getMainLooper())

        // Load snap to grid state
        snapToGrid = prefsManager.snapToGrid
        // Load show grid state
        showGrid = false

        // Initialize widget system
        widgetContainer = findViewById(R.id.widgetContainer)
        gameWidgetManager = WidgetManager(this, WidgetContext.GAME)
        systemWidgetManager = WidgetManager(this, WidgetContext.SYSTEM)

        animationSettings = AnimationSettings(prefsManager)
        imageManager = ImageManager(this, animationSettings, prefsManager)
        widgetPathResolver = WidgetPathResolver(mediaManager, prefsManager, mediaOverrideRepository, gameRepository, prefsManager, this)
        widgetViewBinder = WidgetViewBinder()

        lifecycleScope.launch {
            val esdeRoot = mediaManager.resolveGamelistFolder()
            if (esdeRoot != null && esdeRoot.exists()) {
                GameListSyncManager.syncAll(
                    this@MainActivity,
                    esdeRoot
                )
            }
        }

        // Set initial position off-screen (above the top)
        val displayHeight = resources.displayMetrics.heightPixels.toFloat()
        blackOverlay.translationY = -displayHeight

        // Log display information at startup
        logDisplayInfo()

        setupAppDrawer()
        setupSearchBar()
        setupGestureDetector()
        setupDrawerBackButton()
        setupSettingsButton()
        setupMusicControlButtons()
        setupAndroidSettingsButton()
        gameWidgetManager.load()
        systemWidgetManager.load()

        // Apply drawer transparency
        updateDrawerTransparency()

        menuComposeView = findViewById(R.id.menu_compose_view)
        menuComposeView.translationZ = 50f
        menuComposeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                     val pages = currentWidgetManager().pages
                     if(menuState.showMenu) {
                         MainContextMenu(
                             state = state,
                             uiState = MenuUiState(widgetsLocked, snapToGrid, showGrid),
                             onDismiss = {
                                 hideContextMenu()
                             },
                             widgetActions = WidgetActions(
                                 onToggleLock = { toggleWidgetLock() },
                                 onToggleSnap = { toggleSnapToGrid() },
                                 onToggleGrid = { toggleShowGrid() },
                                 onHelp = {
                                     hideContextMenu()
                                     showWidgetSystemTutorial(false)
                                 }, //this is likely to not work
                                 onAddPage = {
                                     onAddPageSelected()
                                     hideContextMenu()
                                 },
                                 onRemovePage = {
                                     onRemovePageSelected()
                                     hideContextMenu()
                                 },
                                 onAddWidget = { type, stringValue ->
                                     if (!widgetsLocked) {
                                         addNewWidget(type, stringValue)
                                         hideContextMenu()
                                     }
                                 },
                                 onSavePageSettings = { updated ->
                                     onPageUpdated(updated)
                                 }
                             ),
                             artRepository = artRepository,
                             musicResults = musicResults,
                             isSearchingMusic = isSearchingMusic,
                             onMusicSearch = { query -> performMusicSearch(query) },
                             onMusicSelect = { selected, onProgress ->
                                 onMusicResultSelected(
                                     selected,
                                     onProgress
                                 )
                             },
                             onSave = { url, contentType, slot ->
                                 onScraperContentSave(
                                     url,
                                     contentType,
                                     slot
                                 )
                             },
                             currentPageIndex = currentWidgetManager().currentPageIndex,
                             currentPage = currentWidgetManager().getCurrentPage(),
                             mediaService = youtubeService,
                             mediaManager = mediaManager,
                             mediaOverrideRepository = mediaOverrideRepository,
                             onSaveOverride = { mediaOverride -> onMediaOverride(mediaOverride) },
                             onRemoveOverride = { mediaOverride ->
                                 onRemoveMediaOverride(
                                     mediaOverride
                                 )
                             },
                             onCropSave = { file, bitmap -> handleCropSave(file, bitmap) },
                             removeCrop = { file -> removeCrop(file) },
                             pages = pages,
                             onSavePages = { pageItems -> onSaveNewPageOrder(pageItems) },
                             onRenamePage = { name -> onRenamePage(name) },
                             swapMedia = { game, type, system, originalSlot, targetSlot ->
                                 onSwapMedia(
                                     game,
                                     type,
                                     system,
                                     originalSlot,
                                     targetSlot
                                 )
                             },
                             deleteMedia = { game, type, system, originalSlot ->
                                 onDeleteMedia(
                                     game,
                                     type,
                                     system,
                                     originalSlot
                                 )
                             },
                             launchBoxDao = launchBoxDao,
                             animationSettings = animationSettings,
                             musicRepository = gameMusicRepository,
                             onKhMusicSelect = { song, url, onProgress ->
                                 onMusicResultSelectedKh(
                                     song,
                                     url,
                                     onProgress
                                 )
                             },
                             currentGameVolume = currentGameVolume,
                             onVolumeChanged = { volume -> onGameVolumeSaved(volume) },
                             setManualFileForSlot = { uri, type, game, system, slot, onComplete -> setManualFileForSlot(uri, type, game, system, slot, onComplete)}
                         )
                     }
                    if (menuState.widgetToEditState != null) {
                        WidgetSettingsOverlay(
                            widget = menuState.widgetToEditState!!,
                            currentPageIndex = currentWidgetManager().currentPageIndex,
                            onDismiss = {
                                menuState.widgetToEditState = null
                                widgetMenuShowing = false
                            },
                            onUpdate = { updated ->
                                onWidgetUpdated(updated)
                            },
                            onDelete = { deleted ->
                                onWidgetDeleted(deleted)
                                menuState.widgetToEditState = null
                                widgetMenuShowing = false
                            },
                            inSystemView = state is AppState.SystemBrowsing
                        )
                    }
                 }
            }
        }
        widgetToolbarMenuView = findViewById(R.id.compose_menu_host)
        widgetToolbarMenuView.translationZ = 50f
        widgetToolbarMenuView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    if(menuState.widgetSelected.isVisible) {
                        WidgetControlMenu(
                            uiState = menuState.widgetSelected,
                            onAction = { mode ->
                                activeWidget?.currentMode = mode
                                menuState.widgetSelected =
                                    menuState.widgetSelected.copy(mode = mode)
                            },
                            onEdit = { openWidgetSettings(activeWidget?.widget) },
                            onDone = { deselectMenuWidget() },
                            onInteractionChanged = { interacting ->
                                isTouchingWidgetToolbar = interacting
                            },
                            onReorder = {forward -> onWidgetReordered(forward)},
                            onClose = {
                                deselectMenuWidget()
                                toggleWidgetLock()
                            }
                        )
                    }
                }
            }
        }

        backgroundBinder = BackgroundBinder(
            context = this,
            lifecycleOwner = this,
            imageView = findViewById(R.id.gameImageView),
            videoView = findViewById(R.id.videoView),
            videoCover = findViewById(R.id.videoCover),
            dimmerView = findViewById(R.id.dimmingOverlay),
            musicStop = ::releaseMusicPlayer,
            pathResolver = widgetPathResolver,
            widgetContainer = widgetContainer,
            animationSettings = animationSettings,
            imageManager = imageManager,
            mediaManager = mediaManager
        )

        val logsDir = File(mediaManager.getLogsPath())
        val systemScrollFile = File(logsDir, "esde_system_name.txt")
        val gameScrollFile = File(logsDir, "esde_game_filename.txt")

        // Check which file was modified most recently to determine which mode to use
        val systemScrollExists = systemScrollFile.exists()
        val gameScrollExists = gameScrollFile.exists()

        if (systemScrollExists && gameScrollExists) {
            // Both exist, check which was modified last
            if (systemScrollFile.lastModified() > gameScrollFile.lastModified()) {
                loadSystemImage()
            } else {
                loadGameInfo()
            }
        } else if (systemScrollExists) {
            loadSystemImage()
        } else {
            loadGameInfo()
        }

        startFileMonitoring()
        setupBackHandling()

        // Register broadcast receiver for app changes
        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiver(appChangeReceiver, intentFilter)

        // Auto-launch setup wizard if needed
        checkAndLaunchSetupWizard()
    }

    private suspend fun updateScrapers(creds: ScraperCredentials) {
        var steamGridScraper: ArtScraper? = null
        var igdbScraper: ArtScraper? = null
        if(creds.sgdbKey != null && creds.sgdbKey.isNotEmpty()) {
            steamGridScraper = SGDBScraper(creds.sgdbKey)
        }
        val launchBoxScraper = LaunchBoxScraper(launchBoxDao)

        if(creds.igdbId != null && creds.igdbSecret != null && creds.igdbId.isNotEmpty() && creds.igdbSecret.isNotEmpty()) {
            val token = TwitchAuth.getTwitchAccessToken(creds.igdbId, creds.igdbSecret)
            if (token != null) {
                IGDBWrapper.setCredentials(creds.igdbId, token)
                igdbScraper = IgdbArtScraper()
            }
        }
        if(this::artRepository.isInitialized) {
            artRepository.setScraper(steamGridScraper, ScraperType.SGDB)
            artRepository.setScraper(igdbScraper, ScraperType.IGDB)
            artRepository.setScraper(launchBoxScraper, ScraperType.LaunchBox)
        } else {
            artRepository = ArtRepository(steamGridScraper, igdbScraper, launchBoxScraper)
        }

        if(steamGridScraper != null && igdbScraper != null) {
            Log.d("Scraper", "Scrapers re-initialized with latest credentials")
        } else {
            Log.d("Scraper", "Scrapers couldn't be initialized")
        }
    }

    private fun onDeleteMedia(
        game: String,
        type: ContentType,
        system: String,
        slot: MediaSlot
    ) {
        lifecycleScope.launch {
            try {
                mediaService.deleteMedia(game, type, system, slot)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Deleted media", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun onSaveNewPageOrder(pageItems: List<PageEditorItem>) {
        currentWidgetManager().reorderPagesByEditorList(pageItems)
        currentWidgetManager().currentPageIndex = 0
        hideContextMenu()
    }

    fun onRenamePage(name: String) {
        currentWidgetManager().renameCurrentPage(name)
    }

    fun onRemoveMediaOverride(mediaOverride: MediaOverride) {
        if(state as? AppState.GameBrowsing != null) {
            lifecycleScope.launch {
                try {
                    mediaOverrideRepository.removeOverride(mediaOverride)
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to delete override",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }


    fun onMediaOverride(mediaOverride: MediaOverride) {
        if(state as? AppState.GameBrowsing != null) {
            lifecycleScope.launch {
                try {
                    mediaOverrideRepository.updateOverride(mediaOverride)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Failed to save override", Toast.LENGTH_SHORT)
                }
            }
        }
    }

    fun removeCrop(croppedFile: File) {
        if (croppedFile.exists() && croppedFile.name.endsWith("_cropped.png")) {
            croppedFile.delete()
        }
    }

    fun handleCropSave(originalFile: File, bitmap: Bitmap) {
        val croppedFile = File(
            originalFile.parent,
            "${originalFile.nameWithoutExtension}_cropped.png"
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                FileOutputStream(croppedFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                croppedFile.setLastModified(System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e("Crop", "Failed to save: ${e.message}")
            }
        }
    }

    private fun onPageUpdated(updated: WidgetPage) {
        updated.resetValuesForType()
        currentWidgetManager().updatePage(updated)
        refreshWidgets(pageSwap = true)
    }

    @OptIn(ExperimentalCoilApi::class)
     fun onScraperContentSave(
        url: String,
        contentType: ContentType,
        slot: Int
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val s = state as AppState.GameBrowsing
                val fileName = extractGameFilenameWithoutExtension(s.gameFilename)
                val mediaSlot = MediaSlot.fromInt(slot)
                val dir = mediaManager.getDir(
                    s.systemName,
                    mediaManager.getFolderName(contentType),
                    mediaSlot
                )

                val extension = MimeTypeMap.getFileExtensionFromUrl(url).ifEmpty {
                    if (contentType == ContentType.VIDEO) "mp4" else "png"
                }
                val newFile = File(dir, "${fileName}${mediaSlot.suffix}.$extension")

                val existingFile = mediaManager.findMediaFile(contentType, s.systemName, s.gameFilename, mediaSlot)
                if (existingFile != null && existingFile.exists() && existingFile.absolutePath != newFile.absolutePath) {
                    existingFile.delete()
                }

                //download the new file
                if (contentType == ContentType.VIDEO) {
                    downloadFile(url, newFile)
                } else {
                    val snapshot = imageLoader.diskCache?.openSnapshot(url)
                    if (snapshot != null) {
                        snapshot.use {
                            it.data.toFile().copyTo(newFile, overwrite = true)
                        }
                    } else {
                        downloadFile(url, newFile)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Saved to Alt $slot", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("SaveError", "Failed to save content", e)
            }
        }
    }

    private fun downloadFile(url: String, targetFile: File) {
        val client = NetworkClientManager.baseClient
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to download: $response")

            response.body?.byteStream()?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    /**
     * Update the app state and keep legacy state variables in sync.
     *
     * During migration, this updates both the new state and old boolean variables.
     * Once migration is complete, we'll remove the legacy variable updates.
     */
    private fun updateState(newState: AppState) {
        state = newState
    }

    private fun checkAndShowWidgetTutorialForUpdate() {
        Log.d("MainActivity", "=== checkAndShowWidgetTutorialForUpdate CALLED ===")
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val currentVersion = packageInfo.versionName ?: "0.4.3"
            Log.d("MainActivity", "Current version from package: $currentVersion")

            val lastSeenVersion = prefsManager.tutorialVersionShown
            Log.d("MainActivity", "Last seen version from prefs: $lastSeenVersion")

            val hasSeenWidgetTutorial = prefsManager.widgetTutorialShown
            Log.d("MainActivity", "Has seen widget tutorial: $hasSeenWidgetTutorial")

            // Check if default widgets were created (indicates not a fresh install)
            val hasCreatedDefaultWidgets = prefsManager.defaultWidgetsCreated
            Log.d("MainActivity", "Has created default widgets: $hasCreatedDefaultWidgets")

            // NEW LOGIC:
            // Show tutorial if:
            // 1. User hasn't seen it yet AND
            // 2. EITHER they're updating from an older version OR they have default widgets (not fresh install)
            val isOlderVersion = lastSeenVersion != "0.0.0" && isVersionLessThan(lastSeenVersion, currentVersion)
            val shouldShowTutorial = !hasSeenWidgetTutorial && (isOlderVersion || hasCreatedDefaultWidgets)

            Log.d("MainActivity", "Should show tutorial: $shouldShowTutorial")
            Log.d("MainActivity", "  - hasSeenWidgetTutorial: $hasSeenWidgetTutorial")
            Log.d("MainActivity", "  - isOlderVersion: $isOlderVersion")
            Log.d("MainActivity", "  - hasCreatedDefaultWidgets: $hasCreatedDefaultWidgets")

            if (shouldShowTutorial) {
                Log.d("MainActivity", "✓ Showing widget tutorial")
                Handler(Looper.getMainLooper()).postDelayed({
                    showWidgetSystemTutorial(fromUpdate = true)
                }, AppConstants.Timing.TUTORIAL_DELAY)
            }

            // Always update the version tracking
            prefsManager.tutorialVersionShown = currentVersion
            Log.d("MainActivity", "Saved current version to prefs: $currentVersion")

        } catch (e: Exception) {
            Log.e("MainActivity", "Error in version check for widget tutorial", e)
        }
    }

    /**
     * Compare two version strings (e.g., "0.3.3" < "0.3.4")
     * Returns true if v1 < v2
     */
    private fun isVersionLessThan(v1: String, v2: String): Boolean {
        try {
            val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
            val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }

            // Compare each part
            for (i in 0 until maxOf(parts1.size, parts2.size)) {
                val p1 = parts1.getOrNull(i) ?: 0
                val p2 = parts2.getOrNull(i) ?: 0

                if (p1 < p2) return true
                if (p1 > p2) return false
            }

            // Versions are equal
            return false
        } catch (e: Exception) {
            Log.e("MainActivity", "Error comparing versions: $v1 vs $v2", e)
            return false
        }
    }

    private fun checkAndLaunchSetupWizard() {
        // Check if setup has been completed
        val hasCompletedSetup = prefsManager.setupCompleted

        // Check if permissions are granted (Android 13+ simplified)
        val hasPermission = Environment.isExternalStorageManager()

        // Launch setup wizard immediately if:
        // 1. Setup not completed, OR
        // 2. Missing permissions
        if (!hasCompletedSetup || !hasPermission) {
            Log.d("MainActivity", "Setup incomplete or missing permissions - launching wizard immediately")
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(this, SettingsActivity::class.java)
                intent.putExtra("AUTO_START_WIZARD", true)
                settingsLauncher.launch(intent)
            }, AppConstants.Timing.SETTINGS_DELAY)
            return
        }

        // For script check, wait for SD card if needed (setup is complete and has permissions)
        checkScriptsWithRetry()
    }

    /**
     * Show comprehensive widget system tutorial dialog
     * @param fromUpdate - True if showing because user updated the app
     */
    private fun showWidgetSystemTutorial(fromUpdate: Boolean = false) {
        // Get current version dynamically
        val currentVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "0.6.0"  // Fallback version
        }

        // Create custom title view with emoji
        val titleContainer = LinearLayout(this)
        titleContainer.orientation = LinearLayout.HORIZONTAL
        titleContainer.setPadding(60, 40, 60, 20)
        titleContainer.gravity = Gravity.CENTER

        val titleText = TextView(this)
        titleText.text = if (fromUpdate) "🆕 Widget Overlay System" else "📐 Widget Overlay System"
        titleText.textSize = 24f
        titleText.setTextColor(Color.parseColor("#FFFFFF"))
        titleText.gravity = Gravity.CENTER

        titleContainer.addView(titleText)

        // Create scrollable message view
        val scrollView = ScrollView(this)
        val messageText = TextView(this)

        val updatePrefix = if (fromUpdate) {
            "New in version 0.4.0+! The widget overlay system lets you create customizable displays on top of game artwork.\n\n"
        } else {
            ""
        }

        messageText.text = """
${updatePrefix}🎨 What Are Widgets?

Widgets are overlay elements that display game/system artwork like marquees, box art, screenshots, and more. You can position and size them however you want! You can also add more pages to create separate collections of widgets and backgrounds.

🔓 Widget Edit Mode

Widgets are LOCKED by default to prevent accidental changes. To edit widgets:

1. Long-press anywhere on screen → Widget menu appears
2. Toggle "Widget Edit Mode: OFF" to ON
3. Now you can create, move, resize, and delete widgets

➕ Creating Widgets

1. Unlock widgets (see above)
2. Open widget menu (long-press screen)
3. Tap "Add Widget"
4. Choose widget type (Marquee, Box Art, Screenshot, etc.)

✏️ Editing Widgets

Select: Tap a widget to select it (shows purple border)
Move: Drag selected widget to reposition
Resize: Drag the corner handles (⌙ shapes) on selected widgets
Delete: Tap the X button on selected widget
Settings: Tap the ⚙ button for layer ordering options

📐 Grid System

Snap to Grid: Makes positioning precise and aligned
Show Grid: Visual grid overlay to help with alignment

Both options in the widget menu!

🔒 Important: Lock Widgets When Done

After arranging your widgets, toggle Edit Mode back to OFF. This prevents accidental changes during normal use.

💡 Tips

• Pages/widgets are context-aware - create separate layouts for games vs systems
• Use "Bring to Front" / "Send to Back" to layer widgets
• Each widget updates automatically when you browse in ES-DE
• System logos work for both built-in and custom system logos

Access this help anytime from the widget menu!
"""

        messageText.textSize = 16f
        messageText.setTextColor(Color.parseColor("#FFFFFF"))
        messageText.setPadding(60, 20, 60, 20)

        scrollView.addView(messageText)

        // Create "don't show again" checkbox
        val checkboxContainer = LinearLayout(this)
        checkboxContainer.orientation = LinearLayout.HORIZONTAL
        checkboxContainer.setPadding(60, 10, 60, 20)
        checkboxContainer.gravity = Gravity.CENTER_VERTICAL

        val checkbox = CheckBox(this)
        checkbox.text = "Don't show this automatically again"
        checkbox.setTextColor(Color.parseColor("#999999"))
        checkbox.textSize = 14f

        checkboxContainer.addView(checkbox)

        // Create main container
        val mainContainer = LinearLayout(this)
        mainContainer.orientation = LinearLayout.VERTICAL
        mainContainer.addView(scrollView)
        mainContainer.addView(checkboxContainer)

        // Show dialog
        val dialog = AlertDialog.Builder(this)
            .setCustomTitle(titleContainer)
            .setView(mainContainer)
            .setPositiveButton("Got It!") { _, _ ->
                // Mark as shown
                prefsManager.widgetTutorialShown = true

                // If user checked "don't show again", mark preference
                if (checkbox.isChecked) {
                    prefsManager.widgetTutorialDontShowAuto = true
                }
            }
            .setCancelable(true)
            .create()

        // Dark theme for dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(
                ColorDrawable(Color.parseColor("#1A1A1A"))
            )
        }

        dialog.show()

        Log.d("MainActivity", "Widget tutorial dialog shown (fromUpdate: $fromUpdate)")
    }


    /**
     * Check for scripts with retry logic to handle SD card mounting delays
     */
    private fun checkScriptsWithRetry(attempt: Int = 0, maxAttempts: Int = 5) {
        val scriptsPath = prefsManager.scriptsPath.ifEmpty { null }

        // If no custom scripts path is set, scripts are likely on internal storage
        // Check immediately without retry
        if (scriptsPath == null || scriptsPath.startsWith("/storage/emulated/0")) {
            Log.d("MainActivity", "Scripts on internal storage - checking immediately")
            val hasCorrectScripts = checkForCorrectScripts()
            if (!hasCorrectScripts) {
                Log.d("MainActivity", "Scripts missing/outdated on internal storage - showing dialog")

                // Check if scripts exist at all (missing vs outdated)
                val scriptsDir = File(scriptsPath ?: AppConstants.Paths.DEFAULT_SCRIPTS_PATH)
                val gameSelectScript = File(scriptsDir, "game-select/esdecompanion-game-select.sh")

                if (gameSelectScript.exists()) {
                    // Scripts exist but are outdated - show update dialog
                    Handler(Looper.getMainLooper()).postDelayed({
                        showScriptsUpdateAvailableDialog()
                    }, AppConstants.Timing.SETTINGS_DELAY)
                } else {
                    // Scripts missing - launch full wizard
                    launchSetupWizardForScripts()
                }
            }
            return
        }

        // Custom path set - might be on SD card
        // Check if path is accessible (SD card mounted)
        val scriptsDir = File(scriptsPath)
        val isAccessible = scriptsDir.exists() && scriptsDir.canRead()

        if (!isAccessible && attempt < maxAttempts) {
            // SD card not mounted yet - wait and retry
            val delayMs = ((attempt + 1) * AppConstants.Timing.SD_MOUNT_RETRY_BASE_DELAY)
                .coerceAtMost(AppConstants.Timing.SD_MOUNT_RETRY_MAX_DELAY)

            Log.d("MainActivity", "Scripts path not accessible (attempt ${attempt + 1}/$maxAttempts) - waiting ${delayMs}ms for SD card mount: $scriptsPath")

            Handler(Looper.getMainLooper()).postDelayed({
                checkScriptsWithRetry(attempt + 1, maxAttempts)
            }, delayMs)
            return
        }

        // Either accessible now or max attempts reached - check scripts
        val hasCorrectScripts = checkForCorrectScripts()

        if (!hasCorrectScripts) {
            if (isAccessible) {
                // Path is accessible but scripts are missing/invalid
                Log.d("MainActivity", "Scripts missing/outdated on accessible path")

                // Check if scripts exist at all (missing vs outdated)
                val gameSelectScript = File(scriptsDir, "game-select/esdecompanion-game-select.sh")

                Handler(Looper.getMainLooper()).postDelayed({
                    if (gameSelectScript.exists()) {
                        // Scripts exist but are outdated - show update dialog
                        showScriptsUpdateAvailableDialog()
                    } else {
                        // Scripts missing - launch full wizard
                        launchSetupWizardForScripts()
                    }
                }, AppConstants.Timing.SETTINGS_DELAY)
            } else {
                // Max attempts reached and still not accessible
                // SD card might not be mounted - show a helpful message
                Log.w("MainActivity", "Scripts path not accessible after $maxAttempts attempts: $scriptsPath")
                Handler(Looper.getMainLooper()).postDelayed({
                    showSdCardNotMountedDialog(scriptsPath)
                }, AppConstants.Timing.SETTINGS_DELAY)
            }
        } else {
            Log.d("MainActivity", "Scripts found and valid - no wizard needed")
        }
    }


    /**
     * Launch setup wizard specifically for script issues
     */
    private fun launchSetupWizardForScripts() {
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, SettingsActivity::class.java)
            intent.putExtra("AUTO_START_WIZARD", true)
            settingsLauncher.launch(intent)
        }, 1000)
    }

    /**
     * Show dialog when SD card is not mounted
     */
    private fun showSdCardNotMountedDialog(scriptsPath: String) {
        AlertDialog.Builder(this)
            .setTitle("SD Card Not Detected")
            .setMessage("Your scripts folder appears to be on an SD card that is not currently accessible:\n\n$scriptsPath\n\nPlease ensure:\n• The SD card is properly inserted\n• The device has finished booting\n• The SD card is mounted\n\nThe app will work once the SD card becomes accessible.")
            .setPositiveButton("Open Settings") { _, _ ->
                settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
            }
            .setNegativeButton("Dismiss", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    private fun checkForCorrectScripts(): Boolean {
        val scriptsDir = File(prefsManager.scriptsPath)
        return ScriptManager.areScriptsValid(scriptsDir)
    }

    /**
     * Show dialog when old scripts are detected
     */
    private fun showScriptsUpdateAvailableDialog() {
        AlertDialog.Builder(this)
            .setTitle("Script Update Available")
            .setMessage(
                "Your ES-DE integration scripts need to be updated to support games in subfolders.\n\n" +
                        "Changes:\n" +
                        "• Scripts now pass full file paths\n" +
                        "• App handles subfolder detection\n" +
                        "• Improves compatibility with organized ROM collections\n\n" +
                        "Would you like to update the scripts now?"
            )
            .setPositiveButton("Update Scripts") { _, _ ->
                updateScriptsDirectly()
            }
            .setNegativeButton("Later") { _, _ ->
                // User declined - show a toast reminder
                Toast.makeText(
                    this,
                    "You can update scripts anytime from Settings",
                    Toast.LENGTH_LONG
                ).show()
            }
            .setIcon(android.R.drawable.ic_dialog_info)
            .show()
    }

    private fun updateScriptsDirectly() {
        val scriptsPath = prefsManager.scriptsPath

        val result = ScriptManager.createAllScripts(File(scriptsPath))

        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()

        if (result.success) {
            Log.d("MainActivity", "Scripts updated successfully")
        } else {
            Log.e("MainActivity", result.message)
        }
    }

    override fun onPause() {
        super.onPause()
        releaseMusicPlayer(false)
    }

    override fun onResume() {
        super.onResume()

        // Close drawer if it's open (user is returning from Settings or an app)
        // This happens after Settings/app is visible, so no animation is seen
        if (::bottomSheetBehavior.isInitialized &&
            bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED
        ) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        updateMusicControls()

        // Clear search bar
        if (::appSearchBar.isInitialized) {
            appSearchBar.text.clear()
        }

        // Reload grid layout in case column count changed
        val columnCount = prefsManager.columnCount
        appRecyclerView.layoutManager = GridLayoutManager(this, columnCount)

        // Reload images and videos based on current state (don't change modes)
        // Skip reload if returning from settings with no changes
        if (skipNextReload) {
            skipNextReload = false
            Log.d("MainActivity", "Skipping reload - no settings changed")
        } else {
            // Don't reload if game is playing or screensaver is active
            // This prevents unnecessary video loading during these states
            if (state is AppState.GamePlaying) {
                Log.d("MainActivity", "Skipping reload - game playing")
            } else if (state is AppState.Screensaver) {
                Log.d("MainActivity", "Skipping reload - screensaver active")
            } else {
                // Normal reload - this will reload both images and videos
                if (state is AppState.SystemBrowsing) {
                    loadSystemImage(ignoreDefault = true)
                } else {
                    loadGameInfo(ignoreDefault = true)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isActivityVisible = true
        isNavigatingInternally = false
        musicManager.onActivityVisible()
    }

    override fun onStop() {
        super.onStop()
        isActivityVisible = false

        if (!isNavigatingInternally) {
            Log.d("MainActivity", "Going to background - pausing music")
            musicManager.onActivityInvisible()
            hideSongTitle()
        } else {
            Log.d("MainActivity", "Internal navigation detected - keeping music playing")
        }
    }

    /**
     * Set up click listeners for music control buttons.
     */
    private fun setupMusicControlButtons() {
        musicPlayPauseButton.setOnClickListener {
            toggleMusicPlayback()
        }

        musicNextButton.setOnClickListener {
            playNextTrack()
        }
    }

    /**
     * Toggle music playback between play and pause.
     */
    private fun toggleMusicPlayback() {
        if (musicManager.isPlaying()) {
            musicManager.pauseMusic()
            Log.d("MainActivity", "Music paused via button")
        } else {
            musicManager.resumeMusic()
            Log.d("MainActivity", "Music resumed via button")
        }
        // UI update handled by MusicManager callback
    }

    /**
     * Skip to next track in playlist.
     */
    private fun playNextTrack() {
        musicManager.skipToNextTrack()
        Log.d("MainActivity", "Skipped to next track via button")
    }

    private fun updateDrawerTransparency() {
        val transparencyPercent = prefsManager.drawerTransparency
        // Convert percentage (0-100) to hex alpha (00-FF)
        val alpha = (transparencyPercent * 255 / 100).coerceIn(0, 255)
        val hexAlpha = String.format("%02x", alpha)
        val colorString = "#${hexAlpha}000000"

        val color = Color.parseColor(colorString)
        appDrawer.setBackgroundColor(color)
    }

    private fun handleVideoMuteToggle(): Boolean {
        return backgroundBinder.toggleMute()
    }

    private fun setupGestureDetector() {
        gestureDetector =
            GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean {
                    return true
                }

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (e1 == null || menuState.isActive()) return false

                    val diffY = e2.y - e1.y
                    val diffX = e2.x - e1.x

                    // Check if it's more vertical than horizontal
                    if (abs(diffY) > abs(diffX)) {
                        // Vertical fling
                        if (abs(diffY) > 100 && abs(velocityY) > 100) {
                            if (diffY < 0) {  // diffY < 0 means swipe UP
                                // Swipe up - open drawer
                                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                                return true
                            }
                        }
                    }
                    return false
                }
        })
    }

    private fun startLongPressTimer() {
        longPressRunnable?.let { longPressHandler?.removeCallbacks(it) }
        if (longPressHandler == null) longPressHandler = Handler(Looper.getMainLooper())

        longPressRunnable = Runnable {
            if (!longPressTriggered && !menuState.isActive()) {
                longPressTriggered = true
                showContextMenu()
            }
        }
        longPressHandler?.postDelayed(longPressRunnable!!, LONG_PRESS_TIMEOUT)
    }

    fun cancelLongPress() {
        longPressRunnable?.let {
            longPressHandler?.removeCallbacks(it)
        }
    }

    /**
     * Show the black overlay instantly (no animation)
     */
    private fun showBlackOverlay() {
        Log.d("MainActivity", "Showing black overlay")
        isBlackOverlayShown = true

        // Stop video immediately
        backgroundBinder.releasePlayer()
        musicManager.onBlackOverlayChanged(true)
        widgetViewBinder.onBlackscreen(widgetContainer)

        // Show overlay instantly without animation
        blackOverlay.visibility = View.VISIBLE
        blackOverlay.translationY = 0f
    }

    /**
     * Hide the black overlay instantly (no animation)
     */
    private fun hideBlackOverlay() {
        Log.d("MainActivity", "Hiding black overlay")
        isBlackOverlayShown = false

        musicManager.onBlackOverlayChanged(false)

        // Hide overlay instantly without animation
        blackOverlay.visibility = View.GONE

        val displayHeight = resources.displayMetrics.heightPixels.toFloat()
        blackOverlay.translationY = -displayHeight

        //onStateChangedMusicHandler(state)
        refreshWidgets(forcedRefresh = true)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val blackOverlayEnabled = prefsManager.blackOverlayEnabled
        val drawerState = bottomSheetBehavior.state
        val isDrawerOpen = drawerState == BottomSheetBehavior.STATE_EXPANDED ||
                drawerState == BottomSheetBehavior.STATE_SETTLING


        if (!isDrawerOpen) {
            val musicEnabled = prefsManager.musicEnabled

            Log.d("MainActivity", "Two-finger check: pointerCount=${ev.pointerCount}, action=${ev.action}, musicEnabled=$musicEnabled, hasActiveMusic=${musicManager.isPlaying() || musicManager.isPaused()}")

            // Only process two-finger gestures when music and song title are enabled
            if (!menuState.isActive() && musicEnabled && ev.pointerCount == 2 && ev.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                val currentTime = System.currentTimeMillis()
                val timeSinceLast = currentTime - lastTwoFingerTapTime

                Log.d("MainActivity", "Two-finger tap detected: timeSinceLast=${timeSinceLast}ms")

                if (currentTime - lastTwoFingerTapTime > 300) {
                    lastTwoFingerTapTime = currentTime

                    Log.d("MainActivity", "Two-finger tap confirmed - toggling song title")
                    twoFingerTapCount = 0

                    // Check if music is active (playing or paused, not completely stopped)
                    if (musicManager.isPlaying() || musicManager.isPaused()) {
                        // Toggle based on current visibility
                        val isVisible = songTitleOverlay.visibility == View.VISIBLE
                        Log.d("MainActivity", "Current overlay visibility: ${songTitleOverlay.visibility}, isVisible=$isVisible")

                        if (isVisible) {
                            // Currently visible - hide it
                            Log.d("MainActivity", "Hiding visible song title")
                            hideSongTitleOverlay()
                        } else {
                            // Currently hidden - show it with timeout
                            Log.d("MainActivity", "Showing hidden song title with timeout")
                            showSongTitleOverlay()
                        }
                    } else {
                        Log.d("MainActivity", "Music not active - ignoring two-finger tap")
                    }

                    return true
                }
            }
        }

        // Handle black overlay double-tap detection ONLY when drawer is closed and feature is enabled
        if (!isDrawerOpen && !menuState.isActive() && (blackOverlayEnabled || prefsManager.doubleTapVideoEnabled)) {
            if (handleDoubleTapTouchEvent(ev)) return true
        }

        // If overlay is shown, consume all touches
        if (isBlackOverlayShown) {
            return true
        }

        // Handle long press for widget menu (works anywhere, even on widgets)
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = ev.x
                touchDownY = ev.y
                isDrag = false
                if (!menuState.isActive() && drawerState != BottomSheetBehavior.STATE_EXPANDED) {
                    startLongPressTimer()
                    Log.d("MainActivity", "Longpress timer started")
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if(!menuState.isActive()) {
                    val deltaX = abs(ev.x - touchDownX)
                    val deltaY = abs(ev.y - touchDownY)
                    val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

                    if (deltaX > touchSlop || deltaY > touchSlop) {
                        cancelLongPress()
                        Log.d("MainActivity", "Longpress timer cancelled by move")
                        isDrag = true
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                if (!isDrag && !menuState.isActive() && !isDrawerOpen && !isBlackOverlayShown && !longPressTriggered) {
                    val screenWidth = resources.displayMetrics.widthPixels
                    //reduce page flip zone when widgets are editable
                    val flipPercentage = if(widgetsLocked) 0.10f else 0.06f
                    val edgeThreshold = screenWidth * flipPercentage

                    if (widgetsLocked || !isInteractingWithWidget) {
                        var switching = false
                        if (ev.x < edgeThreshold) {
                            switching = flipPage(false)
                        } else if (ev.x > (screenWidth - edgeThreshold)) {
                            switching = flipPage(true)
                        }
                        if(switching) {
                            cancelLongPress()
                            tapCount = 0
                            Log.d("MainActivity", "Longpress timer cancelled by page flip")
                        }
                    }
                }

                if (!widgetsLocked && !isDrag && !menuState.showMenu && menuState.widgetToEditState == null && !isTouchingWidgetToolbar && !isBlackOverlayShown && !longPressTriggered) {
                    val hitWidgets = widgetViewBinder.findWidgetAt(widgetContainer, ev.x, ev.y)

                    if (activeWidget == null || menuState.widgetSelected.mode == WidgetMode.SELECTED || menuState.widgetSelected.mode == WidgetMode.IDLE) {
                        if (hitWidgets.isEmpty() && menuState.widgetSelected.mode == WidgetMode.SELECTED) {
                            activeWidget?.currentMode = WidgetMode.IDLE
                            menuState.widgetSelected = menuState.widgetSelected.copy(
                                isVisible = false,
                                mode = WidgetMode.IDLE
                            )
                            activeWidget = null
                        } else if (hitWidgets.isNotEmpty()) {
                            val currentlySelected = hitWidgets.find { it == activeWidget }
                            if (currentlySelected != null && menuState.widgetSelected.mode == WidgetMode.SELECTED) {
                                val currentIndex = hitWidgets.indexOf(currentlySelected)
                                val nextIndex = (currentIndex + 1) % hitWidgets.size
                                val nextWidget = hitWidgets[nextIndex]

                                if (nextWidget != activeWidget) {
                                    activeWidget?.currentMode = WidgetMode.IDLE
                                    activeWidget = nextWidget
                                    activeWidget?.currentMode = WidgetMode.SELECTED

                                    menuState.widgetSelected = menuState.widgetSelected.copy(
                                        isVisible = true,
                                        mode = WidgetMode.SELECTED
                                    )
                                }
                            } else {
                                activeWidget?.currentMode = WidgetMode.IDLE
                                activeWidget = hitWidgets.first()
                                activeWidget?.currentMode = WidgetMode.SELECTED

                                menuState.widgetSelected = menuState.widgetSelected.copy(
                                    isVisible = true,
                                    mode = WidgetMode.SELECTED
                                )
                            }
                        }

                    }
                }
                isDrag = false
            }
        }

        if ((drawerState == BottomSheetBehavior.STATE_HIDDEN || drawerState == BottomSheetBehavior.STATE_COLLAPSED) && widgetsLocked || (!widgetsLocked && !isInteractingWithWidget)) {
            gestureDetector.onTouchEvent(ev)
        }

        if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) {

            Log.d("MainActivity", "Longpress timer cancelled by final if check")
            cancelLongPress()
            isInteractingWithWidget = false

            if (longPressTriggered) {
                longPressTriggered = false
                return true
            }
        }

        return super.dispatchTouchEvent(ev)
    }

    private fun handleDoubleTapTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val currentTime = System.currentTimeMillis()
            val timeSinceLastTap = currentTime - lastTapTime

            Log.d(
                "MainActivity",
                "Double-tap detection: timeSinceLastTap=${timeSinceLastTap}ms, tapCount=$tapCount"
            )

            // Reset tap count if too much time has passed
            if (timeSinceLastTap > DOUBLE_TAP_TIMEOUT) {
                Log.d(
                    "MainActivity",
                    "Tap timeout exceeded (${timeSinceLastTap}ms > ${DOUBLE_TAP_TIMEOUT}ms) - resetting tap count"
                )
                tapCount = 0
            }

            // Only count tap if enough time has passed since last tap OR it's the first tap
            // (prevents accidental fast touches like brushing the screen)
            if (lastTapTime == 0L || timeSinceLastTap >= MIN_TAP_INTERVAL) {
                tapCount++
                lastTapTime = currentTime

                Log.d("MainActivity", "Tap registered - new tapCount=$tapCount")

                // Check for double-tap
                if (tapCount >= 2) {
                    Log.d(
                        "MainActivity",
                        "Double-tap threshold reached! Toggling black overlay"
                    )
                    tapCount = 0 // Reset counter

                    //hiding black overlay is main priority
                    if (isBlackOverlayShown) {
                        hideBlackOverlay()
                    }
                    //only process the double tap if we're not in a menu
                    else if(!widgetMenuShowing && menuState.widgetToEditState == null && !menuState.showMenu) {
                        var toggledVideoVolume = false
                        if(prefsManager.doubleTapVideoEnabled) {
                            toggledVideoVolume = handleVideoMuteToggle()
                        }
                        //only show backscreenoverlay if we didn't toggle any video sound
                        if(!toggledVideoVolume) {
                            showBlackOverlay()
                        }
                    }
                    return true
                }
            } else {
                // Tap was too fast after previous tap - ignore it
                Log.d(
                    "MainActivity",
                    "Tap IGNORED - too fast (${timeSinceLastTap}ms < ${MIN_TAP_INTERVAL}ms)"
                )
            }
        }
        return false
    }

    private fun setupAppDrawer() {
        bottomSheetBehavior = BottomSheetBehavior.from(appDrawer)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior.peekHeight = 0
        bottomSheetBehavior.isHideable = true
        bottomSheetBehavior.skipCollapsed = true

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    showSettingsPulseHint()
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        appDrawer.post {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            Log.d("MainActivity", "AppDrawer state set to HIDDEN: ${bottomSheetBehavior.state}")
        }

        val columnCount = prefsManager.columnCount
        appRecyclerView.layoutManager = GridLayoutManager(this, columnCount)

        val mainIntent = Intent(Intent.ACTION_MAIN, null)
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)

        val hiddenApps = prefsManager.hiddenApps
        allApps = packageManager.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
            .filter { !hiddenApps.contains(it.activityInfo?.packageName ?: "") }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        // Pass hiddenApps to adapter
        appRecyclerView.adapter = AppAdapter(
            allApps,
            packageManager,
            onAppClick = { app -> launchApp(app) },
            onAppLongClick = { app, view -> showAppOptionsDialog(app) },
            appLaunchPrefs = appLaunchPrefs,
            hiddenApps = hiddenApps  // ADD THIS LINE
        )

        Log.d("MainActivity", "AppDrawer setup complete, initial state: ${bottomSheetBehavior.state}")
    }

    /**
     * Show pulsing animation on settings button when drawer opens
     * Only shows the first 3 times the drawer is opened (total, not per session)
     */
    private fun showSettingsPulseHint() {
        // Only show if user has completed setup
        if (!prefsManager.setupCompleted) return

        // Check how many times hint has been shown (max 3 times total)
        val hintCount = prefsManager.settingsHintCount
        if (hintCount >= 3) return

        // Increment the hint counter
        prefsManager.settingsHintCount = hintCount + 1

        // Delay slightly so drawer animation completes first
        Handler(Looper.getMainLooper()).postDelayed({
            // Create pulsing animation (3 pulses)
            val pulseCount = 3
            var currentPulse = 0

            fun doPulse() {
                if (currentPulse >= pulseCount) return

                settingsButton.animate()
                    .scaleX(1.3f)
                    .scaleY(1.3f)
                    .setDuration(400)
                    .withEndAction {
                        settingsButton.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(400)
                            .withEndAction {
                                currentPulse++
                                if (currentPulse < pulseCount) {
                                    // Small delay between pulses
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        doPulse()
                                    }, 200)
                                }
                            }
                            .start()
                    }
                    .start()
            }

            doPulse()

            // Show a subtle toast as well
            Toast.makeText(
                this,
                "Tip: Tap ☰ to open the app settings",
                Toast.LENGTH_LONG
            ).show()

        }, 800) // Wait for drawer to fully open
    }

    private fun setupSearchBar() {
        // Setup clear button click listener
        searchClearButton.setOnClickListener {
            appSearchBar.text.clear()
        }

        appSearchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.lowercase() ?: ""

                // Show/hide clear button based on whether there's text
                searchClearButton.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE

                val hiddenApps = prefsManager.hiddenApps

                val filteredApps = if (query.isEmpty()) {
                    // No search query - show only visible apps (current behavior)
                    allApps
                } else {
                    // Has search query - search ALL apps including hidden ones
                    val mainIntent = Intent(Intent.ACTION_MAIN, null)
                    mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)

                    packageManager.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
                        .filter { app ->
                            app.loadLabel(packageManager).toString().lowercase().contains(query)
                        }
                        .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
                }

                // Update adapter with filtered results - pass hiddenApps
                appRecyclerView.adapter = AppAdapter(
                    filteredApps,
                    packageManager,
                    onAppClick = { app ->
                        launchApp(app)
                    },
                    onAppLongClick = { app, view ->
                        showAppOptionsDialog(app)
                    },
                    appLaunchPrefs = appLaunchPrefs,
                    hiddenApps = hiddenApps  // ADD THIS LINE
                )
            }
        })
    }

    private fun setupSettingsButton() {
        settingsButton.setOnClickListener {
            // Log current display when settings is opened
            val currentDisplay = getCurrentDisplayId()
            Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d("MainActivity", "SETTINGS BUTTON CLICKED")
            Log.d("MainActivity", "Companion currently on display: $currentDisplay")

            // Also log all available displays
            if (SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                try {
                    val displayManager =
                        getSystemService(DISPLAY_SERVICE) as DisplayManager
                    val displays = displayManager.displays
                    Log.d("MainActivity", "All available displays:")
                    displays.forEachIndexed { index, display ->
                        Log.d(
                            "MainActivity",
                            "  Display $index: ID=${display.displayId}, Name='${display.name}'"
                        )
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error listing displays", e)
                }
            }

            Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            // Don't close the drawer - just launch Settings over it
            // The drawer will still be there when returning, but that's okay
            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupDrawerBackButton() {
        drawerBackButton.setOnClickListener {
            // Clear search and close the app drawer
            appSearchBar.text.clear()
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun setupAndroidSettingsButton() {
        androidSettingsButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_SETTINGS)
            isNavigatingInternally = false
            startActivity(intent)
        }
    }

    private fun startFileMonitoring() {
        val watchDir = File(mediaManager.getLogsPath())
        Log.d("MainActivity", "Starting file monitoring on: ${watchDir.absolutePath}")
        Log.d("MainActivity", "Watch directory exists: ${watchDir.exists()}")

        // Create logs directory if it doesn't exist
        if (!watchDir.exists()) {
            watchDir.mkdirs()
            Log.d("MainActivity", "Created logs directory")
        }

        fileObserver = object : FileObserver(watchDir, MODIFY or CLOSE_WRITE) {
            private var lastEventTime = 0L

            override fun onEvent(event: Int, path: String?) {
                if (path != null && (path == AppConstants.Paths.GAME_FILENAME_LOG || path == AppConstants.Paths.SYSTEM_NAME_LOG ||
                            path == AppConstants.Paths.GAME_START_FILENAME_LOG || path == AppConstants.Paths.GAME_END_FILENAME_LOG ||
                            path == AppConstants.Paths.SCREENSAVER_START_LOG || path == AppConstants.Paths.SCREENSAVER_END_LOG ||
                            path == AppConstants.Paths.SCREENSAVER_GAME_FILENAME_LOG)) {
                    // Debounce: ignore events that happen too quickly
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastEventTime < 50) {
                        return
                    }
                    lastEventTime = currentTime

                    runOnUiThread {
                        // Small delay to ensure file is fully written
                        Handler(Looper.getMainLooper()).postDelayed({
                            // Check if we're waiting for script verification OR error dialog is showing
                            if (isWaitingForScriptVerification) {
                                stopScriptVerification(true)  // Success!
                            } else if (currentErrorDialog != null) {
                                // User was looking at error dialog when they browsed in ES-DE
                                // Dismiss error dialog and show success
                                currentErrorDialog?.dismiss()
                                currentErrorDialog = null
                                onScriptVerificationSuccess()
                            }

                            if (!menuState.showMenu && menuState.widgetToEditState == null) {
                                when (path) {
                                    AppConstants.Paths.SYSTEM_NAME_LOG -> {
                                        // Ignore if launching from screensaver (game-select event between screensaver-end and game-start)
                                        if (isLaunchingFromScreensaver) {
                                            Log.d(
                                                "MainActivity",
                                                "Game scroll ignored - launching from screensaver"
                                            )
                                            return@postDelayed
                                        }

                                        // Ignore if screensaver is active
                                        if (state is AppState.Screensaver) {
                                            Log.d(
                                                "MainActivity",
                                                "System scroll ignored - screensaver active"
                                            )
                                            return@postDelayed
                                        }
                                        Log.d("MainActivity", "System scroll detected")
                                        loadSystemImageDebounced()
                                    }

                                    AppConstants.Paths.GAME_FILENAME_LOG -> {
                                        // Ignore if launching from screensaver (game-select event between screensaver-end and game-start)
                                        if (isLaunchingFromScreensaver) {
                                            Log.d(
                                                "MainActivity",
                                                "Game scroll ignored - launching from screensaver"
                                            )
                                            return@postDelayed
                                        }

                                        // Ignore if screensaver is active
                                        if (state is AppState.Screensaver) {
                                            Log.d(
                                                "MainActivity",
                                                "Game scroll ignored - screensaver active"
                                            )
                                            return@postDelayed
                                        }

                                        // ADDED: Ignore game-select events that happen shortly after game-start or game-end
                                        val currentTime = System.currentTimeMillis()
                                        if (currentTime - lastGameStartTime < GAME_EVENT_DEBOUNCE) {
                                            Log.d(
                                                "MainActivity",
                                                "Game scroll ignored - too soon after game start"
                                            )
                                            return@postDelayed
                                        }
                                        if (currentTime - lastGameEndTime < GAME_EVENT_DEBOUNCE) {
                                            Log.d(
                                                "MainActivity",
                                                "Game scroll ignored - too soon after game end"
                                            )
                                            return@postDelayed
                                        }

                                        // Read the game filename
                                        val gameFile = File(watchDir, "esde_game_filename.txt")
                                        if (gameFile.exists()) {
                                            val gameFilename = gameFile.readText().trim()

                                            // Ignore if this is the same game that's currently playing
                                            if (state is AppState.GamePlaying && gameFilename == (state as AppState.GamePlaying).gameFilename) {
                                                Log.d(
                                                    "MainActivity",
                                                    "Game scroll ignored - same as playing game: $gameFilename"
                                                )
                                                return@postDelayed
                                            }
                                        }

                                        Log.d("MainActivity", "Game scroll detected")
                                        loadGameInfoDebounced()
                                    }

                                    AppConstants.Paths.GAME_START_FILENAME_LOG -> {
                                        Log.d("MainActivity", "Game start detected")
                                        handleGameStart()
                                    }

                                    AppConstants.Paths.GAME_END_FILENAME_LOG -> {
                                        Log.d("MainActivity", "Game end detected")
                                        handleGameEnd()
                                    }

                                    AppConstants.Paths.SCREENSAVER_START_LOG -> {
                                        Log.d("MainActivity", "Screensaver start detected")
                                        handleScreensaverStart()
                                    }

                                    AppConstants.Paths.SCREENSAVER_END_LOG -> {
                                        // Read the screensaver end reason
                                        val screensaverEndFile =
                                            File(watchDir, AppConstants.Paths.SCREENSAVER_END_LOG)
                                        val endReason = if (screensaverEndFile.exists()) {
                                            screensaverEndFile.readText().trim()
                                        } else {
                                            "cancel"
                                        }

                                        Log.d(
                                            "MainActivity",
                                            "Screensaver end detected: $endReason"
                                        )
                                        handleScreensaverEnd(endReason)
                                    }

                                    AppConstants.Paths.SCREENSAVER_GAME_FILENAME_LOG -> {
                                        // DEFENSIVE FIX: Auto-initialize screensaver state if screensaver-start event was missed
                                        if (state !is AppState.Screensaver) {
                                            Log.w(
                                                "MainActivity",
                                                "⚠️ FALLBACK: Screensaver game-select fired without screensaver-start event!"
                                            )
                                            Log.w(
                                                "MainActivity",
                                                "Auto-initializing screensaver state as defensive fallback"
                                            )
                                            Log.d(
                                                "MainActivity",
                                                "Current state before fallback: $state"
                                            )

                                            // Create saved state for screensaver from current state
                                            val savedState = when (val s = state) {
                                                is AppState.SystemBrowsing -> {
                                                    SavedBrowsingState.InSystemView(s.systemName)
                                                }

                                                is AppState.GameBrowsing -> {
                                                    SavedBrowsingState.InGameView(
                                                        systemName = s.systemName,
                                                        gameFilename = s.gameFilename,
                                                        gameName = s.gameName
                                                    )
                                                }

                                                else -> {
                                                    // Fallback for unexpected states
                                                    Log.w(
                                                        "MainActivity",
                                                        "Unexpected state when screensaver fallback: $state"
                                                    )
                                                    SavedBrowsingState.InSystemView(
                                                        state.getCurrentSystemName() ?: ""
                                                    )
                                                }
                                            }

                                            // Apply screensaver behavior preferences
                                            val screensaverBehavior = prefsManager.screensaverBehavior
                                            Log.d(
                                                "MainActivity",
                                                "Applying screensaver behavior: $screensaverBehavior"
                                            )

                                            // Handle black screen preference
                                            if (screensaverBehavior == "black_screen") {
                                                Log.d(
                                                    "MainActivity",
                                                    "Black screen behavior - clearing display"
                                                )
                                                backgroundBinder.onBlackscreen()
                                                gridOverlayView?.visibility = View.GONE
                                            }

                                            // Clear widgets (will be loaded by handleScreensaverGameSelect)
                                            //widgetContainer.removeAllViews()
                                            //activeWidgets.clear()
                                            Log.d(
                                                "MainActivity",
                                                "Fallback initialization complete - widgets cleared"
                                            )

                                            // Update state to Screensaver
                                            updateState(
                                                AppState.Screensaver(
                                                    currentGame = null,
                                                    previousState = savedState
                                                )
                                            )

                                            Log.d(
                                                "MainActivity",
                                                "Saved state for screensaver: $savedState"
                                            )
                                        }

                                        // Read screensaver game info and update state
                                        val filenameFile = File(watchDir, AppConstants.Paths.SCREENSAVER_GAME_FILENAME_LOG)
                                        val nameFile = File(watchDir, AppConstants.Paths.SCREENSAVER_GAME_NAME_LOG)
                                        val systemFile = File(watchDir, AppConstants.Paths.SCREENSAVER_GAME_SYSTEM_LOG)

                                        var gameFilename: String? = null
                                        var gameName: String? = null
                                        var systemName: String? = null

                                        if (filenameFile.exists()) {
                                            gameFilename = filenameFile.readText().trim()
                                        }
                                        if (nameFile.exists()) {
                                            gameName = nameFile.readText().trim()
                                        }
                                        if (systemFile.exists()) {
                                            systemName = systemFile.readText().trim()
                                        }

                                        if (gameFilename.isNullOrBlank() || systemName.isNullOrBlank()) {
                                            Log.w("MainActivity", "⚠️ Incomplete screensaver game data - skipping update")
                                            return@postDelayed
                                        }

                                        if (state is AppState.Screensaver) {
                                            val screensaverState = state as AppState.Screensaver
                                            updateState(
                                                screensaverState.copy(
                                                    currentGame = ScreensaverGame(
                                                        gameFilename = gameFilename,
                                                        gameName = gameName,
                                                        systemName = systemName
                                                    )
                                                )
                                            )
                                        }
                                        handleScreensaverGameSelect()
                                    }
                                }
                            }
                        }, 50) //delay to ensure file is written
                    }
                }
            }
        }
        fileObserver?.startWatching()
        Log.d("MainActivity", "FileObserver started")
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                    // Close the app drawer if it's open
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                } else {
                    // Do nothing - stay on home screen
                    // This prevents cycling through recent apps when pressing back
                }
            }
        })
    }

    /**
     * Start waiting for script activity after configuration
     * Shows a "waiting" dialog and watches for first log update
     */
    fun startScriptVerification() {
        isWaitingForScriptVerification = true

        // Show "waiting" dialog
        showScriptVerificationDialog()

        // Set timeout
        if (scriptVerificationHandler == null) {
            scriptVerificationHandler = Handler(Looper.getMainLooper())
        }

        scriptVerificationRunnable = Runnable {
            if (isWaitingForScriptVerification) {
                // Timeout - scripts not working
                onScriptVerificationFailed()
            }
        }

        scriptVerificationHandler?.postDelayed(
            scriptVerificationRunnable!!,
            SCRIPT_VERIFICATION_TIMEOUT
        )
        Log.d("MainActivity", "Started script verification (15s timeout)")
    }

    /**
     * Stop verification (call when first log update detected)
     */
    private fun stopScriptVerification(success: Boolean) {
        scriptVerificationRunnable?.let {
            scriptVerificationHandler?.removeCallbacks(it)
        }
        isWaitingForScriptVerification = false

        // Dismiss waiting dialog if showing
        currentVerificationDialog?.dismiss()
        currentVerificationDialog = null

        // Dismiss error dialog if showing (user browsed while error was visible)
        currentErrorDialog?.dismiss()
        currentErrorDialog = null

        if (success) {
            onScriptVerificationSuccess()
        }
    }

    /**
     * Show dialog while waiting for script activity
     */
    private fun showScriptVerificationDialog() {
        currentVerificationDialog = AlertDialog.Builder(this)
            .setTitle("🔍 Checking Connection...")
            .setMessage(
                "Waiting for ES-DE to send data...\n\n" +
                        "Please browse to a game or system in ES-DE now.\n\n" +
                        "This verifies that ES-DE scripts are working correctly."
            )
            .setCancelable(false)
            .setNegativeButton("Skip Check") { dialog, _ ->
                stopScriptVerification(false)
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Called when first log update is detected during verification
     */
    private fun onScriptVerificationSuccess() {
        runOnUiThread {
            Toast.makeText(
                this,
                "✓ Connection successful! ES-DE is communicating properly.",
                Toast.LENGTH_LONG
            ).show()

            // Check if this is first time seeing widget tutorial after setup
            val hasSeenWidgetTutorial = prefsManager.widgetTutorialShown
            val hasCompletedSetup = prefsManager.setupCompleted

            if (!hasSeenWidgetTutorial && hasCompletedSetup) {
                // Show widget tutorial after successful verification following setup
                Log.d("MainActivity", "Showing widget tutorial after setup verification")
                Handler(Looper.getMainLooper()).postDelayed({
                    showWidgetSystemTutorial(fromUpdate = false)
                }, AppConstants.Timing.SETTINGS_DELAY)  // 1 second after verification success
            }
        }
    }

    /**
     * Called when verification times out (no log updates)
     */
    private fun onScriptVerificationFailed() {
        runOnUiThread {
            currentVerificationDialog?.dismiss()

            // Create custom title view with X button
            val titleContainer = LinearLayout(this)
            titleContainer.orientation = LinearLayout.HORIZONTAL
            titleContainer.setPadding(60, 40, 20, 20)
            titleContainer.gravity = Gravity.CENTER_VERTICAL

            val titleText = TextView(this)
            titleText.text = "⚠️ No Data Received"
            titleText.textSize = 20f
            titleText.setTextColor(Color.parseColor("#FFFFFF"))
            titleText.layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )

            val closeButton = ImageButton(this)
            closeButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            closeButton.background = null
            closeButton.setPadding(20, 20, 20, 20)

            titleContainer.addView(titleText)
            titleContainer.addView(closeButton)

            val dialog = AlertDialog.Builder(this)
                .setCustomTitle(titleContainer)
                .setMessage(
                    "ES-DE Companion hasn't received any data from ES-DE.\n\n" +
                            "Common issues:\n\n" +
                            "1. Scripts folder path is incorrect\n" +
                            "   → Scripts must be in ES-DE's scripts folder\n\n" +
                            "2. Custom Event Scripts not enabled in ES-DE\n" +
                            "   → Main Menu > Other Settings > Toggle both:\n" +
                            "     • Custom Event Scripts: ON\n" +
                            "     • Browsing Custom Events: ON\n\n" +
                            "3. ES-DE not running or not browsing games\n" +
                            "   → Make sure you're scrolling through games\n\n" +
                            "What would you like to do?"
                )
                .setNegativeButton("Restart Setup") { _, _ ->
                    currentErrorDialog = null  // Clear reference
                    // Launch settings with auto-start wizard flag
                    val intent = Intent(this, SettingsActivity::class.java)
                    intent.putExtra("AUTO_START_WIZARD", true)
                    settingsLauncher.launch(intent)
                }
                .setPositiveButton("Try Again") { _, _ ->
                    currentErrorDialog = null  // Clear reference
                    startScriptVerification()
                }
                .setCancelable(true)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .create()

            closeButton.setOnClickListener {
                dialog.dismiss()
                currentErrorDialog = null  // Clear reference when manually closed
            }

            currentErrorDialog = dialog  // Store reference to error dialog
            dialog.show()
        }
    }

    override fun onDestroy() {
        musicManager.cleanup()

        super.onDestroy()
        // Stop script verification
        scriptVerificationRunnable?.let { scriptVerificationHandler?.removeCallbacks(it) }
        currentVerificationDialog?.dismiss()
        currentErrorDialog?.dismiss()
        fileObserver?.stopWatching()
        unregisterReceiver(appChangeReceiver)
        // Cancel any pending image loads
        imageLoadRunnable?.let { imageLoadHandler.removeCallbacks(it) }
        // Release video player
        backgroundBinder.releasePlayer()
        releaseMusicPlayer(false)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        hasWindowFocus = hasFocus

        if (hasFocus) {
            Log.d("MainActivity", "Window focus gained")
            backgroundBinder.onWindowFocusChanged(hasFocus)
            listeningToAudioRef = true
            refreshWidgets(forcedRefresh = true)
            enableImmersiveMode()
        } else {
            Log.d("MainActivity", "Window focus lost (ignoring for video blocking)")
            // Stop videos when we lose focus (game launched on same screen)
            backgroundBinder.onPause(this)
            listeningToAudioRef = false
        }
    }

    @Suppress("DEPRECATION")
    private fun enableImmersiveMode() {
        if (SDK_INT >= Build.VERSION_CODES.R) {
            // Modern approach for Android 11+ (API 30+)
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                // Hide both status bar and navigation bar
                controller.hide(
                    WindowInsets.Type.statusBars() or
                            WindowInsets.Type.navigationBars()
                )
                // Set behavior to show bars temporarily on swipe, then auto-hide
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // Fallback for Android 10 (API 29)
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    private fun hideSongTitle() {
        hideSongTitleOverlay()
    }

    /**
     * Debounced wrapper for loadSystemImage - delays loading based on scroll speed
     * Systems use debouncing to reduce rapid updates when scrolling quickly
     */
    private fun loadSystemImageDebounced() {
        // Calculate time since last system scroll event
        val currentTime = System.currentTimeMillis()
        val timeSinceLastScroll = currentTime - lastSystemScrollTime
        lastSystemScrollTime = currentTime

        // Determine if user is fast scrolling systems
        val isFastScrolling = timeSinceLastScroll < SYSTEM_FAST_SCROLL_THRESHOLD
        val delay = if (isFastScrolling) SYSTEM_FAST_SCROLL_DELAY else SYSTEM_SLOW_SCROLL_DELAY

        // Cancel any pending image load
        imageLoadRunnable?.let { imageLoadHandler.removeCallbacks(it) }

        // Schedule new image load with appropriate delay
        imageLoadRunnable = Runnable {
            loadSystemImage()
        }

        if (delay > 0) {
            imageLoadHandler.postDelayed(imageLoadRunnable!!, delay)
        } else {
            // Load immediately if no delay configured
            imageLoadRunnable!!.run()
        }
    }

    /**
     * Debounced wrapper for loadGameInfo - loads immediately with no delay
     * Games use instant loading for responsive browsing experience
     */
    private fun loadGameInfoDebounced() {
        // Calculate time since last game scroll event
        val currentTime = System.currentTimeMillis()
        val timeSinceLastScroll = currentTime - lastGameScrollTime
        lastGameScrollTime = currentTime

        // Determine if user is fast scrolling games
        val isFastScrolling = timeSinceLastScroll < GAME_FAST_SCROLL_THRESHOLD
        val delay = if (isFastScrolling) GAME_FAST_SCROLL_DELAY else GAME_SLOW_SCROLL_DELAY

        // Cancel any pending image load
        imageLoadRunnable?.let { imageLoadHandler.removeCallbacks(it) }

        // Schedule new image load with appropriate delay (0 for games = instant)
        imageLoadRunnable = Runnable {
            loadGameInfo()
        }

        if (delay > 0) {
            imageLoadHandler.postDelayed(imageLoadRunnable!!, delay)
        } else {
            // Load immediately for games (default behavior)
            imageLoadRunnable!!.run()
        }
    }

    private fun refreshWidgets(pagePreValidated: Boolean = false, pageSwap: Boolean = false, forcedRefresh: Boolean = false, pendingWidgetId: String? = null, transitionInvalid: Boolean = false) {
        cancelAutoTransition()
        Log.d("TEMP_DEBUG", "Widget Refreshing for: ${state.getCurrentGameFilename()}")
        val currentWidgetContext = state.toWidgetContext()
        if(previousWidgetContext != null && previousWidgetContext != currentWidgetContext) {
            AudioReferee.updateBackgroundState(false)
            AudioReferee.resetWidgetState()
        } else if(pageSwap) {
            AudioReferee.resetWidgetState()
        }
        previousWidgetContext = currentWidgetContext

        var processPage: WidgetPage?
        var wrapperPage: WidgetPage? = null
        val gameLaunchBehaviour = prefsManager.gameLaunchBehavior
        val screensaverBehavior = prefsManager.screensaverBehavior
        if(state is AppState.GamePlaying) { wrapperPage = createWidgetPageForBehaviour(gameLaunchBehaviour)}
        if(state is AppState.Screensaver) { wrapperPage = createWidgetPageForBehaviour(screensaverBehavior)}
        var currentPage: WidgetPage = currentWidgetManager().getCurrentPage()
        //setSolidColorForPage(currentPage)

        lifecycleScope.launch {
            if(!pagePreValidated && !isPageValid(currentPage)) {
                currentWidgetManager().currentPageIndex = 0
                currentPage = currentWidgetManager().getCurrentPage()
            }

            if(wrapperPage != null) {
                wrapperPage.widgets = currentPage.widgets
                processPage = wrapperPage
            } else {
                processPage = currentPage
            }

            val pageMediaFile = widgetPathResolver.resolvePage(processPage, state)
            val transition = if(processPage.transitionToPage) findFirstValidTransition(processPage.transitions) else null
            backgroundBinder.apply(processPage, state, pageMediaFile.content, widgetsLocked,transition = transition,
                { startPageTransition(transition?.targetPageId) }, forcedRefresh)

            if (processPage.displayWidgets) {
                val resolved = widgetPathResolver.resolve(
                    processPage.widgets,
                    state.getCurrentSystemName(),
                    state.getCurrentGameFilename(),
                    resources.displayMetrics
                )

                var gameName = ""
                val system = state.getCurrentSystemName() ?: ""
                if(state is AppState.GameBrowsing) {
                    gameName = (state as AppState.GameBrowsing).gameName ?: ""
                }

                widgetViewBinder.sync(
                    container = widgetContainer,
                    lifecycleOwner = this@MainActivity,
                    dataList = resolved,
                    page = processPage,
                    locked = widgetsLocked,
                    snapToGrid = snapToGrid,
                    gridSize = gridSize,
                    onUpdate = ::onWidgetUpdated,
                    animationSettings = animationSettings,
                    imageManager = imageManager,
                    game = gameName,
                    system = system,
                    onSelect = {widget -> showMenuForWidget(widget)},
                    forcedRefresh = forcedRefresh,
                    pendingWidgetId = pendingWidgetId,
                    onTouch = {widget, drag -> onStartWidgetTouch(widget, drag)},
                    mediaManager = mediaManager
                )
            } else {
                hideWidgets()
            }
        }
    }

    private fun setSolidColorForPage(page: WidgetPage)
    {
        if(page.backgroundType == PageContentType.SOLID_COLOR && state.toWidgetContext() == WidgetContext.SYSTEM) {
            page.solidColor = SystemColorMapper.getColorForSystem(state.getCurrentSystemName())
        }
    }

    private fun createWidgetPageForBehaviour(behaviour: String): WidgetPage {
        val wrapperPage = WidgetPage()
        when (behaviour) {
            "black_screen" -> {
                wrapperPage.solidColor = Color.BLACK
                wrapperPage.backgroundType = PageContentType.SOLID_COLOR
                wrapperPage.displayWidgets = false
            }
            "default_image" -> {
                wrapperPage.backgroundType = PageContentType.CUSTOM_IMAGE
                wrapperPage.customPath = ""
                wrapperPage.displayWidgets = true
            }
            "game_image" -> {
                wrapperPage.backgroundType = PageContentType.FANART
                wrapperPage.displayWidgets = true
            }
        }
        return wrapperPage
    }

    private fun onWidgetUpdated(widget: Widget) {
        currentWidgetManager().updateWidget(widget, resources.displayMetrics)
        val page = currentWidgetManager().getCurrentPage()
        lifecycleScope.launch {
            val resolved = widgetPathResolver.resolveSingle(
                widget,
                state.getCurrentSystemName(),
                state.getCurrentGameFilename(),
                resources.displayMetrics
            )

            var gameName = ""
            val system = state.getCurrentSystemName() ?: ""
            if(state is AppState.GameBrowsing) {
                gameName = (state as AppState.GameBrowsing).gameName ?: ""
            }

            widgetViewBinder.syncSingleWidget(resolved.widget, widgetContainer, page, gameName, system)
        }
    }

    private fun currentWidgetManager(): WidgetManager {
        if(state.toWidgetContext() == WidgetContext.GAME) {
            return gameWidgetManager
        }
        return systemWidgetManager
    }

    private fun onWidgetDeleted(widget: Widget) {
        currentWidgetManager().deleteWidget(widget.id)
        Toast.makeText(this, "Widget has been deleted", Toast.LENGTH_SHORT).show()
        refreshWidgets()
    }

    private fun onWidgetReordered(forward: Boolean) {
        if(activeWidget != null) {
            currentWidgetManager().moveWidgetZOrder(activeWidget?.widget?.id!!, forward)
            refreshWidgets()
        }
    }

    fun addNewWidget(type: ContentType, configData: String? = null) {
        val newWidget = currentWidgetManager().addNewWidgetToCurrentPage(
            type,
            resources.displayMetrics
        )

        if (configData != null) {
            when (type) {
                ContentType.CUSTOM_IMAGE -> {
                    newWidget.contentPath = configData
                }
                ContentType.CUSTOM_FOLDER -> {
                    newWidget.customPath = configData
                }
                ContentType.COLOR_BACKGROUND -> {
                    newWidget.solidColor = Color.parseColor(configData)
                }
                else -> {}
            }

            currentWidgetManager().updateWidget(newWidget, resources.displayMetrics)
        }

        refreshWidgets(pendingWidgetId = newWidget.id)
    }

    fun deselectMenuWidget() {
        activeWidget?.currentMode = WidgetMode.IDLE
        menuState.widgetSelected =
            menuState.widgetSelected.copy(
                isVisible = false,
                mode = WidgetMode.IDLE
            )
    }

    fun onStartWidgetTouch(widget: WidgetView, drag: Boolean) {
        if (activeWidget == widget && (activeWidget?.currentMode == WidgetMode.MOVING || activeWidget?.currentMode == WidgetMode.RESIZING) && menuState.widgetSelected.isDragging != drag) {
            menuState.widgetSelected = WidgetUiState(isVisible = true, mode = widget.currentMode, isDragging = drag)
        }
    }

    fun showMenuForWidget(widget: WidgetView) {
        activeWidget?.currentMode == WidgetMode.IDLE
        activeWidget = widget
        menuState.widgetSelected = WidgetUiState(isVisible = true, mode = widget.currentMode)
        cancelAutoTransition()
    }

    fun setManualFileForSlot(sourceUri: Uri, type: ContentType, game: String, system: String, slot: MediaSlot, onComplete: () -> Unit) {
        lifecycleScope.launch {
            mediaService.deleteMedia(game, type, system, slot)
            val imported = mediaManager.importFileToAltSlot(
                context = this@MainActivity,
                sourceUri = sourceUri,
                contentType = type,
                systemName = system,
                gameFilename = game,
                slot = slot
            )
            if (imported != null) {
                Toast.makeText(this@MainActivity, "Saved file!", Toast.LENGTH_SHORT).show()
                onComplete()
            }
        }
    }

    //used for flipping pages forwards or backwards
    private fun flipPage(next: Boolean): Boolean {
        if (activeWidget != null && activeWidget?.currentMode != WidgetMode.IDLE) {
            return false
        }

        val currentManager = currentWidgetManager()
        var nextIndex = currentManager.currentPageIndex
        val totalPages = currentManager.getPageCount()
        var foundValidPage = false
        val allPages = currentManager.getAllPages()
        val direction = if(next) 1 else -1

        lifecycleScope.launch {
            for (i in 0 until totalPages) {
                nextIndex = (nextIndex + direction + totalPages) % totalPages
                val candidatePage = allPages[nextIndex]

                if ((!candidatePage.transitionOnly || !widgetsLocked) && isPageValid(candidatePage)) {
                    foundValidPage = true
                    break
                }
            }

            if (foundValidPage && currentManager.currentPageIndex != nextIndex) {
                cancelAutoTransition()
                currentManager.currentPageIndex = nextIndex
                refreshWidgets(pagePreValidated = true, pageSwap = true)
            }
        }
        return foundValidPage
    }

    private suspend fun flipToPage(page: WidgetPage?, forcedRefresh: Boolean = false, fromTransition: Boolean = false): Boolean {
        if (page != null) {
            if (menuState.isActive() || (activeWidget != null && activeWidget?.currentMode != WidgetMode.IDLE)) {
                return false
            }
            if (isPageValid(page) && ((!page.transitionOnly || fromTransition) || !widgetsLocked)) {
                val currentManager = currentWidgetManager()
                currentManager.currentPageIndex = currentManager.pages.indexOf(page)
                refreshWidgets(
                    pagePreValidated = true,
                    pageSwap = true,
                    forcedRefresh = forcedRefresh
                )
                return true
            }
        }
        if(!fromTransition) {
            refreshWidgets(pagePreValidated = false, pageSwap = true)
        }
        return false
    }

    fun startPageTransition(targetPageId: String?) {
        if(widgetsLocked && targetPageId != null && targetPageId.isNotEmpty()) {
            lifecycleScope.launch {
                flipToPage(currentWidgetManager().getPageById(targetPageId), fromTransition = true)
            }
        }
    }

    suspend fun findFirstValidTransition(transitions: List<PageTransition>): PageTransition? {
        transitions.forEach { transition ->
            val targetPage = currentWidgetManager().getPageById(transition.targetPageId)
            if(targetPage != null) {
                if(isPageValid(targetPage)) {
                    return transition
                }
            }
        }
        return null
    }

    fun cancelAutoTransition() {
        backgroundBinder.cancelTransition()
    }

    suspend fun isPageValid(page: WidgetPage): Boolean {
        if(!widgetsLocked) return true
        val result = widgetPathResolver.resolvePage(page, state)
        if (page.isRequired && result.missingRequired && page.backgroundType != PageContentType.SOLID_COLOR && page.backgroundType != PageContentType.CUSTOM_FOLDER && page.backgroundType != PageContentType.CUSTOM_IMAGE ) {
            return false
        }

        val system = state.getCurrentSystemName()
        val game = state.getCurrentGameFilename()
        page.widgets.forEach { widget ->
            if(widget.isRequired) {
                val result =
                    widgetPathResolver.resolveSingle(widget, system, game, resources.displayMetrics)
                if (result.missingRequired) return false
            }
        }
        return true
    }


    private fun loadSystemImage(ignoreDefault: Boolean = false) {
        // Don't reload images if game is currently playing - respect game launch behavior
        if (state is AppState.GamePlaying) {
            Log.d(
                "MainActivity",
                "loadSystemImage blocked - game is playing, maintaining game launch display"
            )
            return
        }

        try {
            releaseMusicPlayer(true)

            val logsDir = File(mediaManager.getLogsPath())
            val systemFile = File(logsDir, "esde_system_name.txt")
            if (!systemFile.exists()) return

            val systemName = systemFile.readText().trim()

            // Update state tracking
            updateState(AppState.SystemBrowsing(systemName))
            val defPage = currentWidgetManager().getDefaultPage()
            if(!ignoreDefault && defPage != null) {
                lifecycleScope.launch {
                    flipToPage(defPage)
                }
            } else {
                refreshWidgets()
            }
        } catch (e: Exception) {

        }

    }

    private fun loadGameInfo(switchingFromPlaying: Boolean = false, ignoreDefault: Boolean = false) {
        // Don't reload images if game is currently playing - respect game launch behavior
        if (state is AppState.GamePlaying && !switchingFromPlaying) {
            Log.d(
                "MainActivity",
                "loadGameInfo blocked - game is playing, maintaining game launch display"
            )
            return
        }

        if(switchingFromPlaying && isBlackOverlayShown){
            hideBlackOverlay()
        }

        gameInfoJob?.cancel()
        gameInfoJob = lifecycleScope.launch {
            Log.d("MainActivity", "Releasing music player ")
            releaseMusicPlayer(true)

            val logsDir = File(mediaManager.getLogsPath())
            val gameFile = File(logsDir, "esde_game_filename.txt")
            if (!gameFile.exists()) return@launch

            val gameNameRaw = gameFile.readText().trim()  // Full path from script
            val gameName = extractGameFilenameWithoutExtension(sanitizeGameFilename(gameNameRaw))

            val gameDisplayNameFile = File(logsDir, "esde_game_name.txt")
            val gameDisplayName = readNonBlankTextAsync(gameDisplayNameFile) ?: gameName

            val systemFile = File(logsDir, "esde_game_system.txt")
            val systemName = readNonBlankTextAsync(systemFile)

            if (systemName == null) {
                Log.w("MainActivity", "System name not available after retries - skipping game load")
                return@launch
            }

            if(systemName != state.getCurrentSystemName() || gameNameRaw != state.getCurrentGameFilename() || switchingFromPlaying) {
                withContext(Dispatchers.Main) {
                    updateState(
                        AppState.GameBrowsing(
                            systemName = systemName,
                            gameFilename = gameNameRaw,
                            gameName = gameDisplayName
                        )
                    )

                    if(prefsManager.musicSongTitleSystemOnlyEnabled) {
                        hideSongTitleOverlay()
                    }

                    updateCurrentGameVolume()
                    val defPage = currentWidgetManager().getDefaultPage()
                    if(!ignoreDefault && defPage != null) {
                        flipToPage(defPage, forcedRefresh = switchingFromPlaying)
                    } else {
                        refreshWidgets(forcedRefresh = switchingFromPlaying)
                    }
                }
            }
        }
    }

    private suspend fun readNonBlankTextAsync(file: File, retries: Int = 5, delayMs: Long = 50): String? {
        repeat(retries) {
            if (file.exists()) {
                val text = file.readText().trim()
                if (text.isNotBlank()) return text
            }
            delay(delayMs)
        }
        return null
    }

    /**
     * Get the display ID that this activity is currently running on
     */
    /**
     * Log display information for debugging
     */
    private fun logDisplayInfo() {
        if (SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            try {
                val displayManager =
                    getSystemService(DISPLAY_SERVICE) as DisplayManager
                val displays = displayManager.displays

                Log.d("MainActivity", "═══════════════════════════════════")
                Log.d("MainActivity", "DISPLAY INFORMATION AT STARTUP")
                Log.d("MainActivity", "═══════════════════════════════════")
                Log.d("MainActivity", "Total displays: ${displays.size}")
                displays.forEachIndexed { index, display ->
                    Log.d("MainActivity", "Display $index:")
                    Log.d("MainActivity", "  - ID: ${display.displayId}")
                    Log.d("MainActivity", "  - Name: ${display.name}")
                    if (SDK_INT >= Build.VERSION_CODES.M) {
                        Log.d("MainActivity", "  - State: ${display.state}")
                    }
                }

                val currentDisplayId = getCurrentDisplayId()
                Log.d("MainActivity", "Companion app is on display: $currentDisplayId")
                Log.d("MainActivity", "═══════════════════════════════════")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error logging display info", e)
            }
        }
    }

    private fun getCurrentDisplayId(): Int {
        val displayId = try {
            if (SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ - use display property
                val id1 = display?.displayId ?: -1
                Log.d("MainActivity", "  Method 1 (display): $id1")

                // Also try getting from window
                val id2 = window?.decorView?.display?.displayId ?: -1
                Log.d("MainActivity", "  Method 2 (window.decorView.display): $id2")

                // Use the non-negative one, prefer window method
                if (id2 >= 0) id2 else id1
            } else if (SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                // Android 4.2+ - use windowManager
                @Suppress("DEPRECATION")
                val id = windowManager.defaultDisplay.displayId
                Log.d("MainActivity", "  Method 3 (windowManager.defaultDisplay): $id")
                id
            } else {
                Log.d("MainActivity", "  Method 4 (fallback to 0)")
                0
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error getting display ID", e)
            0
        }

        Log.d(
            "MainActivity",
            "getCurrentDisplayId() FINAL returning: $displayId (SDK: ${SDK_INT})"
        )
        return if (displayId < 0) 0 else displayId
    }

    /**
     * Launch an app on the appropriate display based on user preferences
     */
    private fun launchApp(app: ResolveInfo) {
        val packageName = app.activityInfo?.packageName ?: return

        // Don't close drawer - just launch the app
        // Drawer will remain in its current state

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)

        if (launchIntent != null) {
            val currentDisplayId = getCurrentDisplayId()
            val shouldLaunchOnTop = appLaunchPrefs.shouldLaunchOnTop(packageName)

            Log.d("MainActivity", "═══ LAUNCH REQUEST ═══")
            Log.d("MainActivity", "Companion detected on display: $currentDisplayId")
            Log.d(
                "MainActivity",
                "User preference: ${if (shouldLaunchOnTop) "THIS screen" else "OTHER screen"}"
            )

            // Get all available displays
            val targetDisplayId =
                if (SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    try {
                        val displayManager =
                            getSystemService(DISPLAY_SERVICE) as DisplayManager
                        val displays = displayManager.displays

                        if (shouldLaunchOnTop) {
                            // Launch on THIS screen (same as companion)
                            Log.d(
                                "MainActivity",
                                "Targeting THIS screen (display $currentDisplayId)"
                            )
                            currentDisplayId
                        } else {
                            // Launch on OTHER screen (find the display that's NOT current)
                            val otherDisplay =
                                displays.firstOrNull { it.displayId != currentDisplayId }
                            if (otherDisplay != null) {
                                Log.d(
                                    "MainActivity",
                                    "Targeting OTHER screen (display ${otherDisplay.displayId})"
                                )
                                otherDisplay.displayId
                            } else {
                                Log.w(
                                    "MainActivity",
                                    "No other display found! Using current display"
                                )
                                currentDisplayId
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error finding target display", e)
                        currentDisplayId
                    }
                } else {
                    currentDisplayId
                }

            Log.d("MainActivity", "FINAL target: Display $targetDisplayId")
            Log.d("MainActivity", "═════════════════════")

            launchOnDisplay(launchIntent, targetDisplayId)

            // Close the app drawer after launching
            if (::bottomSheetBehavior.isInitialized) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
        }
    }

    /**
     * Launch app on a specific display ID
     */
    private fun launchOnDisplay(intent: Intent, displayId: Int) {
        if (SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val displayManager =
                    getSystemService(DISPLAY_SERVICE) as DisplayManager
                val displays = displayManager.displays

                Log.d("MainActivity", "launchOnDisplay: Requesting display $displayId")
                Log.d(
                    "MainActivity",
                    "launchOnDisplay: Available displays: ${displays.size}"
                )
                displays.forEachIndexed { index, display ->
                    Log.d(
                        "MainActivity",
                        "  Display $index: ID=${display.displayId}, Name=${display.name}"
                    )
                }

                val targetDisplay = displays.firstOrNull { it.displayId == displayId }

                if (targetDisplay != null) {
                    Log.d(
                        "MainActivity",
                        "✓ Found target display $displayId - Launching now"
                    )
                    val options = ActivityOptions.makeBasic()
                    options.launchDisplayId = displayId
                    startActivity(intent, options.toBundle())
                } else {
                    Log.w(
                        "MainActivity",
                        "✗ Display $displayId not found! Launching on default"
                    )
                    startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(
                    "MainActivity",
                    "Error launching on display $displayId, using default",
                    e
                )
                startActivity(intent)
            }
        } else {
            Log.d("MainActivity", "SDK < O, launching on default display")
            startActivity(intent)
        }
    }

    /**
     * Show app options dialog with launch position toggles
     * Note: "Top" now means "This screen" (same as companion)
     *       "Bottom" now means "Other screen" (opposite of companion)
     */
    private fun showAppOptionsDialog(app: ResolveInfo) {
        val packageName = app.activityInfo?.packageName ?: return
        val appName = app.loadLabel(packageManager).toString()

        val dialogView = layoutInflater.inflate(R.layout.dialog_app_options, null)
        val dialogAppName = dialogView.findViewById<TextView>(R.id.dialogAppName)
        val btnAppInfo = dialogView.findViewById<MaterialButton>(R.id.btnAppInfo)
        val btnHideApp = dialogView.findViewById<MaterialButton>(R.id.btnHideApp)
        val chipGroup = dialogView.findViewById<ChipGroup>(R.id.launchPositionChipGroup)
        val chipLaunchTop = dialogView.findViewById<Chip>(R.id.chipLaunchTop)
        val chipLaunchBottom = dialogView.findViewById<Chip>(R.id.chipLaunchBottom)

        dialogAppName.text = appName

        // Check if app is currently hidden and update button
        val hiddenApps = prefsManager.hiddenApps.toMutableSet()
        val isHidden = hiddenApps.contains(packageName)

        if (isHidden) {
            btnHideApp.text = "Unhide App"
            btnHideApp.backgroundTintList = ColorStateList.valueOf(
                Color.parseColor("#4CAF50")
            )
        } else {
            btnHideApp.text = "Hide App"
            btnHideApp.backgroundTintList = ColorStateList.valueOf(
                Color.parseColor("#CF6679")
            )
        }

        // Set initial chip state
        val currentPosition = appLaunchPrefs.getLaunchPosition(packageName)
        if (currentPosition == AppLaunchManager.POSITION_TOP) {
            chipLaunchTop.isChecked = true
        } else {
            chipLaunchBottom.isChecked = true
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // App Info button
        btnAppInfo.setOnClickListener {
            openAppInfo(packageName)
            dialog.dismiss()
        }

        // Hide/Unhide App button
        btnHideApp.setOnClickListener {
            val currentHiddenApps = prefsManager.hiddenApps.toMutableSet()
            val currentlyHidden = currentHiddenApps.contains(packageName)

            if (currentlyHidden) {
                // Unhide - no confirmation
                currentHiddenApps.remove(packageName)
                prefsManager.hiddenApps = currentHiddenApps
                dialog.dismiss()

                val mainIntent = Intent(Intent.ACTION_MAIN, null)
                mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
                val updatedHiddenApps = prefsManager.hiddenApps
                allApps = packageManager.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
                    .filter { !updatedHiddenApps.contains(it.activityInfo?.packageName ?: "") }
                    .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

                (appRecyclerView.adapter as? AppAdapter)?.updateApps(allApps)
                Toast.makeText(this, "\"$appName\" shown in app drawer", Toast.LENGTH_SHORT).show()
            } else {
                // Hide - show confirmation
                AlertDialog.Builder(this)
                    .setTitle("Hide App")
                    .setMessage("Hide \"$appName\" from the app drawer?\n\nYou can unhide it later from Settings → App Drawer → Manage Apps, or by searching for it.")
                    .setPositiveButton("Hide") { _, _ ->
                        currentHiddenApps.add(packageName)
                        prefsManager.hiddenApps = currentHiddenApps
                        dialog.dismiss()

                        val mainIntent = Intent(Intent.ACTION_MAIN, null)
                        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
                        val updatedHiddenApps = prefsManager.hiddenApps
                        allApps = packageManager.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
                            .filter { !updatedHiddenApps.contains(it.activityInfo?.packageName ?: "") }
                            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

                        (appRecyclerView.adapter as? AppAdapter)?.updateApps(allApps)
                        Toast.makeText(this, "\"$appName\" hidden from app drawer", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .setIcon(android.R.drawable.ic_menu_delete)
                    .show()
            }
        }

        // Chip selection listener - save preference AND launch app
        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            when {
                checkedIds.contains(R.id.chipLaunchTop) -> {
                    // Save preference
                    appLaunchPrefs.setLaunchPosition(packageName, AppLaunchManager.POSITION_TOP)
                    Log.d("MainActivity", "Set $appName to launch on THIS screen")

                    // Launch the app
                    launchApp(app)

                    // Close dialog and drawer
                    dialog.dismiss()
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

                    // Refresh indicators
                    (appRecyclerView.adapter as? AppAdapter)?.refreshIndicators()
                }
                checkedIds.contains(R.id.chipLaunchBottom) -> {
                    // Save preference
                    appLaunchPrefs.setLaunchPosition(packageName, AppLaunchManager.POSITION_BOTTOM)
                    Log.d("MainActivity", "Set $appName to launch on OTHER screen")

                    // Launch the app
                    launchApp(app)

                    // Close dialog and drawer
                    dialog.dismiss()
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

                    // Refresh indicators
                    (appRecyclerView.adapter as? AppAdapter)?.refreshIndicators()
                }
            }
        }

        dialog.show()
    }


    /**
     * Open system app info screen
     */
    private fun openAppInfo(packageName: String) {
        try {
            isNavigatingInternally = false
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to open app info", e)
        }
    }

    // ========== GAME STATE FUNCTIONS ==========

    private fun handleGameStart() {
        lastGameStartTime = System.currentTimeMillis()

        Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d("MainActivity", "GAME START HANDLER")
        Log.d("MainActivity", "Current state: $state")

        // Get the game launch behavior
        val gameLaunchBehavior = prefsManager.gameLaunchBehavior
        Log.d("MainActivity", "Game launch behavior: $gameLaunchBehavior")

        // Extract game info from current state
        val gameInfo = extractGameInfoFromState()

        // Update state to GamePlaying
        if (gameInfo != null) {
            val (systemName, gameFilename) = gameInfo
            updateState(
                AppState.GamePlaying(
                    systemName = systemName,
                    gameFilename = gameFilename
                )
            )
        }

        // Stop any videos
        backgroundBinder.releasePlayer()
        releaseMusicPlayer(true)
        refreshWidgets()
    }

    /**
     * Extract game info from current state for game launch
     * @return Pair of (systemName, gameFilename) or null if unavailable
     */
    private fun extractGameInfoFromState(): Pair<String, String>? {
        return when (val s = state) {
            is AppState.GameBrowsing -> {
                // Normal game launch from browsing
                Pair(s.systemName, s.gameFilename)
            }

            is AppState.Screensaver -> {
                // Game launch from screensaver
                s.currentGame?.let { game ->
                    Pair(game.systemName, game.gameFilename)
                }
            }

            else -> {
                // Shouldn't happen, but try to read from log files as fallback
                Log.w("MainActivity", "Game start from unexpected state: $state")
                tryReadGameInfoFromLogs()
            }
        }
    }

    /**
     * Fallback: Try to read game info from log files
     * @return Pair of (systemName, gameFilename) or null if unavailable
     */
    private fun tryReadGameInfoFromLogs(): Pair<String, String>? {
        val logsDir = File(mediaManager.getLogsPath())
        val gameFile = File(logsDir, "esde_game_filename.txt")
        val systemFile = File(logsDir, "esde_game_system.txt")

        return if (gameFile.exists() && systemFile.exists()) {
            Pair(systemFile.readText().trim(), gameFile.readText().trim())
        } else {
            null
        }
    }

    /**
     * Apply game launch behavior based on settings
     */


// ========== END: Game Start Handler Extraction ==========

    /**
     * Handle game end event - return to normal browsing display
     */
    private fun handleGameEnd() {
        lastGameEndTime = System.currentTimeMillis()

        Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d("MainActivity", "GAME END EVENT")
        Log.d("MainActivity", "Current state: $state")

        // Update state - transition from GamePlaying to GameBrowsing
        // Return to browsing the game that was just playing
        if (state is AppState.GamePlaying) {
            loadGameInfo(true)
        } else {
            Log.w("MainActivity", "Game end but not in GamePlaying state: $state")
        }
    }

    // ========== SCREENSAVER FUNCTIONS ==========

    /**
     * Handle screensaver start event
     */
    private fun handleScreensaverStart() {
        Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d("MainActivity", "SCREENSAVER START")
        Log.d("MainActivity", "Current state before: $state")

        // Create saved state from current state
        val previousState = when (val s = state) {
            is AppState.SystemBrowsing -> {
                SavedBrowsingState.InSystemView(
                    systemName = s.systemName
                )
            }

            is AppState.GameBrowsing -> {
                SavedBrowsingState.InGameView(
                    systemName = s.systemName,
                    gameFilename = s.gameFilename,
                    gameName = s.gameName
                )
            }

            else -> {
                // Fallback for unexpected states
                Log.w(
                    "MainActivity",
                    "Unexpected state when screensaver starts: $state"
                )
                SavedBrowsingState.InSystemView(state.getCurrentSystemName() ?: "")
            }
        }

        // Update state to Screensaver
        updateState(
            AppState.Screensaver(
                currentGame = null,  // No game selected yet
                previousState = previousState
            )
        )
        Log.d("MainActivity", "Saved previous state: $previousState")
    }

    private fun updateGridOverlay() {
        if (gridOverlayView == null) {
            gridOverlayView = GridOverlayView(this, gridSize).apply {
                isClickable = false
                isFocusable = false
            }
            rootLayout.addView(gridOverlayView, rootLayout.indexOfChild(widgetContainer))
        }

        // 2. Simply toggle visibility
        gridOverlayView?.visibility = if (showGrid && widgetContainer.isVisible) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    /**
     * Apply screensaver behavior change while screensaver is already active.
     * Unlike handleScreensaverStart(), this preserves the current screensaver game.
     */


    /**
     * Handle screensaver end event - return to normal browsing display
     * @param reason The reason for screensaver ending: "cancel", "game-jump", or "game-start"
     */
    private fun handleScreensaverEnd(reason: String?) {
        Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d("MainActivity", "SCREENSAVER END: reason=$reason")
        Log.d("MainActivity", "Current state: $state")

        // Get previous state from screensaver
        val previousState = if (state is AppState.Screensaver) {
            (state as AppState.Screensaver).previousState
        } else {
            // Fallback if state tracking wasn't initialized
            Log.w("MainActivity", "Not in Screensaver state, using fallback")
            SavedBrowsingState.InSystemView(state.getCurrentSystemName() ?: "")
        }

        // Get current screensaver game info (if any)
        val screensaverGame = if (state is AppState.Screensaver) {
            (state as AppState.Screensaver).currentGame
        } else {
            null
        }

        Log.d("MainActivity", "Previous state before screensaver: $previousState")
        Log.d("MainActivity", "Current screensaver game: $screensaverGame")
        Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // Reset screensaver initialization flag
        screensaverInitialized = false

        if (reason != null) {
            when (reason) {
                "game-start" -> {
                    // CRITICAL: Set flag IMMEDIATELY to block FileObserver reloads during transition
                    isLaunchingFromScreensaver = true

                    // User is launching a game from screensaver
                    Log.d(
                        "MainActivity",
                        "Screensaver end - game starting, waiting for game-start event"
                    )
                    Log.d(
                        "MainActivity",
                        "isLaunchingFromScreensaver flag set - blocking intermediate reloads"
                    )

                    // Update state - transition to GameBrowsing (waiting for GamePlaying)
                    if (screensaverGame != null) {
                        updateState(
                            AppState.GameBrowsing(
                                systemName = screensaverGame.systemName,
                                gameFilename = screensaverGame.gameFilename,
                                gameName = screensaverGame.gameName
                            )
                        )
                        Log.d(
                            "MainActivity",
                            "Transitioned to GameBrowsing: ${screensaverGame.gameFilename}"
                        )
                    } else {
                        Log.w("MainActivity", "No screensaver game info available")
                    }

                    // The game-start event will handle the display
                    // Flag will be cleared in handleGameStart()
                }

                "game-jump" -> {
                    // User jumped to a different game while in screensaver
                    // The game is now the selected game, so image can be retained
                    Log.d(
                        "MainActivity",
                        "Screensaver end - game-jump, retaining current image"
                    )

                    // Update state - transition to GameBrowsing
                    if (screensaverGame != null) {
                        updateState(
                            AppState.GameBrowsing(
                                systemName = screensaverGame.systemName,
                                gameFilename = screensaverGame.gameFilename,
                                gameName = screensaverGame.gameName
                            )
                        )
                        Log.d(
                            "MainActivity",
                            "Transitioned to GameBrowsing: ${screensaverGame.gameFilename}"
                        )
                    } else {
                        Log.w("MainActivity", "No screensaver game info available")
                    }

                    // The current screensaver game image is already showing, so don't reload
                }

                "cancel" -> {
                    // User cancelled screensaver (pressed back or timeout)
                    // Return to the browsing state from before screensaver started
                    Log.d(
                        "MainActivity",
                        "Screensaver end - cancel, returning to previous state"
                    )

                    // Return to previous state
                    when (previousState) {
                        is SavedBrowsingState.InSystemView -> {
                            Log.d(
                                "MainActivity",
                                "Returning to system view: ${previousState.systemName}"
                            )

                            // Update state first
                            updateState(AppState.SystemBrowsing(previousState.systemName))

                            // Then reload display
                            loadSystemImage()
                        }

                        is SavedBrowsingState.InGameView -> {
                            Log.d(
                                "MainActivity",
                                "Returning to game view: ${previousState.gameFilename}"
                            )

                            // Update state first
                            updateState(
                                AppState.GameBrowsing(
                                    systemName = previousState.systemName,
                                    gameFilename = previousState.gameFilename,
                                    gameName = previousState.gameName
                                )
                            )

                            // Then reload display
                            loadGameInfo()
                        }
                    }
                }

                else -> {
                    // Unknown reason - default to cancel behavior
                    Log.w(
                        "MainActivity",
                        "Screensaver end - unknown reason: $reason, defaulting to cancel behavior"
                    )

                    // Return to previous state (same as cancel)
                    when (previousState) {
                        is SavedBrowsingState.InSystemView -> {
                            Log.d(
                                "MainActivity",
                                "Returning to system view: ${previousState.systemName}"
                            )

                            updateState(AppState.SystemBrowsing(previousState.systemName))
                            loadSystemImage()
                        }

                        is SavedBrowsingState.InGameView -> {
                            Log.d(
                                "MainActivity",
                                "Returning to game view: ${previousState.gameFilename}"
                            )

                            updateState(
                                AppState.GameBrowsing(
                                    systemName = previousState.systemName,
                                    gameFilename = previousState.gameFilename,
                                    gameName = previousState.gameName
                                )
                            )
                            loadGameInfo()
                        }
                    }
                }
            }
        }

        // Don't show/update widgets here - let loadSystemImage() or loadGameInfo() handle it
    }

    /**
     * Handle screensaver game select event (for slideshow/video screensavers)
     */
    private fun handleScreensaverGameSelect() {
        Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d("MainActivity", "SCREENSAVER GAME SELECT EVENT")
        Log.d("MainActivity", "Current state: $state")

        val screensaverBehavior = prefsManager.screensaverBehavior
        Log.d("MainActivity", "Screensaver behavior: $screensaverBehavior")

        // Get current screensaver game from state
        val screensaverGame = if (state is AppState.Screensaver) {
            (state as AppState.Screensaver).currentGame
        } else {
            Log.w("MainActivity", "Not in screensaver state!")
            null
        }

        Log.d("MainActivity", "Screensaver game: ${screensaverGame?.gameFilename}")
        Log.d("MainActivity", "Screensaver initialized: $screensaverInitialized")

        // If black screen, don't load anything
        if (screensaverBehavior == "black_screen") {
            Log.d("MainActivity", "Black screen - ignoring screensaver game select")
            return
        }

        val isFirstGame = !screensaverInitialized

        if (isFirstGame) {
            Log.d(
                "MainActivity",
                "Screensaver: First game event received - initializing display"
            )
            screensaverInitialized = true
        }

        if (screensaverGame != null) {
            refreshWidgets()
        } else {
            Log.w("MainActivity", "No screensaver game info available")
        }

        Log.d("MainActivity", "Screensaver game select complete")
        Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    private fun hideWidgets() {
        widgetViewBinder.setAllVisibility(widgetContainer, false)
    }

    private fun releaseMusicPlayer(transition: Boolean = true) {
        //musicManager.pauseMusic()
        listeningToAudioRef = false
    }

    private fun showContextMenu() {
        if(!this::artRepository.isInitialized || artRepository.getAvailableScraperTypes().size != ScraperType.entries.size) {
            lifecycleScope.launch(Dispatchers.IO) {
                updateScrapers(ApiKeyManager.getInstance(this@MainActivity).scraperCredentials.first())
            }
        }

        if(state !is AppState.GamePlaying && state !is AppState.Screensaver && !menuState.isActive())
        {
            if(this::artRepository.isInitialized ) {
                cancelAutoTransition()
                runOnUiThread {
                    widgetMenuShowing = true
                    menuState.showMenu = true
                    AudioReferee.updateMenuState(true)
                    AudioReferee.forceUpdate()
                }
            } else {
                Toast.makeText(
                    this,
                    "Please wait for the scrapers to initialize (try again in 5 seconds)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            Toast.makeText(
                this,
                "Can't open the menu while playing a game or during screensavers",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun hideContextMenu() {
        menuState.showMenu = false
        widgetMenuShowing = false
        menuState.widgetToEditState = null
        AudioReferee.updateMenuState(false)
        AudioReferee.forceUpdate()
        refreshWidgets(forcedRefresh = true)
    }

    private fun openWidgetSettings(widget: Widget?) {
        if(widget != null) {
            cancelAutoTransition()
            runOnUiThread {
                menuState.widgetSelected = WidgetUiState()
                activeWidget?.currentMode = WidgetMode.IDLE
                menuState.widgetToEditState = widget
                widgetMenuShowing = true
            }
        }
    }

    private fun onAddPageSelected() {
        currentWidgetManager().addNewPage()
        refreshWidgets(pageSwap = true)
        Toast.makeText(
            this,
            "Page ${currentWidgetManager().currentPageIndex + 1} created",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun onRemovePageSelected() {
        currentWidgetManager().removeCurrentPage()
        refreshWidgets(pageSwap = true)
    }

    private fun performMusicSearch(query: String) {
        val s = state as? AppState.GameBrowsing ?: return
        val gameName = s.gameName ?: ""
        val systemName = s.systemName

        musicSearchJob?.cancel()
        musicSearchJob = lifecycleScope.launch {
            isSearchingMusic = true
            musicResults = emptyList()

            val results = withContext(Dispatchers.IO) {
                gameMusicRepository.getAllPotentialResults(query, gameName, systemName)
            }

            musicResults = results
            isSearchingMusic = false
        }
    }

    private fun onMusicResultSelected(selected: StreamInfoItem, onProgress: (Float) -> Unit) {
        val s = state as? AppState.GameBrowsing ?: return
        val systemName = s.systemName
        val gameFilenameSanitized = extractGameFilenameWithoutExtension(sanitizeGameFilename(s.gameFilename))

        lifecycleScope.launch {
            Log.d("CoroutineDebug", "Downloading selected: ${selected.name}")
            gameMusicRepository.manualSelection(gameFilenameSanitized, systemName, selected.url, onProgress)
            val found = musicManager.onManualSelect(s.gameName ?: "", gameFilenameSanitized, systemName, state)
            if(found) {
                Toast.makeText(this@MainActivity, "Saved music!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onMusicResultSelectedKh(selected: KhSong, url: String, onProgress: (Float) -> Unit) {
        val s = state as? AppState.GameBrowsing ?: return
        val systemName = s.systemName
        val gameFilenameSanitized = extractGameFilenameWithoutExtension(sanitizeGameFilename(s.gameFilename))

        lifecycleScope.launch {
            Log.d("CoroutineDebug", "Downloading selected: ${selected.title}")
            gameMusicRepository.manualKhSelection(gameFilenameSanitized, systemName, url, onProgress)
            val found = musicManager.onManualSelect(s.gameName ?: "", gameFilenameSanitized, systemName, state)
            if(found) {
                Toast.makeText(this@MainActivity, "Saved music!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateCurrentGameVolume() {
        val s = state as? AppState.GameBrowsing ?: return
        val systemName = s.systemName
        val gameFilenameSanitized = extractGameFilenameWithoutExtension(sanitizeGameFilename(s.gameFilename))

        lifecycleScope.launch {
            val volume = loudnessService.getVolumeForGame(gameFilenameSanitized, systemName)
            currentGameVolume = volume
        }
    }

    private fun onGameVolumeSaved(value: Double) {
        val s = state as? AppState.GameBrowsing ?: return
        val systemName = s.systemName
        val gameFilenameSanitized = extractGameFilenameWithoutExtension(sanitizeGameFilename(s.gameFilename))

        lifecycleScope.launch {
            loudnessService.saveVolumePreference(gameFilenameSanitized, systemName, value)
            musicManager.setNormalizedVolume(value.toFloat())
            currentGameVolume = value
        }
    }

    private fun toggleSnapToGrid() {
        snapToGrid = !snapToGrid

        // Update all active widgets with the new snap state
        widgetViewBinder.setAllSnapToGrid(widgetContainer, snapToGrid, gridSize)

        // Save snap state to preferences
        prefsManager.snapToGrid = snapToGrid
    }

    private fun toggleShowGrid() {
        showGrid = !showGrid
        updateGridOverlay()
    }

    private fun toggleWidgetLock() {
        widgetsLocked = !widgetsLocked

        // Update all active widgets with the new lock state
        widgetViewBinder.setAlLocked(widgetContainer, widgetsLocked)

        val message = if (widgetsLocked) {
            "Widgets locked - they can no longer be moved, resized, or deleted"
        } else {
            "Widgets unlocked - tap to select, drag to move, resize from corner. BG videos won't play while unlocked!"
        }

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        // Handle video playback and widget reload when toggling widget lock
        if (widgetsLocked) {
            Log.d("MainActivity", "Widget edit mode OFF - allowing videos")
            showGrid = false
            updateGridOverlay()
        } else {
            Log.d(
                "MainActivity",
                "Widget edit mode ON - blocking videos and reloading widgets"
            )
            backgroundBinder.releasePlayer()
            showGrid = true
            updateGridOverlay()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            imageLoader.memoryCache?.clear()
        }
    }

    companion object {
        const val COLUMN_COUNT_KEY = "column_count"
    }
}