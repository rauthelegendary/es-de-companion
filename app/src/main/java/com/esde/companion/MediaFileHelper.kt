package com.esde.companion

import java.io.File

object MediaFileHelper {

    fun extractSystemFromPath(path: String): String {
        val file = File(path)
        return file.parentFile?.name ?: "Unknown"
    }

    fun extractGameFilenameWithoutExtension(path: String): String {
        return File(path).nameWithoutExtension
    }

    fun extractGameFilename(path: String): String {
        return File(path).name
    }

    fun sanitizeGameFilename(fullPath: String): String {
        // Remove backslashes (screensaver case)
        var cleaned = fullPath.replace("\\", "")

        // Get just the filename (after last forward slash)
        cleaned = cleaned.substringAfterLast("/")

        return cleaned
    }
}