package io.ktor.server.cio

import io.ktor.http.cio.*
import io.ktor.util.*

class CIOHeaders(private val headers: HttpHeadersMap) : ValuesMap {
    private val names: Set<String> by lazy {
        LinkedHashSet<String>(headers.size).apply {
            for (i in 0 until headers.size) {
                add(headers.nameAt(i).toString())
            }
        }
    }

    override val caseInsensitiveName: Boolean get() = true

    override fun names() = names
    override fun get(name: String): String? = headers.get(name)?.toString()
    override fun getAll(name: String): List<String>? {
        return get(name)?.let { listOf(it) }
    }

    override fun isEmpty() = headers.size == 0
    override fun entries(): Set<Map.Entry<String, List<String>>> {
        return (0 until headers.size).map { idx -> Entry(idx) }.toSet()
    }

    private inner class Entry(private val idx: Int) : Map.Entry<String, List<String>> {
        override val key: String get() = headers.nameAt(idx).toString()
        override val value: List<String> get() = listOf(headers.valueAt(idx).toString())
    }
}