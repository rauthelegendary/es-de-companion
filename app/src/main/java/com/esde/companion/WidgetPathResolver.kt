package com.esde.companion

import android.content.Context
import android.content.SharedPreferences
import android.util.DisplayMetrics
import android.util.Log
import java.io.File

class WidgetResourceResolver(private val mediaLocator: MediaFileLocator, private val prefs: SharedPreferences) {

    fun resolve(
        rawWidgets: List<OverlayWidget>,
        system: String?,
        gameFilename: String?,
        metrics: DisplayMetrics
    ): List<OverlayWidget> {
        if (system != null) {
            return rawWidgets.map { widget -> resolveSingle(widget, system, gameFilename, metrics)}
        }
        return emptyList()
    }

    fun resolveSingle(
        rawWidget: OverlayWidget,
        system: String?,
        gameFilename: String?,
        metrics: DisplayMetrics
    ): OverlayWidget {
        val resolvedWidget = rawWidget.copy()
        if (system != null) {
            if(rawWidget.contentType == OverlayWidget.ContentType.GAME_DESCRIPTION && gameFilename != null) {
                resolvedWidget.description = getGameDescription(system, gameFilename)!!
            } else if (rawWidget.contentType == OverlayWidget.ContentType.SYSTEM_LOGO) {
                resolvedWidget.imagePath = findSystemLogo(system) ?: ""
            } else if(gameFilename != null){
                val mediaFile = mediaLocator.findMediaFile(rawWidget.contentType, system, gameFilename)
                if (mediaFile != null) {
                    resolvedWidget.imagePath = mediaFile.absolutePath
                }
            }
        }
        resolvedWidget.fromPercentages(metrics.widthPixels, metrics.heightPixels)
        return resolvedWidget
    }

    private fun getGameDescription(systemName: String, gameFilename: String): String? {
        try {
            // Get scripts path and navigate to ES-DE folder
            val scriptsPath = prefs.getString("scripts_path", "/storage/emulated/0/ES-DE/scripts")
                ?: return null

            // Get ES-DE root folder (parent of scripts folder)
            val scriptsDir: File = File(scriptsPath)
            val esdeRoot = scriptsDir.parentFile ?: return null

            // Build path to gamelist.xml: ~/ES-DE/gamelists/<systemname>/gamelist.xml
            val gamelistFile = File(esdeRoot, "gamelists/$systemName/gamelist.xml")

            android.util.Log.d("MainActivity", "Looking for gamelist: ${gamelistFile.absolutePath}")

            if (!gamelistFile.exists()) {
                android.util.Log.d("MainActivity", "Gamelist file not found for system: $systemName")
                return null
            }

            // Parse XML to find the game's description
            val xmlContent = gamelistFile.readText()
            val sanitizedFilename = gameFilename.replace("\\", "").substringAfterLast("/")

            // Look for the game entry with matching path
            // Match pattern: <path>./filename</path>
            val pathPattern = "<path>\\./\\Q$sanitizedFilename\\E</path>".toRegex()
            val pathMatch = pathPattern.find(xmlContent)

            if (pathMatch == null) {
                android.util.Log.d("MainActivity", "Game not found in gamelist: $sanitizedFilename")
                return null
            }

            // Find the <desc> tag after this <path> tag
            val gameStartIndex = pathMatch.range.first

            // Search for <desc>...</desc> within this game entry (before next <game> tag)
            val remainingXml = xmlContent.substring(gameStartIndex)
            val nextGameIndex = remainingXml.indexOf("<game>", startIndex = 1)
            val searchSpace = if (nextGameIndex > 0) {
                remainingXml.substring(0, nextGameIndex)
            } else {
                remainingXml
            }

            // Extract description text between <desc> and </desc>
            val descPattern = "<desc>([\\s\\S]*?)</desc>".toRegex()
            val descMatch = descPattern.find(searchSpace)

            return if (descMatch != null) {
                val description = descMatch.groupValues[1].trim()
                android.util.Log.d("MainActivity", "Found description: ${description.take(100)}...")
                description
            } else {
                android.util.Log.d("MainActivity", "No description found for game: $sanitizedFilename")
                null
            }

        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error parsing gamelist.xml", e)
            return null
        }
    }

    private fun findSystemLogo(systemName: String): String? {
        // Handle ES-DE auto-collections
        val baseFileName = when (systemName.lowercase()) {
            "all" -> "auto-allgames"
            "favorites" -> "auto-favorites"
            "recent" -> "auto-lastplayed"
            else -> systemName.lowercase()
        }
        // First check if custom system logos are enabled
        val customSystemLogosEnabled = prefs.getBoolean("custom_system_logos_enabled", false)

        if (customSystemLogosEnabled) {
            val customLogoPath = prefs.getString("custom_system_logos_path", null)
            if (customLogoPath != null) {
                val customLogoDir = File(customLogoPath)
                if (customLogoDir.exists() && customLogoDir.isDirectory) {
                    // Try different extensions
                    val extensions = listOf("svg", "png", "jpg", "webp")
                    for (ext in extensions) {
                        val logoFile = File(customLogoDir, "$baseFileName.$ext")
                        if (logoFile.exists()) {
                            return logoFile.absolutePath
                        }
                    }
                }
            }
        }
        return "builtin://$baseFileName"
    }
}