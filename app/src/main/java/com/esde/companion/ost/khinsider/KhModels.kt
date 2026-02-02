package com.esde.companion.ost.khinsider

data class KhAlbum(
    val title: String,
    val url: String,
    val iconUrl: String? = null
)

data class KhSong(
    val title: String,
    val detailUrl: String
)

data class KhPlayable(
    val title: String,
    val directAudioUrl: String
)