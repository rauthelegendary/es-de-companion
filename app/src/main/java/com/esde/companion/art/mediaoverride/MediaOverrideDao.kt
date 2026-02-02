package com.esde.companion.art.mediaoverride

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MediaOverrideDao {
    @Query("SELECT * FROM media_overrides")
    fun getAllOverrides(): List<MediaOverride>

    @Query("SELECT * FROM media_overrides WHERE filePath = :filePath AND system = :system AND contentType = :contentType")
    fun getOverride(filePath: String, system: String, contentType: String): MediaOverride

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveOverride(override: MediaOverride)

    @Query("DELETE FROM media_overrides WHERE filePath = :filePath AND system = :system AND contentType = :contentType")
    suspend fun deleteOverride(filePath: String, system: String, contentType: String)
}