package com.aireceptionist.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.aireceptionist.app.service.CallHandlingService
import com.aireceptionist.app.utils.Logger
import java.util.UUID

/**
 * Receives PHONE_STATE broadcasts and hands calls off to CallHandlingService.
 * Tracks state transitions: IDLE → RINGING, RINGING → OFFHOOK (answered),
 * RINGING → IDLE (missed), OFFHOOK → IDLE (ended).
 */
class CallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallReceiver"
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var savedNumber: String? = null
        private var activeCallId: String? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""

        val newState = when (stateStr) {
            TelephonyManager.EXTRA_STATE_IDLE     -> TelephonyManager.CALL_STATE_IDLE
            TelephonyManager.EXTRA_STATE_OFFHOOK  -> TelephonyManager.CALL_STATE_OFFHOOK
            TelephonyManager.EXTRA_STATE_RINGING  -> TelephonyManager.CALL_STATE_RINGING
            else -> return
        }

        onCallStateChanged(context, newState, incomingNumber)
        lastState = newState
    }

    private fun onCallStateChanged(context: Context, newState: Int, number: String) {
        when {
            // Incoming call ringing
            lastState == TelephonyManager.CALL_STATE_IDLE &&
            newState  == TelephonyManager.CALL_STATE_RINGING -> {
                savedNumber = number
                activeCallId = UUID.randomUUID().toString()
                Logger.d(TAG, "Incoming call from $number (id=$activeCallId)")
                CallHandlingService.start(context, activeCallId!!, number, isIncoming = true)
            }
            // Ringing -> answered
            lastState == TelephonyManager.CALL_STATE_RINGING &&
            newState  == TelephonyManager.CALL_STATE_OFFHOOK -> {
                Logger.d(TAG, "Call answered: $savedNumber")
                // Service already started during RINGING; nothing extra needed
            }
            // Ringing -> missed
            lastState == TelephonyManager.CALL_STATE_RINGING &&
            newState  == TelephonyManager.CALL_STATE_IDLE -> {
                Logger.d(TAG, "Missed call from $savedNumber")
                activeCallId?.let { CallHandlingService.stop(context) }
                savedNumber  = null
                activeCallId = null
            }
            // Call ended
            lastState == TelephonyManager.CALL_STATE_OFFHOOK &&
            newState  == TelephonyManager.CALL_STATE_IDLE -> {
                Logger.d(TAG, "Call ended: $savedNumber")
                activeCallId?.let { id ->
                    CallHandlingService.stop(context)
                }
                savedNumber  = null
                activeCallId = null
            }
        }
    }
}
