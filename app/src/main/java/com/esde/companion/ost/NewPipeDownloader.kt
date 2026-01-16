package com.esde.companion.ost

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

class NewPipeDownloader(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val url = request.url()

        // Fix: POST requests MUST have a body in OkHttp
        val body = if (method == "POST") {
            // NewPipe provides the POST data via request.data()
            // We convert that ByteArray to an OkHttp RequestBody
            request.dataToSend()?.toRequestBody(null) ?: "".toRequestBody(null)
        } else {
            null
        }

        val okRequest = okhttp3.Request.Builder()
            .url(url)
            .method(method, body)
            .apply {
                request.headers().forEach { (name, values) ->
                    values.forEach { value -> addHeader(name, value) }
                }
            }
            .build()

        val response = client.newCall(okRequest).execute()

        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            response.body?.string(),
            response.request.url.toString()
        )
    }
}

