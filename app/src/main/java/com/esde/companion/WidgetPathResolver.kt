package com.esde.companion

import android.content.SharedPreferences
import android.util.DisplayMetrics
import com.esde.companion.ui.ContentType
import com.esde.companion.ui.PageContentType
import com.esde.companion.ui.WidgetContext
import java.io.File

class WidgetPathResolver(private val mediaLocator: MediaFileLocator, private val prefs: SharedPreferences) {

    fun resolve(
        rawWidgets: List<OverlayWidget>,
        system: String?,
        gameFilename: String?,
        metrics: DisplayMetrics
    ): List<OverlayWidget> {
        if (system != null) {
            return rawWidgets.map { widget -> resolveSingle(widget, system, gameFilename, metrics).widget }
        }
        return emptyList()
    }

    fun resolveSingle(
        rawWidget: OverlayWidget,
        system: String?,
        gameFilename: String?,
        metrics: DisplayMetrics
    ): ResolutionResult {
        val resolvedWidget = rawWidget.copy()
        var missingRequired = false

        if (system != null) {
            if (rawWidget.contentType == ContentType.GAME_DESCRIPTION && gameFilename != null) {
                resolvedWidget.description = getGameDescription(system, gameFilename)!!

            } else if (rawWidget.contentType == ContentType.SYSTEM_LOGO) {
                resolvedWidget.contentPath = findSystemLogo(system) ?: ""
            } else if (gameFilename != null) {
                val mediaFile = mediaLocator.findMediaFile(
                    rawWidget.contentType,
                    system,
                    gameFilename,
                    rawWidget.slot
                )
                if (mediaFile != null) {
                    resolvedWidget.contentPath = mediaFile.absolutePath
                } else {
                    resolvedWidget.contentPath = ""
                }
            }
        }
        if(resolvedWidget.contentType == ContentType.GAME_DESCRIPTION && (resolvedWidget.description == null || resolvedWidget.description.isEmpty()) && resolvedWidget.isRequired) {
            missingRequired = true
        } else if(resolvedWidget.contentType != ContentType.GAME_DESCRIPTION && resolvedWidget.isRequired && resolvedWidget.contentPath.isEmpty()) {
            missingRequired = true
        }

        resolvedWidget.fromPercentages(metrics.widthPixels, metrics.heightPixels)
        return ResolutionResult(resolvedWidget, missingRequired)
    }

    private fun getGameDescription(systemName: String, gameFilename: String): String? {
        try {
            // Get scripts path and navigate to ES-DE folder
            val scriptsPath = prefs.getString("scripts_path", "/storage/emulated/0/ES-DE/scripts")
                ?: return null

            // Get ES-DE root folder (parent of scripts folder)
            val scriptsDir = File(scriptsPath)
            val esdeRoot = scriptsDir.parentFile ?: return null

            // Build path to gamelist.xml: ~/ES-DE/gamelists/<systemname>/gamelist.xml
            val gamelistFile = File(esdeRoot, "gamelists/$systemName/gamelist.xml")

            android.util.Log.d("MainActivity", "Looking for gamelist: ${gamelistFile.absolutePath}")

            if (!gamelistFile.exists()) {
                android.util.Log.d(
                    "MainActivity",
                    "Gamelist file not found for system: $systemName"
                )
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
                android.util.Log.d(
                    "MainActivity",
                    "No description found for game: $sanitizedFilename"
                )
                null
            }

        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error parsing gamelist.xml", e)
            return null
        }
    }

    fun getSystemImage(system: String) : File?{
        val baseFileName = when (system.lowercase()) {
            "all" -> "auto-allgames"
            "favorites" -> "auto-favorites"
            "recent" -> "auto-lastplayed"
            else -> system.lowercase()
        }

        // Check for custom system image with multiple format support
        var imageToUse: File? = null
        val systemImagePath = mediaLocator.getSystemImagePath()
        val imageExtensions = listOf("webp", "png", "jpg", "jpeg")

        for (ext in imageExtensions) {
            val imageFile = File(systemImagePath, "$baseFileName.$ext")
            if (imageFile.exists()) {
                imageToUse = imageFile
                break
            }
        }
        return imageToUse
    }

    fun getRandomGameImageForSystem(systen: String, screenshotPref: Boolean): File? {
        val mediaBase = File(mediaLocator.getMediaBasePath(), systen)
        // Use system_image_preference instead of image_preference
        val prioritizedFolders = if (screenshotPref) {
            listOf("screenshots", "fanart")
        } else {
            listOf("fanart", "screenshots")
        }
        for (folder in prioritizedFolders) {
            val dir = File(mediaBase, folder)
            if (dir.exists() && dir.isDirectory) {
                val images = dir.listFiles { f ->
                    f.extension.lowercase() in listOf("jpg", "png", "webp")
                } ?: emptyArray()
                if (images.isNotEmpty()) {
                    return images.random()
                }
            }
        }
        return null
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

    fun resolvePage(page: WidgetPage, state: AppState): File? {
        val systemName = state.getCurrentSystemName()
        val gameName = state.getCurrentGameFilename()

        if (page.backgroundType != PageContentType.SOLID_COLOR)  {
            if(page.backgroundType != PageContentType.CUSTOM_IMAGE) {
                if (systemName != null) {
                    //if we're in a game state
                    if (state.toWidgetContext() == WidgetContext.GAME) {
                        if (gameName != null) {
                            return resolvePageMediaPath(page, systemName, gameName)
                        }
                    } else {
                        //If we're in system page
                        val screenshotPref = page.backgroundType == PageContentType.SCREENSHOT
                        //if we want to show a random game fanart or screenshot
                        if (page.backgroundType == PageContentType.FANART || screenshotPref) {
                            return getRandomGameImageForSystem(systemName, screenshotPref)
                        } else {
                            return getSystemImage(systemName)
                        }
                    }
                }
            }
            //Custom path, everything else is irrelevant
            else {
                return File(page.customPath!!)
            }
        }
        return null
    }

    fun resolvePageMediaPath(page: WidgetPage, system: String, gameFilename: String): File? {
        if (page.customPath != null) {
            return File(page.customPath)
        } else {
            return resolvePageMediaPath(page.backgroundType, system, gameFilename, page.slot)
        }
    }

    fun resolvePageMediaPath(contentType: PageContentType, system: String, gameFilename: String, slot: OverlayWidget.MediaSlot): File? {
        var result: File? = null
        if (contentType == PageContentType.VIDEO) {
            result = mediaLocator.findMediaFile(ContentType.VIDEO, system, gameFilename, slot)
        } else if (contentType == PageContentType.FANART) {
            result = mediaLocator.findMediaFile(ContentType.FANART, system, gameFilename, slot)
        }
        //use screenshot as backup
        if (contentType == PageContentType.SCREENSHOT || result == null || !result.exists()) {
            result = mediaLocator.findMediaFile(ContentType.SCREENSHOT, system, gameFilename, slot)
        }
        return result
    }
}

data class ResolutionResult(val widget: OverlayWidget, val missingRequired: Boolean)