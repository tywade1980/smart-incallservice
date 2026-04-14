package com.aireceptionist.app.ai.voice

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.aireceptionist.app.utils.Logger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Wraps Android’s SpeechRecognizer for continuous, hands-free speech recognition
 * during an active AI receptionist call.
 *
 * Bind to this service from CallActivity / AIProcessingService to receive:
 *   • [recognizedText]  – final transcribed utterances
 *   • [partialText]     – partial (streaming) results for live display
 *   • [isListening]     – recogniser state
 *   • [errorCode]       – last SpeechRecognizer error code
 */
@AndroidEntryPoint
class VoiceRecognitionService : Service() {

    // ---- public state ----
    private val _recognizedText = MutableStateFlow<String?>(null)
    val recognizedText: StateFlow<String?> = _recognizedText.asStateFlow()

    private val _partialText = MutableStateFlow<String?>(null)
    val partialText: StateFlow<String?> = _partialText.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _errorCode = MutableStateFlow<Int?>(null)
    val errorCode: StateFlow<Int?> = _errorCode.asStateFlow()

    // ---- internals ----
    private var recognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var shouldRestart = false

    inner class VoiceRecognitionBinder : Binder() {
        fun getService(): VoiceRecognitionService = this@VoiceRecognitionService
    }

    private val binder = VoiceRecognitionBinder()

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        initRecognizer()
        Logger.i(TAG, "VoiceRecognitionService created")
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startListening()
            ACTION_STOP  -> stopListening()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        shouldRestart = false
        recognizer?.destroy()
        recognizer = null
        Logger.i(TAG, "VoiceRecognitionService destroyed")
    }

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Logger.e(TAG, "Speech recognition not available on this device")
            return
        }
        shouldRestart = true
        mainHandler.post { beginRecognition() }
    }

    fun stopListening() {
        shouldRestart = false
        mainHandler.post {
            recognizer?.stopListening()
            _isListening.value = false
        }
    }

    // ---------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------

    private fun initRecognizer() {
        mainHandler.post {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                    setRecognitionListener(listener)
                }
            }
        }
    }

    private fun beginRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start listening", e)
            scheduleRestart(500)
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!shouldRestart) return
        mainHandler.postDelayed({ beginRecognition() }, delayMs)
    }

    // ---------------------------------------------------------------
    // RecognitionListener
    // ---------------------------------------------------------------

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _isListening.value = true
            Logger.d(TAG, "Ready for speech")
        }
        override fun onBeginningOfSpeech() {
            Logger.d(TAG, "Speech started")
        }
        override fun onRmsChanged(rmsdB: Float) { /* volume meter – ignored */ }
        override fun onBufferReceived(buffer: ByteArray?) { /* raw audio – ignored */ }
        override fun onEndOfSpeech() {
            _isListening.value = false
            Logger.d(TAG, "Speech ended")
        }
        override fun onError(error: Int) {
            _isListening.value = false
            _errorCode.value = error
            Logger.w(TAG, "Recognition error $error")
            // Auto-restart on transient / timeout errors
            val delay = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 300L
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1000L
                else -> 500L
            }
            scheduleRestart(delay)
        }
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim()
            if (!text.isNullOrBlank()) {
                Logger.d(TAG, "Recognized: $text")
                _recognizedText.value = text
            }
            _isListening.value = false
            scheduleRestart(200L)   // keep listening continuously
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull()
            if (!partial.isNullOrBlank()) {
                _partialText.value = partial
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) { /* unused */ }
    }

    companion object {
        private const val TAG          = "VoiceRecognitionService"
        const val ACTION_START         = "com.aireceptionist.app.START_VOICE_RECOGNITION"
        const val ACTION_STOP          = "com.aireceptionist.app.STOP_VOICE_RECOGNITION"
    }
}
