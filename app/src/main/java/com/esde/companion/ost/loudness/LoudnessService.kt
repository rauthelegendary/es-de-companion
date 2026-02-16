package com.esde.companion.ost.loudness

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.log10

class LoudnessService(private val loudnessDao: LoudnessDao) {
    suspend fun getVolumeForGame(fileName: String, system: String): Double =
        withContext(Dispatchers.IO) {
            return@withContext loudnessDao.getMetadata(fileName, system)?.integratedLoudness ?: 1.0
        }

    /**
     * Saves the user's manual volume preference.
     */
    suspend fun saveVolumePreference(fileName: String, system: String, volume: Double) {
        withContext(Dispatchers.IO) {
            loudnessDao.insertMetadata(
                LoudnessMetadata(
                    fileName = fileName,
                    system = system,
                    integratedLoudness = volume,
                    lastModified = System.currentTimeMillis()
                )
            )
        }
    }
}