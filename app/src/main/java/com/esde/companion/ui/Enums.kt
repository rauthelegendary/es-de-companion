package com.esde.companion.ui

enum class WidgetContext {
    GAME,    // Shows in game view
    SYSTEM   // Shows in system view
}

enum class ScaleType {
    FIT,      // Fit to container with no cropping (default)
    CROP      // Fill container with cropping (centerCrop)
}

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

enum class PageContentType {
    FANART,
    SCREENSHOT,
    VIDEO,
    SOLID_COLOR,
    SYSTEM_IMAGE,
    CUSTOM_IMAGE;


    fun toDisplayName(): String = when(this) {
        PageContentType.FANART -> "Fan art"
        PageContentType.SOLID_COLOR -> "Solid color"
        PageContentType.CUSTOM_IMAGE -> "Custom image"
        else -> this.name.replace("_", " ").lowercase().capitalize()
    }
}