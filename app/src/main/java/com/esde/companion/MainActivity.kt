package com.esde.companion

import android.app.Activity
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
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
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
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
import androidx.core.view.GestureDetectorCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.annotation.ExperimentalCoilApi
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.dispose
import coil.imageLoader
import coil.load
import coil.request.CachePolicy
import com.esde.companion.MediaFileHelper.extractGameFilenameWithoutExtension
import com.esde.companion.MediaFileHelper.sanitizeGameFilename
import com.esde.companion.OverlayWidget.WidgetContext
import com.esde.companion.animators.PanZoomAnimator
import com.esde.companion.art.ArtRepository
import com.esde.companion.art.SGDB.SGDBScraper
import com.esde.companion.ost.MusicDownloader
import com.esde.companion.ost.MusicPlayer
import com.esde.companion.ost.MusicRepository
import com.esde.companion.ost.loudness.AppDatabase
import com.esde.companion.ost.loudness.LoudnessService
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
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
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
    private lateinit var gameImageView: ImageView
    private lateinit var dimmingOverlay: View
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
    private lateinit var widgetManager: WidgetManager
    private lateinit var widgetResourceResolver: WidgetResourceResolver
    private lateinit var widgetViewBinder: WidgetViewBinder
    private var gridOverlayView: GridOverlayView? = null
    private var widgetsLocked by mutableStateOf(false)
    private var snapToGrid by mutableStateOf(false)
    private val gridSize = 40f
    private var showGrid by mutableStateOf(false)
    private var isInteractingWithWidget = false

    private var longPressHandler: Handler? = null
    private var longPressRunnable: Runnable? = null
    private var longPressTriggered = false
    private var touchDownX = 0f
    private var touchDownY = 0f
    private val LONG_PRESS_TIMEOUT by lazy {
        ViewConfiguration.getLongPressTimeout().toLong()
    }
    private var widgetMenuShowing = false
    private var widgetMenuDialog: android.app.AlertDialog? = null

    // This tracks state alongside existing booleans during migration
    private var state: AppState = AppState.SystemBrowsing("")
        set(value) {
            val oldState = field
            field = value

            // Log state changes for debugging
            android.util.Log.d("MainActivity", "━━━ STATE CHANGE ━━━")
            android.util.Log.d("MainActivity", "FROM: $oldState")
            android.util.Log.d("MainActivity", "TO:   $value")
            android.util.Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━")

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
    private var player: ExoPlayer? = null
    private lateinit var videoView: PlayerView
    private var videoDelayHandler: Handler? = null
    private var videoDelayRunnable: Runnable? = null
    private var currentVideoPath: String? = null
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
    private val imageLoadHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var imageLoadRunnable: Runnable? = null
    private var musicLoadHandler = android.os.Handler(android.os.Looper.getMainLooper())
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
        if (result.resultCode == Activity.RESULT_OK) {
            val showWidgetTutorial =
                result.data?.getBooleanExtra("SHOW_WIDGET_TUTORIAL", false) ?: false
            if (showWidgetTutorial) {
                // Delay slightly to let UI settle after settings closes
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
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
                applyScreensaverBehaviorChange()
                // Skip reload in onResume to prevent override
                skipNextReload = true
            } else if (imagePreferenceChanged) {
                // Image preference changed - reload appropriate view
                if (state is AppState.GamePlaying) {
                    // Game is playing - update game launch display
                    android.util.Log.d(
                        "MainActivity",
                        "Image preference changed during gameplay - reloading display"
                    )
                    handleGameStart()
                    skipNextReload = true
                } else if (state is AppState.SystemBrowsing) {
                    // In system view - reload system image with new preference
                    android.util.Log.d(
                        "MainActivity",
                        "Image preference changed in system view - reloading system image"
                    )
                    loadSystemImage()
                    skipNextReload = true
                } else {
                    // In game browsing view - reload game image with new preference
                    android.util.Log.d(
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

    private fun updateDimmingOverlay() {
        val dimmingPercent = prefs.getInt("dimming", 25)

        // Convert percentage (0-100) to hex alpha (00-FF)
        val alpha = (dimmingPercent * 255 / 100).coerceIn(0, 255)
        val hexAlpha = String.format("%02x", alpha)
        val colorString = "#${hexAlpha}000000"

        val color = android.graphics.Color.parseColor(colorString)
        dimmingOverlay.setBackgroundColor(color)

        // Force the view to redraw
        dimmingOverlay.invalidate()
        dimmingOverlay.requestLayout()
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
        val steamGrid = SGDBScraper(apiKey = "")
        artRepository = ArtRepository(steamGrid)

        musicRepository = MusicRepository(MusicDownloader(), LoudnessService(loudnessDao))
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val musicExoPlayer = ExoPlayer.Builder(this).build()
        musicExoPlayer.setAudioAttributes(
            audioAttributes,
            true
        ) // true = handle audio focus automatically

        musicPlayer = MusicPlayer(musicRepository, musicExoPlayer)


        prefs = getSharedPreferences("ESDESecondScreenPrefs", MODE_PRIVATE)
        appLaunchPrefs = AppLaunchPreferences(this)
        mediaFileLocator = MediaFileLocator(prefs)

        // ========== MUSIC INTEGRATION START ==========

        // ========== MUSIC INTEGRATION END ==========

        // Check if we should show widget tutorial for updating users
        checkAndShowWidgetTutorialForUpdate()

        rootLayout = findViewById(R.id.rootLayout)
        gameImageView = findViewById(R.id.gameImageView)
        dimmingOverlay = findViewById(R.id.dimmingOverlay)
        appDrawer = findViewById(R.id.appDrawer)
        appRecyclerView = findViewById(R.id.appRecyclerView)
        appSearchBar = findViewById(R.id.appSearchBar)
        searchClearButton = findViewById(R.id.searchClearButton)
        drawerBackButton = findViewById(R.id.drawerBackButton)
        settingsButton = findViewById(R.id.settingsButton)
        androidSettingsButton = findViewById(R.id.androidSettingsButton)
        videoView = findViewById(R.id.videoView)
        blackOverlay = findViewById(R.id.blackOverlay)
        // ========== MUSIC INTEGRATION START ==========

        // ========== MUSIC INTEGRATION END ==========


        // Load lock state
        widgetsLocked = prefs.getBoolean("widgets_locked", true)
        // Load snap to grid state
        snapToGrid = prefs.getBoolean("snap_to_grid", true)
        // Load show grid state
        showGrid = prefs.getBoolean("show_grid", false)

        // Initialize widget system
        widgetContainer = findViewById(R.id.widgetContainer)
        widgetManager = WidgetManager(this)
        widgetResourceResolver = WidgetResourceResolver(mediaFileLocator, prefs)
        widgetViewBinder = WidgetViewBinder()

        // Create default widgets on first launch
        createDefaultWidgets()

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
        widgetManager.load()

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
                                }
                            ),
                            artRepository = artRepository,
                            musicResults = musicResults,
                            isSearchingMusic = isSearchingMusic,
                            onMusicSearch = {query -> performMusicSearch(query) },
                            onMusicSelect = {selected -> onMusicResultSelected(selected)},
                            onSave = {url, contentType, slot -> onScraperContentSave(url, contentType, slot) }
                        )
                    }

                    if(menuState.widgetToEditState != null) {
                        WidgetSettingsOverlay(
                            widget = menuState.widgetToEditState!!,
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

        val logsDir = File(getLogsPath())
        android.util.Log.d("MainActivity", "Logs directory: ${logsDir.absolutePath}")
        android.util.Log.d("MainActivity", "Logs directory exists: ${logsDir.exists()}")

        val systemScrollFile = File(logsDir, "esde_system_name.txt")
        val gameScrollFile = File(logsDir, "esde_game_filename.txt")

        android.util.Log.d("MainActivity", "System scroll file: ${systemScrollFile.absolutePath}")
        android.util.Log.d(
            "MainActivity",
            "System scroll file exists: ${systemScrollFile.exists()}"
        )
        android.util.Log.d("MainActivity", "Game scroll file: ${gameScrollFile.absolutePath}")
        android.util.Log.d("MainActivity", "Game scroll file exists: ${gameScrollFile.exists()}")

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

        updateDimmingOverlay()

        startFileMonitoring()
        setupBackHandling()

        // Apply blur effect if set
        updateBlurEffect()

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
    }


     @OptIn(ExperimentalCoilApi::class)
     fun onScraperContentSave(
        url: String,
        contentType: OverlayWidget.ContentType,
        slot: Int
    ) {
         lifecycleScope.launch(Dispatchers.IO) {
             try {
                 val snapshot = imageLoader.diskCache?.openSnapshot(url)
                 snapshot?.use {
                     val s = state as AppState.GameBrowsing
                     val fileName = MediaFileHelper.extractGameFilenameWithoutExtension(s.gameFilename)
                     val mediaSlot = OverlayWidget.MediaSlot.fromInt(slot)
                     val existingFile = mediaFileLocator.findMediaFile(
                         contentType,
                         s.systemName,
                         s.gameFilename,
                         mediaSlot
                     )
                     val dir = mediaFileLocator.getDir(
                             s.systemName,
                             mediaFileLocator.getFolderName(contentType),
                             mediaSlot
                     )
                     val extension = MimeTypeMap.getFileExtensionFromUrl(url).ifEmpty { "png" }
                     val newFile = File(dir, "${fileName}${mediaSlot.suffix}.$extension")

                     if (existingFile != null && existingFile.exists() && existingFile.absolutePath != newFile.absolutePath) {
                         existingFile.delete()
                     }
                     it.data.toFile().copyTo(newFile, overwrite = true)

                     withContext(Dispatchers.Main) {
                         Toast.makeText(this@MainActivity, "Saved to Alt $slot", Toast.LENGTH_SHORT).show()
                         hideContextMenu()
                         refreshWidgets()
                     }
                 }
             } catch (e: Exception) {
                 e.printStackTrace()
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
        android.util.Log.d("MainActivity", "=== checkAndShowWidgetTutorialForUpdate CALLED ===")
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val currentVersion = packageInfo.versionName ?: "0.4.3"
            android.util.Log.d("MainActivity", "Current version from package: $currentVersion")

            val lastSeenVersion = prefs.getString("last_seen_app_version", "0.0.0") ?: "0.0.0"
            android.util.Log.d("MainActivity", "Last seen version from prefs: $lastSeenVersion")

            val hasSeenWidgetTutorial = prefs.getBoolean("widget_tutorial_shown", false)
            android.util.Log.d("MainActivity", "Has seen widget tutorial: $hasSeenWidgetTutorial")

            // Check if default widgets were created (indicates not a fresh install)
            val hasCreatedDefaultWidgets = prefs.getBoolean("default_widgets_created", false)
            android.util.Log.d(
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

            android.util.Log.d("MainActivity", "Should show tutorial: $shouldShowTutorial")
            android.util.Log.d("MainActivity", "  - hasSeenWidgetTutorial: $hasSeenWidgetTutorial")
            android.util.Log.d("MainActivity", "  - isOlderVersion: $isOlderVersion")
            android.util.Log.d(
                "MainActivity",
                "  - hasCreatedDefaultWidgets: $hasCreatedDefaultWidgets"
            )

            if (shouldShowTutorial) {
                android.util.Log.d("MainActivity", "✓ Showing widget tutorial")
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    showWidgetSystemTutorial(fromUpdate = true)
                }, 3000)
            }

            // Always update the version tracking
            prefs.edit().putString("last_seen_app_version", currentVersion).apply()
            android.util.Log.d("MainActivity", "Saved current version to prefs: $currentVersion")

        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error in version check for widget tutorial", e)
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
            android.util.Log.e("MainActivity", "Error comparing versions: $v1 vs $v2", e)
            return false
        }
    }

    private fun checkAndLaunchSetupWizard() {
        // Check if setup has been completed
        val hasCompletedSetup = prefs.getBoolean("setup_completed", false)

        // Check if permissions are granted
        val hasPermission = when {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R ->
                Environment.isExternalStorageManager()

            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M -> {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }

            else -> true
        }

        // Launch setup wizard immediately if:
        // 1. Setup not completed, OR
        // 2. Missing permissions
        if (!hasCompletedSetup || !hasPermission) {
            android.util.Log.d(
                "MainActivity",
                "Setup incomplete or missing permissions - launching wizard immediately"
            )
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
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
        val titleContainer = android.widget.LinearLayout(this)
        titleContainer.orientation = android.widget.LinearLayout.HORIZONTAL
        titleContainer.setPadding(60, 40, 60, 20)
        titleContainer.gravity = android.view.Gravity.CENTER

        val titleText = android.widget.TextView(this)
        titleText.text = if (fromUpdate) "🆕 Widget Overlay System" else "📐 Widget Overlay System"
        titleText.textSize = 24f
        titleText.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
        titleText.gravity = android.view.Gravity.CENTER

        titleContainer.addView(titleText)

        // Create scrollable message view
        val scrollView = android.widget.ScrollView(this)
        val messageText = android.widget.TextView(this)

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
        messageText.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
        messageText.setPadding(60, 20, 60, 20)

        scrollView.addView(messageText)

        // Create "don't show again" checkbox
        val checkboxContainer = android.widget.LinearLayout(this)
        checkboxContainer.orientation = android.widget.LinearLayout.HORIZONTAL
        checkboxContainer.setPadding(60, 10, 60, 20)
        checkboxContainer.gravity = android.view.Gravity.CENTER_VERTICAL

        val checkbox = android.widget.CheckBox(this)
        checkbox.text = "Don't show this automatically again"
        checkbox.setTextColor(android.graphics.Color.parseColor("#999999"))
        checkbox.textSize = 14f

        checkboxContainer.addView(checkbox)

        // Create main container
        val mainContainer = android.widget.LinearLayout(this)
        mainContainer.orientation = android.widget.LinearLayout.VERTICAL
        mainContainer.addView(scrollView)
        mainContainer.addView(checkboxContainer)

        // Show dialog
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
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
                android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#1A1A1A"))
            )
        }

        dialog.show()

        android.util.Log.d("MainActivity", "Widget tutorial dialog shown (fromUpdate: $fromUpdate)")
    }

    /**
     * Check for scripts with retry logic to handle SD card mounting delays
     */
    private fun checkScriptsWithRetry(attempt: Int = 0, maxAttempts: Int = 5) {
        val scriptsPath = prefs.getString("scripts_path", null)

        // If no custom scripts path is set, scripts are likely on internal storage
        // Check immediately without retry
        if (scriptsPath == null || scriptsPath.startsWith("/storage/emulated/0")) {
            android.util.Log.d("MainActivity", "Scripts on internal storage - checking immediately")
            val hasCorrectScripts = scriptManager.checkScriptValidity(scriptsPath)
            if (!hasCorrectScripts) {
                android.util.Log.d(
                    "MainActivity",
                    "Scripts missing/outdated on internal storage - showing dialog"
                )

                // Check if scripts exist at all (missing vs outdated)
                val scriptsDir = File(scriptsPath ?: "/storage/emulated/0/ES-DE/scripts")
                val gameSelectScript = File(scriptsDir, "game-select/esdecompanion-game-select.sh")

                if (gameSelectScript.exists()) {
                    // Scripts exist but are outdated - show update dialog
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
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

            android.util.Log.d(
                "MainActivity",
                "Scripts path not accessible (attempt ${attempt + 1}/$maxAttempts) - waiting ${delayMs}ms for SD card mount: $scriptsPath"
            )

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                checkScriptsWithRetry(attempt + 1, maxAttempts)
            }, delayMs)
            return
        }

        // Either accessible now or max attempts reached - check scripts
        val hasCorrectScripts = scriptManager.checkScriptValidity(scriptsPath)

        if (!hasCorrectScripts) {
            if (isAccessible) {
                // Path is accessible but scripts are missing/invalid
                android.util.Log.d("MainActivity", "Scripts missing/outdated on accessible path")

                // Check if scripts exist at all (missing vs outdated)
                val gameSelectScript = File(scriptsDir, "game-select/esdecompanion-game-select.sh")

                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
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
                android.util.Log.w(
                    "MainActivity",
                    "Scripts path not accessible after $maxAttempts attempts: $scriptsPath"
                )
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    showSdCardNotMountedDialog(scriptsPath)
                }, 1000)
            }
        } else {
            android.util.Log.d("MainActivity", "Scripts found and valid - no wizard needed")
        }
    }

    /**
     * Launch setup wizard specifically for script issues
     */
    private fun launchSetupWizardForScripts() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
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

    private fun createDefaultWidgets() {
        // Check if we've already created default widgets
        val hasCreatedDefaults = prefs.getBoolean("default_widgets_created", false)
        if (hasCreatedDefaults) {
            android.util.Log.d("MainActivity", "Default widgets already created on previous launch")
            return
        }
        android.util.Log.d("MainActivity", "First launch - creating default widgets")

        widgetManager.createDefaultWidgets(resources.displayMetrics)

        // Mark that we've created default widgets
        prefs.edit().putBoolean("default_widgets_created", true).apply()
        android.util.Log.d("MainActivity", "Created default widgets")
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
            android.util.Log.e("MainActivity", "Error updating scripts", e)
        }
    }

    override fun onPause() {
        super.onPause()
        // Cancel any pending video delay timers (prevent video loading while in settings)
        videoDelayRunnable?.let { videoDelayHandler?.removeCallbacks(it) }
        musicLoadRunnable?.let { musicLoadHandler?.removeCallbacks { it } }
        // Stop and release video player when app goes to background
        // This fixes video playback issues on devices with identical display names (e.g., Ayaneo Pocket DS)
        releasePlayer()
        releaseMusicPlayer()
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
            android.util.Log.d("MainActivity", "Skipping reload - no settings changed")
        } else {
            // Don't reload if game is playing or screensaver is active
            // This prevents unnecessary video loading during these states
            if (state is AppState.GamePlaying) {
                android.util.Log.d("MainActivity", "Skipping reload - game playing")
            } else if (state is AppState.Screensaver) {
                android.util.Log.d("MainActivity", "Skipping reload - screensaver active")
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
        android.util.Log.d(
            "MainActivity",
            "Activity VISIBLE (onStart) - videos allowed if other conditions met"
        )

        // ========== MUSIC ==========
        // ===========================
    }

    override fun onStop() {
        super.onStop()
        isActivityVisible = false
        android.util.Log.d("MainActivity", "Activity NOT VISIBLE (onStop) - blocking videos")
        releasePlayer()

        // ========== MUSIC ==========
        // ===========================
    }

    private fun updateBlurEffect() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val blurRadius = prefs.getInt("blur", 0)

            if (blurRadius > 0) {
                val blurEffect = android.graphics.RenderEffect.createBlurEffect(
                    blurRadius.toFloat(),
                    blurRadius.toFloat(),
                    android.graphics.Shader.TileMode.CLAMP
                )
                gameImageView.setRenderEffect(blurEffect)
            } else {
                gameImageView.setRenderEffect(null)
            }
        }
    }

    private fun updateDrawerTransparency() {
        val transparencyPercent = prefs.getInt("drawer_transparency", 70)
        // Convert percentage (0-100) to hex alpha (00-FF)
        val alpha = (transparencyPercent * 255 / 100).coerceIn(0, 255)
        val hexAlpha = String.format("%02x", alpha)
        val colorString = "#${hexAlpha}000000"

        val color = android.graphics.Color.parseColor(colorString)
        appDrawer.setBackgroundColor(color)
    }

    private fun getMediaBasePath(): String {
        val customPath = prefs.getString("media_path", null)
        val path =
            customPath ?: "${Environment.getExternalStorageDirectory()}/ES-DE/downloaded_media"
        android.util.Log.d("ESDESecondScreen", "Media base path: $path")
        return path
    }

    private fun getSystemImagePath(): String {
        val customPath = prefs.getString("system_path", null)
        val path = customPath
            ?: "${Environment.getExternalStorageDirectory()}/ES-DE Companion/system_images"
        android.util.Log.d("ESDESecondScreen", "System image path: $path")
        return path
    }

    private fun getSystemLogosPath(): String {
        val customPath = prefs.getString("system_logos_path", null)
        val path = customPath
            ?: "${Environment.getExternalStorageDirectory()}/ES-DE Companion/system_logos"
        android.util.Log.d("ESDESecondScreen", "System logos path: $path")
        return path
    }

    private fun getLogsPath(): String {
        // Always use fixed internal storage location for logs
        // This ensures FileObserver works reliably (doesn't work well on SD card)
        val path = "/storage/emulated/0/ES-DE Companion/logs"
        android.util.Log.d("MainActivity", "Logs path: $path")
        return path
    }

    private fun loadFallbackBackground(forceCustomImageOnly: Boolean = false) {
        android.util.Log.d(
            "MainActivity",
            "═══ loadFallbackBackground CALLED (forceCustomImageOnly=$forceCustomImageOnly) ═══"
        )

        // CRITICAL: Only check solid color preference if NOT forcing custom image only
        // When forceCustomImageOnly=true (screensaver/game launch "default_image" behavior),
        // we skip the solid color check and go straight to custom background image
        if (!forceCustomImageOnly) {
            val gameImagePref = prefs.getString("game_image_preference", "fanart") ?: "fanart"
            if (gameImagePref == "solid_color") {
                val solidColor = prefs.getInt(
                    "game_background_color",
                    android.graphics.Color.parseColor("#1A1A1A")
                )
                android.util.Log.d(
                    "MainActivity",
                    "Game view solid color selected - using color: ${
                        String.format(
                            "#%06X",
                            0xFFFFFF and solidColor
                        )
                    }"
                )
                val drawable = android.graphics.drawable.ColorDrawable(solidColor)
                gameImageView.setImageDrawable(drawable)
                gameImageView.visibility = View.VISIBLE
                return
            }
        }

        // Check if user has set a custom background
        val customBackgroundPath = prefs.getString("custom_background_uri", null)
        android.util.Log.d("MainActivity", "Custom background path: $customBackgroundPath")

        if (customBackgroundPath != null) {
            try {
                val file = File(customBackgroundPath)
                android.util.Log.d(
                    "MainActivity",
                    "File exists: ${file.exists()}, canRead: ${file.canRead()}"
                )

                if (file.exists() && file.canRead()) {
                    // Use loadImageWithAnimation for consistent behavior
                    loadImageWithAnimation(file, gameImageView) {
                        android.util.Log.d(
                            "MainActivity",
                            "✓ Loaded custom background successfully"
                        )
                    }
                    android.util.Log.d(
                        "MainActivity",
                        "Loading custom background from: $customBackgroundPath"
                    )
                    return
                } else {
                    android.util.Log.w(
                        "MainActivity",
                        "Custom background file not accessible: $customBackgroundPath"
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w(
                    "MainActivity",
                    "Error loading custom background, using built-in default",
                    e
                )
            }
        }

        // No custom background or loading failed - use built-in default
        android.util.Log.d("MainActivity", "Loading built-in fallback background")
        loadBuiltInFallbackBackground()
    }

    private fun loadBuiltInFallbackBackground() {
        try {
            val assetPath = "fallback/default_background.webp"
            // Copy asset to cache for loadImageWithAnimation
            val fallbackFile = File(cacheDir, "default_background.webp")
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
            gameImageView.setBackgroundColor(android.graphics.Color.parseColor("#1A1A1A"))
            gameImageView.setImageDrawable(null)
        }
    }

    private fun loadImageWithAnimation(
        imageFile: File,
        targetView: ImageView,
        onComplete: (() -> Unit)? = null
    ) {
        val animationStyle = prefs.getString("animation_style", "scale_fade") ?: "scale_fade"

        val duration = if (animationStyle == "custom") {
            prefs.getInt("animation_duration", 250)
        } else 250

        val scaleAmount = if (animationStyle == "custom") {
            prefs.getInt("animation_scale", 95) / 100f
        } else 0.95f

        PanZoomAnimator.stopPanZoom(targetView)

        targetView.load(imageFile) {
            val sig = getFileSignature(imageFile)
            memoryCacheKey("${imageFile.absolutePath}_$sig")
            diskCacheKey("${imageFile.absolutePath}_$sig")

            // Replace .diskCacheStrategy(ALL)
            diskCachePolicy(CachePolicy.ENABLED)
            networkCachePolicy(CachePolicy.ENABLED)

            // Handle Crossfade (only if not 'none')
            if (animationStyle != "none") {
                crossfade(duration)
            }

            listener(
                onSuccess = { _, _ ->
                    handleImageReady(targetView, animationStyle, scaleAmount, duration, onComplete)
                },
                onError = { _, _ ->
                    onComplete?.invoke()
                }
            )
        }
    }

    private fun handleImageReady(
        targetView: ImageView,
        animationStyle: String,
        scaleAmount: Float,
        duration: Int,
        onComplete: (() -> Unit)?
    ) {
        PanZoomAnimator.stopPanZoom(targetView)
        targetView.setTag(R.id.tag_base_scale_applied, false)
        PanZoomAnimator.applyBaseScaleOnce(targetView)

        targetView.post {
            val baseScale = targetView.getTag(R.id.tag_base_scale) as? Float ?: return@post

            when (animationStyle) {
                "scale_fade", "custom" -> {
                    val startScale = baseScale * scaleAmount
                    targetView.scaleX = startScale
                    targetView.scaleY = startScale

                    targetView.animate()
                        .scaleX(baseScale)
                        .scaleY(baseScale)
                        .setDuration(duration.toLong())
                        .setInterpolator(DecelerateInterpolator())
                        .withEndAction {
                            PanZoomAnimator.startAnimation(targetView)
                            onComplete?.invoke()
                        }
                        .start()
                }

                else -> {
                    PanZoomAnimator.startAnimation(targetView)
                    onComplete?.invoke()
                }
            }
        }
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
                    if (!widgetMenuShowing) {
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
        android.util.Log.d("MainActivity", "Showing black overlay")
        isBlackOverlayShown = true

        // Stop video immediately
        releasePlayer()
        releaseMusicPlayer()

        // Show overlay instantly without animation
        blackOverlay.visibility = View.VISIBLE
        blackOverlay.translationY = 0f
    }

    /**
     * Hide the black overlay instantly (no animation)
     */
    private fun hideBlackOverlay() {
        android.util.Log.d("MainActivity", "Hiding black overlay")
        isBlackOverlayShown = false

        // Hide overlay instantly without animation
        blackOverlay.visibility = View.GONE

        val displayHeight = resources.displayMetrics.heightPixels.toFloat()
        blackOverlay.translationY = -displayHeight

        // Reload video if applicable (don't reload images)
        when (val s = state) {
            is AppState.GameBrowsing -> {
                // In GameBrowsing, we ALWAYS have systemName and gameFilename (non-null)
                handleVideoForGame(s.systemName, s.gameFilename)
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
                        longPressHandler = Handler(android.os.Looper.getMainLooper())
                    }
                    longPressRunnable = Runnable {
                        if (!longPressTriggered && !widgetMenuShowing) {
                            longPressTriggered = true
                            widgetMenuShowing = true
                            showContextMenu()
                        }
                    }
                    longPressHandler?.postDelayed(longPressRunnable!!, LONG_PRESS_TIMEOUT)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                // Cancel long press if finger moves beyond touch slop threshold
                val deltaX = kotlin.math.abs(ev.x - touchDownX)
                val deltaY = kotlin.math.abs(ev.y - touchDownY)
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

            android.util.Log.d(
                "MainActivity",
                "Double-tap detection: timeSinceLastTap=${timeSinceLastTap}ms, tapCount=$tapCount"
            )

            // Reset tap count if too much time has passed
            if (timeSinceLastTap > DOUBLE_TAP_TIMEOUT) {
                android.util.Log.d(
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

                android.util.Log.d("MainActivity", "Tap registered - new tapCount=$tapCount")

                // Check for double-tap
                if (tapCount >= 2) {
                    android.util.Log.d(
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
                android.util.Log.d(
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
            android.util.Log.d(
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

        android.util.Log.d(
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
            android.util.Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            android.util.Log.d("MainActivity", "SETTINGS BUTTON CLICKED")
            android.util.Log.d("MainActivity", "Companion currently on display: $currentDisplay")

            // Also log all available displays
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                try {
                    val displayManager =
                        getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
                    val displays = displayManager.displays
                    android.util.Log.d("MainActivity", "All available displays:")
                    displays.forEachIndexed { index, display ->
                        android.util.Log.d(
                            "MainActivity",
                            "  Display $index: ID=${display.displayId}, Name='${display.name}'"
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error listing displays", e)
                }
            }

            android.util.Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

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
            val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
            startActivity(intent)
        }
    }

    private fun startFileMonitoring() {
        val watchDir = File(getLogsPath())
        android.util.Log.d("MainActivity", "Starting file monitoring on: ${watchDir.absolutePath}")
        android.util.Log.d("MainActivity", "Watch directory exists: ${watchDir.exists()}")

        // Create logs directory if it doesn't exist
        if (!watchDir.exists()) {
            watchDir.mkdirs()
            android.util.Log.d("MainActivity", "Created logs directory")
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
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
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
                                        android.util.Log.d(
                                            "MainActivity",
                                            "Game scroll ignored - launching from screensaver"
                                        )
                                        return@postDelayed
                                    }

                                    // Ignore if screensaver is active
                                    if (state is AppState.Screensaver) {
                                        android.util.Log.d(
                                            "MainActivity",
                                            "System scroll ignored - screensaver active"
                                        )
                                        return@postDelayed
                                    }
                                    android.util.Log.d("MainActivity", "System scroll detected")
                                    loadSystemImageDebounced()
                                }

                                "esde_game_filename.txt" -> {
                                    // Ignore if launching from screensaver (game-select event between screensaver-end and game-start)
                                    if (isLaunchingFromScreensaver) {
                                        android.util.Log.d(
                                            "MainActivity",
                                            "Game scroll ignored - launching from screensaver"
                                        )
                                        return@postDelayed
                                    }

                                    // Ignore if screensaver is active
                                    if (state is AppState.Screensaver) {
                                        android.util.Log.d(
                                            "MainActivity",
                                            "Game scroll ignored - screensaver active"
                                        )
                                        return@postDelayed
                                    }

                                    // ADDED: Ignore game-select events that happen shortly after game-start or game-end
                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - lastGameStartTime < GAME_EVENT_DEBOUNCE) {
                                        android.util.Log.d(
                                            "MainActivity",
                                            "Game scroll ignored - too soon after game start"
                                        )
                                        return@postDelayed
                                    }
                                    if (currentTime - lastGameEndTime < GAME_EVENT_DEBOUNCE) {
                                        android.util.Log.d(
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
                                            android.util.Log.d(
                                                "MainActivity",
                                                "Game scroll ignored - same as playing game: $gameFilename"
                                            )
                                            return@postDelayed
                                        }
                                    }

                                    android.util.Log.d("MainActivity", "Game scroll detected")
                                    loadGameInfoDebounced()
                                }

                                "esde_gamestart_filename.txt" -> {
                                    android.util.Log.d("MainActivity", "Game start detected")
                                    handleGameStart()
                                }

                                "esde_gameend_filename.txt" -> {
                                    android.util.Log.d("MainActivity", "Game end detected")
                                    handleGameEnd()
                                }

                                "esde_screensaver_start.txt" -> {
                                    android.util.Log.d("MainActivity", "Screensaver start detected")
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

                                    android.util.Log.d(
                                        "MainActivity",
                                        "Screensaver end detected: $endReason"
                                    )
                                    handleScreensaverEnd(endReason)
                                }

                                "esde_screensavergameselect_filename.txt" -> {
                                    // DEFENSIVE FIX: Auto-initialize screensaver state if screensaver-start event was missed
                                    if (state !is AppState.Screensaver) {
                                        android.util.Log.w(
                                            "MainActivity",
                                            "⚠️ FALLBACK: Screensaver game-select fired without screensaver-start event!"
                                        )
                                        android.util.Log.w(
                                            "MainActivity",
                                            "Auto-initializing screensaver state as defensive fallback"
                                        )
                                        android.util.Log.d(
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
                                                android.util.Log.w(
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

                                        android.util.Log.d(
                                            "MainActivity",
                                            "Saved state for screensaver: $savedState"
                                        )

                                        // Apply screensaver behavior preferences
                                        val screensaverBehavior =
                                            prefs.getString("screensaver_behavior", "game_image")
                                                ?: "game_image"
                                        android.util.Log.d(
                                            "MainActivity",
                                            "Applying screensaver behavior: $screensaverBehavior"
                                        )

                                        // Handle black screen preference
                                        if (screensaverBehavior == "black_screen") {
                                            android.util.Log.d(
                                                "MainActivity",
                                                "Black screen behavior - clearing display"
                                            )
                                            gameImageView.dispose()
                                            gameImageView.setImageDrawable(null)
                                            gameImageView.visibility = View.GONE
                                            videoView.visibility = View.GONE
                                            releasePlayer()
                                            gridOverlayView?.visibility = View.GONE
                                        }

                                        // Clear widgets (will be loaded by handleScreensaverGameSelect)
                                        //widgetContainer.removeAllViews()
                                        //activeWidgets.clear()
                                        android.util.Log.d(
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

                                    android.util.Log.d(
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
        android.util.Log.d("MainActivity", "FileObserver started")
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
        android.util.Log.d("MainActivity", "Started script verification (15s timeout)")
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
                android.util.Log.d(
                    "MainActivity",
                    "Showing widget tutorial after setup verification"
                )
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
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
            val titleContainer = android.widget.LinearLayout(this)
            titleContainer.orientation = android.widget.LinearLayout.HORIZONTAL
            titleContainer.setPadding(60, 40, 20, 20)
            titleContainer.gravity = android.view.Gravity.CENTER_VERTICAL

            val titleText = android.widget.TextView(this)
            titleText.text = "⚠️ No Data Received"
            titleText.textSize = 20f
            titleText.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
            titleText.layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )

            val closeButton = android.widget.ImageButton(this)
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
        releasePlayer()
        releaseMusicPlayer()
        videoDelayHandler = null

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
            android.util.Log.d("MainActivity", "Window focus gained")
        } else {
            android.util.Log.d("MainActivity", "Window focus lost (ignoring for video blocking)")
            // Stop videos when we lose focus (game launched on same screen)
            releasePlayer()
            releaseMusicPlayer()
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

    private fun refreshWidgets(pageSwap: Boolean = false) {
        var context: OverlayWidget.WidgetContext = WidgetContext.GAME
        if (state.isInSystemBrowsingMode()) {
            context = WidgetContext.SYSTEM
        }

        refreshWidgets(context, pageSwap)
    }

    private fun refreshWidgets(
        currentViewContext: OverlayWidget.WidgetContext,
        pageSwap: Boolean = false
    ) {
        val widgets = widgetManager.getWidgetsForCurrentPage(currentViewContext)
        val resolved = widgetResourceResolver.resolve(
            widgets,
            state.getCurrentSystemName(),
            state.getCurrentGameFilename(),
            resources.displayMetrics
        )

        widgetViewBinder.sync(
            container = widgetContainer,
            dataList = resolved,
            widgetsLocked,
            snapToGrid,
            gridSize,
            pageSwap,
            onUpdate = ::onWidgetUpdated,
            onEditRequested = ::openWidgetSettings
        )
    }

    private fun onWidgetUpdated(widget: OverlayWidget) {
        widgetManager.updateWidget(widget, resources.displayMetrics)
        refreshWidgets()
    }

    private fun onWidgetDeleted(widget: OverlayWidget) {
        widgetManager.deleteWidget(widget.id)
        refreshWidgets()
    }

    private fun onWidgetReordered(widget: OverlayWidget, forward: Boolean) {
        widgetManager.moveWidgetZOrder(widget.id, forward)
        refreshWidgets()
    }

    fun addNewWidget(type: OverlayWidget.ContentType) {
        widgetManager.addNewWidgetToCurrentPage(
            type,
            state.toWidgetContext(),
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

        if (next) widgetManager.goToNextPage() else widgetManager.goToPreviousPage()
        refreshWidgets(true)
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
    ): android.graphics.drawable.Drawable? {
        return try {
            // Handle ES-DE auto-collections
            val baseFileName = when (systemName.lowercase()) {
                "allgames" -> "auto-allgames"
                "favorites" -> "auto-favorites"
                "lastplayed" -> "auto-lastplayed"
                else -> systemName.lowercase()
            }

            // First check user-provided system logos path with multiple format support
            val userLogosDir = File(getSystemLogosPath())
            if (userLogosDir.exists() && userLogosDir.isDirectory) {
                val extensions = listOf("svg", "png", "jpg", "jpeg", "webp")

                for (ext in extensions) {
                    val logoFile = File(userLogosDir, "$baseFileName.$ext")
                    if (logoFile.exists()) {
                        android.util.Log.d("MainActivity", "Loading logo from user path: $logoFile")

                        return when (ext) {
                            "svg" -> {
                                val svg =
                                    com.caverock.androidsvg.SVG.getFromInputStream(logoFile.inputStream())

                                if (width > 0 && height > 0) {
                                    // Create bitmap at target dimensions
                                    val bitmap = android.graphics.Bitmap.createBitmap(
                                        width,
                                        height,
                                        android.graphics.Bitmap.Config.ARGB_8888
                                    )
                                    val canvas = android.graphics.Canvas(bitmap)

                                    val viewBox = svg.documentViewBox
                                    if (viewBox != null) {
                                        // SVG has viewBox - let AndroidSVG handle scaling
                                        svg.setDocumentWidth(width.toFloat())
                                        svg.setDocumentHeight(height.toFloat())
                                        svg.renderToCanvas(canvas)
                                        android.util.Log.d(
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
                                            android.util.Log.d(
                                                "MainActivity",
                                                "User SVG ($baseFileName) no viewBox, scaled from ${docWidth}x${docHeight} to ${width}x${height}, scale: $scale"
                                            )
                                        }
                                    }

                                    // Return drawable with no intrinsic dimensions
                                    object : android.graphics.drawable.BitmapDrawable(
                                        resources,
                                        bitmap
                                    ) {
                                        override fun getIntrinsicWidth(): Int = -1
                                        override fun getIntrinsicHeight(): Int = -1
                                    }
                                } else {
                                    android.graphics.drawable.PictureDrawable(svg.renderToPicture())
                                }
                            }

                            else -> {
                                // Load bitmap formats (PNG, JPG, WebP) with downscaling
                                val bitmap = loadScaledBitmap(logoFile.absolutePath, 800, 1000)
                                android.graphics.drawable.BitmapDrawable(resources, bitmap)
                            }
                        }
                    }
                }
            }

            // Fall back to built-in SVG assets
            val svgPath = "system_logos/$baseFileName.svg"
            val svg = com.caverock.androidsvg.SVG.getFromAsset(assets, svgPath)

            if (width > 0 && height > 0) {
                // Create bitmap at target dimensions
                val bitmap = android.graphics.Bitmap.createBitmap(
                    width,
                    height,
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bitmap)

                val viewBox = svg.documentViewBox
                if (viewBox != null) {
                    // SVG has viewBox - let AndroidSVG handle scaling
                    svg.setDocumentWidth(width.toFloat())
                    svg.setDocumentHeight(height.toFloat())
                    svg.renderToCanvas(canvas)
                    android.util.Log.d(
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
                        android.util.Log.d(
                            "MainActivity",
                            "Built-in SVG ($baseFileName) no viewBox, scaled from ${docWidth}x${docHeight} to ${width}x${height}, scale: $scale"
                        )
                    }
                }

                // Return drawable with no intrinsic dimensions
                object : android.graphics.drawable.BitmapDrawable(resources, bitmap) {
                    override fun getIntrinsicWidth(): Int = -1
                    override fun getIntrinsicHeight(): Int = -1
                }
            } else {
                android.graphics.drawable.PictureDrawable(svg.renderToPicture())
            }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Failed to load logo for $systemName", e)
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
    ): android.graphics.drawable.Drawable {
        // Clean up game name for display
        val displayName = gameName
            .replaceFirst(Regex("\\.[^.]+$"), "") // Remove file extension
            .replace(Regex("[_-]"), " ") // Replace underscores/hyphens with spaces
            .replace(Regex("\\s+"), " ") // Normalize multiple spaces
            .trim()
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

        val bitmap = android.graphics.Bitmap.createBitmap(
            width,
            height,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)

        // Leave background transparent (no background drawing)

        // Configure text paint
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = height * 0.20f // Start with 20% of height
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
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

        return android.graphics.drawable.BitmapDrawable(resources, bitmap)
    }

    private fun createTextFallbackDrawable(
        systemName: String,
        width: Int = -1,
        height: Int = -1
    ): android.graphics.drawable.Drawable {
        // Clean up system name for display
        val displayName = systemName
            .replace("auto-", "")
            .replace("-", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

        // Create a bitmap to draw text on
        val targetWidth = if (width > 0) width else 400
        val targetHeight = if (height > 0) height else 200

        val bitmap = android.graphics.Bitmap.createBitmap(
            targetWidth,
            targetHeight,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)

        // Configure text paint
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = targetHeight * 0.35f // Scale text to ~35% of height
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }

        // Draw text centered
        val xPos = targetWidth / 2f
        val yPos = (targetHeight / 2f) - ((paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(displayName, xPos, yPos, paint)

        return android.graphics.drawable.BitmapDrawable(resources, bitmap)
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
    ): android.graphics.Bitmap? {
        try {
            // First decode with inJustDecodeBounds=true to check dimensions
            val options = android.graphics.BitmapFactory.Options()
            options.inJustDecodeBounds = true
            android.graphics.BitmapFactory.decodeFile(imagePath, options)

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

            android.util.Log.d("MainActivity", "Loading image: $imagePath")
            android.util.Log.d("MainActivity", "  Original size: ${imageWidth}x${imageHeight}")
            android.util.Log.d("MainActivity", "  Sample size: $inSampleSize")
            android.util.Log.d(
                "MainActivity",
                "  Target size: ~${imageWidth / inSampleSize}x${imageHeight / inSampleSize}"
            )

            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false
            options.inSampleSize = inSampleSize
            options.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 // Use less memory

            return android.graphics.BitmapFactory.decodeFile(imagePath, options)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error loading scaled bitmap: $imagePath", e)
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
    ): android.graphics.drawable.Drawable {
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
        val textPaint = android.text.TextPaint().apply {
            color = android.graphics.Color.WHITE
            textSize = textSizePx
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
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
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.text.StaticLayout.Builder.obtain(
                    displayName,
                    0,
                    displayName.length,
                    textPaint,
                    maxWidth
                )
                    .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
                    .setLineSpacing(8f, 1.0f) // Add some line spacing (8px extra)
                    .setIncludePad(true)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                android.text.StaticLayout(
                    displayName,
                    textPaint,
                    maxWidth,
                    android.text.Layout.Alignment.ALIGN_CENTER,
                    1.0f,
                    8f,
                    true
                )
            }

        // Calculate bitmap dimensions with generous padding
        val horizontalPadding = 100
        val verticalPadding = 60
        val width = staticLayout.width + (horizontalPadding * 2)
        val height = staticLayout.height + (verticalPadding * 2)

        // Create bitmap and draw text
        val bitmap = android.graphics.Bitmap.createBitmap(
            width,
            height,
            android.graphics.Bitmap.Config.ARGB_8888
        )

        val canvas = android.graphics.Canvas(bitmap)

        // Center the text layout on the canvas
        canvas.save()
        canvas.translate(
            horizontalPadding.toFloat(),
            verticalPadding.toFloat()
        )
        staticLayout.draw(canvas)
        canvas.restore()

        return android.graphics.drawable.BitmapDrawable(resources, bitmap)
    }


    private fun loadSystemImage() {
        // Don't reload images if game is currently playing - respect game launch behavior
        if (state is AppState.GamePlaying) {
            android.util.Log.d(
                "MainActivity",
                "loadSystemImage blocked - game is playing, maintaining game launch display"
            )
            return
        }

        try {
            // Stop any video playback when switching to system view
            releasePlayer()
            releaseMusicPlayer()

            val logsDir = File(getLogsPath())
            val systemFile = File(logsDir, "esde_system_name.txt")
            if (!systemFile.exists()) return

            val systemName = systemFile.readText().trim()

            // Update state tracking
            updateState(AppState.SystemBrowsing(systemName))

            // CRITICAL: Check if solid color is selected for system view BEFORE checking for custom images
            val systemImagePref = prefs.getString("system_image_preference", "fanart") ?: "fanart"
            if (systemImagePref == "solid_color") {
                val solidColor = prefs.getInt(
                    "system_background_color",
                    android.graphics.Color.parseColor("#1A1A1A")
                )
                android.util.Log.d(
                    "MainActivity",
                    "System view solid color selected - using color: ${
                        String.format(
                            "#%06X",
                            0xFFFFFF and solidColor
                        )
                    }"
                )
                val drawable = android.graphics.drawable.ColorDrawable(solidColor)
                gameImageView.setImageDrawable(drawable)
                gameImageView.visibility = View.VISIBLE

                // Update system widgets after setting solid color
                refreshWidgets(OverlayWidget.WidgetContext.SYSTEM)
                return
            }

            // Handle ES-DE auto-collections
            val baseFileName = when (systemName.lowercase()) {
                "all" -> "auto-allgames"
                "favorites" -> "auto-favorites"
                "recent" -> "auto-lastplayed"
                else -> systemName.lowercase()
            }

            // Check for custom system image with multiple format support
            var imageToUse: File? = null
            val systemImagePath = getSystemImagePath()
            val imageExtensions = listOf("webp", "png", "jpg", "jpeg")

            for (ext in imageExtensions) {
                val imageFile = File(systemImagePath, "$baseFileName.$ext")
                if (imageFile.exists()) {
                    imageToUse = imageFile
                    break
                }
            }

            if (imageToUse == null) {
                val mediaBase = File(getMediaBasePath(), systemName)
                // Use system_image_preference instead of image_preference
                val prioritizedFolders = if (systemImagePref == "screenshot") {
                    listOf("screenshots", "fanart")
                } else {
                    listOf("fanart", "screenshots")
                }
                for (folder in prioritizedFolders) {
                    val dir = File(mediaBase, folder)
                    if (dir.exists() && dir.isDirectory) {
                        val images = dir.listFiles { f ->
                            f.extension.lowercase() in listOf("jpg", "png", "webp")
                        } ?: emptyArray()
                        if (images.isNotEmpty()) {
                            imageToUse = images.random()
                            break
                        }
                    }
                }
            }

            if (imageToUse != null && imageToUse.exists()) {
                // Check if this is a custom system image (from system_images folder)
                val isCustomSystemImage = imageToUse.absolutePath.contains(getSystemImagePath())

                if (isCustomSystemImage) {
                    // Load custom system image with downscaling to prevent OOM
                    android.util.Log.d(
                        "MainActivity",
                        "Loading custom system image with downscaling"
                    )
                    val bitmap = loadScaledBitmap(imageToUse.absolutePath, 1920, 1080)
                    if (bitmap != null) {
                        val drawable = android.graphics.drawable.BitmapDrawable(resources, bitmap)

                        // Clear any cached images first
                        gameImageView.dispose()
                        gameImageView.setImageDrawable(drawable)
                        android.util.Log.d(
                            "MainActivity",
                            "Custom system image loaded successfully"
                        )
                    } else {
                        android.util.Log.e(
                            "MainActivity",
                            "Failed to load custom system image, using fallback"
                        )
                        loadFallbackBackground()
                    }
                } else {
                    // Normal game artwork - use Glide with animation
                    loadImageWithAnimation(imageToUse, gameImageView)
                }
            } else {
                // No custom image and no game images found - show fallback
                loadFallbackBackground()
            }

            // Update system widgets after loading system image
            refreshWidgets(OverlayWidget.WidgetContext.SYSTEM)

        } catch (e: Exception) {
            // Don't clear images on exception - keep last valid images
            android.util.Log.e("MainActivity", "Error loading system image", e)
        }
    }

    private fun loadGameInfo() {
        // Don't reload images if game is currently playing - respect game launch behavior
        if (state is AppState.GamePlaying) {
            android.util.Log.d(
                "MainActivity",
                "loadGameInfo blocked - game is playing, maintaining game launch display"
            )
            return
        }

        try {

            android.util.Log.d("MainActivity", "Releasing music player ")
            releaseMusicPlayer()

            val logsDir = File(getLogsPath())
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

            musicLoadRunnable = Runnable {
                loadGameMusic()
            }
            musicLoadHandler.postDelayed(musicLoadRunnable!!, 500)


            // Check if we have widgets - if so, hide old marquee system
            val hasWidgets = widgetManager.hasWidgets()

            // Check if solid color is selected for game view
            val gameImagePref = prefs.getString("game_image_preference", "fanart") ?: "fanart"
            if (gameImagePref == "solid_color") {
                val solidColor = prefs.getInt(
                    "game_background_color",
                    android.graphics.Color.parseColor("#1A1A1A")
                )
                val drawable = android.graphics.drawable.ColorDrawable(solidColor)
                gameImageView.setImageDrawable(drawable)
            } else {
                // Try to find game-specific artwork
                val gameImage = findGameImage(systemName, gameNameRaw)

                if (gameImage != null && gameImage.exists()) {
                    // Game has its own artwork - use it
                    loadImageWithAnimation(gameImage, gameImageView)
                } else {
                    // No game artwork - show fallback background
                    loadFallbackBackground()
                }
            }

            // Check if instant video will play (delay = 0)
            val videoPath = findVideoForGame(systemName, gameNameRaw)
            val videoDelay = getVideoDelay()
            val instantVideoWillPlay =
                videoPath != null && isVideoEnabled() && widgetsLocked && videoDelay == 0L

            android.util.Log.d("MainActivity", "loadGameInfo - Video check:")
            android.util.Log.d("MainActivity", "  videoPath: $videoPath")
            android.util.Log.d("MainActivity", "  videoDelay: ${videoDelay}ms")
            android.util.Log.d("MainActivity", "  instantVideoWillPlay: $instantVideoWillPlay")

            // Update game widgets after determining video status
            // Note: updateWidgetsForCurrentGame() calls showWidgets() internally via loadGameWidgets()
            refreshWidgets(WidgetContext.GAME)

            // Handle video playback for the current game
            handleVideoForGame(systemName, gameNameRaw)

// Hide widgets ONLY if instant video is playing (delay = 0)
// For delayed videos, widgets stay visible until loadVideo() hides them

            when (state) {
                is AppState.GameBrowsing -> {
                    if (instantVideoWillPlay) {
                        widgetManager.hasWidgets()
                        android.util.Log.d("MainActivity", "Hiding widgets - instant video playing")
                    } else {
                        android.util.Log.d(
                            "MainActivity",
                            "Keeping widgets shown - no instant video (delay=${videoDelay}ms)"
                        )
                    }
                }

                is AppState.Screensaver -> {
                    // Don't show widgets during screensaver in loadGameInfo
                    // (screensaver handles its own widget display)
                    android.util.Log.d("MainActivity", "Not showing widgets - Screensaver active")
                }

                else -> {
                    android.util.Log.d("MainActivity", "Not showing widgets - state: $state")
                }
            }

        } catch (e: Exception) {
            // Don't clear images on exception - keep last valid images
            android.util.Log.e("MainActivity", "Error loading game info", e)
        }
    }

    private fun loadGameMusic() {
        try {
            val logsDir = File(getLogsPath())
            val gameFile = File(logsDir, "esde_game_filename.txt")
            if (!gameFile.exists()) return

            val gameNameRaw = gameFile.readText().trim()  // Full path from script
            val gameName = MediaFileHelper.extractGameFilenameWithoutExtension(sanitizeGameFilename(gameNameRaw))

            // read the display name from ES-DE if available
            val gameDisplayNameFile = File(logsDir, "esde_game_name.txt")
            val gameDisplayName = if (gameDisplayNameFile.exists()) {
                gameDisplayNameFile.readText().trim()
            } else {
                gameName  // Fallback to filename-based name
            }

            val systemFile = File(logsDir, "esde_game_system.txt")
            if (!systemFile.exists()) return
            val systemName = systemFile.readText().trim()

            //ADDED FOR MUSIC
            musicSearchJob?.cancel()
            musicSearchJob = lifecycleScope.launch {
                android.util.Log.d("MainActivity", "about to go to music player ")
                if (gameName.isNotEmpty()) {
                    musicPlayer.onGameFocused(gameDisplayName, gameName, systemName)
                }
            }
        } catch (e: Exception) {
            // Don't clear images on exception - keep last valid images
            android.util.Log.e("MainActivity", "Error loading game music", e)
        }
    }

    private fun findGameImage(
        systemName: String,
        fullGamePath: String
    ): File? {
        // Get image preference
        val imagePref = prefs.getString("game_image_preference", "fanart") ?: "fanart"

        // Return null if solid color is selected - handled in loadGameInfo()
        if (imagePref == "solid_color") {
            return null
        }

        val preferScreenshot = (imagePref == "screenshot")
        return mediaFileLocator.findGameBackgroundImage(systemName, fullGamePath, preferScreenshot = preferScreenshot)
    }

    /**
     * Get the display ID that this activity is currently running on
     */
    /**
     * Log display information for debugging
     */
    private fun logDisplayInfo() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            try {
                val displayManager =
                    getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
                val displays = displayManager.displays

                android.util.Log.d("MainActivity", "═══════════════════════════════════")
                android.util.Log.d("MainActivity", "DISPLAY INFORMATION AT STARTUP")
                android.util.Log.d("MainActivity", "═══════════════════════════════════")
                android.util.Log.d("MainActivity", "Total displays: ${displays.size}")
                displays.forEachIndexed { index, display ->
                    android.util.Log.d("MainActivity", "Display $index:")
                    android.util.Log.d("MainActivity", "  - ID: ${display.displayId}")
                    android.util.Log.d("MainActivity", "  - Name: ${display.name}")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        android.util.Log.d("MainActivity", "  - State: ${display.state}")
                    }
                }

                val currentDisplayId = getCurrentDisplayId()
                android.util.Log.d("MainActivity", "Companion app is on display: $currentDisplayId")
                android.util.Log.d("MainActivity", "═══════════════════════════════════")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error logging display info", e)
            }
        }
    }

    private fun getCurrentDisplayId(): Int {
        val displayId = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                // Android 11+ - use display property
                val id1 = display?.displayId ?: -1
                android.util.Log.d("MainActivity", "  Method 1 (display): $id1")

                // Also try getting from window
                val id2 = window?.decorView?.display?.displayId ?: -1
                android.util.Log.d("MainActivity", "  Method 2 (window.decorView.display): $id2")

                // Use the non-negative one, prefer window method
                if (id2 >= 0) id2 else id1
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                // Android 4.2+ - use windowManager
                @Suppress("DEPRECATION")
                val id = windowManager.defaultDisplay.displayId
                android.util.Log.d("MainActivity", "  Method 3 (windowManager.defaultDisplay): $id")
                id
            } else {
                android.util.Log.d("MainActivity", "  Method 4 (fallback to 0)")
                0
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error getting display ID", e)
            0
        }

        android.util.Log.d(
            "MainActivity",
            "getCurrentDisplayId() FINAL returning: $displayId (SDK: ${android.os.Build.VERSION.SDK_INT})"
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

            android.util.Log.d("MainActivity", "═══ LAUNCH REQUEST ═══")
            android.util.Log.d("MainActivity", "Companion detected on display: $currentDisplayId")
            android.util.Log.d(
                "MainActivity",
                "User preference: ${if (shouldLaunchOnTop) "THIS screen" else "OTHER screen"}"
            )

            // Get all available displays
            val targetDisplayId =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    try {
                        val displayManager =
                            getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
                        val displays = displayManager.displays

                        if (shouldLaunchOnTop) {
                            // Launch on THIS screen (same as companion)
                            android.util.Log.d(
                                "MainActivity",
                                "Targeting THIS screen (display $currentDisplayId)"
                            )
                            currentDisplayId
                        } else {
                            // Launch on OTHER screen (find the display that's NOT current)
                            val otherDisplay =
                                displays.firstOrNull { it.displayId != currentDisplayId }
                            if (otherDisplay != null) {
                                android.util.Log.d(
                                    "MainActivity",
                                    "Targeting OTHER screen (display ${otherDisplay.displayId})"
                                )
                                otherDisplay.displayId
                            } else {
                                android.util.Log.w(
                                    "MainActivity",
                                    "No other display found! Using current display"
                                )
                                currentDisplayId
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Error finding target display", e)
                        currentDisplayId
                    }
                } else {
                    currentDisplayId
                }

            android.util.Log.d("MainActivity", "FINAL target: Display $targetDisplayId")
            android.util.Log.d("MainActivity", "═════════════════════")

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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                val displayManager =
                    getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
                val displays = displayManager.displays

                android.util.Log.d("MainActivity", "launchOnDisplay: Requesting display $displayId")
                android.util.Log.d(
                    "MainActivity",
                    "launchOnDisplay: Available displays: ${displays.size}"
                )
                displays.forEachIndexed { index, display ->
                    android.util.Log.d(
                        "MainActivity",
                        "  Display $index: ID=${display.displayId}, Name=${display.name}"
                    )
                }

                val targetDisplay = displays.firstOrNull { it.displayId == displayId }

                if (targetDisplay != null) {
                    android.util.Log.d(
                        "MainActivity",
                        "✓ Found target display $displayId - Launching now"
                    )
                    val options = ActivityOptions.makeBasic()
                    options.launchDisplayId = displayId
                    startActivity(intent, options.toBundle())
                } else {
                    android.util.Log.w(
                        "MainActivity",
                        "✗ Display $displayId not found! Launching on default"
                    )
                    startActivity(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e(
                    "MainActivity",
                    "Error launching on display $displayId, using default",
                    e
                )
                startActivity(intent)
            }
        } else {
            android.util.Log.d("MainActivity", "SDK < O, launching on default display")
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
            btnHideApp.backgroundTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#4CAF50")
            )
        } else {
            btnHideApp.text = "Hide App"
            btnHideApp.backgroundTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#CF6679")
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
                    android.util.Log.d("MainActivity", "Set $appName to launch on THIS screen")

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
                    android.util.Log.d("MainActivity", "Set $appName to launch on OTHER screen")

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
            android.util.Log.e("MainActivity", "Failed to open app info", e)
        }
    }

    // ========== GAME STATE FUNCTIONS ==========

    private fun handleGameStart() {
        lastGameStartTime = System.currentTimeMillis()

        android.util.Log.d(
            "MainActivity",
            "gameImageView.visibility at game start: ${gameImageView.visibility}"
        )
        android.util.Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        android.util.Log.d("MainActivity", "GAME START HANDLER")
        android.util.Log.d("MainActivity", "Current state: $state")

        // Get the game launch behavior
        val gameLaunchBehavior =
            prefs.getString("game_launch_behavior", "game_image") ?: "game_image"
        android.util.Log.d("MainActivity", "Game launch behavior: $gameLaunchBehavior")

        // CRITICAL: If black screen, clear everything IMMEDIATELY
        if (gameLaunchBehavior == "black_screen") {
            applyBlackScreenGameLaunch()
            releasePlayer()
            releaseMusicPlayer()
        }

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

        // Handle screensaver transition - if display is already correct, skip
        if (handleGameStartFromScreensaver(gameLaunchBehavior)) {
            android.util.Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            return
        }

        // Apply game launch behavior
        applyGameLaunchBehavior(gameLaunchBehavior)

        // Stop any videos
        releasePlayer()
        releaseMusicPlayer()

        // Clear screensaver launch flag
        isLaunchingFromScreensaver = false

        android.util.Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    // ========== START: Game Start Handler Extraction ==========

    /**
     * Handle black screen game launch behavior
     */
    private fun applyBlackScreenGameLaunch() {
        android.util.Log.d("MainActivity", "Black screen behavior - clearing display immediately")
        gameImageView.dispose()
        gameImageView.setImageDrawable(null)
        gameImageView.visibility = View.GONE
        videoView.visibility = View.GONE
        widgetViewBinder.setAllVisibility(widgetContainer, false)
        releasePlayer()
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
                android.util.Log.w("MainActivity", "Game start from unexpected state: $state")
                tryReadGameInfoFromLogs()
            }
        }
    }

    /**
     * Fallback: Try to read game info from log files
     * @return Pair of (systemName, gameFilename) or null if unavailable
     */
    private fun tryReadGameInfoFromLogs(): Pair<String, String>? {
        val logsDir = File(getLogsPath())
        val gameFile = File(logsDir, "esde_game_filename.txt")
        val systemFile = File(logsDir, "esde_game_system.txt")

        return if (gameFile.exists() && systemFile.exists()) {
            Pair(systemFile.readText().trim(), gameFile.readText().trim())
        } else {
            null
        }
    }

    /**
     * Handle game start when coming from screensaver
     * @return true if display is already correct and no further action needed
     */
    private fun handleGameStartFromScreensaver(gameLaunchBehavior: String): Boolean {
        if (!isLaunchingFromScreensaver) {
            return false
        }

        android.util.Log.d("MainActivity", "Game start from screensaver")

        val screensaverBehavior =
            prefs.getString("screensaver_behavior", "game_image") ?: "game_image"

        // If both behaviors match, display is already correct
        if (screensaverBehavior == gameLaunchBehavior) {
            android.util.Log.d(
                "MainActivity",
                "Same behavior ($gameLaunchBehavior) - keeping current display"
            )
            isLaunchingFromScreensaver = false
            return true // Skip further processing
        }

        android.util.Log.d(
            "MainActivity",
            "Different behaviors - screensaver: $screensaverBehavior, game: $gameLaunchBehavior"
        )
        return false // Need to update display
    }

    /**
     * Apply game launch behavior based on settings
     */
    private fun applyGameLaunchBehavior(gameLaunchBehavior: String) {
        when (gameLaunchBehavior) {
            "black_screen" -> {
                // Already handled at the top of handleGameStart()
                android.util.Log.d("MainActivity", "Black screen - already cleared")
            }

            "default_image" -> {
                android.util.Log.d("MainActivity", "Default image behavior - loading fallback")
                loadFallbackBackground(forceCustomImageOnly = true)
                gameImageView.visibility = View.VISIBLE
                videoView.visibility = View.GONE

                android.util.Log.d("MainActivity", "Loading widgets for default_image behavior")
                refreshWidgets()
            }

            "game_image" -> {
                android.util.Log.d(
                    "MainActivity",
                    "Game image behavior - keeping current game display"
                )
                val systemName = state.getCurrentSystemName()
                val gameFilename = state.getCurrentGameFilename()
                if (systemName != null && gameFilename != null) {
                    val gameImage = findGameImage(systemName, gameFilename)

                    if (gameImage != null && gameImage.exists()) {
                        android.util.Log.d("MainActivity", "Loading game image: ${gameImage.name}")
                        loadImageWithAnimation(gameImage, gameImageView)
                    } else {
                        android.util.Log.d("MainActivity", "No game image found, using fallback")
                        loadFallbackBackground()
                    }

                    gameImageView.visibility = View.VISIBLE
                    videoView.visibility = View.GONE

                    // Load and show widgets directly (can't use updateWidgetsForCurrentGame because state is GamePlaying)
                    android.util.Log.d("MainActivity", "Loading widgets for game_image behavior")
                    refreshWidgets()
                } else {
                    android.util.Log.d("MainActivity", "No game info available, using fallback")
                    loadFallbackBackground()
                    gameImageView.visibility = View.VISIBLE
                    videoView.visibility = View.GONE
                }
            }
        }
    }

// ========== END: Game Start Handler Extraction ==========

    /**
     * Handle game end event - return to normal browsing display
     */
    private fun handleGameEnd() {
        lastGameEndTime = System.currentTimeMillis()

        android.util.Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        android.util.Log.d("MainActivity", "GAME END EVENT")
        android.util.Log.d("MainActivity", "Current state: $state")
        android.util.Log.d("MainActivity", "gameImageView visibility: ${gameImageView.visibility}")

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
        } else {
            android.util.Log.w("MainActivity", "Game end but not in GamePlaying state: $state")
        }

        // Determine how to handle display after game end
        val gameLaunchBehavior =
            prefs.getString("game_launch_behavior", "game_image") ?: "game_image"
        android.util.Log.d("MainActivity", "Game launch behavior: $gameLaunchBehavior")

        when (val s = state) {
            is AppState.GameBrowsing -> {
                // We're in game browsing mode after game end
                if (gameLaunchBehavior == "game_image") {
                    // Images/widgets are already correct, but need to reload videos
                    android.util.Log.d(
                        "MainActivity",
                        "Game image behavior - reloading to restart videos"
                    )
                    android.util.Log.d("MainActivity", "Game: ${s.gameFilename}")

                    // Check if instant video will play
                    val videoPath = findVideoForGame(s.systemName, s.gameFilename)
                    val videoDelay = getVideoDelay()
                    val instantVideoWillPlay =
                        videoPath != null && isVideoEnabled() && widgetsLocked && videoDelay == 0L

                    // Only reload if video needs to start (instant or delayed)
                    if (videoPath != null && isVideoEnabled() && widgetsLocked) {
                        android.util.Log.d(
                            "MainActivity",
                            "Video enabled - calling handleVideoForGame to restart"
                        )

                        // If instant video, hide widgets first to prevent flash
                        if (instantVideoWillPlay) {
                            widgetViewBinder.setAllVisibility(widgetContainer, false)
                        }

                        handleVideoForGame(s.systemName, s.gameFilename)
                    } else {
                        android.util.Log.d(
                            "MainActivity",
                            "No video to restart - keeping current display"
                        )
                    }
                } else {
                    // Different behavior - need to reload
                    android.util.Log.d(
                        "MainActivity",
                        "Reloading display (behavior: $gameLaunchBehavior)"
                    )
                    loadGameInfo()
                }
            }

            is AppState.SystemBrowsing -> {
                // In system view - reload system image
                android.util.Log.d("MainActivity", "Reloading system image after game end")
                loadSystemImage()
            }

            else -> {
                // Shouldn't happen, but handle gracefully
                android.util.Log.w("MainActivity", "Unexpected state after game end: $state")
                loadGameInfo()
            }
        }

        android.util.Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    // ========== SCREENSAVER FUNCTIONS ==========

    /**
     * Handle screensaver start event
     */
    private fun handleScreensaverStart() {
        android.util.Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        android.util.Log.d("MainActivity", "SCREENSAVER START")
        android.util.Log.d("MainActivity", "Current state before: $state")

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
                android.util.Log.w(
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

        android.util.Log.d("MainActivity", "Saved previous state: $previousState")

        val screensaverBehavior =
            prefs.getString("screensaver_behavior", "game_image") ?: "game_image"
        android.util.Log.d("MainActivity", "Screensaver behavior preference: $screensaverBehavior")
        android.util.Log.d("MainActivity", "Current state after: $state")
        android.util.Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // Reset screensaver initialization flag
        screensaverInitialized = false

        // CRITICAL: If black screen, clear everything IMMEDIATELY
        if (screensaverBehavior == "black_screen") {
            android.util.Log.d(
                "MainActivity",
                "Black screen behavior - clearing display immediately"
            )
            gameImageView.dispose()
            gameImageView.setImageDrawable(null)
            gameImageView.visibility = View.GONE
            videoView.visibility = View.GONE
            widgetViewBinder.setAllVisibility(widgetContainer, false)
            releasePlayer()
            releaseMusicPlayer()
            // Hide grid for black screen
            gridOverlayView?.visibility = View.GONE
            return  // Exit early, don't process anything else
        }

        when (screensaverBehavior) {
            "game_image" -> {
                // Game images will be loaded by handleScreensaverGameSelect events
                android.util.Log.d(
                    "MainActivity",
                    "Screensaver behavior: game_image - waiting for game select events"
                )
                android.util.Log.d(
                    "MainActivity",
                    "  - Will load game images when screensaver-game-select events arrive"
                )
                android.util.Log.d(
                    "MainActivity",
                    "  - gameImageView visibility: ${gameImageView.visibility}"
                )
                android.util.Log.d(
                    "MainActivity",
                    "  - videoView visibility: ${videoView.visibility}"
                )
            }

            "default_image" -> {
                // Show default/fallback image immediately
                android.util.Log.d("MainActivity", "Screensaver behavior: default_image")
                // loadFallbackBackground()
                // gameImageView.visibility = View.VISIBLE
                // videoView.visibility = View.GONE
            }
        }

        // Stop any videos
        releasePlayer()
        releaseMusicPlayer()

        // Update grid overlay for screensaver state (for game_image and default_image)
        widgetContainer.visibility = View.VISIBLE
        updateGridOverlay()
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
    private fun applyScreensaverBehaviorChange() {
        android.util.Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        android.util.Log.d("MainActivity", "SCREENSAVER BEHAVIOR CHANGE")
        android.util.Log.d("MainActivity", "Current state: $state")

        val screensaverBehavior =
            prefs.getString("screensaver_behavior", "game_image") ?: "game_image"
        android.util.Log.d("MainActivity", "New screensaver behavior: $screensaverBehavior")

        // Get current screensaver game (if any)
        val screensaverGame = if (state is AppState.Screensaver) {
            (state as AppState.Screensaver).currentGame
        } else {
            null
        }

        when (screensaverBehavior) {
            "black_screen" -> {
                android.util.Log.d("MainActivity", "Switching to black screen")
                gameImageView.dispose()
                gameImageView.setImageDrawable(null)
                gameImageView.visibility = View.GONE
                videoView.visibility = View.GONE
                widgetViewBinder.setAllVisibility(widgetContainer, false)
                releasePlayer()
                gridOverlayView?.visibility = View.GONE
            }

            "default_image" -> {
                android.util.Log.d("MainActivity", "Switching to default image")
                loadFallbackBackground(forceCustomImageOnly = true)
                gameImageView.visibility = View.VISIBLE
                videoView.visibility = View.GONE

                // Show current game widgets if we have a game
                if (screensaverGame != null) {
                    refreshWidgets()
                } else {
                    widgetViewBinder.setAllVisibility(widgetContainer, false)
                }

                releasePlayer()
            }

            "game_image" -> {
                android.util.Log.d("MainActivity", "Switching to game image")

                // If we have a current screensaver game, load it
                if (screensaverGame != null) {
                    val gameImage =
                        findGameImage(screensaverGame.systemName, screensaverGame.gameFilename)

                    if (gameImage != null && gameImage.exists()) {
                        android.util.Log.d(
                            "MainActivity",
                            "Loading current screensaver game image: ${gameImage.name}"
                        )
                        loadImageWithAnimation(gameImage, gameImageView)
                    } else {
                        android.util.Log.d("MainActivity", "No game image found, using fallback")
                        loadFallbackBackground()
                    }

                    gameImageView.visibility = View.VISIBLE
                    videoView.visibility = View.GONE

                    // Load and show widgets
                    refreshWidgets()
                } else {
                    // No game selected yet - just wait
                    android.util.Log.d(
                        "MainActivity",
                        "No screensaver game yet - display will update on next game-select"
                    )
                }

                releasePlayer()
            }
        }

        android.util.Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    /**
     * Handle screensaver end event - return to normal browsing display
     * @param reason The reason for screensaver ending: "cancel", "game-jump", or "game-start"
     */
    private fun handleScreensaverEnd(reason: String?) {
        android.util.Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        android.util.Log.d("MainActivity", "SCREENSAVER END: reason=$reason")
        android.util.Log.d("MainActivity", "Current state: $state")

        // Get previous state from screensaver
        val previousState = if (state is AppState.Screensaver) {
            (state as AppState.Screensaver).previousState
        } else {
            // Fallback if state tracking wasn't initialized
            android.util.Log.w("MainActivity", "Not in Screensaver state, using fallback")
            SavedBrowsingState.InSystemView(state.getCurrentSystemName() ?: "")
        }

        // Get current screensaver game info (if any)
        val screensaverGame = if (state is AppState.Screensaver) {
            (state as AppState.Screensaver).currentGame
        } else {
            null
        }

        android.util.Log.d("MainActivity", "Previous state before screensaver: $previousState")
        android.util.Log.d("MainActivity", "Current screensaver game: $screensaverGame")
        android.util.Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // Reset screensaver initialization flag
        screensaverInitialized = false

        if (reason != null) {
            when (reason) {
                "game-start" -> {
                    // CRITICAL: Set flag IMMEDIATELY to block FileObserver reloads during transition
                    isLaunchingFromScreensaver = true

                    // User is launching a game from screensaver
                    android.util.Log.d(
                        "MainActivity",
                        "Screensaver end - game starting, waiting for game-start event"
                    )
                    android.util.Log.d(
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
                        android.util.Log.d(
                            "MainActivity",
                            "Transitioned to GameBrowsing: ${screensaverGame.gameFilename}"
                        )
                    } else {
                        android.util.Log.w("MainActivity", "No screensaver game info available")
                    }

                    // The game-start event will handle the display
                    // Flag will be cleared in handleGameStart()
                }

                "game-jump" -> {
                    // User jumped to a different game while in screensaver
                    // The game is now the selected game, so image can be retained
                    android.util.Log.d(
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
                        android.util.Log.d(
                            "MainActivity",
                            "Transitioned to GameBrowsing: ${screensaverGame.gameFilename}"
                        )
                    } else {
                        android.util.Log.w("MainActivity", "No screensaver game info available")
                    }

                    // The current screensaver game image is already showing, so don't reload
                }

                "cancel" -> {
                    // User cancelled screensaver (pressed back or timeout)
                    // Return to the browsing state from before screensaver started
                    android.util.Log.d(
                        "MainActivity",
                        "Screensaver end - cancel, returning to previous state"
                    )

                    // Return to previous state
                    when (previousState) {
                        is SavedBrowsingState.InSystemView -> {
                            android.util.Log.d(
                                "MainActivity",
                                "Returning to system view: ${previousState.systemName}"
                            )

                            // Update state first
                            updateState(AppState.SystemBrowsing(previousState.systemName))

                            // Then reload display
                            loadSystemImage()
                        }

                        is SavedBrowsingState.InGameView -> {
                            android.util.Log.d(
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
                    android.util.Log.w(
                        "MainActivity",
                        "Screensaver end - unknown reason: $reason, defaulting to cancel behavior"
                    )

                    // Return to previous state (same as cancel)
                    when (previousState) {
                        is SavedBrowsingState.InSystemView -> {
                            android.util.Log.d(
                                "MainActivity",
                                "Returning to system view: ${previousState.systemName}"
                            )

                            updateState(AppState.SystemBrowsing(previousState.systemName))
                            loadSystemImage()
                        }

                        is SavedBrowsingState.InGameView -> {
                            android.util.Log.d(
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
        android.util.Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        android.util.Log.d("MainActivity", "SCREENSAVER GAME SELECT EVENT")
        android.util.Log.d("MainActivity", "Current state: $state")

        val screensaverBehavior =
            prefs.getString("screensaver_behavior", "game_image") ?: "game_image"
        android.util.Log.d("MainActivity", "Screensaver behavior: $screensaverBehavior")

        // Get current screensaver game from state
        val screensaverGame = if (state is AppState.Screensaver) {
            (state as AppState.Screensaver).currentGame
        } else {
            android.util.Log.w("MainActivity", "Not in screensaver state!")
            null
        }

        android.util.Log.d("MainActivity", "Screensaver game: ${screensaverGame?.gameFilename}")
        android.util.Log.d("MainActivity", "Screensaver initialized: $screensaverInitialized")

        // If black screen, don't load anything
        if (screensaverBehavior == "black_screen") {
            android.util.Log.d("MainActivity", "Black screen - ignoring screensaver game select")
            return
        }

        val isFirstGame = !screensaverInitialized

        if (isFirstGame) {
            android.util.Log.d(
                "MainActivity",
                "Screensaver: First game event received - initializing display"
            )
            screensaverInitialized = true
        }

        if (screensaverGame != null) {
            val gameName = MediaFileHelper.extractGameFilenameWithoutExtension(screensaverGame.gameFilename)

            when (screensaverBehavior) {
                "game_image" -> {
                    android.util.Log.d("MainActivity", "Processing game_image behavior")
                    android.util.Log.d("MainActivity", "  - System: ${screensaverGame.systemName}")
                    android.util.Log.d(
                        "MainActivity",
                        "  - Game: ${screensaverGame.gameName ?: gameName}"
                    )
                    android.util.Log.d(
                        "MainActivity",
                        "  - Filename: ${screensaverGame.gameFilename}"
                    )

                    // Load the screensaver game's artwork
                    val gameImage = findGameImage(
                        screensaverGame.systemName,
                        screensaverGame.gameFilename
                    )

                    android.util.Log.d("MainActivity", "  - Found image path: $gameImage")
                    android.util.Log.d("MainActivity", "  - Image exists: ${gameImage?.exists()}")

                    if (gameImage != null && gameImage.exists()) {
                        android.util.Log.d(
                            "MainActivity",
                            "  ✓ Loading game image via loadImageWithAnimation()"
                        )
                        android.util.Log.d(
                            "MainActivity",
                            "  - Before load - gameImageView visibility: ${gameImageView.visibility}"
                        )
                        loadImageWithAnimation(gameImage, gameImageView)
                    } else {
                        android.util.Log.e(
                            "MainActivity",
                            "  ✗ Game image not found or doesn't exist"
                        )
                        android.util.Log.d("MainActivity", "  - Falling back to default background")
                        loadFallbackBackground()
                    }

                    // Make sure views are visible
                    gameImageView.visibility = View.VISIBLE
                    videoView.visibility = View.GONE
                    android.util.Log.d(
                        "MainActivity",
                        "  - After load - gameImageView visibility: ${gameImageView.visibility}"
                    )
                    android.util.Log.d(
                        "MainActivity",
                        "  - After load - videoView visibility: ${videoView.visibility}"
                    )

                    // Use existing function to load game widgets with correct images
                    android.util.Log.d("MainActivity", "Loading widgets for screensaver game")
                    refreshWidgets()
                }

                "default_image" -> {
                    android.util.Log.d("MainActivity", "Processing default_image behavior")

                    loadFallbackBackground(forceCustomImageOnly = true)

                    // Make sure views are visible
                    gameImageView.visibility = View.VISIBLE
                    videoView.visibility = View.GONE
                    android.util.Log.d(
                        "MainActivity",
                        "  - gameImageView visibility: ${gameImageView.visibility}"
                    )
                    android.util.Log.d(
                        "MainActivity",
                        "  - videoView visibility: ${videoView.visibility}"
                    )

                    // Use existing function to load game widgets with correct images
                    android.util.Log.d("MainActivity", "Loading widgets for screensaver game")
                    refreshWidgets()
                }
            }
        } else {
            android.util.Log.w("MainActivity", "No screensaver game info available")
        }

        android.util.Log.d("MainActivity", "Screensaver game select complete")
        android.util.Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    private fun updateWidgetsForScreensaverGame() {
        android.util.Log.d("MainActivity", "═══ updateWidgetsForScreensaverGame START ═══")

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
        android.util.Log.d("MainActivity", "═══ updateWidgetsForScreensaverGame END ═══")
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
        if (player == null) return

        val audioEnabled = prefs.getBoolean("video_audio_enabled", false)

        if (!audioEnabled) {
            // User has disabled video audio - mute completely
            player?.volume = 0f
            android.util.Log.d("MainActivity", "Video audio disabled by user - volume: 0")
            return
        }
        player?.volume = getSystemVolume()
    }

    private fun getSystemVolume(): Float {
        try {
            val currentDisplayId = getCurrentDisplayId()
            return getNormalizedAudioLevelForCurrentScreen()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error updating video volume", e)
            // Fallback to full volume if there's an error
            return 1f
        }
    }

    private fun updateMusicPlayerVolume() {
        try {
            //musicPlayer.setVolume(getNormalizedAudioLevelForCurrentScreen())
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error updating video volume", e)
            // Fallback to full volume if there's an error
            player?.volume = 1f
        }
    }


    private fun getNormalizedAudioLevelForCurrentScreen(): Float {
        // Get the audio manager
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        // Determine which display we're on
        val currentDisplayId = getCurrentDisplayId()
        var currentVolume = 0
        var maxVolume = 0

        if (currentDisplayId == 0) {
            // Primary display (top screen) - use standard STREAM_MUSIC volume
            currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        } else {
            currentVolume = Settings.System.getInt(
                contentResolver,
                "secondary_screen_volume_level"
            )
            maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
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
                        android.util.Log.d(
                            "MainActivity",
                            "Volume change detected - updating video volume"
                        )
                        updateVideoVolume()
                        updateMusicPlayerVolume()
                    }

                    Settings.ACTION_SOUND_SETTINGS -> {
                        // Sound settings changed (might include secondary screen volume)
                        android.util.Log.d(
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
        android.util.Log.d("MainActivity", "Volume change listener registered")
    }

    /**
     * Unregister volume listener
     */
    private fun unregisterVolumeListener() {
        volumeChangeReceiver?.let {
            try {
                unregisterReceiver(it)
                android.util.Log.d("MainActivity", "Volume change listener unregistered")
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error unregistering volume listener", e)
            }
        }
        volumeChangeReceiver = null
    }

    // Add this variable at the top of MainActivity class
    private var secondaryVolumeObserver: android.database.ContentObserver? = null

// Add this function near the volume functions
    /**
     * Register observer for secondary screen volume changes (Ayn Thor)
     */
    private fun registerSecondaryVolumeObserver() {
        try {
            secondaryVolumeObserver = object :
                android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    android.util.Log.d(
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

            android.util.Log.d("MainActivity", "Secondary volume observer registered")
        } catch (e: Exception) {
            android.util.Log.w(
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
                android.util.Log.d("MainActivity", "Secondary volume observer unregistered")
            } catch (e: Exception) {
                android.util.Log.e(
                    "MainActivity",
                    "Error unregistering secondary volume observer",
                    e
                )
            }
        }
        secondaryVolumeObserver = null
    }

    /**
     * Check if video is currently playing
     */
    private fun isVideoPlaying(): Boolean {
        return player != null && currentVideoPath != null
    }

    /**
     * Get video delay in milliseconds
     */
    private fun getVideoDelay(): Long {
        val progress = prefs.getInt("video_delay", 4) // 4 (2 seconds)
        return (progress * 500L) // Convert to milliseconds (0-5000ms)
    }

    /**
     * Find video file for a game
     * @param systemName ES-DE system name (e.g., "snes", "arcade")
     * @param rawName Game filename with extension (e.g., "Super Mario World.zip")
     * @return Video file path or null if not found
     */
    private fun findVideoForGame(
        systemName: String?,
        rawName: String?
    ): String? {
        if (systemName == null || rawName == null) {
            android.util.Log.d("MainActivity", "findVideoForGame - systemName or rawName is null")
            return null
        }

        android.util.Log.d("MainActivity", "findVideoForGame - Looking for video:")
        android.util.Log.d("MainActivity", "  systemName: $systemName")
        android.util.Log.d("MainActivity", "  rawName: $rawName")

        return mediaFileLocator.findVideoFilePath(systemName, rawName)
    }

    /**
     * Load and play video with animation based on settings
     */
    private fun loadVideo(videoPath: String) {
        try {
            // If same video is already playing, don't reload
            if (currentVideoPath == videoPath && player != null) {
                android.util.Log.d("MainActivity", "Same video already playing: $videoPath")
                return
            }

            // Stop previous player without animation
            player?.release()
            player = null

            // Create new player
            player = ExoPlayer.Builder(this).build()
            videoView.player = player

            // Set volume based on system volume
            updateVideoVolume()

            // Create media item
            val mediaItem = MediaItem.fromUri(videoPath)
            player?.setMediaItem(mediaItem)
            player?.prepare()
            player?.playWhenReady = true
            player?.repeatMode = Player.REPEAT_MODE_ONE // Loop video

            // Hide the game image view and marquee so video is visible
            gameImageView.visibility = View.GONE

            // Hide widgets when video plays
            widgetViewBinder.setAllVisibility(widgetContainer, false)

            // ========== MUSIC ==========
            // ===========================

            player?.volume?.let {
                if (it > 0f) {
                    releaseMusicPlayer()
                }
            }

            // Get animation settings (same as images)
            val animationStyle = prefs.getString("animation_style", "scale_fade") ?: "scale_fade"
            val duration = if (animationStyle == "custom") {
                prefs.getInt("animation_duration", 250)
            } else {
                250
            }
            val scaleAmount = if (animationStyle == "custom") {
                prefs.getInt("animation_scale", 95) / 100f
            } else {
                0.95f
            }

            // Show video view with animation
            videoView.visibility = View.VISIBLE

            when (animationStyle) {
                "none" -> {
                    // No animation - instant display
                    videoView.alpha = 1f
                    videoView.scaleX = 1f
                    videoView.scaleY = 1f
                }

                "fade" -> {
                    // Fade only - no scale
                    videoView.alpha = 0f
                    videoView.scaleX = 1f
                    videoView.scaleY = 1f
                    videoView.animate()
                        .alpha(1f)
                        .setDuration(duration.toLong())
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }

                else -> {
                    // "scale_fade" - default with scale + fade
                    videoView.alpha = 0f
                    videoView.scaleX = scaleAmount
                    videoView.scaleY = scaleAmount
                    videoView.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(duration.toLong())
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
            }

            currentVideoPath = videoPath
            android.util.Log.d(
                "MainActivity",
                "Video loaded with ${animationStyle} animation (${duration}ms, scale: ${scaleAmount})"
            )

        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error loading video: $videoPath", e)
            releasePlayer()
        }
    }

    /**
     * Release video player
     */
    private fun releasePlayer() {
        // ========== MUSIC ==========
        // ===========================

        // Cancel any pending video load
        videoDelayRunnable?.let { videoDelayHandler?.removeCallbacks(it) }

        if (player != null) {
            // Cancel any ongoing animations
            videoView.animate().cancel()

            // Fade out video and release
            videoView.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    videoView.visibility = View.GONE
                    player?.release()
                    player = null
                    currentVideoPath = null

                    // Show the game image view again
                    gameImageView.visibility = View.VISIBLE

                    // Show widgets when game is playing (FIXED)
                    if (state is AppState.GamePlaying) {
                        val hasWidgets = widgetManager.hasWidgets()
                        if (hasWidgets) {
                            refreshWidgets()
                        }
                    }
                }
                .start()

            loadGameMusic()
        } else {
            // No player, just hide the video view and show image view
            videoView.visibility = View.GONE
            gameImageView.visibility = View.VISIBLE
            currentVideoPath = null

            // Show widgets when game is playing (FIXED)
            if (state is AppState.GamePlaying) {
                val hasWidgets = widgetManager.hasWidgets()
                if (hasWidgets) {
                    refreshWidgets()
                }
            }
        }
    }

    private fun releaseMusicPlayer() {
        musicSearchJob?.cancel()
        musicLoadRunnable?.let { musicLoadHandler.removeCallbacks(it) }
        musicPlayer.stopPlaying()
    }

    /**
     * Handle video loading with delay
     */
    private fun handleVideoForGame(
        systemName: String?,
        //strippedName: String?,
        rawName: String?,
        override: Boolean = false
    ) {
        // Cancel any pending video load
        videoDelayRunnable?.let { videoDelayHandler?.removeCallbacks(it) }

        // Only trust isActivityVisible (onStart/onStop) - it's the only truly reliable signal
        // on devices with identical display names.
        if (!isActivityVisible) {
            android.util.Log.d(
                "MainActivity",
                "Video blocked - activity not visible (onStop called)"
            )
            releasePlayer()
            return
        }

        // Additional check: If ES-DE reports a game is playing, block videos
        // This handles same-screen game launches where onStop doesn't fire
        if (state is AppState.GamePlaying) {
            android.util.Log.d("MainActivity", "Video blocked - game is playing (ES-DE event)")
            releasePlayer()
            return
        }

        if (state is AppState.Screensaver) {
            android.util.Log.d("MainActivity", "Video blocked - screensaver active")
            releasePlayer()
            return
        }

        if (!isVideoEnabled() && !override) {
            releasePlayer()
            return
        }

        // Block videos during widget edit mode
        if (!widgetsLocked) {
            android.util.Log.d("MainActivity", "Video blocked - widget edit mode active")
            releasePlayer()
            return
        }

        // Pass the raw name (full path) to findVideoForGame
        val videoPath = findVideoForGame(systemName, rawName)

        if (videoPath != null) {
            val delay = getVideoDelay()

            android.util.Log.d("MainActivity", "Video enabled, delay: ${delay}ms, path: $videoPath")

            if (delay == 0L) {
                // Instant - load video immediately
                loadVideo(videoPath)
                if (override) {
                    releaseMusicPlayer()
                    player?.volume = getSystemVolume()
                }
            } else {
                // Delayed - show image first, then video
                releasePlayer() // Stop any current video

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
                        loadVideo(videoPath)
                        if (override) {
                            releaseMusicPlayer()
                            player?.volume = getSystemVolume()
                        }
                    } else {
                        // Build list of reasons video was cancelled
                        val reasons = mutableListOf<String>()
                        if (!isActivityVisible) reasons.add("not visible")
                        when (state) {
                            is AppState.GamePlaying -> reasons.add("game playing")
                            is AppState.Screensaver -> reasons.add("screensaver")
                            is AppState.SystemBrowsing -> reasons.add("system view")
                            else -> reasons.add("unexpected state: $state")
                        }
                        if (!widgetsLocked) reasons.add("widget edit mode")

                        android.util.Log.d(
                            "MainActivity",
                            "Video delayed load cancelled - ${reasons.joinToString(", ")}"
                        )
                    }
                }

                videoDelayHandler?.postDelayed(videoDelayRunnable!!, delay)
            }
        } else {
            releasePlayer()
        }
    }

    private fun showContextMenu() {
        runOnUiThread {
           // val menuView = findViewById<ComposeView>(R.id.menu_compose_view)
           // menuView.visibility = View.VISIBLE
            menuState.showMenu = true
        }
    }

    private fun hideContextMenu() {
        menuState.showMenu = false
        widgetMenuShowing = false
        //menuComposeView.visibility = View.GONE
        //menuComposeView.disposeComposition()
    }

    private fun openWidgetSettings(widget: OverlayWidget) {
        runOnUiThread {
           // val menuView = findViewById<ComposeView>(R.id.menu_compose_view)
           // menuView.visibility = View.VISIBLE
            menuState.widgetToEditState = widget
        }
    }

    private fun onAddPageSelected() {
        widgetManager.addNewPage()
        refreshWidgets(true)
        Toast.makeText(
            this,
            "Page ${widgetManager.currentPageIndex + 1} created",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun onRemovePageSelected() {
        widgetManager.removeCurrentPage()
        refreshWidgets(true)
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

    private fun onMusicResultSelected(selected: StreamInfoItem) {
        val s = state as? AppState.GameBrowsing ?: return
        val systemName = s.systemName
        val gameFilenameSanitized = extractGameFilenameWithoutExtension(sanitizeGameFilename(s.gameFilename))

        lifecycleScope.launch {
            android.util.Log.d("CoroutineDebug", "Downloading selected: ${selected.name}")
            musicRepository.manualSelection(gameFilenameSanitized, systemName, selected.url)
            musicPlayer.onGameFocused(s.gameName ?: "", gameFilenameSanitized, systemName)
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

        android.util.Log.d("MainActivity", "Show grid toggled: $showGrid")
    }

    private fun toggleWidgetLock() {
        widgetsLocked = !widgetsLocked

        // Update all active widgets with the new lock state
        widgetViewBinder.setAlLocked(widgetContainer, widgetsLocked)

        val message = if (widgetsLocked) {
            "Widgets locked - they can no longer be moved, resized, or deleted"
        } else {
            "Widgets unlocked - tap to select, drag to move, resize from corner"
        }

        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()

        // Save lock state to preferences
        prefs.edit().putBoolean("widgets_locked", widgetsLocked).apply()

        // Handle video playback and widget reload when toggling widget lock
        if (widgetsLocked) {
            // Locked (edit mode OFF) - videos can resume if other conditions allow
            android.util.Log.d("MainActivity", "Widget edit mode OFF - allowing videos")
            // Reload current state to potentially start videos
            if (state is AppState.SystemBrowsing) {
                loadSystemImage()
            } else if (state !is AppState.GamePlaying) {
                loadGameInfo()
            }
        } else {
            // Unlocked (edit mode ON) - stop videos and reload widgets
            android.util.Log.d(
                "MainActivity",
                "Widget edit mode ON - blocking videos and reloading widgets"
            )
            releasePlayer()

            // Reload widgets with current images so they're visible during editing
            refreshWidgets()
        }
    }

    companion object {
        const val COLUMN_COUNT_KEY = "column_count"
    }
}