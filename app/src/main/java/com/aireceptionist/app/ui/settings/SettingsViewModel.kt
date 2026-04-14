package com.aireceptionist.app.ui.settings

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aireceptionist.app.ai.voice.TextToSpeechManager
import com.aireceptionist.app.data.models.BusinessProfile
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: SharedPreferences,
    private val ttsManager: TextToSpeechManager,
    private val gson: Gson
) : ViewModel() {

    companion object {
        private const val KEY_PROFILE = "business_profile"
    }

    private val _profile = MutableStateFlow<BusinessProfile?>(null)
    val profile: StateFlow<BusinessProfile?> = _profile.asStateFlow()

    /** Emits true once per successful save (one-shot event). */
    private val _saveEvent = MutableSharedFlow<Boolean>()
    val saveEvent: SharedFlow<Boolean> = _saveEvent.asSharedFlow()

    init {
        loadProfile()
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
                businessName         = businessName,
                businessType         = businessType,
                aiPersonaName        = aiPersonaName,
                greeting             = greeting,
                afterHoursMessage    = afterHoursMsg,
                escalationPhoneNumber = escalationNumber,
                customInstructions   = customInstructions,
                autoAnswerEnabled    = autoAnswer,
                enableEmotionDetection = emotionDetection,
                enableCallRecording  = callRecording,
                speakingRate         = speakingRate
            )
            prefs.edit().putString(KEY_PROFILE, gson.toJson(updated)).apply()
            _profile.value = updated
            _saveEvent.emit(true)
        }
    }

    fun testGreeting() {
        viewModelScope.launch {
            val greeting = _profile.value?.greeting
                ?: "Thank you for calling! How can I help you today?"
            ttsManager.speak(greeting) { /* done */ }
        }
    }
}
