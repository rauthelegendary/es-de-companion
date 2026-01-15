package com.esde.companion.ost

import android.content.Context
import android.os.Environment
import com.maxrave.kotlinyoutubeextractor.YTExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.ServiceList
import java.io.File

class MusicDownloader() {
    private var newPipe = initNewPipe()
    private final var searchString = "ost music"

    private fun initNewPipe() {
        newPipe = NewPipe.init(NewPipeDownloader(OkHttpClient()))
    }

    suspend fun downloadGameMusic(gameTitle: String, context: Context): File? {
        val videoUrl = getFirstYoutubeSearchResult(gameTitle)
        if (videoUrl != null) {
            val streamUrl = extractYoutubeStreamUrl(videoUrl, context)
            if (streamUrl != null) {
                return downloadToLocal(streamUrl, gameTitle, context)
            }
        }
        return null
    }

    private suspend fun getFirstYoutubeSearchResult(gameTitle: String): String? = withContext(Dispatchers.IO) {
        val searchExtractor = ServiceList.YouTube.getSearchExtractor("$gameTitle $searchString")
        searchExtractor.fetchPage()

        val items = searchExtractor.initialPage.items
        val firstResult = items.filterIsInstance<StreamInfoItem>().firstOrNull()

        return@withContext firstResult?.url
    }

    private suspend fun extractYoutubeStreamUrl(url: String, context: Context): String? {
        val ytExtractor = YTExtractor(context, CACHING = false, LOGGING = false, retryCount = 3)
        ytExtractor.extract(url)
        val result = ytExtractor.getYTFiles()

        // Pick the standard M4A audio stream (itag 140)
        return result?.get(140)?.url
    }

    private suspend fun downloadToLocal(url: String, gameTitle: String, context: Context): File = withContext(Dispatchers.IO) {
        val fileName = "${gameTitle.replace(" ", "_")}.m4a"
        val file = File(context.getExternalFilesDir("es-de/music"), fileName)

        val request = okhttp3.Request.Builder().url(url).build()
        OkHttpClient().newCall(request).execute().use { response ->
            response.body?.byteStream().use { input ->
                file.outputStream().use { output -> input?.copyTo(output) }
            }
        }
        return@withContext file
    }

}