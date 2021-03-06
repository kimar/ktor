package io.ktor.client.call

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.http.*
import java.net.*
import kotlin.reflect.*


data class HttpClientCall(val request: HttpRequest, val response: HttpResponse, private val scope: HttpClient) {
    suspend fun receive(expectedType: KClass<*> = Unit::class): HttpResponseContainer {
        val subject = HttpResponseContainer(expectedType, request, HttpResponseBuilder(response))
        val container = scope.responsePipeline.execute(scope, subject)

        assert(container.response.payload::class === expectedType || HttpResponse::class === expectedType)
        return container
    }
}

fun HttpClientCall.close() = response.close()

suspend fun HttpClient.call(builder: HttpRequestBuilder): HttpClientCall =
        requestPipeline.execute(this, HttpRequestBuilder().takeFrom(builder)) as HttpClientCall

suspend fun HttpClient.call(block: HttpRequestBuilder.() -> Unit = {}): HttpClientCall =
        call(HttpRequestBuilder().apply(block))

suspend fun HttpClient.call(url: URL, block: HttpRequestBuilder.() -> Unit = {}): HttpClientCall {
    val builder = HttpRequestBuilder()
    builder.url.takeFrom(url)
    builder.apply(block)

    return call(builder)
}

suspend fun HttpClient.call(url: String, block: HttpRequestBuilder.() -> Unit = {}): HttpClientCall =
        call(URL(decodeURLPart(url)), block)

suspend inline fun <reified T> HttpClientCall.receive(): T {
    val container = receive(T::class)
    if (T::class === HttpResponse::class) return container.response.build() as T

    return container.response.payload as T
}

