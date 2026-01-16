package com.esde.companion.ost

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.ServiceList
import java.io.File
import java.util.concurrent.TimeUnit

class MusicDownloader() {
    private var newPipe = initNewPipe()
    private final var searchString = "official game main theme"

    private val sharedClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun initNewPipe() {
        newPipe = NewPipe.init(NewPipeDownloader(OkHttpClient()))
    }

    suspend fun downloadGameMusic(gameTitle: String, gameFileName: String, system: String, musicDir: File?): File? {
        val videoUrl = getFirstYoutubeSearchResult(gameTitle, system)
        Log.d("CoroutineDebug", "videourl: " + videoUrl)
        if (videoUrl != null) {
            val streamUrl = getYoutubeAudioUrl(videoUrl)
            Log.d("CoroutineDebug", "streamUrl: " + streamUrl)
            if (streamUrl != null) {
                return downloadToLocal(streamUrl, gameFileName, musicDir)
            }
        }
        return null
    }

    private suspend fun getFirstYoutubeSearchResult(gameTitle: String, system: String): String? = withContext(Dispatchers.IO) {
        val searchExtractor = ServiceList.YouTube.getSearchExtractor("$gameTitle - $system $searchString")
        searchExtractor.fetchPage()

        val items = searchExtractor.initialPage.items
        val firstResult = items.filterIsInstance<StreamInfoItem>().firstOrNull { item -> item.duration in 31..<360 }
        return@withContext firstResult?.url
    }

    private suspend fun getYoutubeAudioUrl(videoUrl: String): String? = withContext(Dispatchers.IO) search@ {
        try {
            val extractor = ServiceList.YouTube.getStreamExtractor(videoUrl)
            extractor.fetchPage()

            // Inside your getDirectAudioUrl function
            val audioStreams = extractor.audioStreams

            // Filter for itag 140 (128kbps M4A) or 251 (160kbps Opus/WebM)
            val bestAudio = audioStreams.find { it.itag == 140 } // M4A (preferred for Android)
                ?: audioStreams.find { it.itag == 251 } // Opus (Better quality, slightly larger)
                ?: audioStreams.find { it.itag == 139 } // 48kbps M4A (Smallest)
                ?: audioStreams.firstOrNull { it.averageBitrate < 200_000 } // Fallback to any low-bitrate stream

            if (bestAudio != null) {
                Log.d("CoroutineDebug", "Selected itag: ${bestAudio.itag} | Size should be small.")
                return@search bestAudio.content
            }
        } catch (e: Exception) {
            Log.e("CoroutineDebug", "Extraction failed", e)
            return@search ""
        }
        return@search ""
    }

    private suspend fun downloadToLocal(url: String, gameTitle: String, musicDir: File?): File = withContext(Dispatchers.IO) {
        try {
            val fileName = "${gameTitle}.m4a"
            val file = File(musicDir, fileName)

            val request = okhttp3.Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36")
                .addHeader("Referer", "https://www.youtube.com/")
                .addHeader("Range", "bytes=0-")
                .addHeader("Connection", "keep-alive")
                .build()

            sharedClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("HTTP Error: ${response.code}")

                val totalBytes = response.body?.contentLength() ?: -1
                Log.d("CoroutineDebug", "Content Length: $totalBytes")

                response.body?.byteStream()?.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8 * 1024) // 8KB buffer
                        var bytesRead: Int
                        var totalDownloaded = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalDownloaded += bytesRead

                            // Log every 500KB so we can see progress
                            if (totalDownloaded % (512 * 1024) < 8192) {
                                Log.d("CoroutineDebug", "Downloaded: $totalDownloaded / $totalBytes")
                            }
                        }
                        output.flush() // Ensure everything is written to disk
                    }
                }
            }

            Log.d("CoroutineDebug", "file being returned from doiwnload local: " + file.toString())
            return@withContext file
        }
        catch (e: Exception) {
            Log.e("CoroutineDebug", "CRITICAL ERROR during download: ${e.message}", e)
            File("") // Return null so you can handle the failure in UI
        }
    }

}