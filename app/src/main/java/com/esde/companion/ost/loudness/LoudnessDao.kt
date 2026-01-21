package com.esde.companion.ost.loudness

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LoudnessDao {
    @Query("SELECT * FROM loudness_metadata WHERE fileName = :path LIMIT 1")
    suspend fun getMetadata(path: String): LoudnessMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: LoudnessMetadata)

    @Query("DELETE FROM loudness_metadata")
    suspend fun clearAllLoudnessData()
}