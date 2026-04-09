package com.scamshield.utils

import android.telecom.Call
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Ephemeral in-memory call state.
 * Nothing here is ever written to disk or transmitted.
 * All data is cleared when the call ends.
 */
object PhoneStateHolder {

    var currentCall: Call? = null
    var callerNumber: String = ""
    var isUnknownCaller: Boolean = false
    var isSpeakerphoneActive: Boolean = false

    // Live scam alert stream
    private val _scamAlerts = MutableStateFlow<List<ScamAlert>>(emptyList())
    val scamAlerts: StateFlow<List<ScamAlert>> = _scamAlerts

    // Risk score 0.0 – 1.0
    private val _riskScore = MutableStateFlow(0.0f)
    val riskScore: StateFlow<Float> = _riskScore

    // Transcription stream (ephemeral, never stored)
    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript

    private val _detectedPatterns = MutableStateFlow<Set<ScamPattern>>(emptySet())
    val detectedPatterns: StateFlow<Set<ScamPattern>> = _detectedPatterns

    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState

    fun updateRiskScore(score: Float) {
        _riskScore.value = score.coerceIn(0f, 1f)
    }

    fun addAlert(alert: ScamAlert) {
        _scamAlerts.value = _scamAlerts.value + alert
    }

    fun updateTranscript(text: String) {
        // Ephemeral only - never written to storage
        _liveTranscript.value = text
    }

    fun addDetectedPattern(pattern: ScamPattern) {
        _detectedPatterns.value = _detectedPatterns.value + pattern
    }

    fun setCallState(state: CallState) {
        _callState.value = state
    }

    /** Clear all ephemeral data when call ends */
    fun clearAll() {
        currentCall = null
        callerNumber = ""
        isUnknownCaller = false
        isSpeakerphoneActive = false
        _scamAlerts.value = emptyList()
        _riskScore.value = 0.0f
        _liveTranscript.value = ""
        _detectedPatterns.value = emptySet()
        _callState.value = CallState.IDLE
    }
}

data class ScamAlert(
    val timestamp: Long = System.currentTimeMillis(),
    val pattern: ScamPattern,
    val severity: AlertSeverity,
    val excerpt: String // Short anonymized text snippet
)

enum class ScamPattern(val displayName: String, val description: String) {
    OTP_REQUEST("OTP Request", "Caller asked for one-time password or verification code"),
    BANK_IMPERSONATION("Bank Impersonation", "Caller claiming to be from a bank"),
    GOVERNMENT_IMPERSONATION("Gov. Impersonation", "Caller claiming to be police, CBI, or government"),
    URGENCY_TACTIC("Urgency/Fear Tactic", "Pressure tactics: 'act now', 'arrest warrant', 'account frozen'"),
    PRIZE_SCAM("Prize/Lottery Scam", "Claims of winning a prize or lottery"),
    TECH_SUPPORT("Tech Support Scam", "Fake tech support claiming device is compromised"),
    REFUND_SCAM("Refund Scam", "False claims of pending refund requiring action"),
    PERSONAL_INFO_REQUEST("Personal Info Request", "Requests for Aadhaar, PAN, passwords or similar"),
    KYC_SCAM("KYC Update Scam", "Fake KYC update requests threatening account closure"),
    REMOTE_ACCESS("Remote Access Request", "Requesting to install apps or share screens")
}

enum class AlertSeverity(val label: String) {
    LOW("Low Risk"),
    MEDIUM("Medium Risk"),
    HIGH("High Risk"),
    CRITICAL("CRITICAL - Likely Scam")
}

enum class CallState {
    IDLE, RINGING, ACTIVE, ANALYZING, ENDED
}
