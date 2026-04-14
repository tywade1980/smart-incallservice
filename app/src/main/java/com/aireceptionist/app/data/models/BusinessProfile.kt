package com.aireceptionist.app.data.models

import com.google.gson.annotations.SerializedName

/**
 * Persistent business configuration stored in SharedPreferences as JSON.
 * Controls AI receptionist persona, voice, call-handling rules, and hours.
 */
data class BusinessProfile(
    @SerializedName("business_name")
    val businessName: String = "",

    @SerializedName("business_type")
    val businessType: String = "",

    @SerializedName("greeting")
    val greeting: String = "Thank you for calling! How can I help you today?",

    @SerializedName("hold_message")
    val holdMessage: String = "Please hold for just a moment.",

    @SerializedName("closing_message")
    val closingMessage: String = "Thank you for calling. Have a wonderful day!",

    @SerializedName("ai_persona_name")
    val aiPersonaName: String = "Alex",

    @SerializedName("voice_type")
    val voiceType: String = "en-US",

    @SerializedName("speaking_rate")
    val speakingRate: Float = 1.0f,

    @SerializedName("pitch")
    val pitch: Float = 0.0f,

    @SerializedName("enable_emotion_detection")
    val enableEmotionDetection: Boolean = true,

    @SerializedName("enable_call_recording")
    val enableCallRecording: Boolean = false,

    @SerializedName("max_call_duration_minutes")
    val maxCallDurationMinutes: Int = 30,

    @SerializedName("auto_answer_enabled")
    val autoAnswerEnabled: Boolean = false,

    @SerializedName("auto_answer_rings")
    val autoAnswerRings: Int = 2,

    @SerializedName("business_hours_start")
    val businessHoursStart: String = "09:00",

    @SerializedName("business_hours_end")
    val businessHoursEnd: String = "17:00",

    @SerializedName("timezone")
    val timezone: String = "America/New_York",

    @SerializedName("after_hours_message")
    val afterHoursMessage: String = "We are currently closed. Please call back during business hours.",

    @SerializedName("escalation_phone_number")
    val escalationPhoneNumber: String = "",

    @SerializedName("language")
    val language: String = "en-US",

    @SerializedName("custom_instructions")
    val customInstructions: String = "",

    @SerializedName("enable_sms_follow_up")
    val enableSmsFollowUp: Boolean = false,

    @SerializedName("department_list")
    val departmentList: String = "Sales,Support,Billing,General"
)
