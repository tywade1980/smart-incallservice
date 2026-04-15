package com.aireceptionist.app.ui.settings

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aireceptionist.app.ai.voice.TextToSpeechManager
import com.aireceptionist.app.data.models.BusinessProfile
import com.aireceptionist.app.telecom.SIPAccountManager
import com.aireceptionist.app.telecom.SIPConfig
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: SharedPreferences,
    private val ttsManager: TextToSpeechManager,
    private val gson: Gson,
    private val sipAccountManager: SIPAccountManager
) : ViewModel() {

    companion object {
        private const val KEY_PROFILE = "business_profile"
    }

    private val _profile = MutableStateFlow<BusinessProfile?>(null)
    val profile: StateFlow<BusinessProfile?> = _profile.asStateFlow()

    private val _sipConfig = MutableStateFlow(SIPConfig())
    val sipConfig: StateFlow<SIPConfig> = _sipConfig.asStateFlow()

    /** Emits true once per successful save (one-shot event). */
    private val _saveEvent = MutableSharedFlow<Boolean>()
    val saveEvent: SharedFlow<Boolean> = _saveEvent.asSharedFlow()

    init {
        loadProfile()
        loadSIPConfig()
    }

    private fun loadProfile() {
        val json = prefs.getString(KEY_PROFILE, null)
        _profile.value = if (json != null) {
            try { gson.fromJson(json, BusinessProfile::class.java) }
            catch (_: Exception) { BusinessProfile() }
        } else {
            BusinessProfile()
        }
    }

    private fun loadSIPConfig() {
        _sipConfig.value = sipAccountManager.getSIPConfig()
    }

    fun saveProfile(
        businessName: String,
        businessType: String,
        aiPersonaName: String,
        greeting: String,
        afterHoursMsg: String,
        escalationNumber: String,
        customInstructions: String,
        autoAnswer: Boolean,
        emotionDetection: Boolean,
        callRecording: Boolean,
        speakingRate: Float
    ) {
        viewModelScope.launch {
            val updated = (_profile.value ?: BusinessProfile()).copy(
                businessName          = businessName,
                businessType          = businessType,
                aiPersonaName         = aiPersonaName,
                greeting              = greeting,
                afterHoursMessage     = afterHoursMsg,
                escalationPhoneNumber = escalationNumber,
                customInstructions    = customInstructions,
                autoAnswerEnabled     = autoAnswer,
                enableEmotionDetection = emotionDetection,
                enableCallRecording   = callRecording,
                speakingRate          = speakingRate
            )
            prefs.edit().putString(KEY_PROFILE, gson.toJson(updated)).apply()
            _profile.value = updated
            _saveEvent.emit(true)
        }
    }

    fun saveSIPConfig(
        server: String,
        port: Int,
        username: String,
        password: String,
        enabled: Boolean
    ) {
        viewModelScope.launch {
            val config = SIPConfig(
                serverAddress = server,
                port          = port,
                username      = username.ifBlank { "aireceptionist" },
                password      = password,
                enabled       = enabled
            )
            if (enabled && config.isValid) {
                sipAccountManager.enableSIPRouting(config)
            } else {
                sipAccountManager.saveSIPConfig(config.copy(enabled = false))
                if (!enabled) sipAccountManager.disableSIPRouting()
            }
            _sipConfig.value = sipAccountManager.getSIPConfig()
            _saveEvent.emit(true)
        }
    }

    fun testGreeting() {
        viewModelScope.launch {
            ttsManager.speak(
                _profile.value?.greeting ?: "Thank you for calling! How can I help you today?"
            )
        }
    }
}
