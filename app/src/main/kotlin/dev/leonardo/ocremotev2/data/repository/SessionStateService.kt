package dev.leonardo.ocremotev2.data.repository

import android.util.Log
import dev.leonardo.ocremotev2.BuildConfig
import dev.leonardo.ocremotev2.di.ApplicationScope
import dev.leonardo.ocremotev2.domain.model.*
import dev.leonardo.ocremotev2.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

fun interface DirectoryResolver { fun resolve(sessionId: String): String? }
fun interface IncompleteAssistantChecker { fun hasIncomplete(sessionId: String): Boolean }
fun interface MessageForceCompleter { fun markIdle(sessionId: String) }

private const val TAG = "SessionStateService"
private const val HISTORY_MAX = 20

@Singleton
class SessionStateService @Inject constructor(
    @ApplicationScope private val appScope: CoroutineScope,
    private val sessionRepoProvider: Provider<SessionRepository>,
) {
    // Injected post-construction (breaks circular dep with EventDispatcher)
    var directoryResolver: DirectoryResolver = DirectoryResolver { null }
    var incompleteChecker: IncompleteAssistantChecker = IncompleteAssistantChecker { false }
    var messageForceCompleter: MessageForceCompleter = MessageForceCompleter {}

    @Volatile private var currentServerId: String? = null

    private val _fsmStates = MutableStateFlow<Map<String, SessionFSMState>>(emptyMap())
    private val _histories = MutableStateFlow<Map<String, List<TransitionRecord>>>(emptyMap())

    val statusFlow: StateFlow<Map<String, SessionStatus>> = _fsmStates
        .map { it.mapValues { e -> e.value.core } }
        .stateIn(appScope, SharingStarted.Eagerly, emptyMap())

    val activityFlow: StateFlow<Map<String, SessionActivity?>> = _fsmStates
        .map { it.mapValues { e -> e.value.activity } }
        .stateIn(appScope, SharingStarted.Eagerly, emptyMap())

    val historyFlow: StateFlow<Map<String, List<TransitionRecord>>> = _histories
        .stateIn(appScope, SharingStarted.Eagerly, emptyMap())

    fun setServerId(serverId: String) { currentServerId = serverId }

    // ============ Event entry points ============
    fun onClientSendParts(sessionId: String) = applyTransition(sessionId, FsmEvent.ClientSendParts)
    fun onClientAbort(sessionId: String) = applyTransition(sessionId, FsmEvent.ClientAbort)
    fun onRestValidation(sessionId: String, status: SessionStatus) =
        applyTransition(sessionId, FsmEvent.RestValidation(status))

    fun onSseEvent(event: SseEvent, sessionId: String) {
        val fsmEvent = mapSseEventToFsm(event) ?: return
        applyTransition(sessionId, fsmEvent)
    }

    private fun mapSseEventToFsm(event: SseEvent): FsmEvent? = when (event) {
        is SseEvent.SessionStatus -> FsmEvent.SseStatus(event.status)
        is SseEvent.SessionIdle -> FsmEvent.SseIdle
        is SseEvent.SessionError -> FsmEvent.SseError(event.error)
        is SseEvent.SessionNext -> mapSessionNextEvent(event.event)
        else -> null
    }

    private fun mapSessionNextEvent(event: SessionNextEvent): FsmEvent? = when (event) {
        is SessionNextEvent.StepStarted -> FsmEvent.StepStarted
        is SessionNextEvent.TextStarted -> FsmEvent.TextStarted
        is SessionNextEvent.TextDelta -> FsmEvent.TextDelta(event.delta)
        is SessionNextEvent.TextEnded -> FsmEvent.TextEnded
        is SessionNextEvent.ToolInputStarted -> FsmEvent.ToolInputStarted(event.tool, event.callId)
        // SessionNextEvent.StepEnded has no finish field — pass null. FSM treats non-"tool-calls"
        // finish as "keep current Activity, wait for Core Idle".
        is SessionNextEvent.StepEnded -> FsmEvent.StepEnded(finish = null)
        is SessionNextEvent.CompactionStarted -> FsmEvent.CompactionStarted
        is SessionNextEvent.CompactionEnded -> FsmEvent.CompactionEnded
        else -> null
    }

    // ============ Core pipeline ============
    fun applyTransition(sessionId: String, event: FsmEvent) {
        val current = _fsmStates.value[sessionId] ?: SessionFSMState.initial()
        val result = SessionStatusFSM.transition(current, event)
        _fsmStates.update { it + (sessionId to result.newState) }
        recordHistory(sessionId, current, result, event)
        if (BuildConfig.DEBUG) logTransition(sessionId, current, result, event)
        // Side effects
        if (result.forceComplete) messageForceCompleter.markIdle(sessionId)
        if (result.isSuspicious) triggerRestValidation(sessionId)
    }

    private fun recordHistory(sessionId: String, from: SessionFSMState, result: SessionStatusFSM.TransitionResult, event: FsmEvent) {
        val record = TransitionRecord(
            sessionId = sessionId,
            timestamp = System.currentTimeMillis(),
            event = event::class.simpleName ?: "Unknown",
            fromCore = from.core::class.simpleName ?: "?",
            toCore = result.newState.core::class.simpleName ?: "?",
            fromActivity = from.activity?.let { it::class.simpleName },
            toActivity = result.newState.activity?.let { it::class.simpleName },
            isSuspicious = result.isSuspicious,
            reason = null,
        )
        _histories.update { h ->
            val list = (h[sessionId] ?: emptyList()) + record
            val trimmed = if (list.size > HISTORY_MAX) list.takeLast(HISTORY_MAX) else list
            h + (sessionId to trimmed)
        }
    }

    private fun logTransition(sessionId: String, from: SessionFSMState, result: SessionStatusFSM.TransitionResult, event: FsmEvent) {
        val actFrom = from.activity?.let { "/${it::class.simpleName}" } ?: ""
        val actTo = result.newState.activity?.let { "/${it::class.simpleName}" } ?: ""
        val flags = buildString {
            if (result.isSuspicious) append(" [SUSPICIOUS]")
            if (result.forceComplete) append(" [force-complete]")
        }
        Log.d(TAG, "[$sessionId] ${from.core::class.simpleName}$actFrom --${event::class.simpleName}--> ${result.newState.core::class.simpleName}$actTo$flags")
    }

    // ============ Lifecycle ============
    fun clearSession(sessionId: String) {
        _fsmStates.update { it - sessionId }
        _histories.update { it - sessionId }
    }

    fun clearForServer(sessionIds: Set<String>) {
        _fsmStates.update { it - sessionIds }
        _histories.update { it - sessionIds }
    }

    fun clearAll() {
        _fsmStates.value = emptyMap()
        _histories.value = emptyMap()
    }

    // Placeholder — implemented in Task 4 (staleness guard)
    internal fun triggerRestValidation(sessionId: String) { /* Task 4 */ }
}
