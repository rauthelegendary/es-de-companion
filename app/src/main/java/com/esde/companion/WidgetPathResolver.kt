package com.esde.companion

import android.R.attr.required
import android.content.Context
import android.icu.util.ULocale.getBaseName
import android.net.Uri
import android.os.Environment
import android.util.DisplayMetrics
import com.esde.companion.art.mediaoverride.MediaOverrideRepository
import com.esde.companion.data.AppState
import com.esde.companion.data.Widget
import com.esde.companion.data.Widget.MediaSlot
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
    private val prefsManager: PreferencesManager,
    private val context: Context
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
            var contentTypeToUse = resolvedWidget.contentType
            if(resolvedWidget.contentType == ContentType.CUSTOM_FOLDER && !resolvedWidget.customPath.isBlank()) {
                resolvedWidget.contentPath = null
                var result: Uri?
                if(gameFilename != null && gameFilename.isNotEmpty()) {
                    result = mediaLocator.findGameMediaUri(context,
                        resolvedWidget.customPath,
                        system,
                        gameFilename
                    )
                } else {
                    result = mediaLocator.findSystemMediaUri(context,
                        resolvedWidget.customPath,
                        system
                    )
                }
                if(result != null) {
                    resolvedWidget.contentPath = result.toString()
                    resolvedWidget.video = mediaLocator.isVideo(result)
                    resolvedWidget.fallback = false
                }
                contentTypeToUse = resolvedWidget.contentFallbackType
            }
            if(resolvedWidget.contentType != ContentType.CUSTOM_FOLDER || (resolvedWidget.contentPath == null || resolvedWidget.contentPath!!.isEmpty())) {
                if(resolvedWidget.contentType == ContentType.CUSTOM_FOLDER) {
                    resolvedWidget.fallback = true
                }
                    resolveContentTypeForWidget(
                        contentTypeToUse,
                        resolvedWidget,
                        gameFilename,
                        resolvedWidget,
                        system
                    )
            }
        }
        if(resolvedWidget.contentType.isTextWidget() && (resolvedWidget.text.isEmpty() && resolvedWidget.isRequired)) {
            missingRequired = true
        } else if(!resolvedWidget.contentType.isTextWidget() && resolvedWidget.contentType != ContentType.CUSTOM_FOLDER && resolvedWidget.contentType != ContentType.CUSTOM_IMAGE && resolvedWidget.contentType != ContentType.COLOR_BACKGROUND && resolvedWidget.isRequired && resolvedWidget.contentPath != null && resolvedWidget.contentPath!!.isEmpty()) {
            missingRequired = true
        }

        resolvedWidget.fromPercentages(metrics.widthPixels, metrics.heightPixels)
        return ResolutionResult(resolvedWidget, missingRequired)
    }

    private suspend fun resolveContentTypeForWidget(
        contentType: ContentType,
        rawWidget: Widget,
        gameFilename: String?,
        resolvedWidget: Widget,
        system: String
    ) {
        if (contentType.isTextWidget() && gameFilename != null) {
            resolvedWidget.text =
                getGameTextWidget(system, gameFilename, contentType) ?: ""
        } else if (contentType == ContentType.SYSTEM_LOGO) {
            resolvedWidget.contentPath = findSystemLogo(system) ?: ""
        } else if (contentType == ContentType.SYSTEM_IMAGE) {
            resolvedWidget.contentPath =
                getSystemImage(system, prefs.systemPath)?.path ?: ""
        } else if (gameFilename == null && (contentType == ContentType.FANART || contentType == ContentType.SCREENSHOT)) {
            val screenshotPref = contentType == ContentType.SCREENSHOT
            resolvedWidget.contentPath = getRandomGameImageForSystem(system, screenshotPref)?.path
        } else if (gameFilename != null && contentType != ContentType.CUSTOM_IMAGE && contentType != ContentType.COLOR_BACKGROUND) {
            val mediaFile = locateFileWithOverride(
                contentType,
                system,
                gameFilename,
                rawWidget.slot
            )
            if (mediaFile != null) {
                resolvedWidget.contentPath = mediaFile.absolutePath
            } else {
                resolvedWidget.contentPath = ""
            }
            if (resolvedWidget.cycle) {
                resolvedWidget.images =
                    getAllImagesForGameAndContentType(contentType, gameFilename, system)
            }
        }
    }

    fun locateFileWithOverride(contentType: ContentType, system: String, gameFilename: String, givenSlot: MediaSlot): File? {
        var slot = givenSlot
        if(givenSlot == MediaSlot.Default) {
            val override = mediaOverrideRepository.getOverride(gameFilename, system, contentType)
            if (override != null) {
                slot = override.altSlot
            }
        }
        return mediaLocator.findMediaFile(contentType,system, gameFilename, slot)
    }

    fun getAllImagesForGameAndContentType(type: ContentType, game: String, system: String): Map<MediaSlot, File?> {
        val imageList = mutableMapOf<MediaSlot, File?>()
        MediaSlot.entries.forEach { slot ->
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

    fun getSystemImage(system: String, folder: String? = "") : File?{
        if(folder == null || folder.isEmpty()) {
            return null
        }
        return mediaLocator.getSystemMedia(system, folder)
    }

    //TODO: refactor this to mediamanager
    fun getRandomGameImageForSystem(systen: String, screenshotPref: Boolean): File? {
        val mediaBase = File(prefs.mediaPath, systen)
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

    //TODO: refactor this to mediamanager
    private fun findSystemLogo(systemName: String): String? {
        if(prefsManager.systemLogosPath != null && prefsManager.systemLogosPath.isNotEmpty()) {
            val result = getSystemImage(systemName, prefsManager.systemLogosPath)
            if(result != null) {
                return result.path
            }
        }

        val baseFileName = getBaseName(systemName)
        return "system_logos/$baseFileName.svg"
    }

    private fun getBaseName(systemName: String): String {
        return when (systemName.lowercase()) {
            "allgames" -> "auto-allgames"
            "all" -> "auto-allgames"
            "favorites" -> "auto-favorites"
            "lastplayed" -> "auto-lastplayed"
            "recent" -> "auto-lastplayed"
            else -> systemName.lowercase()
        }
    }

    fun resolvePage(page: WidgetPage, state: AppState): Any? {
        val systemName = state.getCurrentSystemName()
        val gameName = state.getCurrentGameFilename()

        if(systemName != null) {
            if (page.backgroundType == PageContentType.CUSTOM_FOLDER && page.customPath != null) {
                return handleCustomFolderForPage(gameName, page.customPath, systemName, page)
            }

            if (page.backgroundType != PageContentType.SOLID_COLOR && page.backgroundType != PageContentType.CUSTOM_IMAGE) {
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
                            return getSystemImage(systemName, prefsManager.systemPath)
                        }
                    }
                }
            }
        }
        return null
    }

    private fun handleCustomFolderForPage(
        gameName: String?,
        customPath: String?,
        systemName: String,
        page: WidgetPage
    ): Comparable<Nothing>? {
        var uriResult: Uri?
        if (gameName != null) {
            uriResult =
                mediaLocator.findGameMediaUri(
                    context,
                    customPath!!,
                    systemName,
                    gameName
                )
        } else {
            uriResult =
                mediaLocator.findSystemMediaUri(
                    context,
                    customPath!!,
                    systemName
                )
        }
        if (uriResult != null) {
            page.backgroundPath = uriResult.toString()
            return uriResult
        } else if (gameName != null) {
            return resolvePageMediaPath(
                page.backgroundFallbackType,
                systemName,
                gameName,
                MediaSlot.Default,
                page.isRequired
            )
        } else {
            val screenshotPref = page.backgroundFallbackType == PageContentType.SCREENSHOT
            //if we want to show a random game fanart or screenshot
            if (page.backgroundFallbackType == PageContentType.FANART || screenshotPref) {
                return getRandomGameImageForSystem(systemName, screenshotPref)
            } else {
                return getSystemImage(systemName, prefsManager.systemPath)
            }
        }
    }

    fun resolvePageMediaPath(page: WidgetPage, system: String, gameFilename: String): File? {
        if (page.backgroundType == PageContentType.CUSTOM_IMAGE && page.customPath != null) {
            return null
        } else {
            return resolvePageMediaPath(page.backgroundType, system, gameFilename, page.slot, page.isRequired)
        }
    }

    fun resolvePageMediaPath(contentType: PageContentType, system: String, gameFilename: String, slot: MediaSlot, required: Boolean): File? {
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