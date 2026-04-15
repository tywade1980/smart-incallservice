package com.aireceptionist.app.ui.settings

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aireceptionist.app.databinding.ActivitySettingsBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Business configuration screen.
 *
 * Lets the owner customise:
 *   • Business name / type
 *   • AI persona name + greeting scripts
 *   • Speaking rate (TTS slider)
 *   • Auto-answer, emotion detection, call recording toggles
 *   • Escalation number
 *   • Custom instructions for the AI
 *   • SIP/VoIP server IP, port, credentials, enable toggle
 */
@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Receptionist Settings"
        }
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.btnSave.setOnClickListener { saveSettings() }
        binding.btnTestGreeting.setOnClickListener { viewModel.testGreeting() }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.profile.collect { profile ->
                        profile ?: return@collect
                        binding.etBusinessName.setText(profile.businessName)
                        binding.etBusinessType.setText(profile.businessType)
                        binding.etAiPersonaName.setText(profile.aiPersonaName)
                        binding.etGreeting.setText(profile.greeting)
                        binding.etAfterHoursMessage.setText(profile.afterHoursMessage)
                        binding.etEscalationNumber.setText(profile.escalationPhoneNumber)
                        binding.etCustomInstructions.setText(profile.customInstructions)
                        binding.switchAutoAnswer.isChecked       = profile.autoAnswerEnabled
                        binding.switchEmotionDetection.isChecked = profile.enableEmotionDetection
                        binding.switchCallRecording.isChecked    = profile.enableCallRecording
                        binding.sliderSpeakingRate.value         = profile.speakingRate
                    }
                }

                launch {
                    viewModel.sipConfig.collect { cfg ->
                        binding.etSipServer.setText(cfg.serverAddress)
                        binding.etSipPort.setText(if (cfg.port == 5060) "" else cfg.port.toString())
                        binding.etSipUsername.setText(cfg.username)
                        binding.etSipPassword.setText(cfg.password)
                        binding.switchSipEnabled.isChecked = cfg.enabled
                    }
                }

                launch {
                    viewModel.saveEvent.collect { saved ->
                        if (saved) {
                            Snackbar.make(binding.root, "Settings saved", Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun saveSettings() {
        viewModel.saveProfile(
            businessName       = binding.etBusinessName.text.toString().trim(),
            businessType       = binding.etBusinessType.text.toString().trim(),
            aiPersonaName      = binding.etAiPersonaName.text.toString().trim(),
            greeting           = binding.etGreeting.text.toString().trim(),
            afterHoursMsg      = binding.etAfterHoursMessage.text.toString().trim(),
            escalationNumber   = binding.etEscalationNumber.text.toString().trim(),
            customInstructions = binding.etCustomInstructions.text.toString().trim(),
            autoAnswer         = binding.switchAutoAnswer.isChecked,
            emotionDetection   = binding.switchEmotionDetection.isChecked,
            callRecording      = binding.switchCallRecording.isChecked,
            speakingRate       = binding.sliderSpeakingRate.value
        )
        viewModel.saveSIPConfig(
            server   = binding.etSipServer.text.toString().trim(),
            port     = binding.etSipPort.text.toString().toIntOrNull() ?: 5060,
            username = binding.etSipUsername.text.toString().trim(),
            password = binding.etSipPassword.text.toString(),
            enabled  = binding.switchSipEnabled.isChecked
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
