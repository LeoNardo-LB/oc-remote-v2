package dev.leonardo.ocremoteplus.data.repository

import dev.leonardo.ocremoteplus.domain.repository.TerminalRepository
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Test

class TerminalRepositoryImplTest {
    private val serverRepo: ServerDataStore = mockk(relaxed = true)

    @Test
    fun `impl creates successfully`() = runTest {
        val repo: TerminalRepository = TerminalRepositoryImpl(serverRepo)
        assertNotNull(repo)
    }
}
