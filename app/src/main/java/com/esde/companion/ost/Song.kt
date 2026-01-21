package com.esde.companion.ost

import com.esde.companion.ost.loudness.TARGET_LOUDNESS_DB
import java.io.File

data class Song(
    val file: File,
    var loudnessDb: Double = TARGET_LOUDNESS_DB
)