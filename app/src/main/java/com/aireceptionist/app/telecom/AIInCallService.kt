package com.aireceptionist.app.telecom

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.content.Intent
import android.os.IBinder
import com.aireceptionist.app.ai.agents.AgentManager
import com.aireceptionist.app.ai.agents.AgentInput
import com.aireceptionist.app.ai.agents.InputType
import com.aireceptionist.app.data.models.CallContext
import com.aireceptionist.app.service.CallHandlingService
import com.aireceptionist.app.ui.call.CallActivity
import com.aireceptionist.app.utils.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

/**
 * Android InCallService implementation for handling incoming and outgoing calls.
 * Integrates with the AI agent system and [CallAudioPipeline] for real-time
 * hear→think→speak audio interception on active calls.
 */
@AndroidEntryPoint
class AIInCallService : InCallService() {

    @Inject lateinit var agentManager: AgentManager
    @Inject lateinit var callAudioPipeline: CallAudioPipeline

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentCall: Call? = null
    private val callCallbacks = mutableMapOf<Call, CallCallback>()
    private var pipelineStarted = false

    override fun onBind(intent: Intent?): IBinder? {
        Logger.d(TAG, "AIInCallService bound")
        return super.onBind(intent)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Logger.i(TAG, "Call added: ${call.details.handle}")
        currentCall = call
        val callback = CallCallback(call)
        call.registerCallback(callback)
        callCallbacks[call] = callback
        startAICallHandling(call)
        if (call.state == Call.STATE_RINGING) showCallActivity(call)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Logger.i(TAG, "Call removed")
        callCallbacks[call]?.let { call.unregisterCallback(it); callCallbacks.remove(call) }
        if (currentCall == call) currentCall = null
        stopAICallHandling()
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        Logger.d(TAG, "Audio route: ${audioState.route}")
    }

    // ── Call handling ─────────────────────────────────────────────

    private fun startAICallHandling(call: Call) {
        scope.launch {
            try {
                val callContext = createCallContext(call)
                val serviceIntent = Intent(this@AIInCallService, CallHandlingService::class.java).apply {
                    putExtra("call_id",       callContext.callId)
                    putExtra("caller_number", callContext.callerNumber)
                    putExtra("is_incoming",   callContext.isIncoming)
                }
                startService(serviceIntent)

                val input = AgentInput(
                    type     = InputType.CALL_EVENT,
                    content  = "call_started",
                    context  = callContext,
                    metadata = mapOf(
                        "call_state"     to call.state,
                        "call_direction" to if (call.state == Call.STATE_RINGING) "incoming" else "outgoing"
                    )
                )
                agentManager.processInput(input, callContext).collect { response ->
                    handleAgentResponse(call, response)
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Error starting AI call handling", e)
            }
        }
    }

    private fun stopAICallHandling() {
        if (pipelineStarted) {
            callAudioPipeline.stop()
            pipelineStarted = false
        }
        scope.launch {
            try {
                stopService(Intent(this@AIInCallService, CallHandlingService::class.java))
            } catch (e: Exception) {
                Logger.e(TAG, "Error stopping AI call handling", e)
            }
        }
    }

    /**
     * Called once the call becomes active (STATE_ACTIVE).
     * Requests Bluetooth audio route for SCO intercept, starts the pipeline,
     * then delivers a generated greeting to the caller.
     */
    private fun activateAudioPipeline(call: Call) {
        if (pipelineStarted) return
        pipelineStarted = true

        // Request Bluetooth route — enables SCO mode on supported devices
        try {
            setAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
        } catch (e: Exception) {
            Logger.w(TAG, "Bluetooth route unavailable, using default: ${e.message}")
        }

        val callContext = createCallContext(call)
        callAudioPipeline.start(callContext)

        // Generate and deliver an opening greeting
        scope.launch {
            val greeting = try {
                agentManager.generateIntelligentResponse(
                    userInput           = "",
                    callContext         = callContext.copy(callState = "RINGING"),
                    conversationHistory = emptyList()
                ).content
            } catch (e: Exception) {
                Logger.w(TAG, "Greeting generation failed, using default", e)
                "Thank you for calling! How can I help you today?"
            }
            callAudioPipeline.deliverGreeting(greeting)
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun createCallContext(call: Call): CallContext = CallContext(
        callId        = call.hashCode().toString(),
        callerNumber  = call.details.handle?.schemeSpecificPart,
        callerName    = call.details.callerDisplayName,
        callStartTime = System.currentTimeMillis(),
        isIncoming    = call.state == Call.STATE_RINGING,
        callState     = mapCallState(call.state)
    )

    private fun mapCallState(state: Int) = when (state) {
        Call.STATE_NEW          -> "new"
        Call.STATE_RINGING      -> "ringing"
        Call.STATE_DIALING      -> "dialing"
        Call.STATE_ACTIVE       -> "active"
        Call.STATE_HOLDING      -> "holding"
        Call.STATE_DISCONNECTED -> "disconnected"
        else                    -> "unknown"
    }

    private suspend fun handleAgentResponse(
        call: Call,
        response: com.aireceptionist.app.ai.agents.AgentResponse
    ) {
        response.actions.forEach { action ->
            when (action.actionType) {
                com.aireceptionist.app.ai.agents.ActionType.ANSWER_CALL -> {
                    if (call.state == Call.STATE_RINGING) {
                        call.answer(0)
                        Logger.i(TAG, "Call answered by AI")
                    }
                }
                com.aireceptionist.app.ai.agents.ActionType.END_CALL  -> call.disconnect()
                com.aireceptionist.app.ai.agents.ActionType.HOLD_CALL -> call.hold()
                com.aireceptionist.app.ai.agents.ActionType.TRANSFER_CALL -> {
                    (action.parameters["destination"] as? String)?.let {
                        Logger.i(TAG, "Transfer requested to $it")
                    }
                }
                else -> Logger.d(TAG, "Unhandled action: ${action.actionType}")
            }
        }
    }

    private fun showCallActivity(call: Call) {
        startActivity(Intent(this, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_id",       call.hashCode().toString())
            putExtra("caller_number", call.details.handle?.schemeSpecificPart)
            putExtra("caller_name",   call.details.callerDisplayName)
            putExtra("is_incoming",   call.state == Call.STATE_RINGING)
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        if (pipelineStarted) callAudioPipeline.stop()
        scope.cancel()
        Logger.d(TAG, "AIInCallService destroyed")
    }

    // ── CallCallback ───────────────────────────────────────────────

    inner class CallCallback(private val call: Call) : Call.Callback() {

        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            Logger.d(TAG, "Call state → $state")

            when (state) {
                Call.STATE_ACTIVE -> {
                    // Call just connected — start the AI hear→think→speak loop
                    activateAudioPipeline(call)
                }
                Call.STATE_DISCONNECTED,
                Call.STATE_DISCONNECTING -> {
                    if (pipelineStarted) {
                        callAudioPipeline.stop()
                        pipelineStarted = false
                    }
                }
            }

            scope.launch {
                try {
                    val callContext = createCallContext(call)
                    val input = AgentInput(
                        type     = InputType.CALL_EVENT,
                        content  = "state_changed",
                        context  = callContext,
                        metadata = mapOf("new_state" to state)
                    )
                    agentManager.processInput(input, callContext).collect { response ->
                        handleAgentResponse(call, response)
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "Error in state change handler", e)
                }
            }
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            Logger.d(TAG, "Details changed: ${details.handle}")
        }

        override fun onCallDestroyed(call: Call) {
            Logger.d(TAG, "Call destroyed")
        }

        override fun onCannedTextResponsesLoaded(call: Call, responses: List<String>) {}
        override fun onPostDialWait(call: Call, remaining: String) {}
        override fun onVideoCallChanged(call: Call, videoCall: Call.VideoCall) {}
        override fun onConferenceableCallsChanged(call: Call, calls: List<Call>) {}
    }

    companion object {
        private const val TAG = "AIInCallService"
    }
}
