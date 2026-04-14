package com.aireceptionist.app.ui.call

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aireceptionist.app.ai.voice.VoiceRecognitionService
import com.aireceptionist.app.databinding.ActivityCallBinding
import com.aireceptionist.app.service.AIProcessingService
import com.aireceptionist.app.utils.Logger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Full-screen call UI shown while the AI receptionist is handling a call.
 *
 * Displays:
 *   • Caller name / number
 *   • Live transcript (partial + final results from VoiceRecognitionService)
 *   • AI response bubble
 *   • AI status indicator (Listening / Thinking / Speaking)
 *   • Call duration timer
 *   • Controls: mute, hold, speaker, transfer, end call
 */
@AndroidEntryPoint
class CallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallBinding
    private val viewModel: CallViewModel by viewModels()

    // Service bindings
    private var voiceService: VoiceRecognitionService? = null
    private var processingService: AIProcessingService? = null

    private val voiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            voiceService = (binder as? VoiceRecognitionService.VoiceRecognitionBinder)?.getService()
            viewModel.attachVoiceService(voiceService)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            voiceService = null
        }
    }

    private val processingConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            processingService = (binder as? AIProcessingService.AIProcessingBinder)?.getService()
            viewModel.attachProcessingService(processingService)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            processingService = null
        }
    }

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val callId     = intent.getStringExtra(EXTRA_CALL_ID) ?: ""
        val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: "Unknown"
        val isIncoming  = intent.getBooleanExtra(EXTRA_IS_INCOMING, true)

        viewModel.initCall(callId, phoneNumber, isIncoming)
        setupClickListeners()
        observeViewModel()
        bindServices()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unbindService(voiceConnection)
            unbindService(processingConnection)
        } catch (_: Exception) { /* not bound */ }
        viewModel.endCall()
    }

    // ---------------------------------------------------------------
    // UI wiring
    // ---------------------------------------------------------------

    private fun setupClickListeners() {
        binding.btnEndCall.setOnClickListener {
            viewModel.endCall()
            finish()
        }
        binding.btnMute.setOnClickListener   { viewModel.toggleMute() }
        binding.btnSpeaker.setOnClickListener { viewModel.toggleSpeaker() }
        binding.btnHold.setOnClickListener   { viewModel.toggleHold() }
        binding.btnTransfer.setOnClickListener { viewModel.transferCall() }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.callerName.collect { name ->
                        binding.tvCallerName.text = name
                    }
                }
                launch {
                    viewModel.callerPhone.collect { phone ->
                        binding.tvCallerPhone.text = phone
                    }
                }
                launch {
                    viewModel.callCount.collect { count ->
                        binding.tvCallCount.text = if (count > 0) "$count previous calls" else ""
                    }
                }
                launch {
                    viewModel.callDuration.collect { dur ->
                        binding.tvCallDuration.text = dur
                    }
                }
                launch {
                    viewModel.aiStatus.collect { status ->
                        binding.tvAiStatus.text = status.label
                        binding.viewAiIndicator.setBackgroundColor(
                            getColor(status.colorRes)
                        )
                        binding.progressAi.visibility =
                            if (status == CallViewModel.AIStatus.PROCESSING) View.VISIBLE
                            else View.GONE
                    }
                }
                launch {
                    viewModel.liveTranscript.collect { t ->
                        binding.tvTranscript.text = t
                    }
                }
                launch {
                    viewModel.aiResponse.collect { r ->
                        if (!r.isNullOrBlank()) {
                            binding.tvAiResponse.text = r
                            binding.cardAiResponse.visibility = View.VISIBLE
                        }
                    }
                }
                launch {
                    viewModel.emotionLabel.collect { em ->
                        binding.tvEmotion.text = em
                        binding.tvEmotion.visibility = if (em.isNotBlank()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.isMuted.collect { muted ->
                        binding.btnMute.text = if (muted) "Unmute" else "Mute"
                    }
                }
                launch {
                    viewModel.isOnHold.collect { hold ->
                        binding.btnHold.text = if (hold) "Resume" else "Hold"
                    }
                }
            }
        }
    }

    private fun bindServices() {
        bindService(
            Intent(this, VoiceRecognitionService::class.java),
            voiceConnection,
            BIND_AUTO_CREATE
        )
        bindService(
            Intent(this, AIProcessingService::class.java),
            processingConnection,
            BIND_AUTO_CREATE
        )
    }

    companion object {
        const val EXTRA_CALL_ID      = "call_id"
        const val EXTRA_PHONE_NUMBER = "phone_number"
        const val EXTRA_IS_INCOMING  = "is_incoming"

        fun start(context: android.content.Context, callId: String, phoneNumber: String, isIncoming: Boolean) {
            context.startActivity(
                Intent(context, CallActivity::class.java).apply {
                    putExtra(EXTRA_CALL_ID,      callId)
                    putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                    putExtra(EXTRA_IS_INCOMING,  isIncoming)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}
