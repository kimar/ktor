package io.ktor.client.utils


data class HttpProtocolVersion(val name: String, val major: Int, val minor: Int) {
    companion object {
        val HTTP_2_0 = HttpProtocolVersion("HTTP", 2, 0)
    }
}

