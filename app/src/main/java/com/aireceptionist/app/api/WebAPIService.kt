package com.aireceptionist.app.api

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.aireceptionist.app.utils.Logger
import dagger.hilt.android.AndroidEntryPoint

/**
 * Stubbed Web API Service for Android build.
 * The original implementation relied on com.sun.net.httpserver, which is not available on Android.
 * This stub keeps the Service wiring without starting an embedded HTTP server.
 */
@AndroidEntryPoint
class WebAPIService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Logger.i(TAG, "WebAPIService created (stub, HTTP server disabled)")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.i(TAG, "WebAPIService started (stub)")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.i(TAG, "WebAPIService destroyed (stub)")
    }

    companion object {
        private const val TAG = "WebAPIService"
    }
}