package com.esde.companion.data

import com.esde.companion.ui.ContentType
import com.esde.companion.ui.PageContentType
import com.esde.companion.ui.ScaleType
import com.esde.companion.ui.TextAlignment
import java.io.File
import java.util.UUID

data class Widget(
    val id: String = UUID.randomUUID().toString(),
    val contentType: ContentType,
    var contentPath: String? = "",
    var text: String = "",
    var fontType: PageContentType.FontType = PageContentType.FontType.DEFAULT,
    var fontSize: Float = 24f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val textPadding: Int = 20,
    val shadowRadius: Float = 6f,
    var scrollText: Boolean = false,
    var textAlignment: TextAlignment = TextAlignment.LEFT,
    var x: Float,
    var y: Float,
    var width: Float,
    var height: Float,
    var zIndex: Int = 0,
    var backgroundOpacity: Float = 0.2f,
    var scaleType: ScaleType = ScaleType.FIT,
    var xPercent: Float? = null,
    var yPercent: Float? = null,
    var widthPercent: Float? = null,
    var heightPercent: Float? = null,
    var slot: MediaSlot = MediaSlot.Default,
    var playAudio: Boolean = true,
    var videoVolume: Float = 0.5f,
    var isRequired: Boolean = false,
    var cycle: Boolean = false,
    var solidColor: Int? = null,
    @Transient
    var images: Map<MediaSlot, File?>? = emptyMap()
) {
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

    enum class MediaSlot(val index: Int) {
        Default(0),
        Slot1(1),
        Slot2(2),
        Slot3(3);

        val suffix: String get() = if (this == Default) "" else "_alt$index"

        companion object {
            fun fromInt(value: Int) = entries.find { it.index == value } ?: Default
        }
    }
}