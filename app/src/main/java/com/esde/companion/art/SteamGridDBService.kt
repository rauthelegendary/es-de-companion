package com.esde.companion.art

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface SteamGridDBService {

    @GET("search/autocomplete/{term}")
    suspend fun searchGame(
        @Header("Authorization") auth: String,
        @Path("term") gameName: String
    ): SGDBResponse<List<SGDBGame>>

    @GET("heroes/game/{id}")
    suspend fun getHeroes(
        @Header("Authorization") auth: String,
        @Path("id") gameId: Int
    ): SGDBResponse<List<SGDBImage>>
}