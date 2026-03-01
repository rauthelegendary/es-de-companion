package com.esde.companion

import android.widget.ImageView
import com.esde.companion.data.Widget
import com.esde.companion.data.Widget.MediaSlot
import com.esde.companion.ui.ContentType
import com.esde.companion.ui.PageContentType
import com.esde.companion.ui.ScaleType
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class WidgetPage(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var widgets: MutableList<Widget> = mutableListOf(),
    var backgroundType: PageContentType = PageContentType.FANART,
    var backgroundPath: String? = null,
    var slot: MediaSlot = MediaSlot.Default,
    var backgroundOpacity: Float = 1.0f,
    var isVideoMuted: Boolean = true,
    var videoVolume: Float = 0.5f,
    var blurRadius: Float = 0f,
    var panZoomAnimation: Boolean = true,
    var solidColor: Int? = null,
    var customPath: String? = null,
    var videoDelay: Int = 0,
    var displayWidgets: Boolean = true,
    var isRequired: Boolean = false,
    var displayWidgetsOverVideo: Boolean = true,
    var backgroundFallbackType: PageContentType = PageContentType.FANART,
    var transitionTargetPageId: String = "",
    var transitionToPage: Boolean = false,
    var transitionToPageAfterVideo: Boolean = false,
    var transitionDelay: Int = 2,
    var isDefault: Boolean = false,
    var scaleType: ScaleType = ScaleType.CROP,
    var transitionOnly: Boolean = false
)

fun WidgetPage.hasSameVisualSettings(other: WidgetPage): Boolean {
    if(this.backgroundType == other.backgroundType) {
        return when (this.backgroundType) {
            PageContentType.SOLID_COLOR -> {
                this.solidColor == other.solidColor
            }
            PageContentType.CUSTOM_IMAGE -> {
                this.customPath == other.customPath && panZoomAnimation == other.panZoomAnimation
            }
            PageContentType.CUSTOM_FOLDER -> {
                customPath == other.customPath && backgroundFallbackType == other.backgroundFallbackType && panZoomAnimation == other.panZoomAnimation
            }
            else -> {
                panZoomAnimation == other.panZoomAnimation
            }
        }
    }
    return false
}

fun WidgetPage.resetValuesForType() {
    if(backgroundType == PageContentType.SOLID_COLOR) {
        backgroundPath = null
        slot = MediaSlot.Default
        panZoomAnimation = true
        customPath = null
    } else if(backgroundType == PageContentType.CUSTOM_IMAGE || backgroundType == PageContentType.CUSTOM_FOLDER) {
        slot = MediaSlot.Default
        solidColor = null
    } else {
        customPath = null
        solidColor = null
    }
}