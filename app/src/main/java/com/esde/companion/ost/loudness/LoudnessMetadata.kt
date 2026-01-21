package com.esde.companion.ost.loudness

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "loudness_metadata")
data class LoudnessMetadata(
    @PrimaryKey val fileName: String,
    val integratedLoudness: Double,
    val lastModified: Long
)