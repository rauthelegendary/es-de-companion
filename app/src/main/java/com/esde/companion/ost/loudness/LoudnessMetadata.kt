package com.esde.companion.ost.loudness

import androidx.room.Entity

@Entity(
    tableName = "loudness_metadata",
    primaryKeys = ["fileName", "system"]
)
data class LoudnessMetadata(
    val fileName: String,
    val system: String,
    val integratedLoudness: Double,
    val lastModified: Long
)