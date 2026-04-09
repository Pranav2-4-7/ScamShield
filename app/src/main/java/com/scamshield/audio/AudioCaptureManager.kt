package com.scamshield.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Captures live audio via microphone while speakerphone is active.
 * Audio is NEVER stored - processed in memory only and discarded.
 * This is the core privacy-preserving audio pipeline entry point.
 */
class AudioCaptureManager {

    companion object {
        private const val TAG = "AudioCaptureManager"

        // Audio config optimised for speech
        const val SAMPLE_RATE = 16000          // 16 kHz - standard for speech
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val CHUNK_DURATION_MS = 500      // 500ms chunks
        const val CHUNK_SIZE = SAMPLE_RATE * CHUNK_DURATION_MS / 1000  // samples per chunk
        const val BUFFER_SIZE_MULTIPLIER = 4
    }

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // Emits raw PCM audio chunks (ephemeral - never stored)
    private val _audioChunks = MutableSharedFlow<ShortArray>(extraBufferCapacity = 8)
    val audioChunks: SharedFlow<ShortArray> = _audioChunks

    private val minBufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
    ) * BUFFER_SIZE_MULTIPLIER

    var isCapturing = false
        private set

    fun startCapture() {
        if (isCapturing) {
            Log.w(TAG, "Already capturing")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, // Best for speech in noisy env
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                minBufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord init failed")
                return
            }

            audioRecord?.startRecording()
            isCapturing = true
            Log.d(TAG, "Audio capture started")

            captureJob = scope.launch {
                val buffer = ShortArray(CHUNK_SIZE)
                while (isActive && isCapturing) {
                    val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readCount > 0) {
                        // Emit a copy - original buffer is reused (zero retention)
                        val chunk = buffer.copyOf(readCount)
                        _audioChunks.emit(chunk)
                        // Original buffer data will be overwritten next iteration
                    } else if (readCount < 0) {
                        Log.e(TAG, "AudioRecord read error: $readCount")
                        break
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "RECORD_AUDIO permission denied: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Capture start error: ${e.message}")
        }
    }

    fun stopCapture() {
        isCapturing = false
        captureJob?.cancel()
        captureJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Capture stop error: ${e.message}")
        }

        Log.d(TAG, "Audio capture stopped. All audio data discarded.")
    }

    /**
     * Apply noise reduction to raw PCM samples.
     * Simple spectral gating for telephony noise.
     */
    fun applyNoiseReduction(samples: ShortArray): ShortArray {
        val threshold = 800.toShort() // Noise floor threshold
        return ShortArray(samples.size) { i ->
            if (Math.abs(samples[i].toInt()) < threshold) 0 else samples[i]
        }
    }

    /**
     * Normalize audio amplitude for consistent recognition.
     */
    fun normalizeAmplitude(samples: ShortArray): ShortArray {
        val max = samples.maxOfOrNull { Math.abs(it.toInt()) } ?: 1
        if (max == 0) return samples
        val scale = Short.MAX_VALUE.toFloat() / max
        return ShortArray(samples.size) { i ->
            (samples[i] * scale).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
}
