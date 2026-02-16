package com.esde.companion.managers

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.esde.companion.data.AppConstants
import com.esde.companion.data.PreferenceKeys
import com.esde.companion.ui.AnimationStyle
import com.esde.companion.ui.PageAnimation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.Int
import kotlin.apply

/**
 * Centralized manager for all SharedPreferences access in ES-DE Companion.
 *
 * BENEFITS:
 * - Type-safe preference access (no magic strings)
 * - Single source of truth for defaults
 * - Easy to add migration logic
 * - Consistent preference handling
 *
 * USAGE:
 * ```
 * val prefsManager = PreferencesManager(context)
 *
 * // Read
 * val dimmingLevel = prefsManager.dimmingLevel
 *
 * // Write
 * prefsManager.dimmingLevel = 50
 * ```
 */
class PreferencesManager(context: Context) {

    companion object {
        // Animation preset constants (reference AppConstants for consistency)
        const val PRESET_ANIMATION_DURATION = AppConstants.Timing.DEFAULT_ANIMATION_DURATION
        const val PRESET_ANIMATION_SCALE = AppConstants.UI.DEFAULT_ANIMATION_SCALE
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PreferenceKeys.PREFS_NAME,
        Context.MODE_PRIVATE
    )

    // ========== PATH PROPERTIES ==========

    var mediaPath: String
        get() = prefs.getString(
            PreferenceKeys.KEY_MEDIA_PATH,
            PreferenceKeys.DEFAULT_MEDIA_PATH
        ) ?: PreferenceKeys.DEFAULT_MEDIA_PATH
        set(value) = prefs.edit { putString(PreferenceKeys.KEY_MEDIA_PATH, value) }

    var systemPath: String
        get() = prefs.getString(
            PreferenceKeys.KEY_SYSTEM_PATH,
            PreferenceKeys.DEFAULT_SYSTEM_PATH
        ) ?: PreferenceKeys.DEFAULT_SYSTEM_PATH
        set(value) = prefs.edit { putString(PreferenceKeys.KEY_SYSTEM_PATH, value) }

    var systemLogosPath: String
        get() = prefs.getString(
            PreferenceKeys.KEY_SYSTEM_LOGOS_PATH,
            PreferenceKeys.DEFAULT_SYSTEM_LOGOS_PATH
        ) ?: PreferenceKeys.DEFAULT_SYSTEM_LOGOS_PATH
        set(value) = prefs.edit { putString(PreferenceKeys.KEY_SYSTEM_LOGOS_PATH, value) }

    var customBackgroundPath: String
        get() = prefs.getString(
            PreferenceKeys.KEY_CUSTOM_BACKGROUND,
            PreferenceKeys.DEFAULT_CUSTOM_BACKGROUND
        ) ?: PreferenceKeys.DEFAULT_CUSTOM_BACKGROUND
        set(value) = prefs.edit { putString(PreferenceKeys.KEY_CUSTOM_BACKGROUND, value) }

    var scriptsPath: String
        get() = prefs.getString(
            PreferenceKeys.KEY_SCRIPTS_PATH,
            PreferenceKeys.DEFAULT_SCRIPTS_PATH
        ) ?: PreferenceKeys.DEFAULT_SCRIPTS_PATH
        set(value) = prefs.edit { putString(PreferenceKeys.KEY_SCRIPTS_PATH, value) }

    // ========== UI PROPERTIES ==========
    var drawerTransparency: Int
        get() = prefs.getInt(
            PreferenceKeys.KEY_DRAWER_TRANSPARENCY,
            PreferenceKeys.DEFAULT_DRAWER_TRANSPARENCY
        )
        set(value) = prefs.edit { putInt(PreferenceKeys.KEY_DRAWER_TRANSPARENCY, value) }

    var columnCount: Int
        get() = prefs.getInt(
            PreferenceKeys.KEY_COLUMN_COUNT,
            PreferenceKeys.DEFAULT_COLUMN_COUNT
        )
        set(value) = prefs.edit { putInt(PreferenceKeys.KEY_COLUMN_COUNT, value) }

    // ========== WIDGET PROPERTIES ==========

    var snapToGrid: Boolean
        get() = prefs.getBoolean(
            PreferenceKeys.KEY_SNAP_TO_GRID,
            PreferenceKeys.DEFAULT_SNAP_TO_GRID
        )
        set(value) = prefs.edit { putBoolean(PreferenceKeys.KEY_SNAP_TO_GRID, value) }

    var showGrid: Boolean
        get() = prefs.getBoolean(
            PreferenceKeys.KEY_SHOW_GRID,
            PreferenceKeys.DEFAULT_SHOW_GRID
        )
        set(value) = prefs.edit { putBoolean(PreferenceKeys.KEY_SHOW_GRID, value) }

    // ========== MUSIC PROPERTIES ==========

    var musicEnabled: Boolean
        get() = prefs.getBoolean(
            PreferenceKeys.KEY_MUSIC_ENABLED,
            PreferenceKeys.DEFAULT_MUSIC_ENABLED
        )
        set(value) = prefs.edit { putBoolean(PreferenceKeys.KEY_MUSIC_ENABLED, value) }

    var musicPath: String
        get() = prefs.getString(
            PreferenceKeys.KEY_MUSIC_PATH,
            PreferenceKeys.DEFAULT_MUSIC_PATH
        ) ?: PreferenceKeys.DEFAULT_MUSIC_PATH
        set(value) = prefs.edit { putString(PreferenceKeys.KEY_MUSIC_PATH, value) }

    var musicVideoBehavior: String
        get() = prefs.getString(
            PreferenceKeys.KEY_MUSIC_VIDEO_BEHAVIOR,
            PreferenceKeys.DEFAULT_MUSIC_VIDEO_BEHAVIOR
        ) ?: PreferenceKeys.DEFAULT_MUSIC_VIDEO_BEHAVIOR
        set(value) = prefs.edit { putString(PreferenceKeys.KEY_MUSIC_VIDEO_BEHAVIOR, value) }

    var musicSongTitleEnabled: Boolean
        get() = prefs.getBoolean(
            PreferenceKeys.KEY_MUSIC_SONG_TITLE_ENABLED,
            PreferenceKeys.DEFAULT_MUSIC_SONG_TITLE_ENABLED
        )
        set(value) = prefs.edit { putBoolean(PreferenceKeys.KEY_MUSIC_SONG_TITLE_ENABLED, value) }

    var musicSongTitleSystemOnlyEnabled: Boolean
        get() = prefs.getBoolean(
            PreferenceKeys.KEY_MUSIC_SONG_TITLE_SYSTEM_ONLY_ENABLED,
            PreferenceKeys.DEFAULT_MUSIC_SONG_TITLE_SYSTEM_ONLY_ENABLED
        )
        set(value) = prefs.edit { putBoolean(PreferenceKeys.KEY_MUSIC_SONG_TITLE_SYSTEM_ONLY_ENABLED, value) }


    var musicSongTitleDuration: Int
        get() = prefs.getInt(
            PreferenceKeys.KEY_MUSIC_SONG_TITLE_DURATION,
            PreferenceKeys.DEFAULT_MUSIC_SONG_TITLE_DURATION
        )
        set(value) = prefs.edit { putInt(PreferenceKeys.KEY_MUSIC_SONG_TITLE_DURATION, value) }

    var musicSongTitleOpacity: Int
        get() = prefs.getInt(
            PreferenceKeys.KEY_MUSIC_SONG_TITLE_OPACITY,
            PreferenceKeys.DEFAULT_MUSIC_SONG_TITLE_OPACITY
        )
        set(value) = prefs.edit { putInt(PreferenceKeys.KEY_MUSIC_SONG_TITLE_OPACITY, value) }

    // Music per-state toggles
    var musicSystemEnabled: Boolean
        get() = prefs.getBoolean(
            PreferenceKeys.KEY_MUSIC_SYSTEM_ENABLED,
            PreferenceKeys.DEFAULT_MUSIC_SYSTEM_ENABLED
        )
        set(value) = prefs.edit { putBoolean(PreferenceKeys.KEY_MUSIC_SYSTEM_ENABLED, value) }

    var musicGameEnabled: Boolean
        get() = prefs.getBoolean(
            PreferenceKeys.KEY_MUSIC_GAME_ENABLED,
            PreferenceKeys.DEFAULT_MUSIC_GAME_ENABLED
        )
        set(value) = prefs.edit { putBoolean(PreferenceKeys.KEY_MUSIC_GAME_ENABLED, value) }

    var musicScreensaverEnabled: Boolean
        get() = prefs.getBoolean(
            PreferenceKeys.KEY_MUSIC_SCREENSAVER_ENABLED,
            PreferenceKeys.DEFAULT_MUSIC_SCREENSAVER_ENABLED
        )
        set(value) = prefs.edit { putBoolean(PreferenceKeys.KEY_MUSIC_SCREENSAVER_ENABLED, value) }

    var musicScrapeGameEnabled: Boolean
        get() = prefs.getBoolean(
            PreferenceKeys.KEY_MUSIC_SCRAPE_GAME_ENABLED,
            PreferenceKeys.DEFAULT_MUSIC_SCRAPE_GAME_ENABLED
        )
        set(value) = prefs.edit { putBoolean(PreferenceKeys.KEY_MUSIC_SCRAPE_GAME_ENABLED, value) }

    // ========== BEHAVIOR PROPERTIES ==========

    var gameLaunchBehavior: String
        get() = prefs.getString(
            PreferenceKeys.KEY_GAME_LAUNCH_BEHAVIOR,
            PreferenceKeys.DEFAULT_GAME_LAUNCH_BEHAVIOR
        ) ?: PreferenceKeys.DEFAULT_GAME_LAUNCH_BEHAVIOR
        set(value) = prefs.edit { putString(PreferenceKeys.KEY_GAME_LAUNCH_BEHAVIOR, value) }

    var screensaverBehavior: String
        get() = prefs.getString(
            PreferenceKeys.KEY_SCREENSAVER_BEHAVIOR,
            PreferenceKeys.DEFAULT_SCREENSAVER_BEHAVIOR
        ) ?: PreferenceKeys.DEFAULT_SCREENSAVER_BEHAVIOR
        set(value) = prefs.edit { putString(PreferenceKeys.KEY_SCREENSAVER_BEHAVIOR, value) }

    var systemBackgroundColor: Int
        get() = prefs.getInt(
            PreferenceKeys.KEY_SYSTEM_BACKGROUND_COLOR,
            PreferenceKeys.DEFAULT_SYSTEM_BACKGROUND_COLOR
        )
        set(value) = prefs.edit { putInt(PreferenceKeys.KEY_SYSTEM_BACKGROUND_COLOR, value) }

    var gameBackgroundColor: Int
        get() = prefs.getInt(
            PreferenceKeys.KEY_GAME_BACKGROUND_COLOR,
            PreferenceKeys.DEFAULT_GAME_BACKGROUND_COLOR
        )
        set(value) = prefs.edit { putInt(PreferenceKeys.KEY_GAME_BACKGROUND_COLOR, value) }

    var blackOverlayEnabled: Boolean
        get() = prefs.getBoolean(
            PreferenceKeys.KEY_BLACK_OVERLAY_ENABLED,
            PreferenceKeys.DEFAULT_BLACK_OVERLAY_ENABLED
        )
        set(value) = prefs.edit { putBoolean(PreferenceKeys.KEY_BLACK_OVERLAY_ENABLED, value) }

    var doubleTapVideoEnabled: Boolean
        get() = prefs.getBoolean(
            PreferenceKeys.KEY_VIDEO_DOUBLE_TAP_ENABLED,
            PreferenceKeys.DEFAULT_VIDEO_DOUBLE_TAP_ENABLED
        )
        set(value) = prefs.edit { putBoolean(PreferenceKeys.KEY_VIDEO_DOUBLE_TAP_ENABLED, value) }

    // ========== LOGO PROPERTIES ==========

    var showSystemLogo: Boolean
        get() = prefs.getBoolean(
            PreferenceKeys.KEY_SHOW_SYSTEM_LOGO,
            PreferenceKeys.DEFAULT_SHOW_SYSTEM_LOGO
        )
        set(value) = prefs.edit { putBoolean(PreferenceKeys.KEY_SHOW_SYSTEM_LOGO, value) }

    // ========== ANIMATION PROPERTIES ==========

    var animate: Int
        get() = prefs.getInt(
            PreferenceKeys.KEY_ANIMATION_STYLE,
            PreferenceKeys.DEFAULT_ANIMATION_STYLE
        )
        set(value) = prefs.edit { putInt(PreferenceKeys.KEY_ANIMATION_STYLE, value) }

    var animationDuration: Int
        get() = prefs.getInt(
            PreferenceKeys.KEY_ANIMATION_DURATION,
            PreferenceKeys.DEFAULT_ANIMATION_DURATION
        )
        set(value) = prefs.edit { putInt(PreferenceKeys.KEY_ANIMATION_DURATION, value) }

    var pageTransitionTarget: Int
        get() = prefs.getInt(PreferenceKeys.KEY_PAGE_TRANSITION_TARGET, PreferenceKeys.DEFAULT_PAGE_TRANSITION_TARGET)
        set(value) = prefs.edit {putInt(PreferenceKeys.KEY_PAGE_TRANSITION_TARGET, value)}

    var animateWidgets: Boolean
        get() = prefs.getBoolean(PreferenceKeys.KEY_ANIMATE_WIDGETS, PreferenceKeys.DEFAULT_ANIMATE_WIDGETS)
        set(value) = prefs.edit {putBoolean(PreferenceKeys.KEY_ANIMATE_WIDGETS, value)}

    // ========== SETUP/STATE PROPERTIES ==========

    var setupCompleted: Boolean
        get() = prefs.getBoolean(
            PreferenceKeys.KEY_SETUP_COMPLETED,
            PreferenceKeys.DEFAULT_SETUP_COMPLETED
        )
        set(value) = prefs.edit { putBoolean(PreferenceKeys.KEY_SETUP_COMPLETED, value) }

    var tutorialVersionShown: String
        get() = prefs.getString(
            PreferenceKeys.KEY_TUTORIAL_VERSION_SHOWN,
            PreferenceKeys.DEFAULT_TUTORIAL_VERSION_SHOWN
        ) ?: PreferenceKeys.DEFAULT_TUTORIAL_VERSION_SHOWN
        set(value) = prefs.edit { putString(PreferenceKeys.KEY_TUTORIAL_VERSION_SHOWN, value) }

    var settingsHintCount: Int
        get() = prefs.getInt(
            PreferenceKeys.KEY_SETTINGS_HINT_COUNT,
            PreferenceKeys.DEFAULT_SETTINGS_HINT_COUNT
        )
        set(value) = prefs.edit { putInt(PreferenceKeys.KEY_SETTINGS_HINT_COUNT, value) }

    var hiddenApps: Set<String>
        get() = prefs.getStringSet(
            PreferenceKeys.KEY_HIDDEN_APPS,
            emptySet()
        ) ?: emptySet()
        set(value) = prefs.edit { putStringSet(PreferenceKeys.KEY_HIDDEN_APPS, value) }

    var defaultWidgetsCreated: Boolean
        get() = prefs.getBoolean(
            PreferenceKeys.KEY_DEFAULT_WIDGETS_CREATED,
            PreferenceKeys.DEFAULT_DEFAULT_WIDGETS_CREATED
        )
        set(value) = prefs.edit { putBoolean(PreferenceKeys.KEY_DEFAULT_WIDGETS_CREATED, value) }

    var widgetTutorialShown: Boolean
        get() = prefs.getBoolean(
            PreferenceKeys.KEY_WIDGET_TUTORIAL_SHOWN,
            PreferenceKeys.DEFAULT_WIDGET_TUTORIAL_SHOWN
        )
        set(value) = prefs.edit { putBoolean(PreferenceKeys.KEY_WIDGET_TUTORIAL_SHOWN, value) }

    var widgetTutorialDontShowAuto: Boolean
        get() = prefs.getBoolean(
            PreferenceKeys.KEY_WIDGET_TUTORIAL_DONT_SHOW_AUTO,
            PreferenceKeys.DEFAULT_WIDGET_TUTORIAL_DONT_SHOW_AUTO
        )
        set(value) = prefs.edit { putBoolean(PreferenceKeys.KEY_WIDGET_TUTORIAL_DONT_SHOW_AUTO, value) }

    // ========== HELPER METHODS ==========

    /**
     * Clear all preferences (useful for testing or factory reset).
     */
    fun clearAll() {
        prefs.edit { clear() }
    }

    /**
     * Check if a preference exists.
     */
    fun contains(key: String): Boolean {
        return prefs.contains(key)
    }

    /**
     * Get raw SharedPreferences instance (for legacy compatibility during migration).
     *
     * DEPRECATION WARNING: This should only be used during migration.
     * New code should use the type-safe properties above.
     */
    @Deprecated(
        message = "Use type-safe properties instead",
        replaceWith = ReplaceWith("prefsManager.propertyName")
    )
    fun getRawPreferences(): SharedPreferences {
        return prefs
    }
}