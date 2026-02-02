package com.esde.companion.ost.khinsider

import com.esde.companion.NetworkClientManager
import okhttp3.Request
import org.jsoup.Jsoup

object KhScraper {
    private const val BASE_URL = "https://downloads.khinsider.com"
    private val client = NetworkClientManager.baseClient

    fun searchAlbums(query: String, albumType: Int): List<KhAlbum> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$BASE_URL/search?search=$encodedQuery&album_type=$albumType"

        val html = fetchHtml(searchUrl) ?: return emptyList()
        val doc = Jsoup.parse(html)
        val results = mutableListOf<KhAlbum>()

        val rows = doc.select(".albumList tr")

        for (row in rows) {
            // 1. Skip the header row (the one with <th> tags)
            if (row.select("th").isNotEmpty()) continue

            // 2. The album title and link are in the second <td>
            val cells = row.select("td")
            if (cells.size < 2) continue // Safety check

            val albumCell = cells[1] // The cell containing the album link
            val link = albumCell.select("a").first() ?: continue

            val title = link.text()
            val href = link.attr("href")

            // 3. The icon is in the first <td> (class="albumIcon")
            val iconSrc = cells[0].select("img").first()?.attr("src")

            val fullUrl = if (href.startsWith("http")) href else "$BASE_URL$href"

            results.add(KhAlbum(title, fullUrl, iconSrc))
        }
        return results
    }

    fun getAlbumTracks(albumUrl: String): List<KhSong> {
        val html = fetchHtml(albumUrl) ?: return emptyList()
        val doc = Jsoup.parse(html)
        val songs = mutableListOf<KhSong>()

        val tableRows = doc.select("#songlist tr")

        for (row in tableRows) {
            if (row.select("th").isNotEmpty() || row.id() == "songlist_header") continue

            val nameLink = row.select("td.clickable-row a").first()
                ?: row.select("a[href*='/soundtracks/']").first()
                ?: continue

            val songName = nameLink.text().trim()
            val songHref = nameLink.attr("href")

            val fullDetailUrl = if (songHref.startsWith("http")) {
                songHref
            } else {
                "${BASE_URL.removeSuffix("/")}/${songHref.removePrefix("/")}"
            }
            songs.add(KhSong(songName, fullDetailUrl))
        }
        return songs
    }

    fun resolveAudioLink(songDetailUrl: String): KhPlayable? {
        val html = fetchHtml(songDetailUrl) ?: return null
        val doc = Jsoup.parse(html)

        val audioTag = doc.select("audio#audio").first()
        var mp3Url = audioTag?.attr("src")

        if (mp3Url.isNullOrEmpty()) {
            val downloadLink = doc.select("a[href$=.mp3]").first()
            mp3Url = downloadLink?.attr("href")
        }

        if (mp3Url.isNullOrEmpty()) return null

        val songTitle = doc.select("#echoTopic").text() ?: "Unknown Track"
        return KhPlayable(songTitle, mp3Url)
    }

    private fun fetchHtml(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android 13; Mobile; rv:109.0)")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}