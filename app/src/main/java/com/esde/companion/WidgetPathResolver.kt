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
import com.esde.companion.ui.contextmenu.MediaSlotScreen
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

        val resolutionResult = ResolutionResult(resolvedWidget, false)
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
                    resolutionResult,
                    gameFilename,
                    system
                )
            }
        }
        if(resolvedWidget.contentType.isTextWidget() && (resolvedWidget.text.isEmpty() && resolvedWidget.isRequired)) {
            resolutionResult.missingRequired = true
        } else if(!resolvedWidget.contentType.isTextWidget() && resolvedWidget.contentType != ContentType.CUSTOM_FOLDER && resolvedWidget.contentType != ContentType.CUSTOM_IMAGE && resolvedWidget.contentType != ContentType.COLOR_BACKGROUND && resolvedWidget.isRequired && resolvedWidget.contentPath != null && resolvedWidget.contentPath!!.isEmpty()) {
            resolutionResult.missingRequired = true
        }

        resolvedWidget.fromPercentages(metrics.widthPixels, metrics.heightPixels)
        return resolutionResult
    }

    private suspend fun resolveContentTypeForWidget(
        contentType: ContentType,
        resolutionResult: ResolutionResult,
        gameFilename: String?,
        system: String
    ) {
        if (contentType.isTextWidget() && gameFilename != null) {
            resolutionResult.widget.text =
                getGameTextWidget(system, gameFilename, contentType) ?: ""
        } else if (contentType == ContentType.SYSTEM_LOGO) {
            resolutionResult.widget.contentPath = findSystemLogo(system) ?: ""
            markIfVideo(resolutionResult.widget.contentPath, resolutionResult)
        } else if (contentType == ContentType.SYSTEM_IMAGE) {
            resolutionResult.widget.contentPath =
                getSystemImage(system, prefs.systemPath)?.path ?: ""
            markIfVideo(resolutionResult.widget.contentPath, resolutionResult)
        } else if (gameFilename == null && (contentType == ContentType.FANART || contentType == ContentType.SCREENSHOT)) {
            val screenshotPref = contentType == ContentType.SCREENSHOT
            resolutionResult.widget.contentPath = getRandomGameImageForSystem(system, screenshotPref)?.path
        } else if (gameFilename != null && contentType != ContentType.CUSTOM_IMAGE && contentType != ContentType.COLOR_BACKGROUND) {
            val mediaFile = locateFileWithOverride(
                contentType,
                system,
                gameFilename,
                resolutionResult.widget.slot,
                resolutionResult
            )
            markIfVideo(mediaFile, resolutionResult)
            resolutionResult.widget.contentPath = mediaFile?.absolutePath ?: ""

            if (resolutionResult.widget.cycle) {
                resolutionResult.widget.files =
                    getAllFilesForGameAndContentType(contentType, gameFilename, system)
            }
        }
    }

    private fun markIfVideo(input: Any?, resolutionResult: ResolutionResult) {
        resolutionResult.widget.video = false
        if (input != null) {
            resolutionResult.widget.video = mediaLocator.isVideo(input)
        }
    }

    fun locateFileWithOverride(contentType: ContentType, system: String, gameFilename: String, givenSlot: MediaSlot, result: ResolutionResult? = null, pageResolution: PageResolution? = null): File? {
        var slot = givenSlot
        if(givenSlot == MediaSlot.Default) {
            slot = getDefaultSlotOverride(gameFilename, system, contentType)
        }
        var file = mediaLocator.findMediaFile(contentType,system, gameFilename, slot)
        //if we couldn't find anything or the given slot or existing override, go back to default as backup but mark required as failed
        if(slot != MediaSlot.Default && (file == null || !file.exists())) {
            file = mediaLocator.findMediaFile(contentType,system, gameFilename, getDefaultSlotOverride(gameFilename, system, contentType))
            result?.missingRequired = true
            pageResolution?.missingRequired = true
        } else{
            result?.missingRequired = false
            pageResolution?.missingRequired = false
        }
        return file
    }

    private fun getDefaultSlotOverride(gameFilename: String, system: String, contentType: ContentType): MediaSlot {
        val override = mediaOverrideRepository.getOverride(gameFilename, system, contentType)
        if (override != null) {
            return override.altSlot
        }
        return MediaSlot.Default
    }

    fun getAllFilesForGameAndContentType(type: ContentType, game: String, system: String): Map<MediaSlot, File?> {
        val fileList = mutableMapOf<MediaSlot, File?>()
        MediaSlot.entries.forEach { slot ->
            fileList[slot] = mediaLocator.findMediaFile(type,system, game, slot)
        }
        return fileList
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

    private fun findSystemLogo(systemName: String): String? {
        if(prefsManager.systemLogosPath != null && prefsManager.systemLogosPath.isNotEmpty()) {
            val result = getSystemImage(systemName, prefsManager.systemLogosPath)
            if(result != null) {
                return result.path
            }
        }
        return ""
    }

    fun resolvePage(page: WidgetPage, state: AppState): PageResolution {
        val systemName = state.getCurrentSystemName()
        val gameName = state.getCurrentGameFilename()
        val pageResolution = PageResolution(null, false)

        if(systemName != null) {
            if (page.backgroundType == PageContentType.CUSTOM_FOLDER && page.customPath != null) {
                pageResolution.content = handleCustomFolderForPage(gameName, page.customPath, systemName, page)
                return pageResolution
            }

            if (page.backgroundType != PageContentType.SOLID_COLOR && page.backgroundType != PageContentType.CUSTOM_IMAGE) {
                if (systemName != null) {
                    //if we're in a game state
                    if (state.toWidgetContext() == WidgetContext.GAME) {
                        if (gameName != null) {
                            pageResolution.content = resolvePageMediaPath(page, systemName, gameName, pageResolution)
                            return pageResolution
                        }
                    } else {
                        //If we're in system page
                        val screenshotPref = page.backgroundType == PageContentType.SCREENSHOT
                        //if we want to show a random game fanart or screenshot
                        var content: Any?
                        if (page.backgroundType == PageContentType.FANART || screenshotPref) {
                            content = getRandomGameImageForSystem(systemName, screenshotPref)
                        } else {
                            content = getSystemImage(systemName, prefsManager.systemPath)
                        }
                        pageResolution.content = content
                        return pageResolution
                    }
                }
            }
        }
        return pageResolution
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

    fun resolvePageMediaPath(page: WidgetPage, system: String, gameFilename: String, pageResolution: PageResolution? = null): File? {
        if (page.backgroundType == PageContentType.CUSTOM_IMAGE && page.customPath != null) {
            return null
        } else {
            return resolvePageMediaPath(page.backgroundType, system, gameFilename, page.slot, page.isRequired, pageResolution)
        }
    }

    fun resolvePageMediaPath(contentType: PageContentType, system: String, gameFilename: String, slot: MediaSlot, required: Boolean, pageResolution: PageResolution? = null): File? {
        var result: File? = null
        if (contentType == PageContentType.VIDEO) {
            result = locateFileWithOverride(ContentType.VIDEO, system, gameFilename, slot, pageResolution = pageResolution)
        } else if (contentType == PageContentType.FANART) {
            result = locateFileWithOverride(ContentType.FANART, system, gameFilename, slot, pageResolution = pageResolution)
        }
        //use screenshot as backup
        if (contentType == PageContentType.SCREENSHOT || ((result == null || !result.exists()) && !required)) {
            result = locateFileWithOverride(ContentType.SCREENSHOT, system, gameFilename, slot, pageResolution = pageResolution)
        }
        return result
    }
}

data class ResolutionResult(val widget: Widget, var missingRequired: Boolean)
data class PageResolution(var content: Any?, var missingRequired: Boolean)