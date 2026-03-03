package com.esde.companion

import java.io.File

object MediaFileHelper {

    fun extractSystemFromPath(path: String): String {
        val file = File(path)
        return file.parentFile?.name ?: "Unknown"
    }

    fun extractGameFilenameWithoutExtension(path: String): String {
        val file = File(path)
        val fileName = file.name
        val lastDotIndex = fileName.lastIndexOf('.')

        if (lastDotIndex == -1) return fileName

        val potentialExtension = fileName.substring(lastDotIndex + 1)

        val isRealExtension = potentialExtension.length in 1..6
                && potentialExtension.all { it.isLetterOrDigit() }

        return if (isRealExtension) {
            fileName.take(lastDotIndex)
        } else {
            fileName
        }
    }

    fun extractGameFilename(path: String): String {
        return File(path).name
    }

    fun getRelativePathToGame(path: String): String {
        val file = File(path)
        return "./${file.name}"
    }

    fun sanitizeGameFilename(fullPath: String): String {
        // Remove backslashes (screensaver case)
        var cleaned = fullPath.replace("\\", "")

        // Get just the filename (after last forward slash)
        cleaned = cleaned.substringAfterLast("/")

        return cleaned
    }
}