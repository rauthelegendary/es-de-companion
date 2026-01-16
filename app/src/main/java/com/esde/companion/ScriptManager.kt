package com.esde.companion

import android.content.Context
import com.esde.companion.SettingsActivity.Companion.SCRIPTS_PATH_KEY
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

class ScriptManager (private val context: Context) {

    // Map: Destination Path on Disk -> Source Path in Assets
    private val scriptMap = mapOf(
        "game-select/esdecompanion-game-select.sh" to "scripts/esdecompanion-game-select.sh",
        "system-select/esdecompanion-system-select.sh" to "scripts/esdecompanion-system-select.sh",
        "game-start/esdecompanion-game-start.sh" to "scripts/esdecompanion-game-start.sh",
        "game-end/esdecompanion-game-end.sh" to "scripts/esdecompanion-game-end.sh",
        "screensaver-start/esdecompanion-screensaver-start.sh" to "scripts/esdecompanion-screensaver-start.sh",
        "screensaver-end/esdecompanion-screensaver-end.sh" to "scripts/esdecompanion-screensaver-end.sh",
        "screensaver-game-select/esdecompanion-screensaver-game-select.sh" to "scripts/esdecompanion-screensaver-game-select.sh"
    )

    fun updateScriptsIfNeeded(baseDir: String?): Boolean {
        val path = baseDir ?: "/storage/emulated/0/ES-DE/scripts"
        return updateScriptsIfNeeded(File(path))
    }

    fun updateScriptsIfNeeded(baseDir: File): Boolean {
        var allUpdated = true
        scriptMap.forEach { (targetPath, assetPath) ->
            val targetFile = File(baseDir, targetPath)

            if (!isUpToDate(targetFile, assetPath)) {
                android.util.Log.d("ScriptManager", "Updating/creating script: $targetPath")
                val success = installScript(targetFile, assetPath)
                if (!success) allUpdated = false
            }
        }
        return allUpdated
    }

    fun checkScriptValidity(baseDir: String?): Boolean {
        val path = baseDir ?: "/storage/emulated/0/ES-DE/scripts"
        return checkScriptValidityWithRapport(File(path))[0] == scriptMap.size
    }

    fun checkScriptValidity(baseDir: File): Boolean {
        return checkScriptValidityWithRapport(baseDir)[0] == scriptMap.size
    }

    fun checkScriptValidityWithRapport(baseDir: File): Array<Int> {
        var validCount = 0
        var invalidCount = 0
        var missingCount = 0

        scriptMap.forEach { (targetPath, assetPath) ->
            val targetFile = File(baseDir, targetPath)

            when {
                //file doesn't exist
                !targetFile.exists() -> {
                    missingCount++
                }

                //check if hash matches, otherwise invalid
                isUpToDate(targetFile, assetPath) -> {
                    validCount++
                } else -> {
                    invalidCount++
                }
            }
        }
        // Returns array with valid, invalid and missing count
        return arrayOf(validCount, invalidCount, missingCount)
    }

    private fun isUpToDate(file: File, assetPath: String): Boolean {
        if (!file.exists()) return false

        val assetHash = getHash(context.assets.open(assetPath))
        val fileHash = getHash(file.inputStream())

        return assetHash == fileHash
    }

    private fun installScript(targetFile: File, assetPath: String): Boolean {
        return try {
            // Ensure parent directory exists
            targetFile.parentFile?.mkdirs()

            context.assets.open(assetPath).use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            targetFile.setExecutable(true)
            true
        } catch (e: Exception) {
            android.util.Log.e("ScriptManager", "Failed to install $assetPath", e)
            false
        }
    }

    private fun getHash(inputStream: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        inputStream.use {
            var read = it.read(buffer)
            while (read != -1) {
                md.update(buffer, 0, read)
                read = it.read(buffer)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}