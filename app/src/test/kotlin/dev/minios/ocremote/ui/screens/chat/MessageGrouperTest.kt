package dev.minios.ocremote.ui.screens.chat

import dev.minios.ocremote.domain.model.Message
import dev.minios.ocremote.domain.model.TimeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for message grouping logic.
 * Verifies that consecutive assistant messages are merged into a single
 * AssistantTurn, and user messages remain as individual UserMessage items.
 */
class MessageGrouperTest {

    @Test
    fun `groupMessages returns empty list for empty input`() {
        val result = groupMessages(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `groupMessages groups single user message`() {
        val messages = listOf(
            createChatMessage("u1", isUser = true)
        )

        val result = groupMessages(messages)

        assertEquals(1, result.size)
        assertTrue(result[0] is ChatItem.UserMessage)
        assertEquals("u1", result[0].key)
    }

    @Test
    fun `groupMessages groups single assistant message into AssistantTurn`() {
        val messages = listOf(
            createChatMessage("a1", isUser = false)
        )

        val result = groupMessages(messages)

        assertEquals(1, result.size)
        val turn = result[0] as ChatItem.AssistantTurn
        assertEquals("turn_a1", turn.key)
        assertEquals(1, turn.messages.size)
        assertEquals("a1", turn.messages[0].message.id)
    }

    @Test
    fun `groupMessages separates user and assistant messages`() {
        val messages = listOf(
            createChatMessage("u1", isUser = true),
            createChatMessage("a1", isUser = false),
        )

        val result = groupMessages(messages)

        assertEquals(2, result.size)
        assertTrue(result[0] is ChatItem.UserMessage)
        assertTrue(result[1] is ChatItem.AssistantTurn)
    }

    @Test
    fun `groupMessages merges consecutive assistant messages into single turn`() {
        val messages = listOf(
            createChatMessage("u1", isUser = true),
            createChatMessage("a1", isUser = false),
            createChatMessage("a2", isUser = false),
            createChatMessage("a3", isUser = false),
        )

        val result = groupMessages(messages)

        assertEquals(2, result.size)
        assertTrue(result[0] is ChatItem.UserMessage)

        val turn = result[1] as ChatItem.AssistantTurn
        assertEquals("turn_a1", turn.key)
        assertEquals(3, turn.messages.size)
        assertEquals(listOf("a1", "a2", "a3"), turn.messages.map { it.message.id })
    }

    @Test
    fun `groupMessages handles user-assistant-user pattern`() {
        val messages = listOf(
            createChatMessage("u1", isUser = true),
            createChatMessage("a1", isUser = false),
            createChatMessage("u2", isUser = true),
        )

        val result = groupMessages(messages)

        assertEquals(3, result.size)
        assertTrue(result[0] is ChatItem.UserMessage)
        assertEquals("u1", result[0].key)
        assertTrue(result[1] is ChatItem.AssistantTurn)
        assertTrue(result[2] is ChatItem.UserMessage)
        assertEquals("u2", result[2].key)
    }

    @Test
    fun `groupMessages handles complex interleaved pattern`() {
        // u1 → a1, a2 → u2 → a3 → u3, u4 → a4, a5, a6
        val messages = listOf(
            createChatMessage("u1", isUser = true),
            createChatMessage("a1", isUser = false),
            createChatMessage("a2", isUser = false),
            createChatMessage("u2", isUser = true),
            createChatMessage("a3", isUser = false),
            createChatMessage("u3", isUser = true),
            createChatMessage("u4", isUser = true),
            createChatMessage("a4", isUser = false),
            createChatMessage("a5", isUser = false),
            createChatMessage("a6", isUser = false),
        )

        val result = groupMessages(messages)

        assertEquals(7, result.size)

        // u1
        assertTrue(result[0] is ChatItem.UserMessage)
        assertEquals("u1", result[0].key)

        // a1, a2 merged
        val turn1 = result[1] as ChatItem.AssistantTurn
        assertEquals("turn_a1", turn1.key)
        assertEquals(listOf("a1", "a2"), turn1.messages.map { it.message.id })

        // u2
        assertTrue(result[2] is ChatItem.UserMessage)
        assertEquals("u2", result[2].key)

        // a3
        val turn2 = result[3] as ChatItem.AssistantTurn
        assertEquals("turn_a3", turn2.key)
        assertEquals(1, turn2.messages.size)

        // u3
        assertTrue(result[4] is ChatItem.UserMessage)
        assertEquals("u3", result[4].key)

        // u4
        assertTrue(result[5] is ChatItem.UserMessage)
        assertEquals("u4", result[5].key)

        // a4, a5, a6 merged
        val turn3 = result[6] as ChatItem.AssistantTurn
        assertEquals("turn_a4", turn3.key)
        assertEquals(listOf("a4", "a5", "a6"), turn3.messages.map { it.message.id })
    }

    @Test
    fun `groupMessages uses first assistant message id as turn key`() {
        val messages = listOf(
            createChatMessage("a1", isUser = false),
            createChatMessage("a2", isUser = false),
            createChatMessage("a3", isUser = false),
        )

        val result = groupMessages(messages)

        val turn = result[0] as ChatItem.AssistantTurn
        assertEquals("turn_a1", turn.key)
    }

    @Test
    fun `groupMessages handles starting with assistant messages`() {
        val messages = listOf(
            createChatMessage("a1", isUser = false),
            createChatMessage("u1", isUser = true),
        )

        val result = groupMessages(messages)

        assertEquals(2, result.size)
        assertTrue(result[0] is ChatItem.AssistantTurn)
        assertTrue(result[1] is ChatItem.UserMessage)
    }

    @Test
    fun `groupMessages preserves ChatMessage data in UserMessage`() {
        val msg = createChatMessage("u1", isUser = true)
        val messages = listOf(msg)

        val result = groupMessages(messages)

        val userMsg = result[0] as ChatItem.UserMessage
        assertEquals(msg, userMsg.chatMessage)
    }

    // === Helpers ===

    private fun createChatMessage(id: String, isUser: Boolean): ChatMessage {
        val message = if (isUser) {
            Message.User(
                id = id,
                sessionId = "session-1",
                time = TimeInfo(created = System.currentTimeMillis())
            )
        } else {
            Message.Assistant(
                id = id,
                sessionId = "session-1",
                time = TimeInfo(created = System.currentTimeMillis()),
                parentId = ""
            )
        }
        return ChatMessage(message = message, parts = emptyList())
    }
}
