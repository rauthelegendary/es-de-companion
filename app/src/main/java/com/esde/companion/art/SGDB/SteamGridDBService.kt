package com.esde.companion.art.SGDB

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SteamGridDBService {

    @GET("search/autocomplete/{term}")
    suspend fun searchGame(
        @Path("term") gameName: String
    ): SGDBResponse<List<SGDBGame>>

    @GET("heroes/game/{id}")
    suspend fun getHeroes(
        @Path("id") gameId: String
    ): SGDBResponse<List<SGDBImage>>

    @GET("grids/game/{id}")
    suspend fun getGrids(
        @Path("id") gameId: String
    ): SGDBResponse<List<SGDBImage>>

    @GET("logos/game/{id}")
    suspend fun getLogos(
        @Path("id") gameId: String,
        @Query("types") types: String = "static,animated"
    ): SGDBResponse<List<SGDBImage>>
}