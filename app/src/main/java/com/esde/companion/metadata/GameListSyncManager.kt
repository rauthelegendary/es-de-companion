package com.esde.companion.metadata

import android.content.Context
import android.util.Xml
import androidx.room.withTransaction
import com.esde.companion.AppDatabase
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream

object GameListSyncManager {

    suspend fun syncAll(context: Context, esGamelistDir: File) {
        val db = AppDatabase.getDatabase(context)
        val systems = esGamelistDir.listFiles()?.filter { it.isDirectory } ?: return

        systems.forEach { systemDir ->
            val xmlFile = File(systemDir, "gamelist.xml")
            if (!xmlFile.exists()) return@forEach

            val systemName = systemDir.name
            val lastSync = db.syncDao().getSyncLog(systemName)?.lastModified ?: 0L

            if (xmlFile.lastModified() > lastSync) {
                val parsedGames = parseESXml(xmlFile.inputStream(), systemName)

                db.withTransaction {
                    db.gameDao().insertGames(parsedGames)
                    db.syncDao().upsertSync(SyncLog(systemName, xmlFile.lastModified()))
                }
            }
        }
    }

    fun parseESXml(inputStream: InputStream, system: String): List<ESGameEntity> {
        val games = mutableListOf<ESGameEntity>()
        val parser = Xml.newPullParser().apply { setInput(inputStream, null) }
        var eventType = parser.eventType
        var currentTag: String? = null
        var currentGameMap = mutableMapOf<String, String>()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> currentTag = parser.name
                XmlPullParser.TEXT -> {
                    val text = parser.text.trim()
                    if (text.isNotEmpty()) currentGameMap[currentTag ?: ""] = text
                }
                XmlPullParser.END_TAG -> if (parser.name == "game") {
                    games.add(ESGameEntity(
                        romPath = currentGameMap["path"] ?: "",
                        system = system,
                        name = currentGameMap["name"] ?: "Unknown",
                        description = currentGameMap["desc"],
                        developer = currentGameMap["developer"],
                        publisher = currentGameMap["publisher"],
                        genre = currentGameMap["genre"],
                        releaseDate = currentGameMap["releasedate"]
                    ))
                    currentGameMap = mutableMapOf()
                }
            }
            eventType = parser.next()
        }
        return games
    }
}