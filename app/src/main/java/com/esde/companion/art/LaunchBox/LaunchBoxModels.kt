package com.esde.companion.art.LaunchBox

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "launchbox_games",
    indices = [Index(value = ["name"]), Index(value = ["platform"])]
)
data class LaunchBoxGame(
    @PrimaryKey val databaseId: String,
    val name: String,
    val platform: String,
    val releaseDate: String?,
    val videoUrl: String?
)

@Entity(
    tableName = "launchbox_images",
    indices = [Index(value = ["databaseId"])]
)
data class LaunchBoxImage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val databaseId: String,
    val type: String,
    val region: String?,
    val fileName: String
)