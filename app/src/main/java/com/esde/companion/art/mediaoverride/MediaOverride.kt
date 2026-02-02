package com.esde.companion.art.mediaoverride

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.esde.companion.OverlayWidget

@Entity(tableName = "media_overrides")
data class MediaOverride(
    @Embedded @PrimaryKey val key: MediaOverrideKey,
    val altSlot: OverlayWidget.MediaSlot
)