package dev.leonardo.ocremotev2.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStatusFSMTest {

    private val idle = SessionFSMState.initial()
    private val busyWaiting = idle.copy(core = SessionStatus.Busy, activity = SessionActivity.Waiting)
    private val busyStreaming = busyWaiting.copy(activity = SessionActivity.Streaming)

    @Test
    fun `Idle + ClientSendParts to Busy_Waiting`() {
        val r = SessionStatusFSM.transition(idle, FsmEvent.ClientSendParts)
        assertEquals(SessionStatus.Busy, r.newState.core)
        assertEquals(SessionActivity.Waiting, r.newState.activity)
        assertFalse(r.forceComplete)
    }

    @Test
    fun `Busy_Streaming + TextEnded to Busy_Waiting`() {
        val r = SessionStatusFSM.transition(busyStreaming, FsmEvent.TextEnded)
        assertEquals(SessionActivity.Waiting, r.newState.activity)
        assertEquals(SessionStatus.Busy, r.newState.core)
    }

    @Test
    fun `Busy_Streaming + SseIdle to Idle_null + forceComplete`() {
        val r = SessionStatusFSM.transition(busyStreaming, FsmEvent.SseIdle)
        assertEquals(SessionStatus.Idle, r.newState.core)
        assertNull(r.newState.activity)
        assertTrue(r.forceComplete)
    }

    @Test
    fun `Idle + TextStarted to suspicious, unchanged`() {
        val r = SessionStatusFSM.transition(idle, FsmEvent.TextStarted)
        assertEquals(idle.core, r.newState.core)
        assertTrue(r.isSuspicious)
    }

    @Test
    fun `Busy + RestValidation_Idle to Idle_null + forceComplete`() {
        val r = SessionStatusFSM.transition(busyStreaming, FsmEvent.RestValidation(SessionStatus.Idle))
        assertEquals(SessionStatus.Idle, r.newState.core)
        assertTrue(r.forceComplete)
    }

    @Test
    fun `CompactionStarted saves activity, CompactionEnded restores`() {
        val compacting = SessionStatusFSM.transition(busyStreaming, FsmEvent.CompactionStarted).newState
        assertTrue(compacting.activity is SessionActivity.Compacting)
        assertEquals(SessionActivity.Streaming, (compacting.activity as SessionActivity.Compacting).savedActivity)
        val restored = SessionStatusFSM.transition(compacting, FsmEvent.CompactionEnded).newState
        assertEquals(SessionActivity.Streaming, restored.activity)
    }

    @Test
    fun `invariant - Idle state never holds activity`() {
        val events = listOf(FsmEvent.SseIdle, FsmEvent.ClientAbort, FsmEvent.RestValidation(SessionStatus.Idle))
        for (e in events) {
            val r = SessionStatusFSM.transition(busyStreaming, e)
            assertNull("After $e, activity must be null", r.newState.activity)
            assertEquals(SessionStatus.Idle, r.newState.core)
        }
    }

    @Test
    fun `ClientAbort always forceComplete`() {
        val r = SessionStatusFSM.transition(busyStreaming, FsmEvent.ClientAbort)
        assertTrue(r.forceComplete)
    }
}
