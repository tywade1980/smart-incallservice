package com.aireceptionist.app.telecom

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import com.aireceptionist.app.utils.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers the AI Receptionist app as a VoIP PhoneAccount with Android Telecom.
 *
 * By declaring CAPABILITY_CALL_PROVIDER, the system knows it can route calls
 * through AIConnectionService — giving us full audio control via
 * audioModeIsVoip = true (Option B: SIP/VoIP routing to a user-supplied IP).
 */
@Singleton
class TelecomPhoneAccountManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val telecomManager =
        context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

    val phoneAccountHandle: PhoneAccountHandle by lazy {
        PhoneAccountHandle(
            ComponentName(context, AIConnectionService::class.java),
            ACCOUNT_ID
        )
    }

    /**
     * Register (or re-register) the PhoneAccount.
     * [sipAddress] defaults to localhost; pass the user's SIP URI when VoIP is configured.
     */
    fun registerPhoneAccount(sipAddress: String = "sip:aireceptionist@localhost") {
        try {
            val account = PhoneAccount.builder(phoneAccountHandle, "AI Receptionist")
                .setCapabilities(
                    PhoneAccount.CAPABILITY_CALL_PROVIDER or
                    PhoneAccount.CAPABILITY_CONNECTION_MANAGER
                )
                .addSupportedUriScheme(PhoneAccount.SCHEME_SIP)
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .setAddress(Uri.parse(sipAddress))
                .setSubscriptionAddress(Uri.parse(sipAddress))
                .build()
            telecomManager.registerPhoneAccount(account)
            Logger.i(TAG, "PhoneAccount registered: $sipAddress")
        } catch (e: SecurityException) {
            Logger.e(TAG, "Permission denied registering PhoneAccount", e)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to register PhoneAccount", e)
        }
    }

    fun unregisterPhoneAccount() {
        try {
            telecomManager.unregisterPhoneAccount(phoneAccountHandle)
            Logger.i(TAG, "PhoneAccount unregistered")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to unregister PhoneAccount", e)
        }
    }

    fun isPhoneAccountEnabled(): Boolean = try {
        telecomManager.getPhoneAccount(phoneAccountHandle)?.isEnabled == true
    } catch (e: Exception) {
        false
    }

    companion object {
        private const val TAG = "TelecomPhoneAccountManager"
        const val ACCOUNT_ID = "ai_receptionist_voip_account"
    }
}
