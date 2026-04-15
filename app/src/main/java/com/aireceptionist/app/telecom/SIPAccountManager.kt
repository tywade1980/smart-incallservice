package com.aireceptionist.app.telecom

import android.content.SharedPreferences
import com.aireceptionist.app.utils.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SIP configuration that the user enters in Settings.
 * [sipUri] is the full address the OS uses to route audio:
 *   sip:<username>@<serverAddress>:<port>
 * Point [serverAddress] at your own IP or SIP proxy to own the RTP stream.
 */
data class SIPConfig(
    val serverAddress: String = "",
    val port: Int = 5060,
    val username: String = "aireceptionist",
    val password: String = "",
    val enabled: Boolean = false
) {
    val sipUri: String get() = "sip:$username@$serverAddress:$port"
    val isValid: Boolean get() = serverAddress.isNotBlank() && username.isNotBlank()
}

/**
 * Option B — SIP/VoIP routing.
 *
 * Stores the user's SIP server credentials and registers/unregisters the
 * AI Receptionist PhoneAccount via [TelecomPhoneAccountManager].
 * When enabled, Android routes call audio through AIConnectionService
 * (audioModeIsVoip=true), giving the AI full two-way audio control.
 */
@Singleton
class SIPAccountManager @Inject constructor(
    private val prefs: SharedPreferences,
    private val telecomPhoneAccountManager: TelecomPhoneAccountManager
) {
    fun getSIPConfig(): SIPConfig = SIPConfig(
        serverAddress = prefs.getString(KEY_SERVER, "") ?: "",
        port          = prefs.getInt(KEY_PORT, 5060),
        username      = prefs.getString(KEY_USER, "aireceptionist") ?: "aireceptionist",
        password      = prefs.getString(KEY_PASS, "") ?: "",
        enabled       = prefs.getBoolean(KEY_ENABLED, false)
    )

    fun saveSIPConfig(config: SIPConfig) {
        prefs.edit()
            .putString(KEY_SERVER,   config.serverAddress)
            .putInt(KEY_PORT,        config.port)
            .putString(KEY_USER,     config.username)
            .putString(KEY_PASS,     config.password)
            .putBoolean(KEY_ENABLED, config.enabled)
            .apply()
        Logger.i(TAG, "SIP config saved: ${config.sipUri}")
    }

    /** Register the PhoneAccount pointing at [config.sipUri] (the user's IP:port). */
    fun enableSIPRouting(config: SIPConfig) {
        if (!config.isValid) {
            Logger.w(TAG, "SIP config invalid — cannot enable")
            return
        }
        telecomPhoneAccountManager.registerPhoneAccount(config.sipUri)
        saveSIPConfig(config.copy(enabled = true))
        Logger.i(TAG, "SIP routing enabled → ${config.sipUri}")
    }

    fun disableSIPRouting() {
        telecomPhoneAccountManager.unregisterPhoneAccount()
        saveSIPConfig(getSIPConfig().copy(enabled = false))
        Logger.i(TAG, "SIP routing disabled")
    }

    companion object {
        private const val TAG        = "SIPAccountManager"
        private const val KEY_SERVER  = "sip_server"
        private const val KEY_PORT    = "sip_port"
        private const val KEY_USER    = "sip_username"
        private const val KEY_PASS    = "sip_password"
        private const val KEY_ENABLED = "sip_enabled"
    }
}
