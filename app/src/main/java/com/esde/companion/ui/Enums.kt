package com.esde.companion.ui

enum class WidgetContext {
    GAME,    // Shows in game view
    SYSTEM   // Shows in system view
}

enum class ScaleType {
    FIT,      // Fit to container with no cropping (default)
    CROP      // Fill container with cropping (centerCrop)
}

enum class PageAnimation {
    CONTEXT,
    PAGE,
    NONE;

    fun toDisplayName(): String = when(this) {
        PageAnimation.CONTEXT -> "On game/system switch"
        PageAnimation.PAGE -> "On every page switch"
        PageAnimation.NONE -> "Never"
    }
}

enum class AnimationStyle(val label: String) {
    NONE("None"),
    FADE("Fade"),
    SCALE_FADE("Scale + Fade")
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
    SYSTEM_IMAGE,
    GAME_DESCRIPTION,
    VIDEO,
    TITLE,
    DEVELOPER,
    PUBLISHER,
    GENRE,
    RELEASE_DATE,
    COLOR_BACKGROUND,
    CUSTOM_IMAGE;

    fun toDisplayName(): String = when(this) {
        ContentType.BOX_2D -> "2D Boxart"
        ContentType.BOX_3D -> "3D Boxart"
        else -> this.name.replace("_", " ").lowercase().capitalize()
    }

    fun isTextWidget(): Boolean = when(this) {
        ContentType.GAME_DESCRIPTION -> true
        ContentType.TITLE -> true
        ContentType.DEVELOPER -> true
        ContentType.PUBLISHER -> true
        ContentType.GENRE -> true
        ContentType.RELEASE_DATE -> true
        else -> false
    }
}

enum class TextAlignment {
    LEFT,
    CENTER,
    RIGHT;

    fun toDisplayName(): String = when (this) {
        LEFT -> "Left"
        CENTER -> "Centered"
        RIGHT -> "Right"
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


    fun toDisplayName(): String = when (this) {
        PageContentType.FANART -> "Fan art"
        PageContentType.SOLID_COLOR -> "Solid color"
        PageContentType.CUSTOM_IMAGE -> "Custom image"
        else -> this.name.replace("_", " ").lowercase().capitalize()
    }


    enum class FontType {
        SERIF,
        MONO,
        SANSSERIF,
        DEFAULT;

        fun toDisplayName(): String = when (this) {
            SERIF -> "Serif"
            MONO -> "Monospace"
            SANSSERIF -> "Sans Serif"
            DEFAULT -> "Default"
            else -> this.name.replace("_", " ").lowercase().capitalize()
        }
    }
}