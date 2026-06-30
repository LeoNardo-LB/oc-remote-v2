package dev.leonardo.ocremotev2.data.repository

import dev.leonardo.ocremotev2.domain.model.*
import dev.leonardo.ocremotev2.domain.model.SseEvent
import dev.leonardo.ocremotev2.domain.repository.SessionRepository
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import javax.inject.Provider

/**
 * Fixture note (deviation from brief's literal `runTest { this -> ... }` form):
 *
 * SessionStateService exposes flows built with `stateIn(appScope, SharingStarted.Eagerly, …)`,
 * which launch coroutines in the injected appScope that never complete on their own. Passing
 * `runTest`'s `this` (a TestScope on the default StandardTestDispatcher) caused two failures:
 *   1. Timing — the Eagerly collector was queued behind the test body, so `statusFlow.value`
 *      stayed `emptyMap()` (AssertionError / NPE).
 *   2. `UncompletedCoroutinesError` at teardown — the 3 Eagerly coroutines outlive the test body.
 *
 * Fix mirrors the project's own `ChatViewModelStreamingTest` (UnconfinedTestDispatcher +
 * advanceUntilIdle): drive appScope with an UnconfinedTestDispatcher for eager propagation, and
 * cancel the scope in @After so teardown sees no uncompleted coroutines. All test cases and
 * assertions are unchanged from the brief.
 */
class SessionStateServiceTest {

    private val testScope = TestScope(UnconfinedTestDispatcher())

    private fun newService() = SessionStateService(
        testScope,
        Provider { mockk<SessionRepository>(relaxed = true) },
    )

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun `ClientSendParts transitions Idle to Busy Waiting in statusFlow`() {
        val service = newService()
        service.onClientSendParts("s1")
        testScope.advanceUntilIdle()
        assertEquals(SessionStatus.Busy, service.statusFlow.value["s1"])
        assertEquals(SessionActivity.Waiting, service.activityFlow.value["s1"])
    }

    @Test
    fun `SseIdle after Busy triggers forceComplete on messageForceCompleter`() {
        val forceCompleter = mockk<MessageForceCompleter>(relaxed = true)
        val service = newService()
        service.messageForceCompleter = forceCompleter
        service.onClientSendParts("s1")
        service.onSseEvent(SseEvent.SessionIdle(sessionId = "s1"), "s1")
        testScope.advanceUntilIdle()
        assertEquals(SessionStatus.Idle, service.statusFlow.value["s1"])
        verify { forceCompleter.markIdle("s1") }
    }

    @Test
    fun `transition recorded in history`() {
        val service = newService()
        service.onClientSendParts("s1")
        testScope.advanceUntilIdle()
        val history = service.historyFlow.value["s1"]
        assertEquals(1, history!!.size)
        assertEquals("Idle", history[0].fromCore)
        assertEquals("Busy", history[0].toCore)
    }

    @Test
    fun `history trims to max 20 entries`() {
        val service = newService()
        repeat(25) { service.onClientSendParts("s1") }  // each is a transition
        testScope.advanceUntilIdle()
        val history = service.historyFlow.value["s1"]!!
        assertTrue("history should be trimmed to <= 20, was ${history.size}", history.size <= 20)
    }

    @Test
    fun `clearSession removes state and history`() {
        val service = newService()
        service.onClientSendParts("s1")
        testScope.advanceUntilIdle()
        service.clearSession("s1")
        testScope.advanceUntilIdle()
        assertNull(service.statusFlow.value["s1"])
        assertNull(service.historyFlow.value["s1"])
    }
}
