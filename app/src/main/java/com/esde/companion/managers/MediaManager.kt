package com.esde.companion.managers

import android.R.attr.type
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.esde.companion.MediaFileHelper
import com.esde.companion.MediaFileHelper.sanitizeGameFilename
import com.esde.companion.data.AppConstants
import com.esde.companion.data.Widget.MediaSlot
import com.esde.companion.ui.ContentType
import com.esde.companion.ui.PageContentType
import java.io.File
import android.content.Context
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
class MediaManager(private val prefsManager: PreferencesManager) {

    companion object {
        private val IMAGE_EXTENSIONS = AppConstants.FileExtensions.IMAGE
        private val VIDEO_EXTENSIONS = AppConstants.FileExtensions.VIDEO

        private val MEDIA_EXTENSIONS = IMAGE_EXTENSIONS + VIDEO_EXTENSIONS

        private val ALT_MEDIA_FOLDER = "${Environment.getExternalStorageDirectory()}/ES-DE Companion/downloaded_media/"

    }

    fun getDir(systemName: String, folderName: String, slot: MediaSlot = MediaSlot.Default): File {
        val mediaPath = getMediaPath(slot)
        val dir = File(mediaPath, "$systemName/$folderName")
        if(!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun findMediaFile(
        type: ContentType,
        systemName: String,
        gameFilename: String,
        slot: MediaSlot = MediaSlot.Default
    ): File? {
        val fileFound = this.findMediaFileDefault(type, systemName, gameFilename, slot)
        if(fileFound != null && !isVideo(fileFound)) {
            return checkCroppedAlternative(fileFound)
        }
        return fileFound
    }

    fun findMediaFileDefault(
        type: ContentType,
        systemName: String,
        gameFilename: String?,
        slot: MediaSlot = MediaSlot.Default
    ): File? {
        val folderName = getFolderName(type)
        val dir = getDir(systemName, folderName, slot)
        var found: File? = null
        if(gameFilename != null) {
            found = findFileInDirectory(dir, gameFilename, slot)
        } else {
            found = getSystemMedia(systemName, folderName)
        }
        return found
    }

    private fun checkCroppedAlternative(file: File?): File? {
        if (file == null) return null

        val name = file.nameWithoutExtension
        val ext = "png"
        val croppedFile = File(file.parentFile, "${name}_cropped.$ext")
        return if (croppedFile.exists()) croppedFile else file
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

    private fun getMediaPath(slot: MediaSlot = MediaSlot.Default): String {
        var mediaPath = prefsManager.mediaPath
        if(slot != MediaSlot.Default) {
            mediaPath = ALT_MEDIA_FOLDER
        }
        return mediaPath
    }

    fun getSystemMedia(system: String, folder: String): File? {
        val baseFileName = when (system.lowercase()) {
            "allgames" -> "auto-allgames"
            "all" -> "auto-allgames"
            "favorites" -> "auto-favorites"
            "lastplayed" -> "auto-lastplayed"
            "recent" -> "auto-lastplayed"
            else -> system.lowercase()
        }

        val dir = File(folder)
        val matchingFiles = dir.listFiles { file ->
            file.nameWithoutExtension == baseFileName &&
                    MEDIA_EXTENSIONS.contains(file.extension.lowercase())
        }
        return matchingFiles?.firstOrNull()
    }

    fun isVideo(input: Any?): Boolean {
        val extension = when (input) {
            is File -> input.extension
            is Uri -> {
                val lastSegment = input.lastPathSegment
                lastSegment?.substringAfterLast('.', "")
            }
            is String -> input.substringAfterLast('.', "")
            else -> ""
        }

        val ext = extension?.lowercase() ?: ""
        return VIDEO_EXTENSIONS.contains(ext)
    }

    private fun findFileInDirectory(
        dir: File,
        fullPath: String,
        slot: MediaSlot
    ): File? {
        if (!dir.exists() || !dir.isDirectory) return null
        val nameWithoutExt = MediaFileHelper.extractGameFilenameWithoutExtension(sanitizeGameFilename(fullPath))
        // Get the raw filename (may still have extension)
        val rawName = MediaFileHelper.extractGameFilename(fullPath)
        // Extract subfolder path if present
        val subfolderPath = extractSubfolderPath(fullPath)

        // Try subfolder first if it exists
        if (subfolderPath != null) {
            val subDir = File(dir, subfolderPath)
            if (subDir.exists() && subDir.isDirectory) {
                val file = tryFindFile(subDir, nameWithoutExt, rawName, slot)
                if (file != null) {
                    return file
                }
            }
        }

        // Try root level
        val file = tryFindFile(dir, nameWithoutExt, rawName, slot)
        if (file != null) {
            return file
        }
        return null
    }

    private fun tryFindFile(
        dir: File,
        strippedName: String,
        rawName: String,
        slot: MediaSlot
    ): File? {
        // Try both stripped name and raw name
        for (name in listOf(strippedName, rawName)) {
            for (ext in MEDIA_EXTENSIONS) {
                val file = File(dir, "$name${slot.suffix}.$ext")
                if (file.exists()) {
                    return file
                }
            }
        }
        return null
    }

    fun getLogsPath(): String {
        val path = "/storage/emulated/0/ES-DE Companion/logs"
        android.util.Log.d("MainActivity", "Logs path: $path")
        return path
    }

    private fun extractSubfolderPath(fullPath: String): String? {
        val beforeFilename = fullPath.substringBeforeLast("/", "")

        if (beforeFilename.isEmpty()) {
            return null
        }
        if (beforeFilename.startsWith("/storage/")) {
            val romsFolderIndex = beforeFilename.indexOf("/ROMs/")
            if (romsFolderIndex == -1) {
                return beforeFilename
            }
            val afterRoms = beforeFilename.substring(romsFolderIndex + "/ROMs/".length)
            val afterSystem = afterRoms.substringAfter("/", "")
            return if (afterSystem.isNotEmpty()) afterSystem else null
        }
        // Relative path - return as-is
        return beforeFilename
    }

    fun getPotentialFile(
        type: ContentType,
        systemName: String,
        gameFilename: String,
        slot: MediaSlot = MediaSlot.Default,
        existingFile: File? = null
    ): File {
        val folderName = getFolderName(type)
        val dir = getDir(systemName, folderName, slot)
        val nameWithoutExt = MediaFileHelper.extractGameFilenameWithoutExtension(sanitizeGameFilename(gameFilename))
        val extension = existingFile?.extension
            ?: if (type == ContentType.VIDEO) "mp4" else "png"
        return File(dir, "$nameWithoutExt${slot.suffix}.$extension")
    }

    suspend fun importFileToAltSlot(
        context: Context,
        sourceUri: Uri,
        contentType: ContentType,
        systemName: String,
        gameFilename: String,
        slot: MediaSlot
    ): File? = withContext(Dispatchers.IO) {
        val extension = resolveExtensionFromUri(context, sourceUri) ?: return@withContext null
        val nameWithoutExt = MediaFileHelper.extractGameFilenameWithoutExtension(
            sanitizeGameFilename(gameFilename)
        )
        val targetDir = getDir(systemName, getFolderName(contentType), slot)
        val targetFile = File(targetDir, "$nameWithoutExt${slot.suffix}.$extension")

        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            targetFile
        } catch (e: Exception) {
            Log.e("MediaManager", "Failed to import file", e)
            null
        }
    }

    private fun resolveExtensionFromUri(context: Context, uri: Uri): String? {
        val mimeType = context.contentResolver.getType(uri)
        if (mimeType != null) {
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            if (ext != null) return ext
        }
        val lastSegment = uri.lastPathSegment ?: return null
        val ext = lastSegment.substringAfterLast('.', "")
        return ext.ifEmpty { null }
    }

    private fun sanitizeFilename(fullPath: String): String {
        val normalized = fullPath.replace("\\", "/")
        return normalized.substringAfterLast("/")
    }

    /**
     * Recursively collect all image files from a directory.
     */
    private fun collectImageFiles(dir: File, accumulator: MutableList<File>) {
        if (!dir.exists() || !dir.isDirectory) return

        dir.listFiles()?.forEach { file ->
            when {
                file.isDirectory -> collectImageFiles(file, accumulator)
                file.isFile && file.extension.lowercase() in IMAGE_EXTENSIONS -> {
                    accumulator.add(file)
                }
            }
        }
    }

    fun resolveGamelistFolder(): File? {
        val scriptsDir = File(prefsManager.scriptsPath)
        val esdeRoot = scriptsDir.parentFile ?: return null

        // Standard path: ~/ES-DE/gamelists/
        if (esdeRoot.exists()) {
            return esdeRoot
        }
        return null
    }

    fun findGameMediaUri(
        context: Context,
        rootUriString: String,
        systemName: String,
        gameFilename: String
    ): Uri? {
        val rootUri = Uri.parse(rootUriString)

        // 1. Find the URI of the system folder (e.g., .../snes)
        val systemDirUri = findChildUriByName(context, rootUri, systemName) ?: return null

        // 2. Search for the game file inside that folder
        val game = MediaFileHelper.extractGameFilenameWithoutExtension(sanitizeGameFilename(gameFilename))
        return findMediaViaQuery(context, systemDirUri, game, MEDIA_EXTENSIONS.toSet())
    }

    fun findSystemMediaUri(
        context: Context,
        rootUriString: String,
        systemName: String
    ): Uri? {
        val rootUri = Uri.parse(rootUriString)

        // 1. Find the URI of the "systems" folder
        val systemsDirUri = findChildUriByName(context, rootUri, "systems") ?: return null

        // 2. Search for the system media inside the "systems" folder
        return findMediaViaQuery(context, systemsDirUri, systemName, MEDIA_EXTENSIONS.toSet())
    }

    /**
     * Finds a specific child (folder or file) URI by its display name using a query.
     * This replaces DocumentFile.findFile()
     */
    private fun findChildUriByName(context: Context, parentUri: Uri, displayName: String): Uri? {
        val parentId = if (DocumentsContract.isTreeUri(parentUri)) {
            DocumentsContract.getTreeDocumentId(parentUri)
        } else {
            DocumentsContract.getDocumentId(parentUri)
        }

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, parentId)

        // Some providers fail on 'selection', so we manual-check in the loop for safety
        context.contentResolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1)
                if (name.equals(displayName, ignoreCase = true)) {
                    val docId = cursor.getString(0)
                    // We create a Document URI scoped to this specific item
                    return DocumentsContract.buildDocumentUriUsingTree(parentUri, docId)
                }
            }
        }
        return null
    }

    /**
     * Searches the children of a directory for a file matching baseName + any allowed extension.
     */
    private fun findMediaViaQuery(
        context: Context,
        parentUri: Uri,
        fileName: String,
        extensions: Set<String>
    ): Uri? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            parentUri,
            DocumentsContract.getDocumentId(parentUri)
        )

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_DOCUMENT_ID
        )

        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)

            while (cursor.moveToNext()) {
                val fullDisplayName = cursor.getString(nameIndex) ?: continue
                val nameWithoutExt = fullDisplayName.substringBeforeLast(".")
                val ext = fullDisplayName.substringAfterLast(".", "").lowercase()

                if (nameWithoutExt.equals(fileName, ignoreCase = true) && extensions.contains(ext)) {
                    val documentId = cursor.getString(idIndex)
                    return DocumentsContract.buildDocumentUriUsingTree(parentUri, documentId)
                }
            }
        }
        return null
    }


    /**fun findGameMediaUri(
        context: Context,
        rootUriString: String,
        systemName: String,
        gameFilename: String
    ): Uri? {
        val rootTree = DocumentFile.fromTreeUri(context, Uri.parse(rootUriString)) ?: return null
        val systemDir = rootTree.findFile(systemName) ?: return null
        val game = MediaFileHelper.extractGameFilenameWithoutExtension(sanitizeGameFilename(gameFilename))
        return findFileWithExtensions(systemDir, game, MEDIA_EXTENSIONS)
    }

    fun findSystemMediaUri(
        context: Context,
        rootUriString: String,
        systemName: String
    ): Uri? {
        val rootTree = DocumentFile.fromTreeUri(context, Uri.parse(rootUriString)) ?: return null
        val systemsDir = rootTree.findFile("systems") ?: return null

        return findFileWithExtensions(systemsDir, systemName, MEDIA_EXTENSIONS)
    }

    private fun findFileWithExtensions(
        directory: DocumentFile,
        baseName: String,
        extensions: List<String>
    ): Uri? {
        for (ext in extensions) {
            val file = directory.findFile("$baseName.$ext")
            if (file != null && file.exists()) {
                return file.uri
            }
        }
        return null
    }*/
}