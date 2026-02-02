package com.esde.companion

import android.content.SharedPreferences
import android.util.DisplayMetrics
import com.esde.companion.art.mediaoverride.MediaOverrideRepository
import com.esde.companion.metadata.GameRepository
import com.esde.companion.ui.ContentType
import com.esde.companion.ui.PageContentType
import com.esde.companion.ui.WidgetContext
import java.io.File

class WidgetPathResolver(
    private val mediaLocator: MediaFileLocator,
    private val prefs: SharedPreferences,
    private val mediaOverrideRepository: MediaOverrideRepository,
    private val gameRepository: GameRepository
) {

    suspend fun resolve(
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

    suspend fun resolveSingle(
        rawWidget: OverlayWidget,
        system: String?,
        gameFilename: String?,
        metrics: DisplayMetrics
    ): ResolutionResult {
        val resolvedWidget = rawWidget.copy()
        var missingRequired = false

        if (system != null) {
            if (rawWidget.contentType.isTextWidget() && gameFilename != null) {
                resolvedWidget.text = getGameTextWidget(system, gameFilename, rawWidget.contentType) ?: ""
            } else if (rawWidget.contentType == ContentType.SYSTEM_LOGO) {
                resolvedWidget.contentPath = findSystemLogo(system) ?: ""
            } else if (rawWidget.contentType == ContentType.SYSTEM_IMAGE) {
                resolvedWidget.contentPath = getSystemImage(system)?.path ?: ""
            } else if (gameFilename != null) {
                val mediaFile = locateFileWithOverride(
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
                if(rawWidget.cycle) {
                    rawWidget.images = getAllImagesForGameAndContentType(rawWidget.contentType, gameFilename, system)
                }
            }
        }
        if(resolvedWidget.contentType.isTextWidget() && (resolvedWidget.text.isEmpty() && resolvedWidget.isRequired)) {
            missingRequired = true
        } else if(resolvedWidget.contentType != ContentType.GAME_DESCRIPTION && resolvedWidget.isRequired && resolvedWidget.contentPath != null && resolvedWidget.contentPath!!.isEmpty()) {
            missingRequired = true
        }

        resolvedWidget.fromPercentages(metrics.widthPixels, metrics.heightPixels)
        return ResolutionResult(resolvedWidget, missingRequired)
    }

    fun locateFileWithOverride(contentType: ContentType, system: String, gameFilename: String, givenSlot: OverlayWidget.MediaSlot): File? {
        var slot = givenSlot
        if(givenSlot == OverlayWidget.MediaSlot.Default) {
            val override = mediaOverrideRepository.getOverride(gameFilename, system, contentType)
            if (override != null) {
                slot = override.altSlot
            }
        }
        return mediaLocator.findMediaFile(contentType,system, gameFilename, slot)
    }

    fun getAllImagesForGameAndContentType(type: ContentType, game: String, system: String): Map<OverlayWidget.MediaSlot, File?> {
        val imageList = mutableMapOf<OverlayWidget.MediaSlot, File?>()
        OverlayWidget.MediaSlot.entries.forEach { slot ->
            imageList[slot] = mediaLocator.findMediaFile(type,system, game, slot)
        }
        return imageList
    }

    private suspend fun getGameTextWidget(systemName: String, gameFilename: String, contentType: ContentType): String? {
        val game = gameRepository.getMetadata(MediaFileHelper.getRelativePathToGame(gameFilename), systemName)
        if (game != null) {
            return when (contentType) {
                ContentType.GAME_DESCRIPTION -> game.description
                ContentType.TITLE -> game.name
                ContentType.DEVELOPER -> game.developer
                ContentType.PUBLISHER -> game.publisher
                ContentType.GENRE -> game.genre
                ContentType.RELEASE_DATE -> game.releaseDate
                else -> ""
            }
        }
        return ""
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
            return resolvePageMediaPath(page.backgroundType, system, gameFilename, page.slot, page.isRequired)
        }
    }

    fun resolvePageMediaPath(contentType: PageContentType, system: String, gameFilename: String, slot: OverlayWidget.MediaSlot, required: Boolean): File? {
        var result: File? = null
        if (contentType == PageContentType.VIDEO) {
            result = locateFileWithOverride(ContentType.VIDEO, system, gameFilename, slot)
        } else if (contentType == PageContentType.FANART) {
            result = locateFileWithOverride(ContentType.FANART, system, gameFilename, slot)
        }
        //use screenshot as backup
        if (contentType == PageContentType.SCREENSHOT || ((result == null || !result.exists()) && !required)) {
            result = locateFileWithOverride(ContentType.SCREENSHOT, system, gameFilename, slot)
        }
        return result
    }
}

data class ResolutionResult(val widget: OverlayWidget, val missingRequired: Boolean)