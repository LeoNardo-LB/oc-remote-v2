package dev.minios.ocremote.data.v2

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val TAG = "V2-SseConn"

/**
 * Consumes raw SSE JSON strings (from V1's SharedFlow) and parses them
 * into [SseEventV2] using [EventParser].
 *
 * No longer establishes its own HTTP connection — reuses V1's connection.
 * Retains event deduplication.
 */
class SseConnectionManager(
    private val rawEventsFlow: Flow<String>,
    private val parser: EventParser = EventParser,
    private val deduplicator: EventDeduplicator = EventDeduplicator(),
) {
    /**
     * Connect to the raw SSE event stream and parse events.
     * The [rawEventsFlow] is expected to be V1's [SseClient.rawSseEventFlow].
     */
    fun connect(): Flow<SseEventV2> = flow {
        rawEventsFlow.collect { data ->
            try {
                val event = parser.parse(data)
                if (event != null && !deduplicator.isDuplicate(event)) {
                    emit(event)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Parse error: ${e.message}")
            }
        }
    }
}
