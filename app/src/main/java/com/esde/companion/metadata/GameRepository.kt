package com.esde.companion.metadata

class GameRepository(private val gameDao: GameDao) {

    //rompath is in ES-DE gamelist style, example:  "./Battalion Wars (USA).rvz"
    suspend fun getMetadata(romPath: String, system: String): ESGameEntity? {
        return gameDao.getGame(romPath, system)
    }
}