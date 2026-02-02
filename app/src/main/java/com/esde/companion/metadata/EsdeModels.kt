package com.esde.companion.metadata

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "es_games",
    primaryKeys = ["romPath", "system"]
)
data class ESGameEntity(
    val romPath: String,
    val system: String,
    val name: String,
    val description: String?,
    val developer: String?,
    val publisher: String?,
    val genre: String?,
    val releaseDate: String?
)

@Entity(tableName = "sync_log")
data class SyncLog(
    @PrimaryKey val system: String,
    val lastModified: Long
)
