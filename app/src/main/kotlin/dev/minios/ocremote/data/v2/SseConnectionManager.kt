package dev.minios.ocremote.data.v2

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

class SseConnectionManager(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val authHeader: String? = null,
    private val parser: EventParser = EventParser,
    private val deduplicator: EventDeduplicator = EventDeduplicator(),
) {
    private val initialDelayMs = 1000L
    private val maxDelayMs = 30000L
    private val maxConsecutiveErrors = 10

    fun connect(): Flow<SseEventV2> = flow {
        var delayMs = initialDelayMs
        var consecutiveErrors = 0
        while (currentCoroutineContext().isActive && consecutiveErrors < maxConsecutiveErrors) {
            try {
                httpClient.prepareGet("$baseUrl/global/event") {
                    headers {
                        append("Accept", "text/event-stream")
                        authHeader?.let { append("Authorization", it) }
                    }
                }.execute { response ->
                    val channel = response.bodyAsChannel()
                    while (!channel.isClosedForRead && currentCoroutineContext().isActive) {
                        val line = channel.readLine() ?: continue
                        if (line.startsWith("data: ")) {
                            val data = line.removePrefix("data: ")
                            if (data.isBlank()) continue
                            val event = parser.parse(data)
                            if (event != null && !deduplicator.isDuplicate(event)) {
                                emit(event)
                            }
                        }
                    }
                }
                // Connection closed normally — reset delay and reconnect
                delayMs = initialDelayMs
                consecutiveErrors = 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Connection error — exponential backoff
                consecutiveErrors++
                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(maxDelayMs)
            }
        }
    }
}
