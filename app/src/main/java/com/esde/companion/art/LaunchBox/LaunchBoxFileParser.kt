package com.esde.companion.art.LaunchBox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

object LaunchBoxFileParser {

    suspend fun importLaunchBoxMetadata(zipFile: File, dao: LaunchBoxDao, onProgress: (Float, String) -> Unit) {
        withContext(Dispatchers.IO) {
            val inputStream = FileInputStream(zipFile)
            val zipStream = ZipInputStream(BufferedInputStream(inputStream))

            dao.clearGames()
            dao.clearImages()

            var entry = zipStream.nextEntry
            while (entry != null) {
                if (entry.name.equals("Metadata.xml", ignoreCase = true)) {
                    val factory = XmlPullParserFactory.newInstance()
                    val parser = factory.newPullParser()
                    parser.setInput(zipStream, "UTF-8")

                    parseAndInsert(parser, dao, onProgress)
                    break
                }
                entry = zipStream.nextEntry
            }
            zipStream.close()
        }
    }

    private suspend fun parseAndInsert(parser: XmlPullParser, dao: LaunchBoxDao, onProgress: (Float, String) -> Unit) {
        val gameBuffer = mutableListOf<LaunchBoxGame>()
        val imageBuffer = mutableListOf<LaunchBoxImage>()
        val TOTAL_ESTIMATED = 1_475_000
        val BATCH_SIZE = 2000
        var processedCount = 0

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name

            if (eventType == XmlPullParser.START_TAG) {
                if (tagName == "Game" || tagName == "GameImage") {
                    processedCount++
                    if (processedCount % 2000 == 0) {
                        val percent = processedCount.toFloat() / TOTAL_ESTIMATED
                        onProgress(percent, "Imported $processedCount records...")
                    }
                }
                when (tagName) {
                    "Game" -> {
                        gameBuffer.add(parseGame(parser))
                        if (gameBuffer.size >= BATCH_SIZE) {
                            dao.insertGames(gameBuffer.toList())
                            gameBuffer.clear()
                        }
                    }
                    "GameImage" -> {
                        imageBuffer.add(parseImage(parser))
                        if (imageBuffer.size >= BATCH_SIZE) {
                            dao.insertImages(imageBuffer.toList())
                            imageBuffer.clear()
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        if (gameBuffer.isNotEmpty()) dao.insertGames(gameBuffer)
        if (imageBuffer.isNotEmpty()) dao.insertImages(imageBuffer)
    }

    private fun parseGame(parser: XmlPullParser): LaunchBoxGame {
        var dbId = ""
        var name = ""
        var platform = ""
        var releaseDate: String? = null
        var videoUrl: String? = null

        while (!(parser.next() == XmlPullParser.END_TAG && parser.name == "Game")) {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "DatabaseID" -> dbId = parser.nextText()
                "Name" -> name = parser.nextText()
                "Platform" -> platform = parser.nextText()
                "ReleaseDate" -> releaseDate = parser.nextText()
                "VideoURL" -> videoUrl = parser.nextText()
                else -> skip(parser)
            }
        }
        return LaunchBoxGame(dbId, name, platform, releaseDate, videoUrl)
    }

    private fun parseImage(parser: XmlPullParser): LaunchBoxImage {
        var dbId = ""
        var type = ""
        var region: String? = null
        var fileName = ""

        while (!(parser.next() == XmlPullParser.END_TAG && parser.name == "GameImage")) {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "DatabaseID" -> dbId = parser.nextText()
                "Type" -> type = parser.nextText()
                "Region" -> region = parser.nextText()
                "FileName" -> fileName = parser.nextText()
                else -> skip(parser)
            }
        }
        return LaunchBoxImage(databaseId = dbId, type = type, region = region, fileName = fileName)
    }

    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }
}