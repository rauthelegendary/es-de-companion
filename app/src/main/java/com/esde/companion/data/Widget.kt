package com.esde.companion.data

import android.R.attr.height
import android.R.attr.x
import android.R.attr.y
import androidx.compose.ui.graphics.SolidColor
import com.esde.companion.ui.ContentType
import com.esde.companion.ui.PageContentType
import com.esde.companion.ui.ScaleType
import com.esde.companion.ui.TextAlignment
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.io.File
import java.util.UUID

@Serializable
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
    var glint: Boolean = true,
    var customPath: String = "",
    var contentFallbackType: ContentType = ContentType.MARQUEE,
    var ignoreFallback: Boolean = false,
    @Transient
    var files: Map<MediaSlot, File?>? = emptyMap(),
    @Transient
    var video: Boolean = false,
    @Transient
    var fallback: Boolean = false
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

    fun hasSameVisualSettings(other: Widget): Boolean {
        if(this.contentType == other.contentType && this.width == other.width && this.height == other.height) {
            if(this.contentType == ContentType.VIDEO) {
                return this.playAudio == other.playAudio &&
                        this.contentPath == other.contentPath &&
                        this.videoVolume == other.videoVolume &&
                        this.slot == other.slot
            } else if (this.contentType == ContentType.COLOR_BACKGROUND) {
                    return this.solidColor == other.solidColor
            } else if(this.contentType.isTextWidget()){
                return this.fontType == other.fontType
                        && this.fontSize == other.fontSize
                        && this.textAlignment == other.textAlignment
                        && this.text == other.text
                        && this.textPadding == other.textPadding
                        && this.isItalic == other.isItalic
                        && this.isBold == other.isBold
                        && this.scrollText == other.scrollText
                        && this.backgroundOpacity == other.backgroundOpacity
            } else if(this.contentType == ContentType.MARQUEE && this.glint != other.glint) {
                return false
            } else {
                return this.slot == other.slot &&
                        this.contentPath == other.contentPath &&
                        this.cycle == other.cycle &&
                        this.scaleType == other.scaleType
            }
        }
        return false
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