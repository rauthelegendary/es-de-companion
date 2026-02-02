package com.esde.companion.ost.loudness

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.log10

const val TARGET_LOUDNESS_DB = -12.0
class LoudnessService(private val loudnessDao: LoudnessDao) {
    suspend fun getOrComputeLoudness(file: File, system: String): Double =
        withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext TARGET_LOUDNESS_DB

            val lastModified = file.lastModified()
            val cached = loudnessDao.getMetadata(file.name, system)

            if (cached != null && cached.lastModified == lastModified) {
                return@withContext cached.integratedLoudness
            }

            val computedDb = computeLoudness(file) ?: TARGET_LOUDNESS_DB
            loudnessDao.insertMetadata(
                LoudnessMetadata(
                    file.name,
                    system,
                    computedDb,
                    lastModified
                )
            )
            return@withContext computedDb
        }

    suspend fun computeLoudness(file: File): Double? = withContext(Dispatchers.Default) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(file.path)
            val audioTrackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return@withContext null

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE).toDouble()

            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var totalSumSquare = 0.0
            var sampleCount = 0L

            // K-Weighting Filter States
            // Stage 1 (High Shelf)
            var s1x1 = 0.0;
            var s1y1 = 0.0
            // Stage 2 (RLB High Pass)
            var s2x1 = 0.0;
            var s2x2 = 0.0;
            var s2y1 = 0.0;
            var s2y2 = 0.0

            // RLB Filter Coefficients (Pre-calculated for ~44.1-48kHz)
            // This cuts off the sub-bass energy that skews RMS results
            val a1 = -1.99044765
            val a2 = 0.99044765
            val b0 = 1.0
            val b1 = -2.0
            val b2 = 1.0

            var eosReached = false
            val maxAnalysisSamples = (sampleRate * 25).toLong() // 25s scan is plenty for an average

            while (sampleCount < maxAnalysisSamples && !eosReached) {
                val inIndex = codec.dequeueInputBuffer(5000)
                if (inIndex >= 0) {
                    val buffer = codec.getInputBuffer(inIndex)!!
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) eosReached = true
                    else {
                        codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, 10000)
                if (outIndex >= 0) {
                    val outBuffer = codec.getOutputBuffer(outIndex)!!
                    val shortBuffer = outBuffer.asShortBuffer()

                    while (shortBuffer.hasRemaining()) {
                        val raw = shortBuffer.get().toDouble() / 32768.0

                        // --- STAGE 1: High Shelf (Human Head/Ear Canal simulation) ---
                        val stage1 = (0.7 * s1y1) + (raw - 0.3 * s1x1)
                        s1x1 = raw; s1y1 = stage1

                        // --- STAGE 2: RLB High-Pass (Removes non-perceived bass weight) ---
                        val filtered = b0 * stage1 + b1 * s2x1 + b2 * s2x2 - a1 * s2y1 - a2 * s2y2

                        // Update history for Stage 2
                        s2x2 = s2x1; s2x1 = stage1
                        s2y2 = s2y1; s2y1 = filtered

                        val sq = filtered * filtered

                        // LUFS Gating: Ignore silence and very quiet noise floor
                        // 1e-7 is roughly -70dB
                        if (sq > 1e-7) {
                            totalSumSquare += sq
                            sampleCount++
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                }
            }

            if (sampleCount > 0) {
                val meanSquare = totalSumSquare / sampleCount
                val lufs =
                    (10 * log10(meanSquare.coerceAtLeast(1e-10))) + 0.6 // LUFS adjustment factor
                return@withContext lufs
            }
        } catch (e: Exception) {
            Log.e("Loudness", "Compute failed for ${file.name}", e)
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (e: Exception) {
            }
            extractor.release()
        }
        null
    }
}