package io.ktor.client.features

import io.ktor.client.*
import io.ktor.pipeline.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import java.io.*
import java.nio.charset.*


class HttpPlainText(val defaultCharset: Charset) {
    suspend fun read(response: HttpResponseBuilder): String? {
        val payload = response.body as? HttpMessageBody ?: return null
        val charset = response.headers.charset() ?: defaultCharset

        return when (payload) {
            is OutputStreamBody -> {
                val stream = ByteArrayOutputStream()
                payload.block(stream)
                stream.toString(charset.name())
            }
            is InputStreamBody -> InputStreamReader(payload.stream, charset).readText()
            is EmptyBody -> ""
        }
    }

    fun write(requestBuilder: HttpRequestBuilder): HttpMessageBody? {
        val requestString = requestBuilder.body as? String ?: return null
        val charset = requestBuilder.charset ?: defaultCharset
        val payload = requestString.toByteArray(charset)

        with(requestBuilder.headers) {
            get(HttpHeaders.ContentType) ?: contentType(ContentType.Text.Plain.withCharset(charset))
        }

        return InputStreamBody(ByteArrayInputStream(payload))
    }

    class Config {
        var defaultCharset: Charset = Charset.defaultCharset()

        fun build(): HttpPlainText = HttpPlainText(defaultCharset)
    }

    companion object Feature : HttpClientFeature<Config, HttpPlainText> {
        override val key = AttributeKey<HttpPlainText>("HttpPlainText")

        override fun prepare(block: Config.() -> Unit): HttpPlainText = Config().apply(block).build()

        override fun install(feature: HttpPlainText, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Transform) { builder: HttpRequestBuilder ->
                builder.body = feature.write(builder) ?: return@intercept
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Transform) { (expectedType, _, response) ->
                if (expectedType != String::class) return@intercept

                response.body = feature.read(response) ?: return@intercept
            }
        }
    }
}

