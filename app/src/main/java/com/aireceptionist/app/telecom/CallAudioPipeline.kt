package com.aireceptionist.app.telecom

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.aireceptionist.app.ai.agents.AgentManager
import com.aireceptionist.app.ai.voice.TextToSpeechManager
import com.aireceptionist.app.data.models.CallContext
import com.aireceptionist.app.utils.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class AudioPipelineMode { BLUETOOTH_SCO, SIP_VOIP, NONE }
enum class PipelineState     { IDLE, STARTING, LISTENING, PROCESSING, SPEAKING, ERROR, STOPPED }

/**
 * Orchestrates the full real-time AI-receptionist audio loop:
 *   hear (SpeechRecognizer) → think (AgentManager/LLM) → speak (TTS)
 *
 * Mode selection at [start] time:
 *   SIP_VOIP      — if the user has configured a SIP server IP (Option B).
 *                   Audio routes through AIConnectionService (audioModeIsVoip=true).
 *   BLUETOOTH_SCO — otherwise (Option A). Starts SCO mode so the call audio
 *                   is accessible via VOICE_COMMUNICATION AudioRecord/AudioTrack.
 *                   Falls back to direct VOICE_COMMUNICATION after 3 s timeout.
 */
@Singleton
class CallAudioPipeline @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothSCO: BluetoothSCOAudioInterceptor,
    private val sipAccountManager: SIPAccountManager,
    private val agentManager: AgentManager,
    private val ttsManager: TextToSpeechManager
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var speechRecognizer: SpeechRecognizer? = null
    private var currentCallContext: CallContext? = null
    private val conversationHistory = mutableListOf<String>()

    private val _state = MutableStateFlow(PipelineState.IDLE)
    val state: StateFlow<PipelineState> = _state

    private val _transcript = MutableStateFlow<String?>(null)
    val transcript: StateFlow<String?> = _transcript

    private val _aiResponse = MutableStateFlow<String?>(null)
    val aiResponse: StateFlow<String?> = _aiResponse

    private var activeMode = AudioPipelineMode.NONE
    private var isRunning = false

    // ── Public API ──────────────────────────────────────────────────────────

    /** Start the hear→think→speak loop for [callContext]. Auto-selects SCO or SIP. */
    fun start(callContext: CallContext) {
        if (isRunning) return
        currentCallContext = callContext
        conversationHistory.clear()
        isRunning = true
        _state.value = PipelineState.STARTING

        val sipConfig = sipAccountManager.getSIPConfig()
        activeMode = if (sipConfig.enabled && sipConfig.isValid) {
            Logger.i(TAG, "Pipeline mode: SIP/VoIP → ${sipConfig.sipUri}")
            AudioPipelineMode.SIP_VOIP
        } else {
            Logger.i(TAG, "Pipeline mode: Bluetooth SCO")
            AudioPipelineMode.BLUETOOTH_SCO
        }

        when (activeMode) {
            AudioPipelineMode.BLUETOOTH_SCO -> launchSCOMode()
            AudioPipelineMode.SIP_VOIP      -> launchSIPMode(sipConfig)
            AudioPipelineMode.NONE          -> {}
        }
    }

    /** Speak an AI greeting immediately when the call connects. */
    fun deliverGreeting(text: String) {
        conversationHistory.add("AI: $text")
        speakResponse(text)
    }

    fun stop() {
        if (!isRunning) return
        Logger.i(TAG, "Stopping audio pipeline (mode=$activeMode)")
        isRunning = false
        _state.value = PipelineState.STOPPED

        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null

        when (activeMode) {
            AudioPipelineMode.BLUETOOTH_SCO -> bluetoothSCO.stopSCOInterception()
            AudioPipelineMode.SIP_VOIP      -> { /* SIP lifecycle managed by AIConnectionService */ }
            AudioPipelineMode.NONE          -> {}
        }
        activeMode = AudioPipelineMode.NONE
        currentCallContext = null
    }

    fun destroy() {
        stop()
        bluetoothSCO.destroy()
        scope.cancel()
    }

    // ── Private ─────────────────────────────────────────────────────────────

    private fun launchSCOMode() {
        bluetoothSCO.setCallbacks(
            onAudioFrame  = { /* SpeechRecognizer reads SCO channel automatically */ },
            onConnected   = {
                Logger.i(TAG, "SCO connected — starting speech recognition")
                startRecognition()
            },
            onDisconnected = {
                Logger.w(TAG, "SCO disconnected — falling back to direct mode")
                if (isRunning) startRecognition()
            }
        )
        bluetoothSCO.startSCOInterception()

        // If no Bluetooth device connects within 3 s, fall back to direct VOICE_COMMUNICATION
        scope.launch {
            delay(3_000)
            if (isRunning && _state.value == PipelineState.STARTING) {
                Logger.w(TAG, "SCO timeout — starting recognition in direct mode")
                startRecognition()
            }
        }
    }

    private fun launchSIPMode(config: SIPConfig) {
        Logger.i(TAG, "SIP mode: audio routed via AIConnectionService (audioModeIsVoip=true)")
        sipAccountManager.enableSIPRouting(config)
        // audioModeIsVoip=true on AIConnection grants VOICE_COMMUNICATION audio access
        startRecognition()
    }

    private fun startRecognition() {
        scope.launch(Dispatchers.Main) {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Logger.e(TAG, "SpeechRecognizer not available on this device")
                _state.value = PipelineState.ERROR
                return@launch
            }
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(recognitionListener)
            }
            listen()
        }
    }

    private fun listen() {
        if (!isRunning) return
        _state.value = PipelineState.LISTENING
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        speechRecognizer?.startListening(intent)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH        -> "NO_MATCH"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT  -> "TIMEOUT"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "BUSY"
                SpeechRecognizer.ERROR_NETWORK         -> "NETWORK"
                SpeechRecognizer.ERROR_AUDIO           -> "AUDIO"
                else                                   -> "ERR_$error"
            }
            Logger.w(TAG, "Recognition error: $msg")
            if (isRunning) scope.launch { delay(400); listen() }
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.trim()
            if (!text.isNullOrEmpty()) {
                Logger.i(TAG, "Caller said: \"$text\"")
                _transcript.value = text
                processCallerInput(text)
            } else {
                if (isRunning) listen()
            }
        }

        override fun onPartialResults(partial: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun processCallerInput(text: String) {
        _state.value = PipelineState.PROCESSING
        scope.launch {
            try {
                val ctx = currentCallContext ?: return@launch
                conversationHistory.add("Caller: $text")

                val response = agentManager.generateIntelligentResponse(
                    userInput           = text,
                    callContext         = ctx,
                    conversationHistory = conversationHistory.toList()
                )

                val reply = response.content
                conversationHistory.add("AI: $reply")
                _aiResponse.value = reply
                Logger.i(TAG, "AI: \"$reply\"")
                speakResponse(reply)
            } catch (e: Exception) {
                Logger.e(TAG, "Error processing caller input", e)
                _state.value = PipelineState.ERROR
                if (isRunning) listen()
            }
        }
    }

    private fun speakResponse(text: String) {
        _state.value = PipelineState.SPEAKING
        scope.launch {
            ttsManager.speak(text)  // suspends until TTS playback finishes
            if (isRunning) listen() // resume listening for caller's next utterance
        }
    }

    companion object {
        private const val TAG = "CallAudioPipeline"
    }
}
