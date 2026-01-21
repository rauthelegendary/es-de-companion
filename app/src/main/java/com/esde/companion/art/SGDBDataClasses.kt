package com.esde.companion.art

data class SGDBResponse<T>(
    val success: Boolean,
    val data: T
)

data class SGDBGame(
    val id: Int,
    val name: String
)

data class SGDBImage(
    val id: Int,
    val url: String,
    val thumb: String, // Smaller version for previews
    val votes: Int     // Helpful to pick the best community-rated fanart
)