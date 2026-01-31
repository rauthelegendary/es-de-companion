package com.esde.companion.art.LaunchBox

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LaunchBoxDao {
    @Query("SELECT * FROM launchbox_games WHERE name LIKE :query LIMIT 100")
    suspend fun searchGames(query: String): List<LaunchBoxGame>

    @Query("SELECT * FROM launchbox_images WHERE databaseId = :dbId")
    suspend fun getImagesForGame(dbId: String): List<LaunchBoxImage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGames(games: List<LaunchBoxGame>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImages(images: List<LaunchBoxImage>)

    @Query("DELETE FROM launchbox_games")
    suspend fun clearGames()

    @Query("DELETE FROM launchbox_images")
    suspend fun clearImages()
}