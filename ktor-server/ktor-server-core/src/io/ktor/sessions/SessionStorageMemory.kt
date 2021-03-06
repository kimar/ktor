package io.ktor.sessions

import io.ktor.cio.*
import java.util.concurrent.*

class SessionStorageMemory : SessionStorage {
    private val sessions = ConcurrentHashMap<String, ByteArray>()

    override suspend fun write(id: String, provider: suspend (WriteChannel) -> Unit) {
        val writeChannel = ByteBufferWriteChannel()
        provider(writeChannel)
        sessions[id] = writeChannel.toByteArray()
    }

    override suspend fun <R> read(id: String, consumer: suspend (ReadChannel) -> R): R {
        return sessions[id]?.let { bytes -> consumer(bytes.toReadChannel()) } ?: throw NoSuchElementException("Session $id not found")
    }

    override suspend fun invalidate(id: String) {
        sessions.remove(id)
    }
}
