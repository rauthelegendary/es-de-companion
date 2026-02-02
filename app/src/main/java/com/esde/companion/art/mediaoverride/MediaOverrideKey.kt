package com.esde.companion.art.mediaoverride

import com.esde.companion.ui.ContentType

data class MediaOverrideKey(
    val filePath: String,
    val system: String,
    val contentType: ContentType
)