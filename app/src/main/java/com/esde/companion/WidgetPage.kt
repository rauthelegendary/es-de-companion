package com.esde.companion

import com.esde.companion.data.Widget
import com.esde.companion.data.Widget.MediaSlot
import com.esde.companion.ui.ContentType
import com.esde.companion.ui.PageContentType
import java.util.UUID

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
    var displayWidgetsOverVideo: Boolean = false,
    var backgroundFallbackType: PageContentType = PageContentType.FANART
)

fun WidgetPage.hasSameVisualSettings(other: WidgetPage): Boolean {
    if(this.backgroundType == other.backgroundType) {
        if(this.backgroundType == PageContentType.VIDEO) {
            return this.isVideoMuted == other.isVideoMuted &&
                    this.videoDelay == other.videoDelay &&
                    this.videoVolume == other.videoVolume &&
                    this.backgroundPath == other.backgroundPath &&
                    this.slot == other.slot
        } else if (this.panZoomAnimation == other.panZoomAnimation) {
            if (this.backgroundType == PageContentType.SOLID_COLOR) {
                if(this.solidColor == other.solidColor) return true
            } else if(this.backgroundType == PageContentType.CUSTOM_IMAGE) {
                if(this.customPath == other.customPath) return true
            } else if(this.slot == other.slot && this.backgroundPath == other.backgroundPath) {
                return true
            }
        }
    }
    return false
}