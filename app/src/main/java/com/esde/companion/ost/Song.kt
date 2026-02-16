package com.esde.companion.ost

import java.io.File

data class Song(
    val file: File,
    var loudnessDb: Double = 1.0
)