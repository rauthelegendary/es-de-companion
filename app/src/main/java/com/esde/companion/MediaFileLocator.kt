package com.esde.companion

import android.content.SharedPreferences
import android.os.Environment
import com.esde.companion.MediaFileHelper.sanitizeGameFilename
import com.esde.companion.OverlayWidget.MediaSlot
import com.esde.companion.ui.ContentType
import java.io.File

/**
 * Centralized media file location logic for ES-DE Companion.
 * 
 * Handles finding media files (images and videos) with support for:
 * - Subfolders (e.g., "subfolder/game.zip" finds "media/screenshots/subfolder/game.png")
 * - Multiple file extensions
 * - Fallback search patterns (subfolder -> root level)
 * - Different media types (fanart, screenshots, marquees, videos)
 * 
 * This class eliminates duplicate file-finding logic across MainActivity.
 */
class MediaFileLocator(private val prefs: SharedPreferences) {
    
    companion object {
        private val IMAGE_EXTENSIONS = listOf("jpg", "jpeg", "png", "webp", "gif")
        private val VIDEO_EXTENSIONS = listOf("mp4", "mkv", "avi", "wmv", "mov", "webm")
        private val DEFAULT_MEDIA_FOLDER = "/storage/emulated/0/ES-DE/downloaded_media/"
        private val ALT_MEDIA_FOLDER = "${Environment.getExternalStorageDirectory()}/ES-DE Companion/downloaded_media/"
    }
    
    /**
     * Find an image file in a specific folder by folder name.
     * This is the primary method used throughout MainActivity.
     * 
     * @param systemName The ES-DE system name (e.g., "nes", "snes")
     * @param gameName The game name without extension (e.g., "Super Mario World")
     * @param gameFilename The full game filename/path from ES-DE (may include subfolders)
     * @param folderName The media folder name (e.g., "marquees", "covers", "fanart")
     * @return The image file if found, null otherwise
     */
    fun findImageInFolder(
        systemName: String,
        gameFilename: String,
        folderName: String,
        slot: MediaSlot
    ): File? {
        val dir = getDir(systemName, folderName, slot)
        return findFileInDirectory(dir, gameFilename, slot, IMAGE_EXTENSIONS)
    }

    fun getDir(systemName: String, folderName: String, slot: MediaSlot = MediaSlot.Default): File {
        val mediaPath = getMediaPath(slot)
        val dir = File(mediaPath, "$systemName/$folderName")
        if(!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Find a media image file for a specific widget type.
     * 
     * @param type The type of image to find (MARQUEE, FANART, SCREENSHOT, etc.)
     * @param systemName The ES-DE system name (e.g., "nes", "snes")
     * @param gameFilename The full game filename/path from ES-DE (may include subfolders)
     * @return The image file if found, null otherwise
     */
    fun findMediaFile(
        type: ContentType,
        systemName: String,
        gameFilename: String,
        slot: MediaSlot = MediaSlot.Default
    ): File? {
        val gameName = MediaFileHelper.extractGameFilenameWithoutExtension(sanitizeGameFilename(gameFilename))
        if(type == ContentType.VIDEO) {
            return findVideoFile(systemName, gameFilename, slot)
        } else {
            return findImageInFolder(systemName, gameFilename, getFolderName(type), slot)
        }
    }

    fun getFolderName(type: ContentType): String{
        return when (type) {
            ContentType.MARQUEE -> "marquees"
            ContentType.BOX_2D -> "covers"
            ContentType.BOX_3D -> "3dboxes"
            ContentType.MIX_IMAGE -> "miximages"
            ContentType.BACK_COVER -> "backcovers"
            ContentType.PHYSICAL_MEDIA -> "physicalmedia"
            ContentType.SCREENSHOT -> "screenshots"
            ContentType.FANART -> "fanart"
            ContentType.TITLE_SCREEN -> "titlescreens"
            ContentType.VIDEO -> "videos"
            else -> return ""
        }
    }
    
    /**
     * Find a game background image based on user preference (fanart or screenshot).
     * 
     * @param systemName The ES-DE system name
     * @param gameFilename The full game filename/path from ES-DE
     * @param preferScreenshot If true, search screenshots first; otherwise search fanart first
     * @return The background image file if found, null otherwise
     */
    fun findGameBackgroundImage(
        systemName: String,
        gameFilename: String,
        slot: MediaSlot = MediaSlot.Default,
        preferScreenshot: Boolean
    ): File? {
        val mediaPath = getMediaPath()
        val mediaBase = File(mediaPath, systemName)
        if (!mediaBase.exists()) return null
        
        val dirs = if (preferScreenshot) {
            listOf("screenshots", "fanart")
        } else {
            listOf("fanart", "screenshots")
        }
        
        // Try each directory in order
        for (dirName in dirs) {
            val dir = File(mediaBase, dirName)
            val file = findFileInDirectory(dir, gameFilename,slot,IMAGE_EXTENSIONS)
            if (file != null) {
                android.util.Log.d("MediaFileLocator", "Found background in $dirName: ${file.absolutePath}")
                return file
            }
        }
        
        android.util.Log.d("MediaFileLocator", "No background image found for: $systemName/$gameFilename")
        return null
    }
    
    /**
     * Find a video file for a game.
     * 
     * @param systemName The ES-DE system name
     * @param gameFilename The full game filename/path from ES-DE
     * @return The video file path if found, null otherwise
     */
    fun findVideoFilePath(systemName: String, gameFilename: String, slot: MediaSlot = MediaSlot.Default): String? {
        return findVideoFile(systemName, gameFilename, slot)?.absolutePath
    }

    fun findVideoFile(systemName: String, gameFilename: String, slot: MediaSlot = MediaSlot.Default): File? {
        val dir = getDir(systemName, getFolderName(ContentType.VIDEO), slot)
        if (!dir.exists()) {
            android.util.Log.d("MediaFileLocator", "Video directory does not exist: ${dir.absolutePath}")
            return null
        }
        val videoFile = findFileInDirectory(dir, gameFilename, slot, VIDEO_EXTENSIONS)
        return videoFile
    }

    private fun getMediaPath(slot: MediaSlot = MediaSlot.Default): String {
        var mediaPath = prefs.getString("media_path", DEFAULT_MEDIA_FOLDER)
        if(slot != MediaSlot.Default) {
            mediaPath = ALT_MEDIA_FOLDER
        }
        return mediaPath ?: ""
    }

    /**
     * Find a file in a directory with support for subfolders and multiple extensions.
     * 
     * Search order:
     * 1. Subfolder with stripped name (e.g., "media/subfolder/game.png")
     * 2. Subfolder with raw name (e.g., "media/subfolder/game.zip.png")
     * 3. Root level with stripped name (e.g., "media/game.png")
     * 4. Root level with raw name (e.g., "media/game.zip.png")
     * 
     * @param dir The base directory to search in
     * @param fullPath The full game path/filename from ES-DE
     * @param extensions List of file extensions to try (without dots)
     * @return The file if found, null otherwise
     */
    private fun findFileInDirectory(
        dir: File,
        fullPath: String,
        slot: MediaSlot,
        extensions: List<String>
    ): File? {
        if (!dir.exists() || !dir.isDirectory) return null

        val nameWithoutExt = MediaFileHelper.extractGameFilenameWithoutExtension(sanitizeGameFilename(fullPath))
        
        // Get the raw filename (may still have extension)
        val rawName = MediaFileHelper.extractGameFilename(fullPath)
        
        // Extract subfolder path if present
        val subfolderPath = extractSubfolderPath(fullPath)
        
        android.util.Log.d("MediaFileLocator", "Searching in: ${dir.absolutePath}")
        android.util.Log.d("MediaFileLocator", "  strippedName: $nameWithoutExt")
        android.util.Log.d("MediaFileLocator", "  rawName: $rawName")
        android.util.Log.d("MediaFileLocator", "  subfolderPath: $subfolderPath")
        
        // Try subfolder first if it exists
        if (subfolderPath != null) {
            val subDir = File(dir, subfolderPath)
            if (subDir.exists() && subDir.isDirectory) {
                val file = tryFindFileWithExtensions(subDir, nameWithoutExt, rawName, slot, extensions)
                if (file != null) {
                    android.util.Log.d("MediaFileLocator", "Found in subfolder: ${file.absolutePath}")
                    return file
                }
            }
        }
        
        // Try root level
        val file = tryFindFileWithExtensions(dir, nameWithoutExt, rawName, slot, extensions)
        if (file != null) {
            android.util.Log.d("MediaFileLocator", "Found in root: ${file.absolutePath}")
            return file
        }
        
        android.util.Log.d("MediaFileLocator", "File not found")
        return null
    }
    
    /**
     * Try to find a file with multiple name variations and extensions.
     * 
     * @param dir The directory to search in
     * @param strippedName The filename without extension
     * @param rawName The raw filename (may have extension)
     * @param extensions List of extensions to try
     * @return The file if found, null otherwise
     */
    private fun tryFindFileWithExtensions(
        dir: File,
        strippedName: String,
        rawName: String,
        slot: MediaSlot,
        extensions: List<String>
    ): File? {
        // Try both stripped name and raw name
        for (name in listOf(strippedName, rawName)) {
            for (ext in extensions) {
                val file = File(dir, "$name${slot.suffix}.$ext")
                if (file.exists()) {
                    return file
                }
            }
        }
        return null
    }

    fun getMediaBasePath(): String {
        val customPath = prefs.getString("media_path", null)
        val path =
            customPath ?: "${Environment.getExternalStorageDirectory()}/ES-DE/downloaded_media"
        android.util.Log.d("ESDESecondScreen", "Media base path: $path")
        return path
    }

    fun getSystemImagePath(): String {
        val customPath = prefs.getString("system_path", null)
        val path = customPath
            ?: "${Environment.getExternalStorageDirectory()}/ES-DE Companion/system_images"
        android.util.Log.d("ESDESecondScreen", "System image path: $path")
        return path
    }

    fun getSystemLogosPath(): String {
        val customPath = prefs.getString("system_logos_path", null)
        val path = customPath
            ?: "${Environment.getExternalStorageDirectory()}/ES-DE Companion/system_logos"
        android.util.Log.d("ESDESecondScreen", "System logos path: $path")
        return path
    }

    fun getLogsPath(): String {
        // Always use fixed internal storage location for logs
        // This ensures FileObserver works reliably (doesn't work well on SD card)
        val path = "/storage/emulated/0/ES-DE Companion/logs"
        android.util.Log.d("MainActivity", "Logs path: $path")
        return path
    }
    
    /**
     * Extract the subfolder path from a full game path.
     * 
     * Examples:
     * - "subfolder/game.zip" -> "subfolder"
     * - "deep/nested/game.zip" -> "deep/nested"
     * - "game.zip" -> null
     * 
     * @param fullPath The full path from ES-DE
     * @return The subfolder path, or null if no subfolder
     */
    private fun extractSubfolderPath(fullPath: String): String? {
        // Get everything before the filename
        val beforeFilename = fullPath.substringBeforeLast("/", "")
        
        // Get just the immediate subfolder (everything after the system name)
        val subfolder = beforeFilename.substringAfterLast("/", "")
        
        return if (subfolder.isNotEmpty()) subfolder else null
    }
}
