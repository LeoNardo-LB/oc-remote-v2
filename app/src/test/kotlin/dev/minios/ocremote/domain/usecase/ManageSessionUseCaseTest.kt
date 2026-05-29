package dev.minios.ocremote.domain.usecase

import dev.minios.ocremote.data.api.OpenCodeApi
import dev.minios.ocremote.data.api.ServerConnection
import dev.minios.ocremote.domain.model.Session
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManageSessionUseCaseTest {

    private val api: OpenCodeApi = mockk()
    private val useCase = ManageSessionUseCase(api)
    private val conn = ServerConnection.from("http://localhost:8080")

    @Test
    fun `getSession delegates to api`() = runTest {
        val session = Session(id = "s1", title = "Chat", time = Session.Time(created = 1000L, updated = 1000L))
        coEvery { api.getSession(any(), "s1") } returns session

        val result = useCase.getSession(conn, "s1")

        assertEquals(session, result)
    }

    @Test
    fun `listMessages delegates to api`() = runTest {
        coEvery { api.listMessages(any(), "s1", any()) } returns emptyList()

        val result = useCase.listMessages(conn, "s1", limit = 50)

        assertTrue(result.isEmpty())
    }
}
