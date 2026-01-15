package com.esde.companion.ost

import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

class NewPipeDownloader(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: Request): Response {
        val okHttpRequest = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), null)
            .build()
        val response = client.newCall(okHttpRequest).execute()
        return Response(response.code, response.message,
            response.headers.toMultimap(), response.body?.string(), request.url())
    }
}

