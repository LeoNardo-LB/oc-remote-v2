package dev.minios.ocremote.data.repository

import dev.minios.ocremote.domain.model.*
import dev.minios.ocremote.domain.model.SseEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Characterization tests for EventReducer session/message/part handling.
 *
 * These tests lock in existing behavior so the upcoming Phase 0 refactoring
 * doesn't accidentally break state management. Every test documents *what*
 * the current code does, not *what it should do*.
 */
class EventReducerSessionTest {

    private lateinit var reducer: EventReducer

    @Before
    fun setup() {
        reducer = EventReducer()
    }

    // ============ Helpers ============

    private fun createTestSession(
        id: String = "session-1",
        title: String? = "Test Session",
        updated: Long = 1000L,
        created: Long = 900L
    ): Session = Session(
        id = id,
        slug = "test-session",
        projectId = "proj-1",
        title = title,
        time = Session.Time(created = created, updated = updated)
    )

    private fun createTestMessage(
        id: String = "msg-1",
        sessionId: String = "session-1",
        role: String = "user",
        created: Long = 1000L
    ): Message = Message.User(
        id = id,
        sessionId = sessionId,
        role = role,
        time = TimeInfo(created = created)
    )

    private fun createAssistantMessage(
        id: String = "msg-2",
        sessionId: String = "session-1",
        parentId: String = "msg-1",
        created: Long = 1100L
    ): Message = Message.Assistant(
        id = id,
        sessionId = sessionId,
        role = "assistant",
        time = TimeInfo(created = created),
        parentId = parentId
    )

    private fun createTextPart(
        id: String = "part-1",
        sessionId: String = "session-1",
        messageId: String = "msg-1",
        text: String = "Hello"
    ): Part.Text = Part.Text(
        id = id,
        sessionId = sessionId,
        messageId = messageId,
        text = text
    )

    private fun createToolPart(
        id: String = "part-tool-1",
        sessionId: String = "session-1",
        messageId: String = "msg-2",
        callId: String = "call-1",
        tool: String = "bash",
        state: ToolState = ToolState.Pending()
    ): Part.Tool = Part.Tool(
        id = id,
        sessionId = sessionId,
        messageId = messageId,
        callId = callId,
        tool = tool,
        state = state
    )

    // ============ Session Lifecycle ============

    @Test
    fun `SessionCreated adds session to sessions list`() = runTest {
        val session = createTestSession(id = "s1")

        reducer.processEvent(SseEvent.SessionCreated(session), "server-1")

        val sessions = reducer.sessions.value
        assertEquals(1, sessions.size)
        assertEquals(session, sessions[0])
    }

    @Test
    fun `SessionCreated sets initial status to Idle`() = runTest {
        val session = createTestSession(id = "s1")

        reducer.processEvent(SseEvent.SessionCreated(session), "server-1")

        assertEquals(SessionStatus.Idle, reducer.sessionStatuses.value["s1"])
    }

    @Test
    fun `SessionCreated tracks session under serverId`() = runTest {
        val session = createTestSession(id = "s1")

        reducer.processEvent(SseEvent.SessionCreated(session), "server-1")

        assertTrue(reducer.serverSessions.value["server-1"]!!.contains("s1"))
    }

    @Test
    fun `SessionUpdated updates existing session in list`() = runTest {
        val original = createTestSession(id = "s1", title = "Old Title", updated = 1000L)
        val updated = createTestSession(id = "s1", title = "New Title", updated = 2000L)

        reducer.processEvent(SseEvent.SessionCreated(original), "server-1")
        reducer.processEvent(SseEvent.SessionUpdated(updated), "server-1")

        val sessions = reducer.sessions.value
        assertEquals(1, sessions.size)
        assertEquals("New Title", sessions[0].title)
    }

    @Test
    fun `SessionUpdated upserts session not yet in list`() = runTest {
        // No SessionCreated sent — simulate missed event
        val session = createTestSession(id = "s1", title = "Upserted")

        reducer.processEvent(SseEvent.SessionUpdated(session), "server-1")

        val sessions = reducer.sessions.value
        assertEquals(1, sessions.size)
        assertEquals("Upserted", sessions[0].title)
    }

    @Test
    fun `SessionDeleted removes session and associated data`() = runTest {
        val session = createTestSession(id = "s1")
        val msg = createTestMessage(id = "m1", sessionId = "s1")
        val perm = SseEvent.PermissionAsked(id = "p1", sessionId = "s1", permission = "bash")

        reducer.processEvent(SseEvent.SessionCreated(session), "server-1")
        reducer.processEvent(SseEvent.MessageUpdated(msg), "server-1")
        reducer.processEvent(perm, "server-1")

        reducer.processEvent(SseEvent.SessionDeleted(session), "server-1")

        assertTrue(reducer.sessions.value.isEmpty())
        assertFalse(reducer.sessionStatuses.value.containsKey("s1"))
        assertFalse(reducer.messages.value.containsKey("s1"))
        assertFalse(reducer.permissions.value.containsKey("s1"))
    }

    @Test
    fun `sessions are sorted by updated descending`() = runTest {
        val old = createTestSession(id = "s1", updated = 1000L)
        val recent = createTestSession(id = "s2", updated = 3000L)
        val mid = createTestSession(id = "s3", updated = 2000L)

        reducer.processEvent(SseEvent.SessionCreated(old), "server-1")
        reducer.processEvent(SseEvent.SessionCreated(recent), "server-1")
        reducer.processEvent(SseEvent.SessionCreated(mid), "server-1")

        val sessions = reducer.sessions.value
        assertEquals("s2", sessions[0].id) // most recent first
        assertEquals("s3", sessions[1].id)
        assertEquals("s1", sessions[2].id)
    }

    // ============ Session Status ============

    @Test
    fun `SessionStatus event updates status map`() = runTest {
        val session = createTestSession(id = "s1")
        reducer.processEvent(SseEvent.SessionCreated(session), "server-1")

        reducer.processEvent(SseEvent.SessionStatus("s1", SessionStatus.Busy), "server-1")

        assertEquals(SessionStatus.Busy, reducer.sessionStatuses.value["s1"])
    }

    @Test
    fun `SessionIdle event sets status to Idle`() = runTest {
        val session = createTestSession(id = "s1")
        reducer.processEvent(SseEvent.SessionCreated(session), "server-1")
        reducer.processEvent(SseEvent.SessionStatus("s1", SessionStatus.Busy), "server-1")

        reducer.processEvent(SseEvent.SessionIdle("s1"), "server-1")

        assertEquals(SessionStatus.Idle, reducer.sessionStatuses.value["s1"])
    }

    @Test
    fun `updateSessionStatus manually sets status`() = runTest {
        reducer.updateSessionStatus("s1", SessionStatus.Busy)

        assertEquals(SessionStatus.Busy, reducer.sessionStatuses.value["s1"])
    }

    @Test
    fun `Retry status can be set`() = runTest {
        val retry = SessionStatus.Retry(attempt = 2, message = "retrying", next = 9999L)
        reducer.updateSessionStatus("s1", retry)

        assertEquals(retry, reducer.sessionStatuses.value["s1"])
    }

    @Test
    fun `CommandExecuted event resets session to Idle`() = runTest {
        val session = createTestSession(id = "s1")
        reducer.processEvent(SseEvent.SessionCreated(session), "server-1")
        reducer.updateSessionStatus("s1", SessionStatus.Busy)

        reducer.processEvent(SseEvent.CommandExecuted(name = "bash", sessionId = "s1"), "server-1")

        assertEquals(SessionStatus.Idle, reducer.sessionStatuses.value["s1"])
    }

    // ============ Message Handling ============

    @Test
    fun `MessageUpdated adds message to session`() = runTest {
        val msg = createTestMessage(id = "m1", sessionId = "s1")

        reducer.processEvent(SseEvent.MessageUpdated(msg), "server-1")

        val msgs = reducer.messages.value["s1"]
        assertEquals(1, msgs?.size)
        assertEquals(msg, msgs!![0])
    }

    @Test
    fun `MessageUpdated sorts messages by created descending`() = runTest {
        val old = createTestMessage(id = "m1", sessionId = "s1", created = 1000L)
        val recent = createTestMessage(id = "m2", sessionId = "s1", created = 3000L)
        val mid = createTestMessage(id = "m3", sessionId = "s1", created = 2000L)

        reducer.processEvent(SseEvent.MessageUpdated(old), "server-1")
        reducer.processEvent(SseEvent.MessageUpdated(recent), "server-1")
        reducer.processEvent(SseEvent.MessageUpdated(mid), "server-1")

        val msgs = reducer.messages.value["s1"]
        assertEquals("m2", msgs!![0].id) // newest first
        assertEquals("m3", msgs[1].id)
        assertEquals("m1", msgs[2].id)
    }

    @Test
    fun `MessageUpdated replaces existing message by id`() = runTest {
        val original = createTestMessage(id = "m1", sessionId = "s1", created = 1000L)
        val updated = Message.User(
            id = "m1",
            sessionId = "s1",
            role = "user",
            time = TimeInfo(created = 1000L, completed = 1500L)
        )

        reducer.processEvent(SseEvent.MessageUpdated(original), "server-1")
        reducer.processEvent(SseEvent.MessageUpdated(updated), "server-1")

        val msgs = reducer.messages.value["s1"]
        assertEquals(1, msgs?.size)
        assertEquals(1500L, msgs!![0].time.completed)
    }

    @Test
    fun `MessageRemoved deletes message from session`() = runTest {
        val m1 = createTestMessage(id = "m1", sessionId = "s1")
        val m2 = createTestMessage(id = "m2", sessionId = "s1")
        reducer.processEvent(SseEvent.MessageUpdated(m1), "server-1")
        reducer.processEvent(SseEvent.MessageUpdated(m2), "server-1")

        reducer.processEvent(SseEvent.MessageRemoved("s1", "m1"), "server-1")

        val msgs = reducer.messages.value["s1"]
        assertEquals(1, msgs?.size)
        assertEquals("m2", msgs!![0].id)
    }

    @Test
    fun `MessageRemoved also removes associated parts`() = runTest {
        val part = createTextPart(id = "p1", messageId = "m1")
        reducer.processEvent(SseEvent.MessagePartUpdated(part), "server-1")

        reducer.processEvent(SseEvent.MessageRemoved("s1", "m1"), "server-1")

        assertFalse(reducer.parts.value.containsKey("m1"))
    }

    // ============ Part Handling ============

    @Test
    fun `MessagePartUpdated adds part to message`() = runTest {
        val part = createTextPart(id = "p1", messageId = "m1")

        reducer.processEvent(SseEvent.MessagePartUpdated(part), "server-1")

        val parts = reducer.parts.value["m1"]
        assertEquals(1, parts?.size)
        assertEquals(part, parts!![0])
    }

    @Test
    fun `MessagePartUpdated replaces existing part by id`() = runTest {
        val original = createTextPart(id = "p1", messageId = "m1", text = "Hello")
        val updated = createTextPart(id = "p1", messageId = "m1", text = "Hello World")

        reducer.processEvent(SseEvent.MessagePartUpdated(original), "server-1")
        reducer.processEvent(SseEvent.MessagePartUpdated(updated), "server-1")

        val parts = reducer.parts.value["m1"]
        assertEquals(1, parts?.size)
        assertEquals("Hello World", (parts!![0] as Part.Text).text)
    }

    @Test
    fun `MessagePartDelta appends text to Text part`() = runTest {
        val part = createTextPart(id = "p1", messageId = "m1", text = "Hello")
        reducer.processEvent(SseEvent.MessagePartUpdated(part), "server-1")

        reducer.processEvent(
            SseEvent.MessagePartDelta(
                sessionId = "s1",
                messageId = "m1",
                partId = "p1",
                field = "text",
                delta = " World"
            ),
            "server-1"
        )

        val parts = reducer.parts.value["m1"]
        assertEquals("Hello World", (parts!![0] as Part.Text).text)
    }

    @Test
    fun `MessagePartDelta appends text to Reasoning part`() = runTest {
        val reasoning = Part.Reasoning(
            id = "p1", sessionId = "s1", messageId = "m1", text = "Thinking"
        )
        reducer.processEvent(SseEvent.MessagePartUpdated(reasoning), "server-1")

        reducer.processEvent(
            SseEvent.MessagePartDelta(
                sessionId = "s1", messageId = "m1", partId = "p1", field = "text", delta = " more"
            ),
            "server-1"
        )

        val parts = reducer.parts.value["m1"]
        assertEquals("Thinking more", (parts!![0] as Part.Reasoning).text)
    }

    @Test
    fun `MessagePartDelta does nothing for unknown partId`() = runTest {
        // No parts exist for m1
        reducer.processEvent(
            SseEvent.MessagePartDelta(
                sessionId = "s1", messageId = "m1", partId = "nonexistent",
                field = "text", delta = "ignored"
            ),
            "server-1"
        )

        assertTrue(reducer.parts.value.isEmpty())
    }

    @Test
    fun `MessagePartDelta does nothing for non-text part types`() = runTest {
        val toolPart = createToolPart(messageId = "m1")
        reducer.processEvent(SseEvent.MessagePartUpdated(toolPart), "server-1")

        reducer.processEvent(
            SseEvent.MessagePartDelta(
                sessionId = "s1", messageId = "m1", partId = "part-tool-1",
                field = "text", delta = "ignored"
            ),
            "server-1"
        )

        // Tool part remains unchanged
        val parts = reducer.parts.value["m1"]
        assertEquals(1, parts?.size)
        assertTrue(parts!![0] is Part.Tool)
    }

    @Test
    fun `MessagePartRemoved removes part from message`() = runTest {
        val p1 = createTextPart(id = "p1", messageId = "m1")
        val p2 = createTextPart(id = "p2", messageId = "m1")
        reducer.processEvent(SseEvent.MessagePartUpdated(p1), "server-1")
        reducer.processEvent(SseEvent.MessagePartUpdated(p2), "server-1")

        reducer.processEvent(
            SseEvent.MessagePartRemoved("s1", "m1", "p1"), "server-1"
        )

        val parts = reducer.parts.value["m1"]
        assertEquals(1, parts?.size)
        assertEquals("p2", parts!![0].id)
    }

    // ============ Server Session Tracking ============

    @Test
    fun `multiple sessions tracked under same server`() = runTest {
        val s1 = createTestSession(id = "s1")
        val s2 = createTestSession(id = "s2")

        reducer.processEvent(SseEvent.SessionCreated(s1), "server-1")
        reducer.processEvent(SseEvent.SessionCreated(s2), "server-1")

        val serverSessionIds = reducer.serverSessions.value["server-1"]
        assertEquals(2, serverSessionIds?.size)
        assertTrue(serverSessionIds!!.contains("s1"))
        assertTrue(serverSessionIds.contains("s2"))
    }

    @Test
    fun `sessions from different servers are tracked separately`() = runTest {
        val s1 = createTestSession(id = "s1")
        val s2 = createTestSession(id = "s2")

        reducer.processEvent(SseEvent.SessionCreated(s1), "server-A")
        reducer.processEvent(SseEvent.SessionCreated(s2), "server-B")

        assertTrue(reducer.serverSessions.value["server-A"]!!.contains("s1"))
        assertFalse(reducer.serverSessions.value["server-A"]!!.contains("s2"))
        assertTrue(reducer.serverSessions.value["server-B"]!!.contains("s2"))
    }

    // ============ Batch Operations ============

    @Test
    fun `setSessions loads initial session list`() = runTest {
        val s1 = createTestSession(id = "s1", updated = 2000L)
        val s2 = createTestSession(id = "s2", updated = 1000L)

        reducer.setSessions("server-1", listOf(s1, s2))

        val sessions = reducer.sessions.value
        assertEquals(2, sessions.size)
        assertEquals("s1", sessions[0].id) // sorted by updated desc
        assertTrue(reducer.serverSessions.value["server-1"]!!.contains("s1"))
        assertTrue(reducer.serverSessions.value["server-1"]!!.contains("s2"))
    }

    @Test
    fun `setMessages loads messages and parts`() = runTest {
        val msg = createTestMessage(id = "m1", sessionId = "s1")
        val part = createTextPart(id = "p1", messageId = "m1")
        val msgWithParts = MessageWithParts(info = msg, parts = listOf(part))

        reducer.setMessages("s1", listOf(msgWithParts))

        val msgs = reducer.messages.value["s1"]
        assertEquals(1, msgs?.size)
        assertEquals(msg, msgs!![0])

        val parts = reducer.parts.value["m1"]
        assertEquals(1, parts?.size)
        assertEquals(part, parts!![0])
    }

    @Test
    fun `mergeMessages adds new messages while preserving existing`() = runTest {
        val existing = createTestMessage(id = "m1", sessionId = "s1", created = 1000L)
        val part = createTextPart(id = "p1", messageId = "m1")
        reducer.setMessages("s1", listOf(MessageWithParts(existing, listOf(part))))

        val newMsg = createTestMessage(id = "m2", sessionId = "s1", created = 2000L)
        reducer.mergeMessages("s1", listOf(MessageWithParts(newMsg, emptyList())))

        val msgs = reducer.messages.value["s1"]
        // Characterization: mergeMessages only keeps incoming messages — existing
        // messages not in the incoming batch are dropped (current behavior, may be a bug).
        assertEquals(1, msgs?.size)
        assertEquals("m2", msgs!![0].id)
    }

    @Test
    fun `mergeMessages skips parts for already-known messages`() = runTest {
        val existing = createTestMessage(id = "m1", sessionId = "s1", created = 1000L)
        reducer.setMessages("s1", listOf(MessageWithParts(existing, emptyList())))

        // Now merge with a part — should NOT overwrite since m1 already known
        val updatedMsg = createTestMessage(id = "m1", sessionId = "s1", created = 1000L)
        val newPart = createTextPart(id = "p-new", messageId = "m1")
        reducer.mergeMessages("s1", listOf(MessageWithParts(updatedMsg, listOf(newPart))))

        // Part should NOT be added because message m1 was already known
        assertTrue(reducer.parts.value["m1"].isNullOrEmpty())
    }

    // ============ Clear Operations ============

    @Test
    fun `clearAll resets all state`() = runTest {
        val session = createTestSession(id = "s1")
        reducer.processEvent(SseEvent.SessionCreated(session), "server-1")
        reducer.updateSessionStatus("s1", SessionStatus.Busy)

        reducer.clearAll()

        assertTrue(reducer.sessions.value.isEmpty())
        assertTrue(reducer.sessionStatuses.value.isEmpty())
        assertTrue(reducer.messages.value.isEmpty())
        assertTrue(reducer.parts.value.isEmpty())
        assertTrue(reducer.serverSessions.value.isEmpty())
        assertTrue(reducer.permissions.value.isEmpty())
        assertTrue(reducer.questions.value.isEmpty())
        assertNull(reducer.vcsBranch.value)
        assertNull(reducer.projectInfo.value)
    }

    @Test
    fun `clearForServer removes only that server's sessions`() = runTest {
        val s1 = createTestSession(id = "s1")
        val s2 = createTestSession(id = "s2")
        reducer.processEvent(SseEvent.SessionCreated(s1), "server-A")
        reducer.processEvent(SseEvent.SessionCreated(s2), "server-B")

        reducer.clearForServer("server-A")

        assertEquals(1, reducer.sessions.value.size)
        assertEquals("s2", reducer.sessions.value[0].id)
        assertFalse(reducer.serverSessions.value.containsKey("server-A"))
        assertTrue(reducer.serverSessions.value.containsKey("server-B"))
    }

    @Test
    fun `clearForServer removes messages and parts for server sessions`() = runTest {
        val session = createTestSession(id = "s1")
        val msg = createTestMessage(id = "m1", sessionId = "s1")
        val part = createTextPart(id = "p1", messageId = "m1")
        reducer.processEvent(SseEvent.SessionCreated(session), "server-1")
        reducer.processEvent(SseEvent.MessageUpdated(msg), "server-1")
        reducer.processEvent(SseEvent.MessagePartUpdated(part), "server-1")

        reducer.clearForServer("server-1")

        assertTrue(reducer.messages.value.isEmpty())
        assertTrue(reducer.parts.value.isEmpty())
    }

    @Test
    fun `clearForServer with no sessions removes server from tracking`() = runTest {
        reducer.clearForServer("nonexistent-server")

        assertFalse(reducer.serverSessions.value.containsKey("nonexistent-server"))
    }

    // ============ Session Diff ============

    @Test
    fun `SessionDiff stores diff for session`() = runTest {
        val diff = FileDiff(file = "main.kt", additions = 5, deletions = 2)

        reducer.processEvent(SseEvent.SessionDiff("s1", listOf(diff)), "server-1")

        val diffs = reducer.sessionDiffs.value["s1"]
        assertEquals(1, diffs?.size)
        assertEquals("main.kt", diffs!![0].file)
    }

    // ============ VCS Branch ============

    @Test
    fun `VcsBranchUpdated stores branch name`() = runTest {
        reducer.processEvent(SseEvent.VcsBranchUpdated("main"), "server-1")

        assertEquals("main", reducer.vcsBranch.value)
    }

    // ============ Project Info ============

    @Test
    fun `ProjectUpdated stores project info`() = runTest {
        val project = Project(id = "proj-1", name = "My Project", worktree = "/home/user/project")

        reducer.processEvent(SseEvent.ProjectUpdated(project), "server-1")

        assertEquals(project, reducer.projectInfo.value)
    }

    // ============ Initial State ============

    @Test
    fun `initial state is empty`() = runTest {
        assertTrue(reducer.sessions.value.isEmpty())
        assertTrue(reducer.sessionStatuses.value.isEmpty())
        assertTrue(reducer.messages.value.isEmpty())
        assertTrue(reducer.parts.value.isEmpty())
        assertTrue(reducer.serverSessions.value.isEmpty())
        assertTrue(reducer.permissions.value.isEmpty())
        assertTrue(reducer.questions.value.isEmpty())
        assertTrue(reducer.todos.value.isEmpty())
        assertNull(reducer.vcsBranch.value)
        assertNull(reducer.projectInfo.value)
    }

    // ============ Server Heartbeat and No-op Events ============

    @Test
    fun `ServerHeartbeat does not change state`() = runTest {
        val session = createTestSession(id = "s1")
        reducer.processEvent(SseEvent.SessionCreated(session), "server-1")

        reducer.processEvent(SseEvent.ServerHeartbeat, "server-1")

        assertEquals(1, reducer.sessions.value.size)
    }

    @Test
    fun `SessionError does not change state`() = runTest {
        reducer.processEvent(SseEvent.SessionError("s1", "something failed"), "server-1")

        assertTrue(reducer.sessionStatuses.value.isEmpty())
    }

    @Test
    fun `LspUpdated does not change state`() = runTest {
        reducer.processEvent(SseEvent.LspUpdated, "server-1")

        assertTrue(reducer.sessions.value.isEmpty())
    }

    // ============ Question Handling ============

    @Test
    fun `setQuestions adds questions for session`() = runTest {
        val q = SseEvent.QuestionAsked(
            id = "q1", sessionId = "s1",
            questions = listOf(
                SseEvent.QuestionAsked.Question(
                    header = "Choose", question = "Which option?", options = listOf(
                        SseEvent.QuestionAsked.Option("A", "Option A"),
                        SseEvent.QuestionAsked.Option("B", "Option B")
                    )
                )
            )
        )
        reducer.setQuestions("s1", listOf(q))

        val questions = reducer.questions.value["s1"]
        assertEquals(1, questions?.size)
        assertEquals(q, questions!![0])
    }

    @Test
    fun `removeQuestion removes from all sessions`() = runTest {
        val q1 = SseEvent.QuestionAsked(
            id = "q1", sessionId = "s1",
            questions = listOf(
                SseEvent.QuestionAsked.Question("H", "Q?", options = emptyList())
            )
        )
        val q2 = SseEvent.QuestionAsked(
            id = "q2", sessionId = "s1",
            questions = listOf(
                SseEvent.QuestionAsked.Question("H2", "Q2?", options = emptyList())
            )
        )
        reducer.setQuestions("s1", listOf(q1, q2))

        reducer.removeQuestion("q1")

        val questions = reducer.questions.value["s1"]
        assertEquals(1, questions?.size)
        assertEquals("q2", questions!![0].id)
    }
}
