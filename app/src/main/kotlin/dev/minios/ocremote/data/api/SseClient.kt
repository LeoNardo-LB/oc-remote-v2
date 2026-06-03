package dev.minios.ocremote.data.api

import android.util.Log
import dev.minios.ocremote.BuildConfig
import dev.minios.ocremote.domain.model.*
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import io.ktor.utils.io.ClosedReadChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SseClient"
private const val HEARTBEAT_TIMEOUT_MS = 40_000L

/**
 * 读取原始字节直到遇到 \n，不做 UTF-8 解码。
 * 返回 null 表示 channel 已关闭且无更多数据。
 * 兼容 CRLF：跳过 \r 字节。
 */
private suspend fun ByteReadChannel.readRawLineBytes(): List<Byte>? {
    val result = mutableListOf<Byte>()
    try {
        while (true) {
            val b = readByte()
            if (b == '\n'.code.toByte()) break
            if (b == '\r'.code.toByte()) continue  // 兼容 CRLF
            result.add(b)
        }
    } catch (e: ClosedReadChannelException) {
        if (result.isEmpty()) return null
    }
    return result
}

/**
 * 将 byte 块列表拼接为完整字节数组，然后一次性 UTF-8 解码。
 */
private fun buildStringFromBytes(chunks: List<List<Byte>>): String {
    val totalSize = chunks.sumOf { it.size }
    if (totalSize == 0) return ""
    val array = ByteArray(totalSize)
    var pos = 0
    for (chunk in chunks) {
        for (b in chunk) {
            array[pos++] = b
        }
    }
    return array.toString(Charsets.UTF_8)
}

/**
 * SSE (Server-Sent Events) Client
 *
 * Stateless — all connection info comes from the [ServerConnection] parameter.
 * Safe to use for multiple servers concurrently.
 */
@Singleton
class SseClient @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json
) {

    /**
     * Connect to the global event stream.
     * Returns a Flow that emits SSE events.
     * The flow does NOT auto-reconnect internally — callers should handle
     * reconnection themselves (the service already does exponential backoff).
     */
    fun connectToGlobalEvents(conn: ServerConnection, directory: String? = null): Flow<SseEvent> = flow {
        val sseUrl = "${conn.baseUrl}/global/event"
        Log.i(TAG, "Connecting to SSE: $sseUrl (auth=${conn.authHeader != null})")

        val statement = httpClient.prepareGet(sseUrl) {
            conn.authHeader?.let { header("Authorization", it) }
            header("Accept", "text/event-stream")
            directory?.let { header("x-opencode-directory", URLEncoder.encode(it, "UTF-8")) }

            timeout {
                requestTimeoutMillis = Long.MAX_VALUE
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = Long.MAX_VALUE
            }
        }

        statement.execute { response ->
            val statusCode = response.status.value
            Log.i(TAG, "SSE response: status=$statusCode, contentType=${response.headers["content-type"]}")

            if (statusCode == 401) {
                Log.e(TAG, "SSE auth failed (401). Check username/password.")
                throw SseAuthException("Authentication failed (401)")
            }

            if (statusCode !in 200..299) {
                Log.e(TAG, "SSE failed with HTTP $statusCode")
                throw SseConnectionException("HTTP $statusCode")
            }

            val channel = response.bodyAsChannel()
            var lastHeartbeat = System.currentTimeMillis()
            val buffer = mutableListOf<List<Byte>>()
            var eventCount = 0

            Log.i(TAG, "SSE stream opened, reading events...")

            while (!channel.isClosedForRead) {
                if (System.currentTimeMillis() - lastHeartbeat > HEARTBEAT_TIMEOUT_MS) {
                    Log.w(TAG, "Heartbeat timeout after $eventCount events, reconnecting...")
                    break
                }

                val lineBytes = channel.readRawLineBytes() ?: break

                if (lineBytes.isEmpty()) {
                    // 空白行 = SSE event 边界 → 解码整个 buffer
                    val data = buildStringFromBytes(buffer)
                    if (data.isNotEmpty()) {
                        try {
                            val event = parseEvent(data)
                            if (event != null) {
                                eventCount++
                                if (event is SseEvent.ServerHeartbeat) {
                                    lastHeartbeat = System.currentTimeMillis()
                                    if (BuildConfig.DEBUG) Log.d(TAG, "Heartbeat received (total events: $eventCount)")
                                } else {
                                    if (BuildConfig.DEBUG) Log.d(TAG, "Event #$eventCount: ${event::class.simpleName}")
                                    emit(event)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Parse error: ${data.take(200)}", e)
                        }
                        buffer.clear()
                    }
                } else {
                    // data: 行 → 提取 payload 的原始字节
                    val prefix = "data:".encodeToByteArray()
                    var start = 0
                    if (lineBytes.size >= prefix.size &&
                        lineBytes.subList(0, prefix.size) == prefix.toList()) {
                        start = prefix.size
                        if (start < lineBytes.size && lineBytes[start] == ' '.code.toByte()) {
                            start++  // 跳过 "data: " 中的空格
                        }
                    }
                    if (start < lineBytes.size) {
                        buffer.add(lineBytes.subList(start, lineBytes.size))
                    }
                }
            }

            Log.w(TAG, "SSE stream closed after $eventCount events")
        }
    }

    /**
     * Connect to the per-instance event stream (V2).
     * GET /event
     * Returns a Flow that emits SSE events.
     */
    fun connectToInstanceEvents(conn: ServerConnection, directory: String? = null): Flow<SseEvent> = flow {
        val sseUrl = "${conn.baseUrl}/event"
        Log.i(TAG, "Connecting to instance SSE: $sseUrl (auth=${conn.authHeader != null})")

        val statement = httpClient.prepareGet(sseUrl) {
            conn.authHeader?.let { header("Authorization", it) }
            header("Accept", "text/event-stream")
            directory?.let { header("x-opencode-directory", URLEncoder.encode(it, "UTF-8")) }

            timeout {
                requestTimeoutMillis = Long.MAX_VALUE
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = Long.MAX_VALUE
            }
        }

        statement.execute { response ->
            val statusCode = response.status.value
            Log.i(TAG, "Instance SSE response: status=$statusCode")

            if (statusCode == 401) {
                throw SseAuthException("Authentication failed (401)")
            }

            if (statusCode !in 200..299) {
                throw SseConnectionException("HTTP $statusCode")
            }

            val channel = response.bodyAsChannel()
            var lastHeartbeat = System.currentTimeMillis()
            val buffer = mutableListOf<List<Byte>>()
            var eventCount = 0

            while (!channel.isClosedForRead) {
                if (System.currentTimeMillis() - lastHeartbeat > HEARTBEAT_TIMEOUT_MS) {
                    Log.w(TAG, "Instance SSE heartbeat timeout after $eventCount events")
                    break
                }

                val lineBytes = channel.readRawLineBytes() ?: break

                if (lineBytes.isEmpty()) {
                    // 空白行 = SSE event 边界 → 解码整个 buffer
                    val data = buildStringFromBytes(buffer)
                    if (data.isNotEmpty()) {
                        try {
                            val event = parseEvent(data)
                            if (event != null) {
                                eventCount++
                                if (event is SseEvent.ServerHeartbeat) {
                                    lastHeartbeat = System.currentTimeMillis()
                                } else {
                                    if (BuildConfig.DEBUG) Log.d(TAG, "Instance event #$eventCount: ${event::class.simpleName}")
                                    emit(event)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Instance parse error: ${data.take(200)}", e)
                        }
                        buffer.clear()
                    }
                } else {
                    // data: 行 → 提取 payload 的原始字节
                    val prefix = "data:".encodeToByteArray()
                    var start = 0
                    if (lineBytes.size >= prefix.size &&
                        lineBytes.subList(0, prefix.size) == prefix.toList()) {
                        start = prefix.size
                        if (start < lineBytes.size && lineBytes[start] == ' '.code.toByte()) {
                            start++  // 跳过 "data: " 中的空格
                        }
                    }
                    if (start < lineBytes.size) {
                        buffer.add(lineBytes.subList(start, lineBytes.size))
                    }
                }
            }

            Log.w(TAG, "Instance SSE stream closed after $eventCount events")
        }
    }

    /**
     * Parse SSE event from raw JSON.
     * Global endpoint wraps events: {directory, payload: {type, properties}}
     * Per-instance endpoint sends directly: {type, properties}
     */
    private fun parseEvent(data: String): SseEvent? {
        val root = json.parseToJsonElement(data).jsonObject

        val payload = root["payload"]?.jsonObject ?: root
        val type = payload["type"]?.jsonPrimitive?.content ?: return null
        val properties = payload["properties"]?.jsonObject ?: JsonObject(emptyMap())

        return parseEventByType(type, properties)
    }

    private fun parseEventByType(type: String, props: JsonObject): SseEvent? {
        return try {
            when (type) {
                "server.connected" -> SseEvent.ServerConnected
                "server.heartbeat" -> SseEvent.ServerHeartbeat

                "session.status" -> {
                    val sessionId = props.str("sessionID")
                    val statusObj = props["status"]?.jsonObject
                    val statusType = statusObj?.get("type")?.jsonPrimitive?.content ?: "idle"

                    val status = when (statusType) {
                        "idle" -> SessionStatus.Idle
                        "busy" -> SessionStatus.Busy
                        "retry" -> SessionStatus.Retry(
                            attempt = statusObj?.get("attempt")?.jsonPrimitive?.int ?: 0,
                            message = statusObj?.get("message")?.jsonPrimitive?.content ?: "",
                            next = statusObj?.get("next")?.jsonPrimitive?.long ?: 0
                        )
                        else -> SessionStatus.Idle
                    }

                    Log.i(TAG, "Session $sessionId status -> $statusType")
                    SseEvent.SessionStatus(sessionId = sessionId, status = status)
                }

                "session.idle" -> {
                    val sessionId = props.str("sessionID")
                    Log.i(TAG, "Session $sessionId idle")
                    SseEvent.SessionIdle(sessionId = sessionId)
                }

                "session.created" -> {
                    val infoObj = props["info"]?.jsonObject ?: props
                    val info = json.decodeFromJsonElement<Session>(infoObj)
                    SseEvent.SessionCreated(info)
                }

                "session.updated" -> {
                    val infoObj = props["info"]?.jsonObject ?: props
                    val info = json.decodeFromJsonElement<Session>(infoObj)
                    SseEvent.SessionUpdated(info)
                }

                "session.deleted" -> {
                    val infoObj = props["info"]?.jsonObject ?: props
                    val info = json.decodeFromJsonElement<Session>(infoObj)
                    SseEvent.SessionDeleted(info)
                }

                "session.error" -> {
                    val sessionId = props["sessionID"]?.jsonPrimitive?.content
                    val error = props.str("error", "Unknown error")
                    SseEvent.SessionError(sessionId = sessionId, error = error)
                }

                "session.diff" -> {
                    val sessionId = props.str("sessionID")
                    val diffArr = props["diff"]?.jsonArray
                    val diffs = diffArr?.map { json.decodeFromJsonElement<FileDiff>(it) } ?: emptyList()
                    SseEvent.SessionDiff(sessionId = sessionId, diff = diffs)
                }

                "message.updated" -> {
                    val infoObj = props["info"]?.jsonObject ?: return null
                    val message = parseMessage(infoObj) ?: return null
                    SseEvent.MessageUpdated(info = message)
                }

                "message.removed" -> {
                    val sessionId = props.str("sessionID")
                    val messageId = props.str("messageID")
                    SseEvent.MessageRemoved(sessionId = sessionId, messageId = messageId)
                }

                "message.part.updated" -> {
                    val partObj = props["part"]?.jsonObject ?: return null
                    val part = parsePart(partObj) ?: return null
                    SseEvent.MessagePartUpdated(part = part)
                }

                "message.part.delta" -> {
                    val sessionId = props.str("sessionID")
                    val messageId = props.str("messageID")
                    val partId = props.str("partID")
                    val field = props.str("field", "text")
                    val delta = props.str("delta")
                    SseEvent.MessagePartDelta(
                        sessionId = sessionId,
                        messageId = messageId,
                        partId = partId,
                        field = field,
                        delta = delta
                    )
                }

                "message.part.removed" -> {
                    val sessionId = props.str("sessionID")
                    val messageId = props.str("messageID")
                    val partId = props.str("partID")
                    SseEvent.MessagePartRemoved(
                        sessionId = sessionId,
                        messageId = messageId,
                        partId = partId
                    )
                }

                "permission.asked" -> {
                    val id = props.str("id")
                    val sessionId = props.str("sessionID")
                    val permission = props.str("permission")
                    val patterns = props["patterns"]?.jsonArray
                        ?.map { it.jsonPrimitive.content } ?: emptyList()
                    // V2: always is Boolean; fallback to V1 List<String> for backward compat
                    val always = props["always"]?.let { el ->
                        when {
                            el is JsonPrimitive -> el.booleanOrNull ?: false
                            el is JsonArray -> el.isNotEmpty()
                            else -> false
                        }
                    } ?: false
                    val metadata = props["metadata"]?.jsonObject?.let { obj ->
                        obj.mapValues { (_, v) -> v.jsonPrimitive.contentOrNull ?: v.toString() }
                    }
                    val toolRef = props["tool"]?.jsonObject?.let { toolObj ->
                        ToolRef(
                            messageId = toolObj.str("messageID"),
                            callId = toolObj.str("callID")
                        )
                    }

                    Log.i(TAG, "Permission asked: $permission for session $sessionId")
                    SseEvent.PermissionAsked(
                        id = id,
                        sessionId = sessionId,
                        permission = permission,
                        patterns = patterns,
                        always = always,
                        metadata = metadata,
                        tool = toolRef
                    )
                }

                "permission.replied" -> {
                    val sessionId = props.str("sessionID")
                    val requestId = props.str("requestID")
                    SseEvent.PermissionReplied(sessionId = sessionId, requestId = requestId)
                }

                "question.asked" -> {
                    val id = props.str("id")
                    val sessionId = props.str("sessionID")
                    val toolRef = props["tool"]?.jsonObject?.let { toolObj ->
                        ToolRef(
                            messageId = toolObj.str("messageID"),
                            callId = toolObj.str("callID")
                        )
                    }
                    Log.i(TAG, "Question asked for session $sessionId")
                    val questionsArr = props["questions"]?.jsonArray
                    val questions = questionsArr?.map { qElement ->
                        val qObj = qElement.jsonObject
                        val optionsArr = qObj["options"]?.jsonArray ?: JsonArray(emptyList())
                        val options = optionsArr.map { oElement ->
                            val oObj = oElement.jsonObject
                            SseEvent.QuestionAsked.Option(
                                label = oObj.str("label"),
                                description = oObj.str("description")
                            )
                        }
                        SseEvent.QuestionAsked.Question(
                            header = qObj.str("header"),
                            question = qObj.str("question"),
                            multiple = qObj["multiple"]?.jsonPrimitive?.booleanOrNull ?: false,
                            custom = qObj["custom"]?.jsonPrimitive?.booleanOrNull ?: true,
                            options = options
                        )
                    } ?: emptyList()
                    SseEvent.QuestionAsked(
                        id = id,
                        sessionId = sessionId,
                        questions = questions,
                        tool = toolRef
                    )
                }

                "question.replied" -> {
                    val sessionId = props.str("sessionID")
                    val requestId = props.str("requestID")
                    SseEvent.QuestionReplied(sessionId = sessionId, requestId = requestId)
                }

                "question.rejected" -> {
                    val sessionId = props.str("sessionID")
                    val requestId = props.str("requestID")
                    SseEvent.QuestionRejected(sessionId = sessionId, requestId = requestId)
                }

                "todo.updated" -> {
                    val sessionId = props.str("sessionID")
                    val todosArr = props["todos"]?.jsonArray
                    val todos = todosArr?.map { tElement ->
                        val tObj = tElement.jsonObject
                        SseEvent.TodoUpdated.Todo(
                            content = tObj.str("content"),
                            status = tObj.str("status", "pending"),
                            priority = tObj.str("priority", "medium")
                        )
                    } ?: emptyList()
                    SseEvent.TodoUpdated(sessionId = sessionId, todos = todos)
                }

                "vcs.branch.updated" -> {
                    val branch = props.str("branch")
                    SseEvent.VcsBranchUpdated(branch = branch)
                }

                "lsp.updated" -> SseEvent.LspUpdated

                "project.updated" -> {
                    val infoObj = props["info"]?.jsonObject ?: props
                    val info = json.decodeFromJsonElement<Project>(infoObj)
                    SseEvent.ProjectUpdated(info)
                }

                // V2 new events
                "session.compacted" -> {
                    SseEvent.SessionCompacted(sessionId = props.str("sessionID"))
                }

                "pty.created" -> {
                    SseEvent.PtyCreated(
                        id = props.str("id"),
                        title = props.str("title"),
                        command = props.str("command"),
                        cwd = props.str("cwd")
                    )
                }

                "pty.updated" -> {
                    SseEvent.PtyUpdated(
                        id = props.str("id"),
                        title = props.str("title"),
                        command = props.str("command"),
                        status = props.str("status")
                    )
                }

                "pty.deleted" -> {
                    SseEvent.PtyDeleted(id = props.str("id"))
                }

                "workspace.ready" -> {
                    SseEvent.WorkspaceReady(workspaceId = props.str("workspaceID"))
                }

                "workspace.failed" -> {
                    SseEvent.WorkspaceFailed(
                        workspaceId = props.str("workspaceID"),
                        error = props.strOrNull("error")
                    )
                }

                "file.edited" -> {
                    SseEvent.FileEdited(path = props.str("path"))
                }

                "mcp.tools.changed" -> {
                    SseEvent.McpToolsChanged(server = props.str("server"))
                }

                "command.executed" -> {
                    SseEvent.CommandExecuted(
                        name = props.str("name"),
                        sessionId = props.str("sessionID"),
                        arguments = props.str("arguments"),
                        messageId = props.str("messageID")
                    )
                }

                "file.watcher.updated" -> {
                    SseEvent.FileWatcherUpdated(path = props.str("path"))
                }

                "installation.updated" -> {
                    SseEvent.InstallationUpdated(version = props.str("version"))
                }

                "installation.update_available" -> {
                    SseEvent.InstallationUpdateAvailable(version = props.str("version"))
                }

                "worktree.ready" -> {
                    SseEvent.WorktreeReady(path = props.str("path"))
                }

                "worktree.failed" -> {
                    SseEvent.WorktreeFailed(
                        path = props.str("path"),
                        error = props.strOrNull("error")
                    )
                }

                else -> if (type.startsWith("session.next.")) {
                    val nextEvent = parseSessionNextEvent(type, props)
                    SseEvent.SessionNext(nextEvent)
                } else {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Unhandled event: $type")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse $type: ${e.message}", e)
            null
        }
    }

    // ============ Session Next Event Parsing ============

    /**
     * Parse a session.next.* event from type string and properties.
     * Called when the SSE event type starts with "session.next.".
     * Uses kotlinx.serialization Json to decode into the appropriate SessionNextEvent variant.
     */
    fun parseSessionNextEvent(type: String, props: JsonObject): SessionNextEvent {
        return try {
            // Inject the type into props so the discriminator can select the correct variant
            val propsWithType = JsonObject(props + ("type" to JsonPrimitive(type)))
            val result = json.decodeFromString<SessionNextEvent>(propsWithType.toString())
            // Serializer routes unknown types to Unknown but doesn't populate rawType from "type"
            if (result is SessionNextEvent.Unknown && result.rawType.isEmpty()) {
                result.copy(rawType = type, rawJson = props.toString())
            } else {
                result
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse session.next event: $type — ${e.message}")
            SessionNextEvent.Unknown(rawType = type, rawJson = props.toString())
        }
    }

    // ============ Message Parsing ============

    /**
     * Parse a Message from JSON, dispatching on "role" field.
     */
    private fun parseMessage(obj: JsonObject): Message? {
        val role = obj["role"]?.jsonPrimitive?.content ?: return null
        return when (role) {
            "user" -> json.decodeFromJsonElement<Message.User>(obj)
            "assistant" -> json.decodeFromJsonElement<Message.Assistant>(obj)
            else -> {
                Log.w(TAG, "Unknown message role: $role")
                null
            }
        }
    }

    /**
     * Parse a Part from JSON, dispatching on "type" field.
     */
    private fun parsePart(obj: JsonObject): Part? {
        val type = obj["type"]?.jsonPrimitive?.content ?: return null
        return try {
            when (type) {
                "text" -> json.decodeFromJsonElement<Part.Text>(obj)
                "reasoning" -> json.decodeFromJsonElement<Part.Reasoning>(obj)
                "tool" -> json.decodeFromJsonElement<Part.Tool>(obj)
                "step-start" -> json.decodeFromJsonElement<Part.StepStart>(obj)
                "step-finish" -> json.decodeFromJsonElement<Part.StepFinish>(obj)
                "file" -> json.decodeFromJsonElement<Part.File>(obj)
                "snapshot" -> json.decodeFromJsonElement<Part.Snapshot>(obj)
                "patch" -> json.decodeFromJsonElement<Part.Patch>(obj)
                "subtask" -> json.decodeFromJsonElement<Part.Subtask>(obj)
                "compaction" -> json.decodeFromJsonElement<Part.Compaction>(obj)
                "retry" -> json.decodeFromJsonElement<Part.Retry>(obj)
                "agent" -> json.decodeFromJsonElement<Part.Agent>(obj)
                else -> {
                    Log.w(TAG, "Unknown part type: $type")
                    // Return an Unknown part so it's at least tracked
                    Part.Unknown(
                        id = obj.str("id"),
                        sessionId = obj.str("sessionID"),
                        messageId = obj.str("messageID")
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse part type=$type: ${e.message}", e)
            null
        }
    }

    // ============ Helpers ============

    /** Safe string extraction with default. */
    private fun JsonObject.str(key: String, default: String = ""): String =
        this[key]?.jsonPrimitive?.content ?: default

    /** Nullable string extraction. */
    private fun JsonObject.strOrNull(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull
}

// ============ SSE Read Timeout Tracking ============

/**
 * Constants for SSE read timeout behavior.
 */
object SseClientDefaults {
    const val DEFAULT_READ_TIMEOUT_MS = 30_000L
    const val MAX_CONSECUTIVE_TIMEOUTS = 5
    const val COOLDOWN_DURATION_MS = 300_000L
}

/**
 * Tracks consecutive SSE read timeouts and manages cooldown state.
 *
 * After [maxConsecutiveTimeouts] consecutive timeouts, the tracker enters
 * a cooldown period ([cooldownDurationMs]) during which reconnection is delayed.
 */
class SseReadTimeoutTracker(
    val maxConsecutiveTimeouts: Int = SseClientDefaults.MAX_CONSECUTIVE_TIMEOUTS,
    val cooldownDurationMs: Long = SseClientDefaults.COOLDOWN_DURATION_MS
) {
    var consecutiveTimeouts: Int = 0
        private set
    private var cooldownUntilMs: Long = 0L

    /** Record a read timeout event. */
    fun recordTimeout() {
        consecutiveTimeouts++
    }

    /** Record a successful read — resets the consecutive counter. */
    fun recordSuccess() {
        consecutiveTimeouts = 0
    }

    /** Whether the tracker has reached the threshold for cooldown. */
    fun shouldEnterCooldown(): Boolean = consecutiveTimeouts >= maxConsecutiveTimeouts

    /** Enter cooldown mode. */
    fun enterCooldown() {
        cooldownUntilMs = System.currentTimeMillis() + cooldownDurationMs
    }

    /** Whether currently in the cooldown period. */
    fun isInCooldown(): Boolean = System.currentTimeMillis() < cooldownUntilMs

    /** Fully reset the tracker (clears both timeouts and cooldown). */
    fun reset() {
        consecutiveTimeouts = 0
        cooldownUntilMs = 0L
    }
}

/** Thrown when SSE returns 401 */
class SseAuthException(message: String) : Exception(message)

/** Thrown for non-2xx SSE responses */
class SseConnectionException(message: String) : Exception(message)
