package com.scamshield.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.scamshield.R
import com.scamshield.audio.AudioCaptureManager
import com.scamshield.audio.SlidingWindowBuffer
import com.scamshield.audio.SpeechToTextEngine
import com.scamshield.ml.ScamDetectionEngine
import com.scamshield.ui.screens.InCallActivity
import com.scamshield.utils.AlertSeverity
import com.scamshield.utils.CallState
import com.scamshield.utils.PhoneStateHolder
import com.scamshield.utils.ScamAlert
import com.scamshield.utils.ScamPattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Core orchestration service for real-time scam detection.
 * Runs as foreground service during active calls.
 *
 * Pipeline:
 * Mic → AudioCapture → SpeechToText → SlidingWindow → MLInference → Alert
 *
 * ALL processing is ephemeral. Zero data retention.
 */
class ScamDetectionService : Service() {

    companion object {
        private const val TAG = "ScamDetectionService"
        const val ACTION_START = "com.scamshield.START_DETECTION"
        const val ACTION_STOP = "com.scamshield.STOP_DETECTION"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "scam_detection_channel"
        private const val ANALYSIS_INTERVAL_MS = 2000L // Analyze every 2 seconds
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var analysisJob: Job? = null

    private lateinit var audioCaptureManager: AudioCaptureManager
    private lateinit var speechToTextEngine: SpeechToTextEngine
    private lateinit var scamDetectionEngine: ScamDetectionEngine
    private val slidingWindow = SlidingWindowBuffer(maxWords = 150, maxSegments = 30)

    private var cumulativeRiskScore = 0f
    private var inferenceCount = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioCaptureManager = AudioCaptureManager()
        speechToTextEngine = SpeechToTextEngine(this)
        scamDetectionEngine = ScamDetectionEngine(this)
        Log.d(TAG, "ScamDetectionService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startDetectionPipeline()
            ACTION_STOP -> stopDetectionPipeline()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startDetectionPipeline() {
        Log.d(TAG, "Starting detection pipeline")
        startForeground(NOTIFICATION_ID, buildNotification("Monitoring call for scam patterns..."))

        PhoneStateHolder.setCallState(CallState.ANALYZING)

        // Initialize ML engine
        serviceScope.launch(Dispatchers.IO) {
            scamDetectionEngine.initialize()
        }

        // Initialize STT engine on main thread (required for SpeechRecognizer)
        speechToTextEngine.initialize()

        // Start audio capture
        serviceScope.launch(Dispatchers.IO) {
            audioCaptureManager.startCapture()
        }

        // Listen to transcript chunks and run analysis
        analysisJob = serviceScope.launch {
            collectTranscriptsAndAnalyze()
        }

        // Start STT listening
        speechToTextEngine.startContinuousRecognition()

        Log.d(TAG, "Detection pipeline started")
    }

    private suspend fun collectTranscriptsAndAnalyze() {
        speechToTextEngine.transcripts.collect { chunk ->
            // Add to sliding window buffer
            slidingWindow.append(chunk.text)

            // Update live transcript display (ephemeral)
            PhoneStateHolder.updateTranscript(
                slidingWindow.getRecentWords(30) // Show last 30 words only
            )

            // Run inference on final results or every 2 seconds
            if (chunk.isFinal || slidingWindow.size() >= 20) {
                runAnalysis()
            }
        }
    }

    private suspend fun runAnalysis() {
        val windowText = slidingWindow.getWindow()
        if (windowText.isBlank()) return

        val result = scamDetectionEngine.analyze(windowText)

        // Update cumulative risk score (weighted running average)
        if (result.riskScore > 0f) {
            inferenceCount++
            cumulativeRiskScore = if (inferenceCount == 1) {
                result.riskScore
            } else {
                // Exponential moving average - recent results weighted more
                (cumulativeRiskScore * 0.7f) + (result.riskScore * 0.3f)
            }

            PhoneStateHolder.updateRiskScore(cumulativeRiskScore)
        }

        // Process newly detected patterns
        for (pattern in result.detectedPatterns) {
            if (!PhoneStateHolder.detectedPatterns.value.contains(pattern)) {
                PhoneStateHolder.addDetectedPattern(pattern)

                val severity = scamDetectionEngine.getSeverity(result.riskScore)
                val alert = ScamAlert(
                    pattern = pattern,
                    severity = severity,
                    excerpt = getAnonymizedExcerpt(pattern, windowText)
                )
                PhoneStateHolder.addAlert(alert)

                // Trigger vibration + notification for high severity
                if (severity == AlertSeverity.HIGH || severity == AlertSeverity.CRITICAL) {
                    triggerHighSeverityAlert(alert)
                }

                Log.d(TAG, "Alert triggered: ${pattern.displayName} (severity: $severity)")
            }
        }

        // Update notification with current risk level
        updateNotification(cumulativeRiskScore)
    }

    private fun getAnonymizedExcerpt(pattern: ScamPattern, text: String): String {
        // Return a very short, anonymized snippet (no PII)
        val keywords = when (pattern) {
            ScamPattern.OTP_REQUEST -> listOf("otp", "code", "pin")
            ScamPattern.BANK_IMPERSONATION -> listOf("bank", "account", "kyc")
            ScamPattern.GOVERNMENT_IMPERSONATION -> listOf("police", "arrest", "cbi")
            ScamPattern.URGENCY_TACTIC -> listOf("urgent", "immediately", "now")
            else -> emptyList()
        }

        val words = text.split(" ")
        for (kw in keywords) {
            val idx = words.indexOfFirst { it.contains(kw, ignoreCase = true) }
            if (idx >= 0) {
                val start = maxOf(0, idx - 2)
                val end = minOf(words.size - 1, idx + 3)
                return "\"...${words.subList(start, end + 1).joinToString(" ")}...\""
            }
        }
        return "Pattern detected in conversation"
    }

    private fun triggerHighSeverityAlert(alert: ScamAlert) {
        // Vibrate
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
        }
        val pattern = longArrayOf(0, 300, 100, 300, 100, 600)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
        }

        // Update notification to show alert
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildAlertNotification(alert))
    }

    private fun updateNotification(riskScore: Float) {
        val message = when {
            riskScore >= 0.85f -> "⛔ CRITICAL: Very likely scam call!"
            riskScore >= 0.65f -> "🔴 HIGH RISK: Scam patterns detected"
            riskScore >= 0.40f -> "🟡 MEDIUM RISK: Suspicious patterns"
            else -> "🟢 Monitoring call..."
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun stopDetectionPipeline() {
        Log.d(TAG, "Stopping detection pipeline")

        analysisJob?.cancel()
        analysisJob = null

        speechToTextEngine.stopRecognition()

        serviceScope.launch(Dispatchers.IO) {
            audioCaptureManager.stopCapture()
        }

        scamDetectionEngine.destroy()
        slidingWindow.clear()

        PhoneStateHolder.setCallState(CallState.ENDED)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        Log.d(TAG, "All audio/transcript data discarded.")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "ScamDetectionService destroyed")
    }

    // ── Notifications ──

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ScamShield Protection",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time scam call detection"
                enableVibration(true)
                setShowBadge(false)
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, InCallActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ScamShield Active")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }

    private fun buildAlertNotification(alert: ScamAlert): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⚠️ Scam Alert: ${alert.pattern.displayName}")
            .setContentText(alert.excerpt)
            .setSmallIcon(R.drawable.ic_shield_alert)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }
}
