package com.esde.companion.ost.loudness

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.log10
import kotlin.math.sqrt

const val TARGET_LOUDNESS_DB = -24.0
class LoudnessService(private val loudnessDao: LoudnessDao) {
    suspend fun getOrComputeLoudness(file: File): Double = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext TARGET_LOUDNESS_DB

        val lastModified = file.lastModified()
        val cached = loudnessDao.getMetadata(file.name)

        //if we already have this file and its loudness stored
        if (cached != null && cached.lastModified == lastModified) {
            return@withContext cached.integratedLoudness
        }

        val computedDb = computeLoudness(file) ?: TARGET_LOUDNESS_DB
        loudnessDao.insertMetadata(LoudnessMetadata(file.name, computedDb, lastModified))

        return@withContext computedDb
    }

    suspend fun computeLoudness(file: File): Double? = withContext(Dispatchers.Default) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(file.path)
            val audioTrackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return@withContext null

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)

            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()

            var totalSumSquare = 0.0
            var totalSamples = 0L
            var maxPulseDb = -100.0

            val samplePoints = listOf(0.01, 0.1, 0.2, 0.35, 0.5, 0.75, 0.9)

            for (point in samplePoints) {
                val seekTime = (durationUs * point).toLong()
                extractor.seekTo(seekTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                codec.flush()

                var pulseSumSquare = 0.0
                var pulseSamples = 0L
                val targetSamples = sampleRate / 2

                var eosReached = false
                // Simplified Feed/Drain
                while (pulseSamples < targetSamples && !eosReached) {
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
                            val sample = shortBuffer.get().toFloat() / 32768f
                            pulseSumSquare += (sample * sample).toDouble()
                            pulseSamples++
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                    }
                }

                if (pulseSamples > 0) {
                    val pulseRms = Math.sqrt(pulseSumSquare / pulseSamples)
                    val pulseDb = 20 * Math.log10(pulseRms.coerceAtLeast(1e-10))

                    // Track the absolute loudest peak pulse found
                    if (pulseDb > maxPulseDb) maxPulseDb = pulseDb

                    // THE GATE: Only average audible music
                    if (pulseDb > -45.0) {
                        totalSumSquare += pulseSumSquare
                        totalSamples += pulseSamples
                    }
                }
            }

            if (totalSamples > 0) {
                val finalRms = sqrt(totalSumSquare / totalSamples)
                var finalDb = 20 * log10(finalRms)

                /**
                 * SENSITIVE CREST DETECTION
                 * maxPulseDb is the loudest 0.5s window.
                 * finalDb is the gated average of the whole song.
                 *
                 * If a song has a 'chorus' or 'scream' that is 5dB+ louder than
                 * the rest of the song, we treat it as a high-alert track.
                 */
                val globalCrest = maxPulseDb - finalDb

                // Lowered the threshold from 8.0 to 4.0 to make it much easier to hit
                if (globalCrest > 4.0) {
                    // Apply a sliding penalty: the spikier the song, the more we add
                    val penalty = (globalCrest - 4.0) * 1.5
                    finalDb += penalty.coerceAtMost(5.0) // Cap penalty at 5dB so we don't overcorrect
                }

                if (finalDb > -16) {
                    finalDb += 1.8
                }
                else if (finalDb > -17.5) {
                    finalDb += 1.3
                }
                else if (finalDb > -18.5) {
                    finalDb += 0.7
                }

                android.util.Log.d("Loudness", "Result: $finalDb | Crest: $globalCrest | Max: $maxPulseDb")
                return@withContext finalDb
            }
        } catch (e: Exception) {
            android.util.Log.e("Loudness", "Error: ${e.message}")
        } finally {
            codec?.stop()
            codec?.release()
            extractor.release()
        }
        null
    }
}