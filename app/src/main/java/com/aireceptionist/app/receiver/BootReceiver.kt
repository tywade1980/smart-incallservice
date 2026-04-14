package com.aireceptionist.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.aireceptionist.app.service.CallHandlingService
import com.aireceptionist.app.utils.Logger

/**
 * Restores CallHandlingService after device boot so the AI receptionist
 * is always ready to receive calls without the user having to reopen the app.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        Logger.i("BootReceiver", "Device boot detected – warming up AI Receptionist services")
        warmUpService(context)
    }

    private fun warmUpService(context: Context) {
        try {
            // Start the service with a null call ID so it initialises and stays
            // alive as START_STICKY, ready for the first incoming call.
            val intent = Intent(context, CallHandlingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Logger.i("BootReceiver", "CallHandlingService warm-up requested")
        } catch (e: Exception) {
            Logger.e("BootReceiver", "Failed to start CallHandlingService on boot", e)
        }
    }
}
