package com.esde.companion.metadata

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GameDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGames(games: List<ESGameEntity>)

    // Now you query using both parts of the key
    @Query("SELECT * FROM es_games WHERE romPath = :path AND system = :system LIMIT 1")
    suspend fun getGame(path: String, system: String): ESGameEntity?

    @Query("DELETE FROM es_games WHERE system = :system")
    suspend fun deleteSystem(system: String)
}