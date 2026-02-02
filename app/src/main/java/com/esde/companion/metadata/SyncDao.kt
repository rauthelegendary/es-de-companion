package com.esde.companion.metadata

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSync(log: SyncLog)

    @Query("SELECT * FROM sync_log WHERE system = :system")
    suspend fun getSyncLog(system: String): SyncLog?
}