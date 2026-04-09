package com.scamshield.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * On-device speech-to-text using Android's SpeechRecognizer.
 * Transcripts are EPHEMERAL - never written to disk or transmitted.
 * Uses offline recognition when available for maximum privacy.
 */
class SpeechToTextEngine(private val context: Context) {

    companion object {
        private const val TAG = "SpeechToTextEngine"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    // Emits partial + final transcripts (ephemeral)
    private val _transcripts = MutableSharedFlow<TranscriptChunk>(extraBufferCapacity = 16)
    val transcripts: SharedFlow<TranscriptChunk> = _transcripts

    fun initialize() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Speech recognition not available on this device")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(createListener())
        Log.d(TAG, "STT engine initialized")
    }

    fun startContinuousRecognition() {
        if (isListening) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN") // Indian English - handles accents
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // Get partial results for real-time
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
            // Prefer offline for privacy
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        try {
            speechRecognizer?.startListening(intent)
            isListening = true
            Log.d(TAG, "Continuous recognition started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recognition: ${e.message}")
        }
    }

    fun stopRecognition() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
            isListening = false
            Log.d(TAG, "Recognition stopped. All transcript data discarded.")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recognition: ${e.message}")
        }
    }

    private fun restartListening() {
        isListening = false
        startContinuousRecognition()
    }

    private fun createListener() = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Speech started")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Voice activity indicator - not processed
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Raw audio buffer - immediately discarded
        }

        override fun onEndOfSpeech() {
            Log.d(TAG, "Speech ended")
        }

        override fun onError(error: Int) {
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                SpeechRecognizer.ERROR_CLIENT -> "Client error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissions error"
                SpeechRecognizer.ERROR_NETWORK -> "Network error (offline mode fallback)"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                else -> "Unknown error: $error"
            }
            Log.w(TAG, "Recognition error: $errorMsg")

            // Auto-restart on recoverable errors
            if (error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                restartListening()
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: return

            Log.d(TAG, "Final result received")
            _transcripts.tryEmit(TranscriptChunk(text, isFinal = true))

            // Restart for continuous detection
            restartListening()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = partial?.firstOrNull() ?: return

            if (text.length > 5) { // Skip very short partials
                _transcripts.tryEmit(TranscriptChunk(text, isFinal = false))
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}

data class TranscriptChunk(
    val text: String,
    val isFinal: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
