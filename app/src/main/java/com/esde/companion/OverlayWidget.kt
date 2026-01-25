package com.esde.companion

import java.util.UUID

data class OverlayWidget(
    val id: String = UUID.randomUUID().toString(),
    val contentType: ContentType,
    var contentPath: String,
    var description: String,
    var x: Float,
    var y: Float,
    var width: Float,
    var height: Float,
    var zIndex: Int = 0,
    var backgroundOpacity: Float = 0.2f,
    var scaleType: ScaleType = ScaleType.FIT,
    val widgetContext: WidgetContext = WidgetContext.GAME,
    var xPercent: Float? = null,
    var yPercent: Float? = null,
    var widthPercent: Float? = null,
    var heightPercent: Float? = null,
    var slotIndex: Int = 0,
    var playAudio: Boolean = true
) {
    val slot: MediaSlot get() = MediaSlot.fromInt(slotIndex)

    enum class ContentType {
        MARQUEE,
        BOX_2D,
        BOX_3D,
        MIX_IMAGE,
        BACK_COVER,
        PHYSICAL_MEDIA,
        SCREENSHOT,
        FANART,
        TITLE_SCREEN,
        SYSTEM_LOGO,
        GAME_DESCRIPTION,
        VIDEO;

        fun toDisplayName(): String = when(this) {
            ContentType.BOX_2D -> "2D Boxart"
            ContentType.BOX_3D -> "3D Boxart"
            ContentType.SYSTEM_LOGO -> "Logo"
            else -> this.name.replace("_", " ").lowercase().capitalize()
        }
    }

    enum class WidgetContext {
        GAME,    // Shows in game view
        SYSTEM   // Shows in system view
    }

    enum class ScaleType {
        FIT,      // Fit to container with no cropping (default)
        CROP      // Fill container with cropping (centerCrop)
    }

    /**
     * Convert absolute pixels to percentages based on screen dimensions
     */
    fun toPercentages(screenWidth: Int, screenHeight: Int) {
        xPercent = (x / screenWidth) * 100f
        yPercent = (y / screenHeight) * 100f
        widthPercent = (width / screenWidth) * 100f
        heightPercent = (height / screenHeight) * 100f
    }

    /**
     * Convert percentages to absolute pixels based on screen dimensions
     */
    fun fromPercentages(screenWidth: Int, screenHeight: Int) {
        // Only apply percentages if they exist (for migration)
        if (xPercent != null && yPercent != null && widthPercent != null && heightPercent != null) {
            x = (xPercent!! / 100f) * screenWidth
            y = (yPercent!! / 100f) * screenHeight
            width = (widthPercent!! / 100f) * screenWidth
            height = (heightPercent!! / 100f) * screenHeight
        }
    }

    //this is just a wrapper for the slotIndex value. Assuming 0 is default everywhere in the code is ugly and prone to breaking if the logic ever changes, so we use this to define what default or alternative means
    sealed class MediaSlot(val index: Int) {
        object Default : MediaSlot(0)
        data class Alternative(val id: Int) : MediaSlot(id)

        val suffix: String get() = when(this) {
            is Default -> ""
            is Alternative -> "_alt$id"
        }

        companion object {
            fun fromInt(index: Int): MediaSlot =
                if (index <= 0) Default else Alternative(index)
        }
    }
}