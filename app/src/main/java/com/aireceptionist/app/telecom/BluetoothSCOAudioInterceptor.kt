package com.aireceptionist.app.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.AudioAttributes
import com.aireceptionist.app.utils.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Option A — Bluetooth SCO audio intercept.
 *
 * Calling [startSCOInterception] puts the audio subsystem into SCO mode:
 *   • AudioRecord(VOICE_COMMUNICATION) captures the caller's voice from the SCO stream.
 *   • AudioTrack(USAGE_VOICE_COMMUNICATION) injects TTS audio back into the stream,
 *     so the caller hears the AI speaking through their earpiece.
 *
 * SpeechRecognizer automatically reads from the SCO channel when SCO is active,
 * so no manual PCM piping to the recognizer is needed.
 */
@Singleton
class BluetoothSCOAudioInterceptor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var onAudioFrameCaptured: ((ByteArray) -> Unit)? = null
    private var onSCOConnected: (() -> Unit)? = null
    private var onSCODisconnected: (() -> Unit)? = null

    private var isCapturing = false

    var isSCOActive = false
        private set

    // ── SCO state broadcast receiver ────────────────────────────────────────

    private val scoStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED) return
            when (intent.getIntExtra(
                AudioManager.EXTRA_SCO_AUDIO_STATE,
                AudioManager.SCO_AUDIO_STATE_DISCONNECTED
            )) {
                AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                    Logger.i(TAG, "Bluetooth SCO connected — call audio intercepted")
                    isSCOActive = true
                    startAudioCapture()
                    onSCOConnected?.invoke()
                }
                AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                    Logger.w(TAG, "Bluetooth SCO disconnected")
                    isSCOActive = false
                    stopAudioCapture()
                    onSCODisconnected?.invoke()
                }
                AudioManager.SCO_AUDIO_STATE_ERROR -> {
                    Logger.e(TAG, "Bluetooth SCO error")
                    isSCOActive = false
                    onSCODisconnected?.invoke()
                }
            }
        }
    }

    // ── Public API ──────────────────────────────────────────────────────────

    fun setCallbacks(
        onAudioFrame: (ByteArray) -> Unit = {},
        onConnected: () -> Unit = {},
        onDisconnected: () -> Unit = {}
    ) {
        onAudioFrameCaptured = onAudioFrame
        onSCOConnected = onConnected
        onSCODisconnected = onDisconnected
    }

    /** Engage SCO mode and route call audio through the Bluetooth channel. */
    fun startSCOInterception() {
        try {
            Logger.i(TAG, "Starting Bluetooth SCO interception")
            context.registerReceiver(
                scoStateReceiver,
                IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            )
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start SCO", e)
        }
    }

    fun stopSCOInterception() {
        Logger.i(TAG, "Stopping Bluetooth SCO interception")
        stopAudioCapture()
        runCatching {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        }
        runCatching { context.unregisterReceiver(scoStateReceiver) }
        isSCOActive = false
    }

    /**
     * Write raw PCM bytes into the active SCO stream.
     * The caller will hear this audio through their earpiece.
     * Use this to inject TTS-synthesized speech (16-bit, mono, 16 kHz).
     */
    fun injectAudioIntoCall(pcmData: ByteArray) {
        if (!isSCOActive) return
        scope.launch {
            val track = audioTrack ?: return@launch
            if (track.state != AudioTrack.STATE_INITIALIZED) return@launch
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
            track.write(pcmData, 0, pcmData.size)
        }
    }

    fun destroy() {
        stopSCOInterception()
        scope.cancel()
    }

    // ── Private ─────────────────────────────────────────────────────────────

    private fun startAudioCapture() {
        if (isCapturing) return
        try {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(BUFFER_SIZE)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf
            )
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Logger.e(TAG, "AudioRecord init failed — state ${audioRecord?.state}")
                audioRecord?.release(); audioRecord = null
                return
            }

            val playBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(BUFFER_SIZE)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(playBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioRecord?.startRecording()
            isCapturing = true

            captureJob = scope.launch {
                val buf = ByteArray(minBuf)
                Logger.d(TAG, "PCM capture loop started")
                while (isActive && isCapturing) {
                    val read = audioRecord?.read(buf, 0, buf.size) ?: break
                    if (read > 0) onAudioFrameCaptured?.invoke(buf.copyOf(read))
                }
                Logger.d(TAG, "PCM capture loop ended")
            }
            Logger.i(TAG, "Audio capture started (${SAMPLE_RATE} Hz, mono, PCM 16-bit)")
        } catch (e: SecurityException) {
            Logger.e(TAG, "RECORD_AUDIO permission denied", e)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start audio capture", e)
        }
    }

    private fun stopAudioCapture() {
        isCapturing = false
        captureJob?.cancel(); captureJob = null
        runCatching { audioRecord?.stop(); audioRecord?.release() }
        audioRecord = null
        runCatching { audioTrack?.stop(); audioTrack?.release() }
        audioTrack = null
    }

    companion object {
        private const val TAG = "BluetoothSCOAudioInterceptor"
        private const val SAMPLE_RATE = 16_000
        private const val BUFFER_SIZE = 4_096
    }
}
