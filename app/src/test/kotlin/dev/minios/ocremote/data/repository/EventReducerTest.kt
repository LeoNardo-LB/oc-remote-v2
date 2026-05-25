package dev.minios.ocremote.data.repository

import app.cash.turbine.test
import dev.minios.ocremote.domain.model.SseEvent
import dev.minios.ocremote.domain.model.ToolRef
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EventReducerTest {

    private lateinit var reducer: EventReducer

    @Before
    fun setup() {
        reducer = EventReducer()
    }

    // ============ setPermissions ============

    @Test
    fun `setPermissions adds permissions for session`() = runTest {
        val permission = createTestPermission(id = "p1", sessionId = "s1")

        reducer.setPermissions("s1", listOf(permission))

        val result = reducer.permissions.value
        assertEquals(1, result.size)
        assertEquals(listOf(permission), result["s1"])
    }

    @Test
    fun `setPermissions with empty list removes session entry`() = runTest {
        val permission = createTestPermission(id = "p1", sessionId = "s1")
        reducer.setPermissions("s1", listOf(permission))

        reducer.setPermissions("s1", emptyList())

        val result = reducer.permissions.value
        assertTrue(result.isEmpty())
        assertTrue(result.containsKey("s1").not())
    }

    @Test
    fun `setPermissions replaces existing permissions`() = runTest {
        val oldPermission = createTestPermission(id = "p1", sessionId = "s1", permission = "bash")
        val newPermission = createTestPermission(id = "p2", sessionId = "s1", permission = "write")
        reducer.setPermissions("s1", listOf(oldPermission))

        reducer.setPermissions("s1", listOf(newPermission))

        val result = reducer.permissions.value
        assertEquals(1, result.size)
        assertEquals(listOf(newPermission), result["s1"])
    }

    @Test
    fun `setPermissions does not affect other sessions`() = runTest {
        val permissionA = createTestPermission(id = "p1", sessionId = "sA")
        val permissionB = createTestPermission(id = "p2", sessionId = "sB")
        reducer.setPermissions("sA", listOf(permissionA))

        reducer.setPermissions("sB", listOf(permissionB))

        val result = reducer.permissions.value
        assertEquals(2, result.size)
        assertEquals(listOf(permissionA), result["sA"])
        assertEquals(listOf(permissionB), result["sB"])
    }

    // ============ removePermission ============

    @Test
    fun `removePermission removes specific permission from all sessions`() = runTest {
        val permission1 = createTestPermission(id = "target-id", sessionId = "s1")
        val permission2 = createTestPermission(id = "other-id", sessionId = "s1")
        val permission3 = createTestPermission(id = "target-id", sessionId = "s2")
        reducer.setPermissions("s1", listOf(permission1, permission2))
        reducer.setPermissions("s2", listOf(permission3))

        reducer.removePermission("target-id")

        val result = reducer.permissions.value
        assertEquals(listOf(permission2), result["s1"])
        assertTrue(result["s2"]!!.isEmpty())
    }

    @Test
    fun `removePermission with non-existent id does nothing`() = runTest {
        val permission = createTestPermission(id = "p1", sessionId = "s1")
        reducer.setPermissions("s1", listOf(permission))

        reducer.removePermission("non-existent")

        val result = reducer.permissions.value
        assertEquals(listOf(permission), result["s1"])
    }

    @Test
    fun `removePermission only affects target id across multiple sessions`() = runTest {
        val p1 = createTestPermission(id = "id-1", sessionId = "s1")
        val p2 = createTestPermission(id = "id-2", sessionId = "s1")
        val p3 = createTestPermission(id = "id-3", sessionId = "s2")
        val p4 = createTestPermission(id = "id-4", sessionId = "s2")
        reducer.setPermissions("s1", listOf(p1, p2))
        reducer.setPermissions("s2", listOf(p3, p4))

        reducer.removePermission("id-2")

        val result = reducer.permissions.value
        assertEquals(listOf(p1), result["s1"])
        assertEquals(listOf(p3, p4), result["s2"])
    }

    // ============ Flow emission via Turbine ============

    @Test
    fun `permissions flow emits updated value after setPermissions`() = runTest {
        val permission = createTestPermission(id = "p1", sessionId = "s1")

        reducer.permissions.test {
            // Initial value
            assertEquals(emptyMap<String, List<SseEvent.PermissionAsked>>(), awaitItem())

            reducer.setPermissions("s1", listOf(permission))

            val emitted = awaitItem()
            assertEquals(1, emitted.size)
            assertEquals(listOf(permission), emitted["s1"])

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `permissions flow emits updated value after removePermission`() = runTest {
        val p1 = createTestPermission(id = "p1", sessionId = "s1")
        val p2 = createTestPermission(id = "p2", sessionId = "s1")
        reducer.setPermissions("s1", listOf(p1, p2))

        reducer.permissions.test {
            // Skip initial emission
            skipItems(1)

            reducer.removePermission("p1")

            val emitted = awaitItem()
            assertEquals(listOf(p2), emitted["s1"])

            cancelAndConsumeRemainingEvents()
        }
    }

    // ============ clearAll ============

    @Test
    fun `clearAll resets permissions to empty`() = runTest {
        val permission = createTestPermission(id = "p1", sessionId = "s1")
        reducer.setPermissions("s1", listOf(permission))

        reducer.clearAll()

        assertTrue(reducer.permissions.value.isEmpty())
    }

    // ============ Helper ============

    private fun createTestPermission(
        id: String,
        sessionId: String,
        permission: String = "bash",
        patterns: List<String> = listOf("/home/user/project"),
        metadata: Map<String, String>? = null,
        always: Boolean = false,
        tool: ToolRef? = null
    ): SseEvent.PermissionAsked {
        return SseEvent.PermissionAsked(
            id = id,
            sessionId = sessionId,
            permission = permission,
            patterns = patterns,
            metadata = metadata,
            always = always,
            tool = tool
        )
    }
}
