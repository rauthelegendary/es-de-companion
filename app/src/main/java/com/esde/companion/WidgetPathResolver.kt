package com.esde.companion

import android.os.Environment
import android.util.DisplayMetrics
import com.esde.companion.art.mediaoverride.MediaOverrideRepository
import com.esde.companion.data.AppState
import com.esde.companion.data.Widget
import com.esde.companion.data.getCurrentGameFilename
import com.esde.companion.data.getCurrentSystemName
import com.esde.companion.data.toWidgetContext
import com.esde.companion.managers.MediaManager
import com.esde.companion.managers.PreferencesManager
import com.esde.companion.metadata.GameRepository
import com.esde.companion.ui.ContentType
import com.esde.companion.ui.PageContentType
import com.esde.companion.ui.WidgetContext
import java.io.File

class WidgetPathResolver(
    private val mediaLocator: MediaManager,
    private val prefs: PreferencesManager,
    private val mediaOverrideRepository: MediaOverrideRepository,
    private val gameRepository: GameRepository,
    private val prefsManager: PreferencesManager
) {

    suspend fun resolve(
        rawWidgets: List<Widget>,
        system: String?,
        gameFilename: String?,
        metrics: DisplayMetrics
    ): List<Widget> {
        if (system != null) {
            return rawWidgets.map { widget -> resolveSingle(widget, system, gameFilename, metrics).widget }
        }
        return emptyList()
    }

    suspend fun resolveSingle(
        rawWidget: Widget,
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
            } else if (gameFilename == null && (rawWidget.contentType == ContentType.FANART || rawWidget.contentType == ContentType.SCREENSHOT)) {
                val screenshotPref = rawWidget.contentType == ContentType.SCREENSHOT
                resolvedWidget.contentPath = getRandomGameImageForSystem(system, screenshotPref)?.path
            } else if (gameFilename != null && rawWidget.contentType != ContentType.CUSTOM_IMAGE && rawWidget.contentType != ContentType.COLOR_BACKGROUND) {
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
        } else if(!resolvedWidget.contentType.isTextWidget() && resolvedWidget.isRequired && resolvedWidget.contentPath != null && resolvedWidget.contentPath!!.isEmpty()) {
            missingRequired = true
        }

        resolvedWidget.fromPercentages(metrics.widthPixels, metrics.heightPixels)
        return ResolutionResult(resolvedWidget, missingRequired)
    }

    fun locateFileWithOverride(contentType: ContentType, system: String, gameFilename: String, givenSlot: Widget.MediaSlot): File? {
        var slot = givenSlot
        if(givenSlot == Widget.MediaSlot.Default) {
            val override = mediaOverrideRepository.getOverride(gameFilename, system, contentType)
            if (override != null) {
                slot = override.altSlot
            }
        }
        return mediaLocator.findMediaFile(contentType,system, gameFilename, slot)
    }

    fun getAllImagesForGameAndContentType(type: ContentType, game: String, system: String): Map<Widget.MediaSlot, File?> {
        val imageList = mutableMapOf<Widget.MediaSlot, File?>()
        Widget.MediaSlot.entries.forEach { slot ->
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
        val systemImagePath = prefs.systemPath
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
        val mediaBase = File(prefs.mediaPath, systen)
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
        val baseFileName = when (systemName.lowercase()) {
            "allgames" -> "auto-allgames"
            "all" -> "auto-allgames"
            "favorites" -> "auto-favorites"
            "lastplayed" -> "auto-lastplayed"
            "recent" -> "auto-lastplayed"
            else -> systemName.lowercase()
        }

        val customPath = if (prefsManager.systemLogosPath.isEmpty()) null else prefsManager.systemLogosPath
        val path = customPath ?: "${Environment.getExternalStorageDirectory()}/ES-DE Companion/system_logos"

        val userLogosDir = File(path)
        if (userLogosDir.exists() && userLogosDir.isDirectory) {
            val extensions = listOf("svg", "png", "jpg", "jpeg", "webp", "gif")

            for (ext in extensions) {
                val logoFile = File(userLogosDir, "$baseFileName.$ext")
                if (logoFile.exists()) {
                    return logoFile.absolutePath
                }
            }
        }
        return "system_logos/$baseFileName.svg"
    }

    fun resolvePage(page: WidgetPage, state: AppState): File? {
        val systemName = state.getCurrentSystemName()
        val gameName = state.getCurrentGameFilename()

        if (page.backgroundType != PageContentType.SOLID_COLOR && page.backgroundType != PageContentType.CUSTOM_IMAGE)  {
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
        return null
    }

    fun resolvePageMediaPath(page: WidgetPage, system: String, gameFilename: String): File? {
        if (page.backgroundType == PageContentType.CUSTOM_IMAGE && page.customPath != null) {
            return null
        } else {
            return resolvePageMediaPath(page.backgroundType, system, gameFilename, page.slot, page.isRequired)
        }
    }

    fun resolvePageMediaPath(contentType: PageContentType, system: String, gameFilename: String, slot: Widget.MediaSlot, required: Boolean): File? {
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

data class ResolutionResult(val widget: Widget, val missingRequired: Boolean)