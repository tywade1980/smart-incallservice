package com.aireceptionist.app.ui.call

import android.app.Application
import androidx.annotation.ColorRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aireceptionist.app.ai.agents.AgentManager
import com.aireceptionist.app.ai.voice.VoiceRecognitionService
import com.aireceptionist.app.data.dao.CallerHistoryDao
import com.aireceptionist.app.data.models.CallContext
import com.aireceptionist.app.service.AIProcessingService
import com.aireceptionist.app.utils.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * ViewModel for [CallActivity].
 *
 * Orchestrates:
 *   • Caller information lookup (CallerHistoryDao)
 *   • Live transcript via VoiceRecognitionService
 *   • AI response via AIProcessingService → AgentManager
 *   • Call duration timer
 *   • Mute / hold state
 */
@HiltViewModel
class CallViewModel @Inject constructor(
    application: Application,
    private val agentManager: AgentManager,
    private val callerHistoryDao: CallerHistoryDao
) : AndroidViewModel(application) {

    // ---- caller info ----
    private val _callerName  = MutableStateFlow("Connecting…")
    val callerName: StateFlow<String> = _callerName.asStateFlow()

    private val _callerPhone = MutableStateFlow("")
    val callerPhone: StateFlow<String> = _callerPhone.asStateFlow()

    private val _callCount   = MutableStateFlow(0)
    val callCount: StateFlow<Int> = _callCount.asStateFlow()

    // ---- AI state ----
    private val _aiStatus      = MutableStateFlow(AIStatus.IDLE)
    val aiStatus: StateFlow<AIStatus> = _aiStatus.asStateFlow()

    private val _liveTranscript = MutableStateFlow("Listening…")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _aiResponse    = MutableStateFlow<String?>(null)
    val aiResponse: StateFlow<String?> = _aiResponse.asStateFlow()

    private val _emotionLabel  = MutableStateFlow("")
    val emotionLabel: StateFlow<String> = _emotionLabel.asStateFlow()

    // ---- call controls ----
    private val _isMuted   = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isOnHold  = MutableStateFlow(false)
    val isOnHold: StateFlow<Boolean> = _isOnHold.asStateFlow()

    private val _callDuration = MutableStateFlow("00:00")
    val callDuration: StateFlow<String> = _callDuration.asStateFlow()

    // ---- internals ----
    private var callStartTime = 0L
    private var currentCallId = ""
    private var durationJob: Job? = null

    private var voiceService: VoiceRecognitionService? = null
    private var processingService: AIProcessingService? = null
    private var voiceCollectJob: Job? = null
    private var processingCollectJob: Job? = null

    // ---------------------------------------------------------------
    // Initialisation
    // ---------------------------------------------------------------

    fun initCall(callId: String, phoneNumber: String, isIncoming: Boolean) {
        currentCallId = callId
        callStartTime = System.currentTimeMillis()
        _callerPhone.value = phoneNumber

        startDurationTimer()
        loadCallerHistory(phoneNumber)

        // Set greeting state while we wait for services to bind
        viewModelScope.launch {
            _aiStatus.value = AIStatus.GREETING
            val ctx = makeCallContext(callId, phoneNumber, "RINGING")
            try {
                val resp = agentManager.generateIntelligentResponse(
                    userInput           = "",
                    callContext         = ctx,
                    conversationHistory = emptyList()
                )
                _aiResponse.value  = resp.content
                _liveTranscript.value = "AI: ${resp.content}"
            } catch (e: Exception) {
                Logger.e(TAG, "Greeting generation failed", e)
                _aiResponse.value = "Thank you for calling. How can I help you today?"
            }
            delay(300)
            _aiStatus.value = AIStatus.LISTENING
        }
    }

    // ---------------------------------------------------------------
    // Service attachment (called from Activity after bind)
    // ---------------------------------------------------------------

    fun attachVoiceService(service: VoiceRecognitionService?) {
        voiceService = service
        voiceCollectJob?.cancel()
        service ?: return

        val ctx = makeCallContext(currentCallId, _callerPhone.value, "active")
        processingService?.setCallContext(ctx)
        service.startListening()

        voiceCollectJob = viewModelScope.launch {
            launch {
                service.recognizedText.collect { text ->
                    if (!text.isNullOrBlank()) {
                        _liveTranscript.value = "Caller: $text"
                        processInput(text)
                    }
                }
            }
            launch {
                service.partialText.collect { partial ->
                    if (!partial.isNullOrBlank()) {
                        _liveTranscript.value = "Caller: $partial…"
                    }
                }
            }
        }
    }

    fun attachProcessingService(service: AIProcessingService?) {
        processingService = service
        processingCollectJob?.cancel()
        service ?: return

        processingCollectJob = viewModelScope.launch {
            launch {
                service.statusFlow.collect { status ->
                    _aiStatus.value = when (status) {
                        AIProcessingService.ProcessingStatus.LISTENING   -> AIStatus.LISTENING
                        AIProcessingService.ProcessingStatus.PROCESSING  -> AIStatus.PROCESSING
                        AIProcessingService.ProcessingStatus.RESPONDING  -> AIStatus.RESPONDING
                        AIProcessingService.ProcessingStatus.ERROR       -> AIStatus.ERROR
                        else                                             -> AIStatus.IDLE
                    }
                }
            }
            launch {
                service.responseFlow.collect { response ->
                    if (!response.isNullOrBlank()) {
                        _aiResponse.value     = response
                        _liveTranscript.value = "AI: $response"
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Input processing
    // ---------------------------------------------------------------

    private fun processInput(input: String) {
        if (_isOnHold.value) return          // don’t respond while on hold
        _aiStatus.value = AIStatus.PROCESSING

        // Delegate to the bound service if available; otherwise process inline
        val svc = processingService
        if (svc != null) {
            svc.processUserInput(input, currentCallId)
        } else {
            viewModelScope.launch {
                try {
                    val ctx  = makeCallContext(currentCallId, _callerPhone.value, "active")
                    val resp = agentManager.generateIntelligentResponse(
                        userInput           = input,
                        callContext         = ctx,
                        conversationHistory = emptyList()
                    )
                    _aiResponse.value     = resp.content
                    _liveTranscript.value = "AI: ${resp.content}"
                    _aiStatus.value       = AIStatus.RESPONDING
                    delay(1500)
                    _aiStatus.value       = AIStatus.LISTENING
                } catch (e: Exception) {
                    Logger.e(TAG, "Inline processing failed", e)
                    _aiStatus.value = AIStatus.ERROR
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Call controls
    // ---------------------------------------------------------------

    fun toggleMute()    { _isMuted.value  = !_isMuted.value }
    fun toggleSpeaker() { /* hook into AudioManager in production */ }
    fun toggleHold()    {
        _isOnHold.value = !_isOnHold.value
        if (_isOnHold.value) {
            processingService?.pauseListening()
            _aiStatus.value = AIStatus.IDLE
        } else {
            processingService?.resumeListening()
            _aiStatus.value = AIStatus.LISTENING
        }
    }
    fun transferCall()  { /* dial escalation number via TelecomManager */ }

    fun endCall() {
        durationJob?.cancel()
        voiceCollectJob?.cancel()
        processingCollectJob?.cancel()
        voiceService?.stopListening()
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private fun makeCallContext(callId: String, phoneNumber: String, state: String) =
        CallContext(
            callId        = callId,
            callerNumber  = phoneNumber,
            callerName    = _callerName.value.takeIf { it != "Connecting…" },
            callStartTime = callStartTime,
            isIncoming    = true,
            callState     = state
        )

    private fun loadCallerHistory(phoneNumber: String) {
        viewModelScope.launch {
            try {
                val history = callerHistoryDao.getCallerHistory(phoneNumber)
                if (history != null) {
                    _callerName.value = if (history.notes?.isNotBlank() == true)
                        history.notes!! else phoneNumber
                    _callCount.value  = history.totalCalls
                } else {
                    _callerName.value = phoneNumber
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to load caller history", e)
                _callerName.value = phoneNumber
            }
        }
    }

    private fun startDurationTimer() {
        durationJob = viewModelScope.launch {
            while (true) {
                val elapsed = System.currentTimeMillis() - callStartTime
                val mins    = TimeUnit.MILLISECONDS.toMinutes(elapsed)
                val secs    = TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60
                _callDuration.value = String.format("%02d:%02d", mins, secs)
                delay(1000)
            }
        }
    }

    // ---------------------------------------------------------------
    // Status enum
    // ---------------------------------------------------------------

    enum class AIStatus(val label: String, @ColorRes val colorRes: Int) {
        IDLE(     "Standby",    android.R.color.darker_gray),
        GREETING( "Greeting…",  android.R.color.holo_blue_light),
        LISTENING("Listening…", android.R.color.holo_green_light),
        PROCESSING("Thinking…", android.R.color.holo_orange_light),
        RESPONDING("Speaking…", android.R.color.holo_blue_dark),
        ERROR(    "Error",       android.R.color.holo_red_light)
    }

    companion object {
        private const val TAG = "CallViewModel"
    }
}
