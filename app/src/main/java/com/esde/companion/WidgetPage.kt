package com.esde.companion

import com.esde.companion.OverlayWidget.MediaSlot
import com.esde.companion.ui.PageContentType
import java.util.UUID

data class WidgetPage(
    val id: String = UUID.randomUUID().toString(),
    var widgets: MutableList<OverlayWidget> = mutableListOf(),
    var backgroundType: PageContentType = PageContentType.FANART,
    var backgroundPath: String? = null,
    var slot: MediaSlot = MediaSlot.Default,
    var backgroundOpacity: Float = 1.0f,
    var isVideoMuted: Boolean = true,
    var blurRadius: Float = 0f,
    var swapAnimation: Boolean = true,
    var animationDuration: Int = 250,
    var panZoomAnimation: Boolean = true,
    var solidColor: Int? = null,
    var customPath: String? = null,
    var videoDelay: Int = 0,
    var displayWidgets: Boolean = true,
    var isRequired: Boolean = false
)

fun WidgetPage.hasSameVisualSettings(other: WidgetPage): Boolean {
    return this.backgroundType == other.backgroundType &&
            this.backgroundPath == other.backgroundPath &&
            this.slot == other.slot &&
            this.backgroundOpacity == other.backgroundOpacity &&
            this.isVideoMuted == other.isVideoMuted &&
            this.blurRadius == other.blurRadius &&
            this.swapAnimation == other.swapAnimation &&
            this.animationDuration == other.animationDuration &&
            this.panZoomAnimation == other.panZoomAnimation &&
            this.solidColor == other.solidColor &&
            this.customPath == other.customPath &&
            this.videoDelay == other.videoDelay &&
            this.displayWidgets == other.displayWidgets
}