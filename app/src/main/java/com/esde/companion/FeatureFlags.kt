package com.esde.companion

/**
 * ═══════════════════════════════════════════════════════════
 * FEATURE FLAGS
 * ═══════════════════════════════════════════════════════════
 * Centralized feature toggles for ES-DE Companion.
 *
 * These flags allow features to be easily disabled at compile
 * time or removed entirely from the codebase.
 * ═══════════════════════════════════════════════════════════
 */
object FeatureFlags {

    /**
     * ═══════════════════════════════════════════════════════════
     * BACKGROUND MUSIC FEATURE
     * ═══════════════════════════════════════════════════════════
     * Controls the background music system that plays audio while
     * browsing systems and games in ES-DE.
     *
     * WHY THIS MIGHT BE TEMPORARY:
     * ES-DE may incorporate native background music support in a
     * future release. If that happens, this feature can be cleanly
     * removed from ES-DE Companion.
     *
     * TO DISABLE THIS FEATURE:
     * Set ENABLE_BACKGROUND_MUSIC = false
     *
     * TO REMOVE THIS FEATURE ENTIRELY:
     * 1. Set ENABLE_BACKGROUND_MUSIC = false and test
     * 2. Search codebase for "========== MUSIC" markers
     * 3. Delete all code blocks between markers
     * 4. Delete the /music/ package folder
     * 5. Remove music settings section from activity_settings.xml
     * 6. Remove music preference keys (see removal checklist below)
     * 7. Delete this flag from FeatureFlags.kt
     *
     * REMOVAL CHECKLIST:
     * □ Delete: /app/src/main/java/com/esde/companion/music/
     * □ Delete: MainActivity music integration blocks (marked)
     * □ Delete: SettingsActivity music setup blocks (marked)
     * □ Delete: Music settings section in activity_settings.xml
     * □ Clean: Remove "music.*" SharedPreferences keys
     * □ Test: Compile and verify no references remain
     * ═══════════════════════════════════════════════════════════
     */
    const val ENABLE_BACKGROUND_MUSIC = true
}