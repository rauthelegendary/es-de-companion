package com.esde.companion.ost

import android.R
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.internal.userAgent
import org.schabi.newpipe.extractor.Extractor
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchExtractor
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.collections.mutableListOf
import kotlin.ranges.contains

class MusicDownloader() {
    private var newPipe = initNewPipe()
    private val minResults = 20
    private val maxResults = 40
    private val secondarySearchString = "VGM"

    companion object {
        const val searchString = "OST"
    }

    private val systemAliases = mapOf(
        "gc" to setOf("gc", "gamecube", "nintendo gamecube", "game cube", "ngc"),
        "ps2" to setOf("ps2", "playstation 2", "playstation", "sony playstation 2"),
        "snes" to setOf("snes", "super nintendo", "super nintendo entertainment system", "super famicom", "sfc"),
        "gba" to setOf("gba", "gameboy advance", "game boy advance"),
        "nds" to setOf("nds", "nintendo ds", "ds"),
        "wii" to setOf("wii", "nintendo wii"),
        "n3ds" to setOf("n3ds", "3ds", "nintendo 3ds"),
        "switch" to setOf("switch", "nintendo switch"),
        "mastersystem" to setOf("master system", "sega master system", "mastersystem", "SMS"),
        "gamegear" to setOf("game gear", "gamegear", "sega game gear"),
        "megadrive" to setOf("megadrive", "mega drive", "sega megadrive","sega mega drive", "genesis", "sega genesis", "32x", "sega32x", "sega 32x"),
        "sega32x" to setOf("megadrive", "mega drive", "sega megadrive","sega mega drive", "genesis", "sega genesis", "32x", "sega32x", "sega 32x"),
        "dreamcast" to setOf("dreamcast", "sega dreamcast", "dream cast", "dc"),
        "psx" to setOf("psx", "playstation", "sony playstation", "ps1"),
        "psp" to setOf("psp", "playstation portable"),
        "psvita" to setOf("psvita", "playstation vita", "vita"),
        "fbneo" to setOf("fbneo", "mame", "arcade", "final burn neo"),
        "atari2600" to setOf("atari2600", "atari 2600", "atari"),
        "atari5200" to setOf("atari5200", "atari 5200", "atari"),
        "atari7800" to setOf("atari7800", "atari 7800", "atari"),
        "atarilynx" to setOf("atarilynx", "atari lynx", "lynx"),
        "mame" to setOf("fbneo", "mame", "arcade", "final burn neo"),
        "nes" to setOf("nes", "nintendo entertainment system", "nintendo", "famicon"),
        "gb" to setOf("gameboy", "gb", "game boy", "nintendo game boy", "nintendo gameboy"),
        "n64" to setOf("n64", "nintendo 64", "64"),
        "gbc" to setOf("gameboy", "gbc", "game boy", "gameboy color", "nintendo game boy", "nintendo gameboy", "game boy color", "nintendo game boy color", "nintendo gameboy color"),
        "gba" to setOf("gameboy", "gba", "game boy", "nintendo game boy", "nintendo gameboy", "gameboy advance", "game boy advance", "nintendo game boy advance", "nintendo gameboy advance")
    )

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
            return downloadGameMusicWithUrl(gameFileName, musicDir, videoUrl)
        }
        return null
    }

    suspend fun downloadGameMusicWithUrl(
        gameFilename: String,
        musicDir: File?,
        url: String
    ): File? {
        val streamUrl = getYoutubeAudioUrl(url)
        Log.d("CoroutineDebug", "streamUrl: " + streamUrl)
        if (streamUrl != null) {
            return downloadToLocal(streamUrl, gameFilename, musicDir)
        }
        return null
    }

    public suspend fun getYoutubeSearchResultsFiltered(query: String, gameTitle: String, system: String, first: Boolean): List<StreamInfoItem> = withContext(Dispatchers.IO) {
        val uniqueResults = mutableMapOf<String, StreamInfoItem>()

        try {
            performParallelSearch(query, gameTitle, system, uniqueResults, first)
            if(first && uniqueResults.isEmpty()) {
                performParallelSearch(createSearchString(gameTitle,secondarySearchString), gameTitle, system, uniqueResults, true)
            }
        } catch (e: Exception) {
            // This catches the "Permission Denied" and "SocketException" errors
            Log.e("MusicDebug", "Search failed unexpectedly: ${e.message}")
        }
        return@withContext uniqueResults.values.toList().sortedByDescending { it.viewCount }
    }

    private suspend fun performParallelSearch(
        query: String,
        gameTitle: String,
        system: String,
        uniqueResults: MutableMap<String, StreamInfoItem>,
        first: Boolean
    ) = coroutineScope {

        val videoExtractor = ServiceList.YouTube.getSearchExtractor(query, listOf("videos"), "")
        val videoTask = async {
            try {
                videoExtractor.fetchPage()
                videoExtractor.initialPage
            } catch (e: Exception) { null }
        }
        val videoPage = videoTask.await()
        videoPage?.let { getResultsFromExtractor(it, gameTitle, system, uniqueResults) }

        if(!first) {
            val playlistTask = async {
                try {
                    val extractor =
                        ServiceList.YouTube.getSearchExtractor(query, listOf("playlists"), "")
                    extractor.fetchPage()
                    extractor.initialPage
                } catch (e: Exception) {
                    null
                }
            }

            val playlistPage = playlistTask.await()
            playlistPage?.let { getResultsFromExtractor(it, gameTitle, system, uniqueResults) }
        }

        //loop more pages if low on results
        var currentPage = videoPage

        while (!first && uniqueResults.size < minResults && currentPage?.hasNextPage() == true && currentPage.items.isNotEmpty()) {
            val nextPage = videoExtractor.getPage(currentPage.nextPage)
            getResultsFromExtractor(nextPage, gameTitle, system, uniqueResults)
            currentPage = nextPage
        }
    }

    private fun normalize(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ") // Replace all punctuation with spaces
            .replace(Regex("\\s+"), " ")         // Collapse multiple spaces into one
            .trim()
    }

    private fun isPlaylistRelevant(playlistTitle: String, gameTitle: String, system: String): Boolean {
        val cleanPlaylist = normalize(playlistTitle)
        val cleanGame = normalize(gameTitle)

        //if it's an exact match after normalization, it's perfect.
        if (cleanPlaylist.contains(cleanGame)) return true

        val gameNumbers = "\\d+".toRegex().findAll(cleanGame).map { it.value }.toSet()
        val playlistNumbers = "\\d+".toRegex().findAll(cleanPlaylist)
            .map { it.value }
            .filter { it.length < 4 } // Ignore years like 2002, 1998
            .toSet()

        //make sure the playlist doesn't contain a different number than we're looking for
        if (gameNumbers.isNotEmpty()) {
            val isMissingGameNumber = gameNumbers.any { num -> !playlistNumbers.contains(num) }
            if (isMissingGameNumber) return false
        }

        val aliases = systemAliases.getOrDefault(system, setOf(system))
        val hasSystemMatch = aliases.any { alias -> cleanPlaylist.contains(alias) }

        //check for words that indicate music, remove words that are irrelevant for matching
        //also remove digits from the keywords (we've already made sure there's no mismatch earlier)
        val musicContext = setOf("ost", "vgm", "soundtrack", "music", "bgm", "score")
        val stopWords = setOf("the", "and", "for", "with", "all", "of", "a", "an")
        val gameKeywords = cleanGame.split(" ")
            .filter { it.length > 1 && it !in stopWords && it !in musicContext }
            .filter { !it.all { char -> char.isDigit() } }

        if (gameKeywords.isEmpty()) return false

        val matchCount = gameKeywords.count { keyword -> cleanPlaylist.contains(keyword) }
        val matchRatio = matchCount.toDouble() / gameKeywords.size
        val hasMusicContext = musicContext.any { context -> cleanPlaylist.contains(context) }

        return when {
            //single word titles need to match with context
            gameKeywords.size == 1 -> {
                matchCount == 1 && hasMusicContext
            }

            //two word titles need to fully match or partially match with music context
            gameKeywords.size == 2 -> {
                when {
                    matchCount == 2 -> true // High confidence in the game identity
                    matchCount == 1 && hasMusicContext -> true // Partial game match but confirmed music
                    else -> false
                }
            }

            //for longer titles, we want a partial match, but with the correct context we can lower the match threshold
            else -> {
                val scoreRequirement = when {
                    hasMusicContext && hasSystemMatch -> 0.5
                    hasMusicContext -> 0.6
                    else -> 0.7
                }
                matchRatio >= scoreRequirement
            }
        }
    }

    private suspend fun getResultsFromExtractor(itemsPage: ListExtractor.InfoItemsPage<InfoItem>, gameTitle: String, system: String, results: MutableMap<String, StreamInfoItem>) = withContext(Dispatchers.IO){
        try {
            for (item in itemsPage.items) {
                when (item) {
                    is StreamInfoItem -> {
                        if (item.duration in 31..359) results[item.url] = item
                    }

                    is org.schabi.newpipe.extractor.playlist.PlaylistInfoItem -> {
                        try {
                            if(isPlaylistRelevant(item.name, gameTitle, system)) {
                                val playlistExtractor =
                                    ServiceList.YouTube.getPlaylistExtractor(item.url)
                                playlistExtractor.fetchPage()

                                val playlistSongs = playlistExtractor.initialPage.items
                                    .filterIsInstance<StreamInfoItem>()
                                    .filter { it.duration in 31..359 }

                                for (item in playlistSongs) {
                                    if (results.size >= maxResults) break
                                    results[item.url] = item
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e(
                                "MusicDebug",
                                "Failed to unpack playlist: ${item.name}"
                            )
                        }
                    }
                }
                if(results.size >= maxResults) {
                    break
                }
            }
        } catch (e: Exception) {
            android.util.Log.d("MusicDebug", "Error processing page items")
            null
        }
    }

    private suspend fun getFirstYoutubeSearchResult(gameTitle: String, systemName: String): String? = withContext(Dispatchers.IO) {
        return@withContext getYoutubeSearchResultsFiltered(createSearchString(gameTitle, searchString), gameTitle, systemName, true).firstOrNull()?.url
    }

    private fun createSearchString(gameTitle: String, chosenSearchString: String): String {
        return "\"$gameTitle $chosenSearchString\""
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
            if (file.exists()) {
                file.delete()
            }

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
            File("")
        }
    }
}