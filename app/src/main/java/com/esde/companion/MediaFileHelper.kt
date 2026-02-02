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

        val cleanName = if (lastDotIndex != -1 && (lastDotIndex + 1) < fileName.length) {
            val nextChar = fileName[lastDotIndex + 1]
            // If there is a space or '(' right after the dot, it's NOT an extension
            if (nextChar == ' ' || nextChar == '(') {
                fileName
            } else {
                file.nameWithoutExtension
            }
        } else {
            fileName
        }
        return cleanName
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