package com.esde.companion

import android.Manifest
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.ColorStateList
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import android.hardware.display.DisplayManager
import android.media.AudioManager
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
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextWatcher
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateDecelerateInterpolator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
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
import coil.imageLoader
import com.api.igdb.request.IGDBWrapper
import com.caverock.androidsvg.SVG
import com.esde.companion.MediaFileHelper.extractGameFilenameWithoutExtension
import com.esde.companion.MediaFileHelper.sanitizeGameFilename
import com.esde.companion.art.ArtRepository
import com.esde.companion.art.IGDB.IgdbArtScraper
import com.esde.companion.art.IGDB.TwitchAuth
import com.esde.companion.art.SGDB.SGDBScraper
import com.esde.companion.ost.MusicPlayer
import com.esde.companion.ost.MusicRepository
import com.esde.companion.ost.YoutubeMediaService
import com.esde.companion.ost.loudness.AppDatabase
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
import java.io.IOException
import kotlin.math.abs


class ContextMenuStateHolder {
    fun isActive(): Boolean {
        return showMenu || widgetToEditState != null
    }

    var widgetToEditState by mutableStateOf<OverlayWidget?>(null)
    var showMenu by mutableStateOf(false)
}

class MainActivity : AppCompatActivity(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(true)
            .okHttpClient {
                NetworkClientManager.baseClient
            }
            .components {
                if (SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    private lateinit var menuComposeView: ComposeView
    private lateinit var artRepository: ArtRepository
    private lateinit var rootLayout: RelativeLayout
    private lateinit var appDrawer: View
    private lateinit var appRecyclerView: RecyclerView
    private lateinit var appSearchBar: EditText
    private lateinit var searchClearButton: ImageButton
    private lateinit var drawerBackButton: ImageButton
    private lateinit var settingsButton: ImageButton
    private lateinit var androidSettingsButton: ImageButton
    private lateinit var prefs: SharedPreferences
    private lateinit var appLaunchPrefs: AppLaunchPreferences
    private lateinit var mediaFileLocator: MediaFileLocator

    // ========== MUSIC INTEGRATION START ==========

    // ========== MUSIC INTEGRATION END ==========

    private lateinit var blackOverlay: View
    private var isBlackOverlayShown = false

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var gestureDetector: GestureDetectorCompat

    // Widget system
    private lateinit var widgetContainer: ResizableWidgetContainer
    private lateinit var gameWidgetManager: WidgetManager
    private lateinit var systemWidgetManager: WidgetManager
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
    private var touchDownX = 0f
    private var touchDownY = 0f
    private val LONG_PRESS_TIMEOUT by lazy {
        ViewConfiguration.getLongPressTimeout().toLong()
    }
    private var widgetMenuShowing = false

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

            // ========== MUSIC ==========
            // ===========================
        }

    private var fileObserver: FileObserver? = null
    private var allApps = listOf<ResolveInfo>()  // Store all apps for search filtering
    private var hasWindowFocus = true  // Track if app has window focus (is on top)

    // Note: hasWindowFocus is window-level state, not app state
    private var isLaunchingFromScreensaver =
        false  // Track if we're launching game from screensaver
    private var screensaverInitialized = false  // Track if screensaver has loaded its first game

    // Video playback variables
    private var volumeChangeReceiver: BroadcastReceiver? = null
    private var isActivityVisible = true  // Track onStart/onStop - most reliable signal

    // Flag to skip reload in onResume (used when returning from settings with no changes)
    private var skipNextReload = false

    // Double-tap detection variables
    private var tapCount = 0
    private var lastTapTime = 0L

    // Standard Android double-tap timeout (max time between taps)
    private val DOUBLE_TAP_TIMEOUT by lazy {
        ViewConfiguration.getDoubleTapTimeout().toLong() // Default: 300ms
    }

    // Custom minimum interval to prevent accidental activations (100ms)
    // This is intentionally higher than Android's internal 40ms hardware filter:
    // - 40ms filters touch controller artifacts (hardware-level)
    // - 100ms filters user errors like screen brushing (UX-level)
    // Still imperceptible to users while significantly reducing false positives
    private val MIN_TAP_INTERVAL =
        100L // 100ms minimum time between taps (prevents accidental fast touches)

    // Scripts verification
    private var isWaitingForScriptVerification = false
    private var scriptVerificationHandler: Handler? = null
    private var scriptVerificationRunnable: Runnable? = null
    private var currentVerificationDialog: AlertDialog? = null
    private var currentErrorDialog: AlertDialog? = null
    private val SCRIPT_VERIFICATION_TIMEOUT = 15000L  // 15 seconds

    // Dynamic debouncing for fast scrolling - separate tracking for systems and games
    private val imageLoadHandler = Handler(Looper.getMainLooper())
    private var imageLoadRunnable: Runnable? = null
    private var musicLoadHandler = Handler(Looper.getMainLooper())
    private var musicLoadRunnable: Runnable? = null
    private var musicSearchJob: Job? = null
    private var musicResults by mutableStateOf<List<StreamInfoItem>>(emptyList())
    private var isSearchingMusic by mutableStateOf(false)
    private var lastSystemScrollTime = 0L
    private var lastGameScrollTime = 0L

    // System scrolling: Enable debouncing to reduce rapid updates
    private val SYSTEM_FAST_SCROLL_THRESHOLD =
        250L // If scrolling faster than 250ms between changes, it's "fast"
    private val SYSTEM_FAST_SCROLL_DELAY = 300L // 300ms delay for fast system scrolling
    private val SYSTEM_SLOW_SCROLL_DELAY = 150L // 150ms delay for slow system scrolling

    // Game scrolling: No debouncing for instant response
    private val GAME_FAST_SCROLL_THRESHOLD = 250L
    private val GAME_FAST_SCROLL_DELAY = 0L // No delay for games
    private val GAME_SLOW_SCROLL_DELAY = 0L // No delay for games

    // Filter out game-select on game-start and game-end
    private var lastGameStartTime = 0L
    private var lastGameEndTime = 0L
    private val GAME_EVENT_DEBOUNCE = 2000L  // 2 seconds

    private val scriptManager = ScriptManager(this)

    private lateinit var musicRepository: MusicRepository
    private lateinit var musicPlayer: MusicPlayer

    private lateinit var volumeFader: VolumeFader

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
                }, 500)
            }
            val needsRecreate = result.data?.getBooleanExtra("NEEDS_RECREATE", false) ?: false
            val appsHiddenChanged =
                result.data?.getBooleanExtra("APPS_HIDDEN_CHANGED", false) ?: false
            // ========== MUSIC ==========
            // ===========================
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
                    Log.d(
                        "MainActivity",
                        "Image preference changed during gameplay - reloading display"
                    )
                    handleGameStart()
                    skipNextReload = true
                } else if (state is AppState.SystemBrowsing) {
                    // In system view - reload system image with new preference
                    Log.d(
                        "MainActivity",
                        "Image preference changed in system view - reloading system image"
                    )
                    loadSystemImage()
                    skipNextReload = true
                } else {
                    // In game browsing view - reload game image with new preference
                    Log.d(
                        "MainActivity",
                        "Image preference changed in game view - reloading game image"
                    )
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
                // ========== MUSIC ==========

                // ===========================
            } else {
                // No settings changed that require reload - skip the reload in onResume
                skipNextReload = true
            }
            // Note: Video audio changes are handled automatically in onResume

            // Start script verification if requested
            if (startVerification) {
                // Delay slightly to let UI settle
                Handler(Looper.getMainLooper()).postDelayed({
                    startScriptVerification()
                }, 500)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val database = AppDatabase.getDatabase(this)
        val loudnessDao = database.loudnessDao()

        /**lifecycleScope.launch(Dispatchers.IO) {
        loudnessDao.clearAllLoudnessData()
        }**/

        ////SCRAPING STUFF////
        val steamGrid = SGDBScraper(BuildConfig.STEAM_GRID_API_KEY)
        lifecycleScope.launch(Dispatchers.IO) {
        val token = TwitchAuth.getTwitchAccessToken()
            if(token != null) {
                IGDBWrapper.setCredentials(
                    BuildConfig.IGDB_CLIENT_ID,
                    token
                )
                var igdbScraper: IgdbArtScraper = IgdbArtScraper()
                artRepository = ArtRepository(steamGrid, igdbScraper)
            }
        }
        var youtubeService = YoutubeMediaService(NetworkClientManager.baseClient)

        musicRepository = MusicRepository(youtubeService, LoudnessService(loudnessDao))
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val musicExoPlayer = ExoPlayer.Builder(this).build()
        musicExoPlayer.setAudioAttributes(
            audioAttributes,
            true
        )

        musicPlayer = MusicPlayer(musicRepository, musicExoPlayer)
        volumeFader = VolumeFader(musicExoPlayer)


        prefs = getSharedPreferences("ESDESecondScreenPrefs", MODE_PRIVATE)
        appLaunchPrefs = AppLaunchPreferences(this)
        mediaFileLocator = MediaFileLocator(prefs)

        // ========== MUSIC INTEGRATION START ==========

        // ========== MUSIC INTEGRATION END ==========

        // Check if we should show widget tutorial for updating users
        checkAndShowWidgetTutorialForUpdate()

        rootLayout = findViewById(R.id.rootLayout)
        appDrawer = findViewById(R.id.appDrawer)
        appRecyclerView = findViewById(R.id.appRecyclerView)
        appSearchBar = findViewById(R.id.appSearchBar)
        searchClearButton = findViewById(R.id.searchClearButton)
        drawerBackButton = findViewById(R.id.drawerBackButton)
        settingsButton = findViewById(R.id.settingsButton)
        androidSettingsButton = findViewById(R.id.androidSettingsButton)
        blackOverlay = findViewById(R.id.blackOverlay)
        // ========== MUSIC INTEGRATION START ==========

        // ========== MUSIC INTEGRATION END ==========

        // Load snap to grid state
        snapToGrid = prefs.getBoolean("snap_to_grid", true)
        // Load show grid state
        showGrid = prefs.getBoolean("show_grid", false)

        // Initialize widget system
        widgetContainer = findViewById(R.id.widgetContainer)
        gameWidgetManager = WidgetManager(this, WidgetContext.GAME)
        systemWidgetManager = WidgetManager(this, WidgetContext.SYSTEM)
        widgetPathResolver = WidgetPathResolver(mediaFileLocator, prefs)
        widgetViewBinder = WidgetViewBinder()


        backgroundBinder = BackgroundBinder(
            context = this,
            lifecycleOwner = this,
            imageView = findViewById(R.id.gameImageView),
            videoView = findViewById(R.id.videoView),
            dimmerView = findViewById(R.id.dimmingOverlay),
            widgetsLocked = widgetsLocked,
            musicStop = ::releaseMusicPlayer,
            widgetHide = ::hideWidgets,
            pathResolver = widgetPathResolver
        )


        // Set initial position off-screen (above the top)
        val displayHeight = resources.displayMetrics.heightPixels.toFloat()
        blackOverlay.translationY = -displayHeight

        // Log display information at startup
        logDisplayInfo()

        setupAppDrawer()
        setupSearchBar()
        setupGestureDetector()  // Must be after setupAppDrawer so bottomSheetBehavior is initialized
        setupDrawerBackButton()
        setupSettingsButton()
        setupAndroidSettingsButton()
        gameWidgetManager.load()
        systemWidgetManager.load()

        // Apply drawer transparency
        updateDrawerTransparency()

        menuComposeView = findViewById<ComposeView>(R.id.menu_compose_view)

        menuComposeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    if (menuState.showMenu) {
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
                                onAddWidget = { type ->
                                    if (!widgetsLocked) {
                                        addNewWidget(type)
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
                            onMusicSelect = { selected, onProgress -> onMusicResultSelected(selected, onProgress) },
                            onSave = { url, contentType, slot ->
                                onScraperContentSave(
                                    url,
                                    contentType,
                                    slot
                                )
                            },
                            currentPageIndex = currentWidgetManager().currentPageIndex,
                            currentPage = currentWidgetManager().getCurrentPage(),
                            mediaService = youtubeService
                        )
                    }

                    if(menuState.widgetToEditState != null) {
                        WidgetSettingsOverlay(
                            widget = menuState.widgetToEditState!!,
                            currentPageIndex = currentWidgetManager().currentPageIndex,
                            onDismiss = {
                                menuState.widgetToEditState = null
                            },
                            onUpdate = { updated ->
                                onWidgetUpdated(updated)
                                menuState.widgetToEditState = null
                            },
                            onDelete = {deleted -> onWidgetDeleted(deleted)},
                            onReorder = {widget, forward -> onWidgetReordered(widget, forward)}
                        )
                    }
                }
            }
        }

        val logsDir = File(mediaFileLocator.getLogsPath())
        Log.d("MainActivity", "Logs directory: ${logsDir.absolutePath}")
        Log.d("MainActivity", "Logs directory exists: ${logsDir.exists()}")

        val systemScrollFile = File(logsDir, "esde_system_name.txt")
        val gameScrollFile = File(logsDir, "esde_game_filename.txt")

        Log.d("MainActivity", "System scroll file: ${systemScrollFile.absolutePath}")
        Log.d(
            "MainActivity",
            "System scroll file exists: ${systemScrollFile.exists()}"
        )
        Log.d("MainActivity", "Game scroll file: ${gameScrollFile.absolutePath}")
        Log.d("MainActivity", "Game scroll file exists: ${gameScrollFile.exists()}")

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

        // Register volume change listener for real-time updates
        registerVolumeListener()
        registerSecondaryVolumeObserver()

        lifecycleScope.launch {
            AudioReferee.currentPriority
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { priority ->
                    onAudioPriorityChanged(priority)
            }
        }
    }

    private fun onPageUpdated(updated: WidgetPage) {
        currentWidgetManager().updatePage(updated)
        refreshWidgets(pageSwap = true)
    }

    private fun onAudioPriorityChanged(priority: AudioReferee.AudioSource) {
        if (priority == AudioReferee.AudioSource.MUSIC) {
            if (!musicPlayer.isPlaying() && !AudioReferee.getMenuState() && state.toWidgetContext() == WidgetContext.GAME) {// && musicPlayer.hasBeenPlayed()) {
                startMusicPlayer()
            }
        } else if (musicPlayer.isPlaying()) {
            volumeFader.fadeTo(0f, 100) {
                musicPlayer.pause()
           }
        }
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
                val mediaSlot = OverlayWidget.MediaSlot.fromInt(slot)
                val dir = mediaFileLocator.getDir(
                    s.systemName,
                    mediaFileLocator.getFolderName(contentType),
                    mediaSlot
                )

                val extension = MimeTypeMap.getFileExtensionFromUrl(url).ifEmpty {
                    if (contentType == ContentType.VIDEO) "mp4" else "png"
                }
                val newFile = File(dir, "${fileName}${mediaSlot.suffix}.$extension")

                val existingFile = mediaFileLocator.findMediaFile(contentType, s.systemName, s.gameFilename, mediaSlot)
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
                    hideContextMenu()
                    refreshWidgets()
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

            val lastSeenVersion = prefs.getString("last_seen_app_version", "0.0.0") ?: "0.0.0"
            Log.d("MainActivity", "Last seen version from prefs: $lastSeenVersion")

            val hasSeenWidgetTutorial = prefs.getBoolean("widget_tutorial_shown", false)
            Log.d("MainActivity", "Has seen widget tutorial: $hasSeenWidgetTutorial")

            // Check if default widgets were created (indicates not a fresh install)
            val hasCreatedDefaultWidgets = prefs.getBoolean("default_widgets_created", false)
            Log.d(
                "MainActivity",
                "Has created default widgets: $hasCreatedDefaultWidgets"
            )

            // NEW LOGIC:
            // Show tutorial if:
            // 1. User hasn't seen it yet AND
            // 2. EITHER they're updating from an older version OR they have default widgets (not fresh install)
            val isOlderVersion =
                lastSeenVersion != "0.0.0" && isVersionLessThan(lastSeenVersion, currentVersion)
            val shouldShowTutorial =
                !hasSeenWidgetTutorial && (isOlderVersion || hasCreatedDefaultWidgets)

            Log.d("MainActivity", "Should show tutorial: $shouldShowTutorial")
            Log.d("MainActivity", "  - hasSeenWidgetTutorial: $hasSeenWidgetTutorial")
            Log.d("MainActivity", "  - isOlderVersion: $isOlderVersion")
            Log.d(
                "MainActivity",
                "  - hasCreatedDefaultWidgets: $hasCreatedDefaultWidgets"
            )

            if (shouldShowTutorial) {
                Log.d("MainActivity", "✓ Showing widget tutorial")
                Handler(Looper.getMainLooper()).postDelayed({
                    showWidgetSystemTutorial(fromUpdate = true)
                }, 3000)
            }

            // Always update the version tracking
            prefs.edit().putString("last_seen_app_version", currentVersion).apply()
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
        val hasCompletedSetup = prefs.getBoolean("setup_completed", false)

        // Check if permissions are granted
        val hasPermission = when {
            SDK_INT >= Build.VERSION_CODES.R ->
                Environment.isExternalStorageManager()

            SDK_INT >= Build.VERSION_CODES.M -> {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }

            else -> true
        }

        // Launch setup wizard immediately if:
        // 1. Setup not completed, OR
        // 2. Missing permissions
        if (!hasCompletedSetup || !hasPermission) {
            Log.d(
                "MainActivity",
                "Setup incomplete or missing permissions - launching wizard immediately"
            )
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(this, SettingsActivity::class.java)
                intent.putExtra("AUTO_START_WIZARD", true)
                settingsLauncher.launch(intent)
            }, 1000)
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
            "0.4.3"  // Fallback version
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

Widgets are overlay elements that display game/system artwork like marquees, box art, screenshots, and more. You can position and size them however you want!

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

• Widgets are context-aware - create separate layouts for games vs systems
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
                prefs.edit().putBoolean("widget_tutorial_shown", true).apply()

                // If user checked "don't show again", mark preference
                if (checkbox.isChecked) {
                    prefs.edit().putBoolean("widget_tutorial_dont_show_auto", true).apply()
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
        val scriptsPath = prefs.getString("scripts_path", null)

        // If no custom scripts path is set, scripts are likely on internal storage
        // Check immediately without retry
        if (scriptsPath == null || scriptsPath.startsWith("/storage/emulated/0")) {
            Log.d("MainActivity", "Scripts on internal storage - checking immediately")
            val hasCorrectScripts = scriptManager.checkScriptValidity(scriptsPath)
            if (!hasCorrectScripts) {
                Log.d(
                    "MainActivity",
                    "Scripts missing/outdated on internal storage - showing dialog"
                )

                // Check if scripts exist at all (missing vs outdated)
                val scriptsDir = File(scriptsPath ?: "/storage/emulated/0/ES-DE/scripts")
                val gameSelectScript = File(scriptsDir, "game-select/esdecompanion-game-select.sh")

                if (gameSelectScript.exists()) {
                    // Scripts exist but are outdated - show update dialog
                    Handler(Looper.getMainLooper()).postDelayed({
                        showScriptsUpdateAvailableDialog()
                    }, 1000)
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
            val delayMs = when (attempt) {
                0 -> 1000L  // 1 second
                1 -> 2000L  // 2 seconds
                2 -> 3000L  // 3 seconds
                3 -> 4000L  // 4 seconds
                else -> 5000L  // 5 seconds
            }

            Log.d(
                "MainActivity",
                "Scripts path not accessible (attempt ${attempt + 1}/$maxAttempts) - waiting ${delayMs}ms for SD card mount: $scriptsPath"
            )

            Handler(Looper.getMainLooper()).postDelayed({
                checkScriptsWithRetry(attempt + 1, maxAttempts)
            }, delayMs)
            return
        }

        // Either accessible now or max attempts reached - check scripts
        val hasCorrectScripts = scriptManager.checkScriptValidity(scriptsPath)

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
                }, 1000)
            } else {
                // Max attempts reached and still not accessible
                // SD card might not be mounted - show a helpful message
                Log.w(
                    "MainActivity",
                    "Scripts path not accessible after $maxAttempts attempts: $scriptsPath"
                )
                Handler(Looper.getMainLooper()).postDelayed({
                    showSdCardNotMountedDialog(scriptsPath)
                }, 1000)
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
        try {
            scriptManager.updateScriptsIfNeeded(
                prefs.getString(
                    "scripts_path",
                    "/storage/emulated/0/ES-DE/scripts"
                )
            )
            // Show success message
            Toast.makeText(
                this,
                "Scripts updated successfully!",
                Toast.LENGTH_LONG
            ).show()

            // Start verification
            Handler(Looper.getMainLooper()).postDelayed({
                startScriptVerification()
            }, 1000)
        } catch (e: Exception) {
            // Show error message
            Toast.makeText(
                this,
                "Error updating scripts: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            Log.e("MainActivity", "Error updating scripts", e)
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

        // Update video volume based on current system volume
        updateVideoVolume()
        updateMusicPlayerVolume()

        // Clear search bar
        if (::appSearchBar.isInitialized) {
            appSearchBar.text.clear()
        }

        // Reload grid layout in case column count changed
        val columnCount = prefs.getInt("column_count", 4)
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
                    loadSystemImage()
                } else {
                    loadGameInfo()  // This calls handleVideoForGame() internally
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isActivityVisible = true
    }

    override fun onStop() {
        super.onStop()
        isActivityVisible = false
        releaseMusicPlayer(false)
    }

    private fun updateDrawerTransparency() {
        val transparencyPercent = prefs.getInt("drawer_transparency", 70)
        // Convert percentage (0-100) to hex alpha (00-FF)
        val alpha = (transparencyPercent * 255 / 100).coerceIn(0, 255)
        val hexAlpha = String.format("%02x", alpha)
        val colorString = "#${hexAlpha}000000"

        val color = Color.parseColor(colorString)
        appDrawer.setBackgroundColor(color)
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

                override fun onSingleTapUp(event: MotionEvent): Boolean {
                    if (!widgetMenuShowing && menuState.widgetToEditState == null && !menuState.showMenu) {
                        val screenWidth = resources.displayMetrics.widthPixels
                        val touchX = event.x
                        val edgeThreshold = screenWidth * 0.10f // 10% of screen width

                        when {
                            touchX < edgeThreshold -> {
                                // Left edge tap
                                flipPage(false)
                                return true
                            }

                            touchX > (screenWidth - edgeThreshold) -> {
                                // Right edge tap
                                flipPage(true)
                                return true
                            }
                        }
                        return true
                    }
                    return true
                }
            })
    }

    /**
     * Cancel any pending long press - called by WidgetView when interaction starts
     */
    fun cancelLongPress() {
        longPressRunnable?.let {
            longPressHandler?.removeCallbacks(it)
            longPressTriggered = false
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
        releaseMusicPlayer(false)

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

        // Hide overlay instantly without animation
        blackOverlay.visibility = View.GONE

        val displayHeight = resources.displayMetrics.heightPixels.toFloat()
        blackOverlay.translationY = -displayHeight

        // Reload video if applicable (don't reload images)
        when (val s = state) {
            is AppState.GameBrowsing -> {
                // In GameBrowsing, we ALWAYS have systemName and gameFilename (non-null)
                //handleVideoForGame(s.systemName, s.gameFilename)
            }

            else -> {
                // Not in game browsing mode - don't play video
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Check if black overlay feature is enabled
        val doubleTapBehaviour = prefs.getString("double_tap_behavior", "off")

        // Check drawer state first
        val drawerState = bottomSheetBehavior.state
        val isDrawerOpen = drawerState == BottomSheetBehavior.STATE_EXPANDED ||
                drawerState == BottomSheetBehavior.STATE_SETTLING

        // Handle black overlay double-tap detection ONLY when drawer is closed and feature is enabled
        if (!isDrawerOpen && doubleTapBehaviour.equals("black_overlay")) {
            if (handleBlackOverlayTouchEvent(ev)) return true
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
                longPressTriggered = false

                // Cancel any existing callbacks first
                longPressRunnable?.let {
                    longPressHandler?.removeCallbacks(it)
                }

                // Allow long press in system view too
                if (!widgetMenuShowing && drawerState == BottomSheetBehavior.STATE_HIDDEN) {
                    if (longPressHandler == null) {
                        longPressHandler = Handler(Looper.getMainLooper())
                    }
                    longPressRunnable = Runnable {
                        if (!longPressTriggered && !widgetMenuShowing) {
                            longPressTriggered = true
                            showContextMenu()
                        }
                    }
                    longPressHandler?.postDelayed(longPressRunnable!!, LONG_PRESS_TIMEOUT)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                // Cancel long press if finger moves beyond touch slop threshold
                val deltaX = abs(ev.x - touchDownX)
                val deltaY = abs(ev.y - touchDownY)
                val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

                if (deltaX > touchSlop || deltaY > touchSlop) {
                    longPressRunnable?.let {
                        longPressHandler?.removeCallbacks(it)
                        longPressTriggered = false
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // CHANGED: Always cancel the callback on finger lift
                longPressRunnable?.let {
                    longPressHandler?.removeCallbacks(it)
                }

                if (longPressTriggered) {
                    // Long press was triggered, consume this event
                    longPressTriggered = false  // ADDED: Reset immediately
                    return true
                }
            }
        }

        // Handle tapping outside widgets to deselect them
        if (ev.action == MotionEvent.ACTION_UP && !longPressTriggered) {
            if (!widgetViewBinder.isWidgetOnLocation(widgetContainer, ev.x, ev.y)) {
                // Tapped outside any widget - deselect all
                widgetViewBinder.deselectAll(widgetContainer)
            }
        }

        // Track widget interaction state for gesture detector
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                val widgetOnLocation = widgetViewBinder.findWidgetAt(widgetContainer, ev.x, ev.y)
                isInteractingWithWidget =
                    widgetOnLocation != null && widgetOnLocation.isWidgetSelected
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isInteractingWithWidget = false
            }
        }

        // Only use gesture detector if NOT actively interacting with a SELECTED widget AND drawer is hidden
        if (drawerState == BottomSheetBehavior.STATE_HIDDEN && !isInteractingWithWidget) {
            gestureDetector.onTouchEvent(ev)
        }

        return super.dispatchTouchEvent(ev)
    }

    private fun handleBlackOverlayTouchEvent(ev: MotionEvent): Boolean {
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

                    // Toggle black overlay
                    if (isBlackOverlayShown) {
                        hideBlackOverlay()
                    } else {
                        showBlackOverlay()
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

    private fun checkForDoubleTap(): Boolean {
        val currentTime = System.currentTimeMillis()

        // Reset tap count if too much time has passed
        if (currentTime - lastTapTime > DOUBLE_TAP_TIMEOUT) {
            tapCount = 0
        }

        tapCount++
        lastTapTime = currentTime
        // Check for triple-tap
        if (tapCount >= 2) {
            tapCount = 0 // Reset counter
            return true
        }
        return false
    }

    private fun setupAppDrawer() {
        bottomSheetBehavior = BottomSheetBehavior.from(appDrawer)
        bottomSheetBehavior.peekHeight = 0
        bottomSheetBehavior.isHideable = true
        bottomSheetBehavior.skipCollapsed = true

        bottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    showSettingsPulseHint()
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        appDrawer.post {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            Log.d(
                "MainActivity",
                "AppDrawer state set to HIDDEN: ${bottomSheetBehavior.state}"
            )
        }

        val columnCount = prefs.getInt(COLUMN_COUNT_KEY, 4)
        appRecyclerView.layoutManager = GridLayoutManager(this, columnCount)

        val mainIntent = Intent(Intent.ACTION_MAIN, null)
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)

        val hiddenApps = prefs.getStringSet("hidden_apps", setOf()) ?: setOf()
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

        Log.d(
            "MainActivity",
            "AppDrawer setup complete, initial state: ${bottomSheetBehavior.state}"
        )
    }

    /**
     * Show pulsing animation on settings button when drawer opens
     * Only shows the first 3 times the drawer is opened (total, not per session)
     */
    private fun showSettingsPulseHint() {
        // Only show if user has completed setup
        if (!prefs.getBoolean("setup_completed", false)) return

        // Check how many times hint has been shown (max 3 times total)
        val hintCount = prefs.getInt("settings_hint_count", 0)
        if (hintCount >= 3) return

        // Increment the hint counter
        prefs.edit().putInt("settings_hint_count", hintCount + 1).apply()

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

                val hiddenApps = prefs.getStringSet("hidden_apps", setOf()) ?: setOf()

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
            startActivity(intent)
        }
    }

    private fun startFileMonitoring() {
        val watchDir = File(mediaFileLocator.getLogsPath())
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
                if (path != null && (path == "esde_game_filename.txt" || path == "esde_system_name.txt" ||
                            path == "esde_gamestart_filename.txt" || path == "esde_gameend_filename.txt" ||
                            path == "esde_screensaver_start.txt" || path == "esde_screensaver_end.txt" ||
                            path == "esde_screensavergameselect_filename.txt")
                ) {
                    // Debounce: ignore events that happen too quickly
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastEventTime < 100) {
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

                            when (path) {
                                "esde_system_name.txt" -> {
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

                                "esde_game_filename.txt" -> {
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

                                "esde_gamestart_filename.txt" -> {
                                    Log.d("MainActivity", "Game start detected")
                                    handleGameStart()
                                }

                                "esde_gameend_filename.txt" -> {
                                    Log.d("MainActivity", "Game end detected")
                                    handleGameEnd()
                                }

                                "esde_screensaver_start.txt" -> {
                                    Log.d("MainActivity", "Screensaver start detected")
                                    handleScreensaverStart()
                                }

                                "esde_screensaver_end.txt" -> {
                                    // Read the screensaver end reason
                                    val screensaverEndFile =
                                        File(watchDir, "esde_screensaver_end.txt")
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

                                "esde_screensavergameselect_filename.txt" -> {
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

                                        // Apply screensaver behavior preferences
                                        val screensaverBehavior =
                                            prefs.getString("screensaver_behavior", "game_image")
                                                ?: "game_image"
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
                                    }

                                    // Read screensaver game info and update state
                                    val filenameFile =
                                        File(watchDir, "esde_screensavergameselect_filename.txt")
                                    val nameFile =
                                        File(watchDir, "esde_screensavergameselect_name.txt")
                                    val systemFile =
                                        File(watchDir, "esde_screensavergameselect_system.txt")

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

                                    // Update screensaver state with current game
                                    if (state is AppState.Screensaver && gameFilename != null && systemName != null) {
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

                                    Log.d(
                                        "MainActivity",
                                        "Screensaver game: $gameName ($gameFilename) - $systemName"
                                    )
                                    handleScreensaverGameSelect()
                                }
                            }
                        }, 50) // 50ms delay to ensure file is written
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
            val hasSeenWidgetTutorial = prefs.getBoolean("widget_tutorial_shown", false)
            val hasCompletedSetup = prefs.getBoolean("setup_completed", false)

            if (!hasSeenWidgetTutorial && hasCompletedSetup) {
                // Show widget tutorial after successful verification following setup
                Log.d(
                    "MainActivity",
                    "Showing widget tutorial after setup verification"
                )
                Handler(Looper.getMainLooper()).postDelayed({
                    showWidgetSystemTutorial(fromUpdate = false)
                }, 1000)  // 1 second after verification success
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
        super.onDestroy()
        // Stop script verification
        scriptVerificationRunnable?.let { scriptVerificationHandler?.removeCallbacks(it) }
        currentVerificationDialog?.dismiss()
        currentErrorDialog?.dismiss()
        fileObserver?.stopWatching()
        unregisterReceiver(appChangeReceiver)
        unregisterVolumeListener()
        unregisterSecondaryVolumeObserver()
        // Cancel any pending image loads
        imageLoadRunnable?.let { imageLoadHandler.removeCallbacks(it) }
        // Release video player
        backgroundBinder.releasePlayer()
        releaseMusicPlayer(false)

        // ========== MUSIC ==========
        // ===========================
    }

    // ========== MUSIC INTEGRATION START ==========
    // ========== MUSIC INTEGRATION END ==========

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        hasWindowFocus = hasFocus

        // Don't use focus changes to block videos - too unreliable
        // Just log for debugging
        if (hasFocus) {
            Log.d("MainActivity", "Window focus gained")
            backgroundBinder.onWindowFocusChanged(hasFocus)
            startMusicPlayer()
        } else {
            Log.d("MainActivity", "Window focus lost (ignoring for video blocking)")
            // Stop videos when we lose focus (game launched on same screen)
            backgroundBinder.onPause(this)
            musicPlayer.pause()
        }
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

    private fun refreshWidgets(pagePreValidated: Boolean = false, pageSwap: Boolean = false) {
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
        val gameLaunchBehaviour = prefs.getString("game_launch_behavior", "game_image") ?: "game_image"
        val screensaverBehavior =
            prefs.getString("screensaver_behavior", "game_image") ?: "game_image"
        if(state is AppState.GamePlaying) { wrapperPage = createWidgetPageForBehaviour(gameLaunchBehaviour)}
        if(state is AppState.Screensaver) { wrapperPage = createWidgetPageForBehaviour(screensaverBehavior)}
        var currentPage: WidgetPage = currentWidgetManager().getCurrentPage()

        if(!pagePreValidated && !isPageValid(currentPage)) {
            currentWidgetManager().currentPageIndex = 0
            currentPage = currentWidgetManager().getCurrentPage()
        }

        //TODO: what happens when we change settings/widgets on a wrapper page?
        if(wrapperPage != null) {
            wrapperPage.widgets = currentPage.widgets
            processPage = wrapperPage
        } else {
            processPage = currentPage
        }


        val pageMediaFile = widgetPathResolver.resolvePage(processPage, state)
        backgroundBinder.apply(processPage, state, pageMediaFile)

        //TODO: do we need a method callback that releases everything?

        if(processPage.displayWidgets) {
            val resolved = widgetPathResolver.resolve(
                processPage.widgets,
                state.getCurrentSystemName(),
                state.getCurrentGameFilename(),
                resources.displayMetrics
            )

            widgetViewBinder.sync(
                container = widgetContainer,
                lifecycleOwner = this,
                dataList = resolved,
                widgetsLocked,
                snapToGrid,
                gridSize,
                pageSwap,
                onUpdate = ::onWidgetUpdated,
                onEditRequested = ::openWidgetSettings
            )
        } else {
            hideWidgets()
        }
    }

    private fun isCurrentPageValid() {
        TODO("Not yet implemented")
    }

    private fun startMusicPlayer() {
        if (AudioReferee.currentPriority.value == AudioReferee.AudioSource.MUSIC && !musicPlayer.isPlaying() && !AudioReferee.getMenuState() && state.toWidgetContext() == WidgetContext.GAME) {
            musicPlayer.setVolume(0f)
            musicPlayer.play()
            volumeFader.fadeTo(musicPlayer.targetVolume, 750)
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
                wrapperPage.backgroundType = PageContentType.SYSTEM_IMAGE
                wrapperPage.displayWidgets = true
            }
            "game_image" -> {
                //get prefs for screenshot/fanart
                wrapperPage.backgroundType = PageContentType.FANART
                wrapperPage.displayWidgets = true
            }
            else -> {
                wrapperPage.backgroundType = PageContentType.CUSTOM_IMAGE
                wrapperPage.customPath = ""
                wrapperPage.displayWidgets = true
            }
        }
        return wrapperPage
    }

    private fun onWidgetUpdated(widget: OverlayWidget) {
        currentWidgetManager().updateWidget(widget, resources.displayMetrics)
        refreshWidgets()
    }

    private fun currentWidgetManager(): WidgetManager {
        if(state.toWidgetContext() == WidgetContext.GAME) {
            return gameWidgetManager
        }
        return systemWidgetManager
    }

    private fun onWidgetDeleted(widget: OverlayWidget) {
        currentWidgetManager().deleteWidget(widget.id)
        hideContextMenu()
        refreshWidgets()
    }

    private fun onWidgetReordered(widget: OverlayWidget, forward: Boolean) {
        currentWidgetManager().moveWidgetZOrder(widget.id, forward)
        refreshWidgets()
    }

    fun addNewWidget(type: ContentType) {
        currentWidgetManager().addNewWidgetToCurrentPage(
            type,
            resources.displayMetrics
        )
        refreshWidgets()
        val newView = widgetContainer.getChildAt(widgetContainer.childCount - 1) as? WidgetView
        newView?.let {
            widgetViewBinder.deselectAll(widgetContainer)
            it.isWidgetSelected = true
        }
    }

    private fun flipPage(next: Boolean) {
        if (widgetViewBinder.isAnyWidgetBusy(widgetContainer)) {
            return
        }

        val currentManager = currentWidgetManager()
        var nextIndex = currentManager.currentPageIndex
        val totalPages = currentManager.getPageCount()
        var foundValidPage = false
        val allPages = currentManager.getAllPages()
        val direction = if(next) 1 else -1

        for (i in 0 until totalPages) {
            nextIndex = (nextIndex + direction + totalPages) % totalPages
            val candidatePage = allPages[nextIndex]

            if (isPageValid(candidatePage)) {
                currentManager.currentPageIndex = nextIndex
                foundValidPage = true
                break
            }
        }

        if (foundValidPage) {
            //screenTransition({})
            refreshWidgets(pagePreValidated = true, pageSwap = true)
        }
    }

    fun isPageValid(page: WidgetPage): Boolean {
        if(!widgetsLocked) return true
        val bgFile = widgetPathResolver.resolvePage(page, state)
        if (page.backgroundType != PageContentType.SOLID_COLOR && page.isRequired && (bgFile == null || !bgFile.exists())) {
            return false
        }

        val system = state.getCurrentSystemName()
        val game = state.getCurrentGameFilename()

        page.widgets.forEach { widget ->
            val result = widgetPathResolver.resolveSingle(widget, system, game, resources.displayMetrics)
            if (result.missingRequired) return false
        }

        return true
    }

    private fun screenTransition(onFadeOut: () -> Unit) {
        onFadeOut()
        return
        widgetContainer.animation?.cancel()
        widgetContainer.animate()
            .alpha(0f)
            .setDuration(100)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {

                widgetContainer.animate()
                    .alpha(1f)
                    .setDuration(100)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
            .start()

        rootLayout.animation?.cancel()
        rootLayout.animate()
            .alpha(0f)
            .setStartDelay(50)
            .setDuration(50)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                onFadeOut()

                rootLayout.animate()
                    .alpha(1f)
                    .setDuration(50)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
            .start()
    }

    /**
     * Load a built-in system logo SVG from assets folder
     * Handles both regular systems and ES-DE auto-collections
     * Returns drawable if found, null otherwise
     */
    fun loadSystemLogoFromAssets(
        systemName: String,
        width: Int = -1,
        height: Int = -1
    ): Drawable? {
        return try {
            // Handle ES-DE auto-collections
            val baseFileName = when (systemName.lowercase()) {
                "allgames" -> "auto-allgames"
                "favorites" -> "auto-favorites"
                "lastplayed" -> "auto-lastplayed"
                else -> systemName.lowercase()
            }

            // First check user-provided system logos path with multiple format support
            val userLogosDir = File(mediaFileLocator.getSystemLogosPath())
            if (userLogosDir.exists() && userLogosDir.isDirectory) {
                val extensions = listOf("svg", "png", "jpg", "jpeg", "webp")

                for (ext in extensions) {
                    val logoFile = File(userLogosDir, "$baseFileName.$ext")
                    if (logoFile.exists()) {
                        Log.d("MainActivity", "Loading logo from user path: $logoFile")

                        return when (ext) {
                            "svg" -> {
                                val svg =
                                    SVG.getFromInputStream(logoFile.inputStream())

                                if (width > 0 && height > 0) {
                                    // Create bitmap at target dimensions
                                    val bitmap = Bitmap.createBitmap(
                                        width,
                                        height,
                                        Bitmap.Config.ARGB_8888
                                    )
                                    val canvas = Canvas(bitmap)

                                    val viewBox = svg.documentViewBox
                                    if (viewBox != null) {
                                        // SVG has viewBox - let AndroidSVG handle scaling
                                        svg.setDocumentWidth(width.toFloat())
                                        svg.setDocumentHeight(height.toFloat())
                                        svg.renderToCanvas(canvas)
                                        Log.d(
                                            "MainActivity",
                                            "User SVG ($baseFileName) with viewBox rendered at ${width}x${height}"
                                        )
                                    } else {
                                        // No viewBox - manually scale using document dimensions
                                        val docWidth = svg.documentWidth
                                        val docHeight = svg.documentHeight

                                        if (docWidth > 0 && docHeight > 0) {
                                            val scaleX = width.toFloat() / docWidth
                                            val scaleY = height.toFloat() / docHeight
                                            val scale = minOf(scaleX, scaleY)

                                            val scaledWidth = docWidth * scale
                                            val scaledHeight = docHeight * scale
                                            val translateX = (width - scaledWidth) / 2f
                                            val translateY = (height - scaledHeight) / 2f

                                            canvas.translate(translateX, translateY)
                                            canvas.scale(scale, scale)
                                            svg.renderToCanvas(canvas)
                                            Log.d(
                                                "MainActivity",
                                                "User SVG ($baseFileName) no viewBox, scaled from ${docWidth}x${docHeight} to ${width}x${height}, scale: $scale"
                                            )
                                        }
                                    }

                                    // Return drawable with no intrinsic dimensions
                                    object : BitmapDrawable(
                                        resources,
                                        bitmap
                                    ) {
                                        override fun getIntrinsicWidth(): Int = -1
                                        override fun getIntrinsicHeight(): Int = -1
                                    }
                                } else {
                                    PictureDrawable(svg.renderToPicture())
                                }
                            }

                            else -> {
                                // Load bitmap formats (PNG, JPG, WebP) with downscaling
                                val bitmap = loadScaledBitmap(logoFile.absolutePath, 800, 1000)
                                BitmapDrawable(resources, bitmap)
                            }
                        }
                    }
                }
            }

            // Fall back to built-in SVG assets
            val svgPath = "system_logos/$baseFileName.svg"
            val svg = SVG.getFromAsset(assets, svgPath)

            if (width > 0 && height > 0) {
                // Create bitmap at target dimensions
                val bitmap = Bitmap.createBitmap(
                    width,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)

                val viewBox = svg.documentViewBox
                if (viewBox != null) {
                    // SVG has viewBox - let AndroidSVG handle scaling
                    svg.setDocumentWidth(width.toFloat())
                    svg.setDocumentHeight(height.toFloat())
                    svg.renderToCanvas(canvas)
                    Log.d(
                        "MainActivity",
                        "Built-in SVG ($baseFileName) with viewBox rendered at ${width}x${height}"
                    )
                } else {
                    // No viewBox - manually scale using document dimensions
                    val docWidth = svg.documentWidth
                    val docHeight = svg.documentHeight

                    if (docWidth > 0 && docHeight > 0) {
                        val scaleX = width.toFloat() / docWidth
                        val scaleY = height.toFloat() / docHeight
                        val scale = minOf(scaleX, scaleY)

                        val scaledWidth = docWidth * scale
                        val scaledHeight = docHeight * scale
                        val translateX = (width - scaledWidth) / 2f
                        val translateY = (height - scaledHeight) / 2f

                        canvas.translate(translateX, translateY)
                        canvas.scale(scale, scale)
                        svg.renderToCanvas(canvas)
                        Log.d(
                            "MainActivity",
                            "Built-in SVG ($baseFileName) no viewBox, scaled from ${docWidth}x${docHeight} to ${width}x${height}, scale: $scale"
                        )
                    }
                }

                // Return drawable with no intrinsic dimensions
                object : BitmapDrawable(resources, bitmap) {
                    override fun getIntrinsicWidth(): Int = -1
                    override fun getIntrinsicHeight(): Int = -1
                }
            } else {
                PictureDrawable(svg.renderToPicture())
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "Failed to load logo for $systemName", e)
            // Return text-based drawable as fallback
            createTextFallbackDrawable(systemName, width, height)
        }
    }

    /**
     * Create a text-based drawable as fallback for marquee images when no image is available
     * @param gameName The game name to display
     * @param width Target width in pixels (default 800 for marquees)
     * @param height Target height in pixels (default 300 for marquees)
     * @return A drawable with centered text on transparent background
     */
    fun createMarqueeTextFallback(
        gameName: String,
        width: Int = 800,
        height: Int = 300
    ): Drawable {
        // Clean up game name for display
        val displayName = gameName
            .replaceFirst(Regex("\\.[^.]+$"), "") // Remove file extension
            .replace(Regex("[_-]"), " ") // Replace underscores/hyphens with spaces
            .replace(Regex("\\s+"), " ") // Normalize multiple spaces
            .trim()
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)

        // Leave background transparent (no background drawing)

        // Configure text paint
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = height * 0.20f // Start with 20% of height
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        // Word wrap logic with line limit
        val maxWidth = width * 1.0f
        val lineHeight = paint.textSize * 1.2f
        val maxLines =
            (height * 0.9f / lineHeight).toInt().coerceAtLeast(1) // Calculate how many lines fit

        val words = displayName.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                    if (lines.size >= maxLines) break // Stop if we've reached max lines
                }
                currentLine = word
            }
        }

        // Handle the last line with ellipsis if needed
        if (currentLine.isNotEmpty()) {
            if (lines.size >= maxLines) {
                // Truncate last line with ellipsis
                val lastLine = lines[maxLines - 1]
                var truncated = lastLine
                while (paint.measureText("$truncated...") > maxWidth && truncated.isNotEmpty()) {
                    truncated = truncated.dropLast(1).trimEnd()
                }
                lines[maxLines - 1] = "$truncated..."
            } else {
                lines.add(currentLine)
            }
        }

        // Draw lines centered vertically
        val totalHeight = lines.size * lineHeight
        var yPos = (height - totalHeight) / 2f + lineHeight * 0.8f

        for (line in lines) {
            canvas.drawText(line, width / 2f, yPos, paint)
            yPos += lineHeight
        }

        return BitmapDrawable(resources, bitmap)
    }

    private fun createTextFallbackDrawable(
        systemName: String,
        width: Int = -1,
        height: Int = -1
    ): Drawable {
        // Clean up system name for display
        val displayName = systemName
            .replace("auto-", "")
            .replace("-", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

        // Create a bitmap to draw text on
        val targetWidth = if (width > 0) width else 400
        val targetHeight = if (height > 0) height else 200

        val bitmap = Bitmap.createBitmap(
            targetWidth,
            targetHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)

        // Configure text paint
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = targetHeight * 0.35f // Scale text to ~35% of height
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        // Draw text centered
        val xPos = targetWidth / 2f
        val yPos = (targetHeight / 2f) - ((paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(displayName, xPos, yPos, paint)

        return BitmapDrawable(resources, bitmap)
    }

    /**
     * Load a scaled bitmap to prevent out-of-memory errors with large images
     * @param imagePath Path to the image file
     * @param maxWidth Maximum width in pixels
     * @param maxHeight Maximum height in pixels
     * @return Scaled bitmap
     */
    private fun loadScaledBitmap(
        imagePath: String,
        maxWidth: Int,
        maxHeight: Int
    ): Bitmap? {
        try {
            // First decode with inJustDecodeBounds=true to check dimensions
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(imagePath, options)

            // Calculate inSampleSize
            val imageHeight = options.outHeight
            val imageWidth = options.outWidth
            var inSampleSize = 1

            if (imageHeight > maxHeight || imageWidth > maxWidth) {
                val halfHeight = imageHeight / 2
                val halfWidth = imageWidth / 2

                // Calculate the largest inSampleSize value that is a power of 2 and keeps both
                // height and width larger than the requested height and width.
                while ((halfHeight / inSampleSize) >= maxHeight && (halfWidth / inSampleSize) >= maxWidth) {
                    inSampleSize *= 2
                }
            }

            Log.d("MainActivity", "Loading image: $imagePath")
            Log.d("MainActivity", "  Original size: ${imageWidth}x${imageHeight}")
            Log.d("MainActivity", "  Sample size: $inSampleSize")
            Log.d(
                "MainActivity",
                "  Target size: ~${imageWidth / inSampleSize}x${imageHeight / inSampleSize}"
            )

            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false
            options.inSampleSize = inSampleSize
            options.inPreferredConfig = Bitmap.Config.RGB_565 // Use less memory

            return BitmapFactory.decodeFile(imagePath, options)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading scaled bitmap: $imagePath", e)
            return null
        }
    }

    /**
     * Get a signature for an image file to invalidate cache when file changes
     * Uses file's last modified time to detect changes
     */
    private fun getFileSignature(file: File): String {
        return if (file.exists()) {
            // Combine multiple signals for better cache invalidation
            "${file.lastModified()}_${file.length()}"
        } else {
            "0"
        }
    }

    /**
     * Create a text drawable for system name when no logo exists
     * Size is based on logo size setting
     */
    private fun createTextDrawable(
        systemName: String,
        logoSize: String
    ): Drawable {
        // Determine text size based on logo size setting
        val textSizePx = when (logoSize) {
            "small" -> 90f
            "medium" -> 120f
            "large" -> 150f
            else -> 120f // default to medium
        }

        // Define max width wider than logo container sizes to reduce wrapping
        val maxWidthDp = when (logoSize) {
            "small" -> 400    // Back to original
            "large" -> 600    // Back to original
            else -> 500       // Back to original (medium)
        }
        val maxWidth = (maxWidthDp * resources.displayMetrics.density).toInt()

        // Create paint for text
        val textPaint = TextPaint().apply {
            color = Color.WHITE
            textSize = textSizePx
            typeface = Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )
            isAntiAlias = true
        }

        // Format system name (capitalize, replace underscores)
        val displayName = systemName
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }

        // Create StaticLayout for multi-line text support
        val staticLayout =
            if (SDK_INT >= Build.VERSION_CODES.M) {
                StaticLayout.Builder.obtain(
                    displayName,
                    0,
                    displayName.length,
                    textPaint,
                    maxWidth
                )
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setLineSpacing(8f, 1.0f) // Add some line spacing (8px extra)
                    .setIncludePad(true)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                (StaticLayout(
                    displayName,
                    textPaint,
                    maxWidth,
                    Layout.Alignment.ALIGN_CENTER,
                    1.0f,
                    8f,
                    true
                ))
            }

        // Calculate bitmap dimensions with generous padding
        val horizontalPadding = 100
        val verticalPadding = 60
        val width = staticLayout.width + (horizontalPadding * 2)
        val height = staticLayout.height + (verticalPadding * 2)

        // Create bitmap and draw text
        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)

        // Center the text layout on the canvas
        canvas.save()
        canvas.translate(
            horizontalPadding.toFloat(),
            verticalPadding.toFloat()
        )
        staticLayout.draw(canvas)
        canvas.restore()

        return BitmapDrawable(resources, bitmap)
    }


    private fun loadSystemImage() {
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

            val logsDir = File(mediaFileLocator.getLogsPath())
            val systemFile = File(logsDir, "esde_system_name.txt")
            if (!systemFile.exists()) return

            val systemName = systemFile.readText().trim()

            // Update state tracking
            updateState(AppState.SystemBrowsing(systemName))
            refreshWidgets()
        } catch (e: Exception) {

        }

    }

    private fun loadGameInfo() {
        // Don't reload images if game is currently playing - respect game launch behavior
        if (state is AppState.GamePlaying) {
            Log.d(
                "MainActivity",
                "loadGameInfo blocked - game is playing, maintaining game launch display"
            )
            return
        }

        try {

            Log.d("MainActivity", "Releasing music player ")
            releaseMusicPlayer(true)

            val logsDir = File(mediaFileLocator.getLogsPath())
            val gameFile = File(logsDir, "esde_game_filename.txt")
            if (!gameFile.exists()) return

            val gameNameRaw = gameFile.readText().trim()  // Full path from script
            val gameName = extractGameFilenameWithoutExtension(sanitizeGameFilename(gameNameRaw))

            // Read the display name from ES-DE if available
            val gameDisplayNameFile = File(logsDir, "esde_game_name.txt")
            val gameDisplayName = if (gameDisplayNameFile.exists()) {
                gameDisplayNameFile.readText().trim()
            } else {
                gameName  // Fallback to filename-based name
            }

            val systemFile = File(logsDir, "esde_game_system.txt")
            if (!systemFile.exists()) return
            val systemName = systemFile.readText().trim()

            // Update state tracking
            updateState(
                AppState.GameBrowsing(
                    systemName = systemName,
                    gameFilename = gameNameRaw,
                    gameName = gameDisplayName
                )
            )
            musicResults = emptyList()
            musicLoadRunnable = Runnable {
                loadGameMusic()
            }
            musicLoadHandler.postDelayed(musicLoadRunnable!!, 500)
            screenTransition({refreshWidgets()})
        } catch (e: Exception) {
            // Don't clear images on exception - keep last valid images
            Log.e("MainActivity", "Error loading game info", e)
        }
    }

    private fun loadGameMusic() {
        try {
            val logsDir = File(mediaFileLocator.getLogsPath())
            val gameFile = File(logsDir, "esde_game_filename.txt")
            if (!gameFile.exists()) return

            val gameNameRaw = gameFile.readText().trim()  // Full path from script
            val gameName = extractGameFilenameWithoutExtension(sanitizeGameFilename(gameNameRaw))

            // read the display name from ES-DE if available
            val gameDisplayNameFile = File(logsDir, "esde_game_name.txt")
            val gameDisplayName = if (gameDisplayNameFile.exists()) {
                gameDisplayNameFile.readText().trim()
            } else {
                gameName
            }

            val systemFile = File(logsDir, "esde_game_system.txt")
            if (!systemFile.exists()) return
            val systemName = systemFile.readText().trim()

            //ADDED FOR MUSIC
            musicSearchJob?.cancel()
            musicSearchJob = lifecycleScope.launch {
                Log.d("MainActivity", "about to go to music player ")
                if (gameName.isNotEmpty()) {
                    val found = musicPlayer.onGameFocused(gameDisplayName, gameName, systemName)
                    if(found) {
                        startMusicPlayer()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading game music", e)
        }
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
        val hiddenApps =
            prefs.getStringSet("hidden_apps", setOf())?.toMutableSet() ?: mutableSetOf()
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
        if (currentPosition == AppLaunchPreferences.POSITION_TOP) {
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
            val currentHiddenApps =
                prefs.getStringSet("hidden_apps", setOf())?.toMutableSet() ?: mutableSetOf()
            val currentlyHidden = currentHiddenApps.contains(packageName)

            if (currentlyHidden) {
                // Unhide - no confirmation
                currentHiddenApps.remove(packageName)
                prefs.edit().putStringSet("hidden_apps", currentHiddenApps).apply()
                dialog.dismiss()

                val mainIntent = Intent(Intent.ACTION_MAIN, null)
                mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
                val updatedHiddenApps = prefs.getStringSet("hidden_apps", setOf()) ?: setOf()
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
                        prefs.edit().putStringSet("hidden_apps", currentHiddenApps).apply()
                        dialog.dismiss()

                        val mainIntent = Intent(Intent.ACTION_MAIN, null)
                        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
                        val updatedHiddenApps =
                            prefs.getStringSet("hidden_apps", setOf()) ?: setOf()
                        allApps = packageManager.queryIntentActivities(
                            mainIntent,
                            PackageManager.MATCH_ALL
                        )
                            .filter {
                                !updatedHiddenApps.contains(
                                    it.activityInfo?.packageName ?: ""
                                )
                            }
                            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

                        (appRecyclerView.adapter as? AppAdapter)?.updateApps(allApps)
                        Toast.makeText(
                            this,
                            "\"$appName\" hidden from app drawer",
                            Toast.LENGTH_SHORT
                        ).show()
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
                    appLaunchPrefs.setLaunchPosition(packageName, AppLaunchPreferences.POSITION_TOP)
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
                    appLaunchPrefs.setLaunchPosition(
                        packageName,
                        AppLaunchPreferences.POSITION_BOTTOM
                    )
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
        val gameLaunchBehavior =
            prefs.getString("game_launch_behavior", "game_image") ?: "game_image"
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
        val logsDir = File(mediaFileLocator.getLogsPath())
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
            val playingState = state as AppState.GamePlaying
            updateState(
                AppState.GameBrowsing(
                    systemName = playingState.systemName,
                    gameFilename = playingState.gameFilename,
                    gameName = null  // Will be loaded by loadGameInfo if needed
                )
            )
            refreshWidgets()
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
        if (showGrid && widgetContainer.visibility == View.VISIBLE) {
            // Always recreate grid overlay to ensure it's properly attached
            if (gridOverlayView != null && gridOverlayView?.parent != null) {
                // Remove existing grid if it exists
                widgetContainer.removeView(gridOverlayView)
                gridOverlayView = null
            }

            // Create fresh grid overlay
            gridOverlayView = GridOverlayView(this, gridSize)
            val params = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            )
            widgetContainer.addView(gridOverlayView, 0)  // Add as first child (behind widgets)
            Log.d("MainActivity", "Grid overlay recreated and added")
        } else {
            // Remove grid overlay completely (but keep widget container visible)
            if (gridOverlayView != null) {
                widgetContainer.removeView(gridOverlayView)
                gridOverlayView = null
                Log.d("MainActivity", "Grid overlay removed")
            }
            // Don't hide the widget container - widgets should still be visible
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

        val screensaverBehavior =
            prefs.getString("screensaver_behavior", "game_image") ?: "game_image"
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
            val gameName = extractGameFilenameWithoutExtension(screensaverGame.gameFilename)

            when (screensaverBehavior) {
                "game_image" -> {
                    refreshWidgets()
                }

                "default_image" -> {
                    Log.d("MainActivity", "Processing default_image behavior")
                    //TODO: find a better solution for all of this repeated behaviour
                    refreshWidgets()
                }
            }
        } else {
            Log.w("MainActivity", "No screensaver game info available")
        }

        Log.d("MainActivity", "Screensaver game select complete")
        Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    private fun updateWidgetsForScreensaverGame() {
        Log.d("MainActivity", "═══ updateWidgetsForScreensaverGame START ═══")

        val systemName = if (state is AppState.Screensaver) {
            (state as AppState.Screensaver).currentGame?.systemName
        } else {
            null
        }
        val gameFilename = if (state is AppState.Screensaver) {
            (state as AppState.Screensaver).currentGame?.gameFilename
        } else {
            null
        }

        if (systemName != null && gameFilename != null) {
            // Load saved widgets and update with screensaver game images
            refreshWidgets()
        }
        // Make sure container is visible
        widgetContainer.visibility = View.VISIBLE
        Log.d("MainActivity", "═══ updateWidgetsForScreensaverGame END ═══")
    }

    // ========== VIDEO PLAYBACK FUNCTIONS ==========

    /**
     * Check if video is enabled in settings
     */
    private fun isVideoEnabled(): Boolean {
        return prefs.getBoolean("video_enabled", false)
    }

    /**
     * Update video volume based on system volume for the current display
     * This respects per-display volume controls on devices like Ayn Thor
     *
     * Ayn Thor uses:
     * - Standard STREAM_MUSIC volume for top screen (display 0)
     * - Settings.System "secondary_screen_volume_level" for bottom screen (display 1)
     */
    private fun updateVideoVolume() {
        /**val player: ExoPlayer = backgroundBinder.player

        val audioEnabled = prefs.getBoolean("video_audio_enabled", false)

        if (!audioEnabled) {
            // User has disabled video audio - mute completely
            player.volume = 0f
            android.util.Log.d("MainActivity", "Video audio disabled by user - volume: 0")
            return
        }
        player.volume = getSystemVolume()*/
    }

    private fun getSystemVolume(): Float {
        try {
            val currentDisplayId = getCurrentDisplayId()
            return getNormalizedAudioLevelForCurrentScreen()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error updating video volume", e)
            // Fallback to full volume if there's an error
            return 1f
        }
    }

    private fun updateMusicPlayerVolume() {
        try {
            //musicPlayer.setVolume(getNormalizedAudioLevelForCurrentScreen())
        } catch (e: Exception) {
            Log.e("MainActivity", "Error updating video volume", e)
            // Fallback to full volume if there's an error
            //backgroundBinder.player?.volume = 1f
        }
    }


    private fun getNormalizedAudioLevelForCurrentScreen(): Float {
        // Get the audio manager
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        // Determine which display we're on
        val currentDisplayId = getCurrentDisplayId()
        var currentVolume = 0
        var maxVolume = 0

        if (currentDisplayId == 0) {
            // Primary display (top screen) - use standard STREAM_MUSIC volume
            currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        } else {
            currentVolume = Settings.System.getInt(
                contentResolver,
                "secondary_screen_volume_level"
            )
            maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        }
        val normalizedVolume: Float = if (maxVolume > 0) {
            currentVolume.toFloat() / maxVolume.toFloat()
        } else {
            1f
        }
        return normalizedVolume
    }

    /**
     * Register listener for system volume changes
     * Listens for both standard volume and Ayn Thor's secondary screen volume
     */
    private fun registerVolumeListener() {
        volumeChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "android.media.VOLUME_CHANGED_ACTION" -> {
                        // Standard volume changed (top screen)
                        Log.d(
                            "MainActivity",
                            "Volume change detected - updating video volume"
                        )
                        updateVideoVolume()
                        updateMusicPlayerVolume()
                    }

                    Settings.ACTION_SOUND_SETTINGS -> {
                        // Sound settings changed (might include secondary screen volume)
                        Log.d(
                            "MainActivity",
                            "Sound settings changed - updating video volume"
                        )
                        updateVideoVolume()
                        updateMusicPlayerVolume()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction("android.media.VOLUME_CHANGED_ACTION")
            // Note: Settings.System changes don't broadcast reliably, so we also check in onResume
        }
        registerReceiver(volumeChangeReceiver, filter)
        Log.d("MainActivity", "Volume change listener registered")
    }

    /**
     * Unregister volume listener
     */
    private fun unregisterVolumeListener() {
        volumeChangeReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d("MainActivity", "Volume change listener unregistered")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error unregistering volume listener", e)
            }
        }
        volumeChangeReceiver = null
    }

    // Add this variable at the top of MainActivity class
    private var secondaryVolumeObserver: ContentObserver? = null

// Add this function near the volume functions
    /**
     * Register observer for secondary screen volume changes (Ayn Thor)
     */
    private fun registerSecondaryVolumeObserver() {
        try {
            secondaryVolumeObserver = object :
                ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    Log.d(
                        "MainActivity",
                        "Secondary screen volume changed - updating video volume"
                    )
                    updateVideoVolume()
                    updateMusicPlayerVolume()
                }
            }

            // Observe the secondary_screen_volume_level setting
            contentResolver.registerContentObserver(
                Settings.System.getUriFor("secondary_screen_volume_level"),
                false,
                secondaryVolumeObserver!!
            )

            Log.d("MainActivity", "Secondary volume observer registered")
        } catch (e: Exception) {
            Log.w(
                "MainActivity",
                "Could not register secondary volume observer (not an Ayn Thor?)",
                e
            )
        }
    }

    /**
     * Unregister secondary volume observer
     */
    private fun unregisterSecondaryVolumeObserver() {
        secondaryVolumeObserver?.let {
            try {
                contentResolver.unregisterContentObserver(it)
                Log.d("MainActivity", "Secondary volume observer unregistered")
            } catch (e: Exception) {
                Log.e(
                    "MainActivity",
                    "Error unregistering secondary volume observer",
                    e
                )
            }
        }
        secondaryVolumeObserver = null
    }

    private fun hideWidgets() {
        widgetViewBinder.setAllVisibility(widgetContainer, false)
    }

    private fun releaseMusicPlayer(transition: Boolean = true) {
        musicSearchJob?.cancel()
        musicLoadRunnable?.let { musicLoadHandler.removeCallbacks(it) }
        if(transition && musicPlayer.isPlaying()) {
            volumeFader.fadeTo(0f, 300, {musicPlayer.stopPlaying()})
        } else {
            musicPlayer.stopPlaying()
        }
    }

    private fun showContextMenu() {
        if(this::artRepository.isInitialized) {
            runOnUiThread {
                widgetMenuShowing = true
                menuState.showMenu = true
                AudioReferee.updateMenuState(true)
                musicPlayer.pause()
            }
        } else {
            Toast.makeText(
                this,
                "Please wait for the repositories to load (try again in 5 seconds)",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun hideContextMenu() {
        menuState.showMenu = false
        widgetMenuShowing = false
        menuState.widgetToEditState == null
        AudioReferee.updateMenuState(false)
        AudioReferee.forceUpdate()
    }

    private fun openWidgetSettings(widget: OverlayWidget) {
        runOnUiThread {
           // val menuView = findViewById<ComposeView>(R.id.menu_compose_view)
           // menuView.visibility = View.VISIBLE
            menuState.widgetToEditState = widget
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
                musicRepository.getAllPotentialResults(query, gameName, systemName)
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
            musicRepository.manualSelection(gameFilenameSanitized, systemName, selected.url, onProgress)
            val found = musicPlayer.onGameFocused(s.gameName ?: "", gameFilenameSanitized, systemName)
            if(found) {
                Toast.makeText(this@MainActivity, "Saved music!", Toast.LENGTH_SHORT).show()
                if(AudioReferee.currentPriority.value == AudioReferee.AudioSource.MUSIC) {
                    if(AudioReferee.getMenuState()) {
                        //musicPlayer.setVolume(0f)
                    } else {
                        musicPlayer.setVolume(musicPlayer.targetVolume)
                        musicPlayer.play()
                    }
                }
            }
        }
    }

    private fun toggleSnapToGrid() {
        snapToGrid = !snapToGrid

        // Update all active widgets with the new snap state
        widgetViewBinder.setAllSnapToGrid(widgetContainer, snapToGrid, gridSize)

        // Save snap state to preferences
        prefs.edit().putBoolean("snap_to_grid", snapToGrid).apply()
    }

    private fun toggleShowGrid() {
        showGrid = !showGrid
        updateGridOverlay()

        // Save show grid state to preferences
        prefs.edit().putBoolean("show_grid", showGrid).apply()

        Log.d("MainActivity", "Show grid toggled: $showGrid")
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
            // Locked (edit mode OFF) - videos can resume if other conditions allow
            Log.d("MainActivity", "Widget edit mode OFF - allowing videos")
            // Reload current state to potentially start videos
            if (state is AppState.SystemBrowsing) {
                loadSystemImage()
            } else if (state !is AppState.GamePlaying) {
                loadGameInfo()
            }
        } else {
            // Unlocked (edit mode ON) - stop videos and reload widgets
            Log.d(
                "MainActivity",
                "Widget edit mode ON - blocking videos and reloading widgets"
            )
            backgroundBinder.releasePlayer()

            // Reload widgets with current images so they're visible during editing
            refreshWidgets()
        }
    }

    companion object {
        const val COLUMN_COUNT_KEY = "column_count"
    }
}