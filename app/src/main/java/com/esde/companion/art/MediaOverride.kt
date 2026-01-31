package com.esde.companion.art

import androidx.room.Entity
import com.esde.companion.OverlayWidget
import com.esde.companion.ui.ContentType

@Entity(
    tableName = "media_overrides",
    primaryKeys = ["filePath", "contentType"]
)
//used to signal which local media should be used to override the default retrieved from ES-DE folders
data class MediaOverride(
    val filePath: String,  //game file path as ID
    val contentType: ContentType, //content type being overridden
    val altSlot: OverlayWidget.MediaSlot //the slot that overrides
)