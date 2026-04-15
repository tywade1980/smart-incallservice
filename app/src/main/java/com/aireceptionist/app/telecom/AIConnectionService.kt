package com.aireceptionist.app.telecom

import android.net.Uri
import android.telecom.*
import com.aireceptionist.app.utils.Logger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * ConnectionService for VoIP calls routed through the AI Receptionist PhoneAccount.
 *
 * Registers the PhoneAccount in [onCreate] so Android knows this service
 * can handle SIP/TEL calls. Setting [AIConnection.audioModeIsVoip] = true
 * grants full audio control via VOICE_COMMUNICATION source/stream —
 * this is the entry point for Option B (SIP/VoIP audio routing).
 */
@AndroidEntryPoint
class AIConnectionService : ConnectionService() {

    @Inject lateinit var telecomPhoneAccountManager: TelecomPhoneAccountManager

    override fun onCreate() {
        super.onCreate()
        telecomPhoneAccountManager.registerPhoneAccount()
        Logger.i(TAG, "AIConnectionService created, PhoneAccount registered")
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection? {
        Logger.i(TAG, "Creating outgoing connection to: ${request?.address}")
        return request?.let {
            AIConnection(it.address, true).apply { setInitializing() }
        }
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection? {
        Logger.i(TAG, "Creating incoming connection from: ${request?.address}")
        return request?.let {
            AIConnection(it.address, false).apply { setRinging() }
        }
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        super.onCreateOutgoingConnectionFailed(connectionManagerPhoneAccount, request)
        Logger.e(TAG, "Outgoing connection failed: ${request?.address}")
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request)
        Logger.e(TAG, "Incoming connection failed: ${request?.address}")
    }

    /**
     * AI-managed connection. [audioModeIsVoip] = true gives us full
     * VOICE_COMMUNICATION audio access for the hear→think→speak pipeline.
     */
    inner class AIConnection(
        private val address: Uri,
        private val isOutgoing: Boolean
    ) : Connection() {

        init {
            connectionCapabilities =
                CAPABILITY_SUPPORT_HOLD or
                CAPABILITY_HOLD or
                CAPABILITY_MUTE or
                CAPABILITY_SUPPORTS_VT_LOCAL_RX or
                CAPABILITY_SUPPORTS_VT_LOCAL_TX or
                CAPABILITY_SUPPORTS_VT_REMOTE_RX or
                CAPABILITY_SUPPORTS_VT_REMOTE_TX
            audioModeIsVoip = true
            callerDisplayName = "AI Receptionist"
            Logger.d(TAG, "AIConnection created: $address (outgoing=$isOutgoing)")
        }

        override fun onAnswer()                      { setActive() }
        override fun onAnswer(videoState: Int)       { setActive() }
        override fun onReject()                      { setDisconnected(DisconnectCause(DisconnectCause.REJECTED)); destroy() }
        override fun onReject(rejectReason: Int)     { setDisconnected(DisconnectCause(DisconnectCause.REJECTED)); destroy() }
        override fun onDisconnect()                  { setDisconnected(DisconnectCause(DisconnectCause.LOCAL)); destroy() }
        override fun onAbort()                       { setDisconnected(DisconnectCause(DisconnectCause.CANCELED)); destroy() }
        override fun onHold()                        { setOnHold() }
        override fun onUnhold()                      { setActive() }
        override fun onSeparate()                    { setDisconnected(DisconnectCause(DisconnectCause.OTHER)); destroy() }
        override fun onMuteStateChanged(isMuted: Boolean) {}
        override fun onPlayDtmfTone(c: Char)         {}
        override fun onStopDtmfTone()                {}
        override fun onPostDialContinue(proceed: Boolean) {}
        override fun onCallAudioStateChanged(state: CallAudioState) {
            Logger.d(TAG, "Audio state: ${state.route}")
        }
        override fun onStateChanged(state: Int) {
            super.onStateChanged(state)
            Logger.d(TAG, "Connection state: $state")
        }
    }

    companion object {
        private const val TAG = "AIConnectionService"
    }
}
