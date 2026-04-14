package com.aireceptionist.app.service

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.aireceptionist.app.ai.agents.AgentManager
import com.aireceptionist.app.ai.agents.AgentInput
import com.aireceptionist.app.ai.agents.InputType
import com.aireceptionist.app.ai.voice.TextToSpeechManager
import com.aireceptionist.app.data.models.CallContext
import com.aireceptionist.app.ui.call.CallActivity
import com.aireceptionist.app.utils.Logger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/**
 * Foreground service that drives the AI conversation loop during an active call.
 *
 * Flow:
 *   SpeechRecognitionService → [recognized text] → AIProcessingService
 *       → AgentManager.generateIntelligentResponse()
 *       → TextToSpeechManager.speak()
 *       → notify bound client (CallActivity) via [responseFlow]
 */
@AndroidEntryPoint
class AIProcessingService : LifecycleService() {

    @Inject lateinit var agentManager: AgentManager
    @Inject lateinit var ttsManager: TextToSpeechManager

    // ---- public state flows (observed by CallActivity via binding) ----
    private val _responseFlow = MutableStateFlow<String?>(null)
    val responseFlow: StateFlow<String?> = _responseFlow.asStateFlow()

    private val _statusFlow = MutableStateFlow(ProcessingStatus.IDLE)
    val statusFlow: StateFlow<ProcessingStatus> = _statusFlow.asStateFlow()

    // ---- internals ----
    private var currentCallContext: CallContext? = null
    private var activeJob: Job? = null

    inner class AIProcessingBinder : Binder() {
        fun getService(): AIProcessingService = this@AIProcessingService
    }

    private val binder = AIProcessingBinder()

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Logger.i(TAG, "AIProcessingService created")
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification("AI Receptionist ready"))
                _statusFlow.value = ProcessingStatus.LISTENING
                Logger.i(TAG, "Service started")
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        activeJob?.cancel()
        ttsManager.shutdown()
        Logger.i(TAG, "AIProcessingService destroyed")
    }

    // ---------------------------------------------------------------
    // Public API (called by bound CallActivity / VoiceRecognitionService)
    // ---------------------------------------------------------------

    fun setCallContext(context: CallContext) {
        currentCallContext = context
    }

    /**
     * Process a caller utterance through the full agent pipeline and speak the response.
     */
    fun processUserInput(input: String, callId: String) {
        activeJob?.cancel()
        activeJob = lifecycleScope.launch {
            try {
                _statusFlow.value = ProcessingStatus.PROCESSING
                updateNotification("Thinking…")

                val ctx = currentCallContext ?: CallContext(
                    callId      = callId,
                    callerNumber = null,
                    callerName  = null,
                    callStartTime = System.currentTimeMillis(),
                    isIncoming  = true,
                    callState   = "active"
                )

                val agentResponse = agentManager.generateIntelligentResponse(
                    userInput           = input,
                    callContext         = ctx,
                    conversationHistory = emptyList()
                )

                val reply = agentResponse.content
                _responseFlow.value = reply
                _statusFlow.value   = ProcessingStatus.RESPONDING
                updateNotification("Speaking…")

                // Speak the reply; resume listening once TTS is done
                ttsManager.speak(reply) {
                    _statusFlow.value = ProcessingStatus.LISTENING
                    updateNotification("Listening…")
                }

            } catch (e: Exception) {
                Logger.e(TAG, "Error processing input", e)
                _statusFlow.value = ProcessingStatus.ERROR
                updateNotification("Error – retrying")
            }
        }
    }

    /** Called when the AI should greet the caller upon call answer. */
    fun greetCaller() {
        processUserInput("", currentCallContext?.callId ?: "")
    }

    fun pauseListening() {
        _statusFlow.value = ProcessingStatus.IDLE
    }

    fun resumeListening() {
        _statusFlow.value = ProcessingStatus.LISTENING
        updateNotification("Listening…")
    }

    // ---------------------------------------------------------------
    // Notification helpers
    // ---------------------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI Receptionist",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "AI processing during active calls"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, CallActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("AI Receptionist")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    // ---------------------------------------------------------------
    // Status enum
    // ---------------------------------------------------------------

    enum class ProcessingStatus {
        IDLE, LISTENING, PROCESSING, RESPONDING, ERROR
    }

    companion object {
        private const val TAG            = "AIProcessingService"
        const val CHANNEL_ID             = "ai_processing_channel"
        const val NOTIFICATION_ID        = 1002
        const val ACTION_START           = "com.aireceptionist.app.AI_PROCESSING_START"
        const val ACTION_STOP            = "com.aireceptionist.app.AI_PROCESSING_STOP"
    }
}
