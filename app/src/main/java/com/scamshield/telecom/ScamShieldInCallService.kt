package com.scamshield.telecom

import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import com.scamshield.service.ScamDetectionService
import com.scamshield.ui.screens.InCallActivity
import com.scamshield.ui.screens.IncomingCallActivity
import com.scamshield.utils.ContactsHelper
import com.scamshield.utils.PhoneStateHolder

class ScamShieldInCallService : InCallService() {

    companion object {
        private const val TAG = "ScamShieldInCallService"
        var instance: ScamShieldInCallService? = null
    }

    private val callCallbacks = mutableMapOf<Call, ScamCallCallback>()

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "InCallService created")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "InCallService destroyed")
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "Call added: ${call.state}")

        PhoneStateHolder.currentCall = call
        val callback = ScamCallCallback(this, call)
        callCallbacks[call] = callback
        call.registerCallback(callback)

        handleCallState(call)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "Call removed")

        callCallbacks[call]?.let { callback ->
            call.unregisterCallback(callback)
            callCallbacks.remove(call)
        }

        stopScamDetection()
        PhoneStateHolder.currentCall = null

        // Show post-call summary
        val intent = Intent(this, com.scamshield.ui.screens.PostCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun handleCallState(call: Call) {
        val details = call.details
        val number = details?.handle?.schemeSpecificPart ?: "Unknown"
        val isIncoming = call.state == Call.STATE_RINGING

        Log.d(TAG, "Handling call state: ${call.state}, number: $number")

        if (isIncoming) {
            val isKnown = ContactsHelper.isKnownContact(applicationContext, number)
            PhoneStateHolder.isUnknownCaller = !isKnown
            PhoneStateHolder.callerNumber = number

            // Launch incoming call UI
            val intent = Intent(this, IncomingCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("caller_number", number)
                putExtra("is_unknown", !isKnown)
            }
            startActivity(intent)

        } else if (call.state == Call.STATE_ACTIVE) {
            onCallAnswered(call)
        }
    }

    fun onCallAnswered(call: Call) {
        val isUnknown = PhoneStateHolder.isUnknownCaller
        Log.d(TAG, "Call answered. Unknown: $isUnknown")

        // Launch in-call UI
        val intent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("is_unknown", isUnknown)
            putExtra("caller_number", PhoneStateHolder.callerNumber)
        }
        startActivity(intent)

        if (isUnknown) {
            // Enable speakerphone automatically for unknown callers
            enableSpeakerphone(call)
            // Start scam detection pipeline
            startScamDetection()
        }
    }

    private fun enableSpeakerphone(call: Call) {
        try {
            // Use AudioManager to route to speaker
            val audioManager = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
            audioManager.mode = android.media.AudioManager.MODE_IN_CALL
            audioManager.isSpeakerphoneOn = true
            PhoneStateHolder.isSpeakerphoneActive = true
            Log.d(TAG, "Speakerphone enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable speakerphone: ${e.message}")
        }
    }

    private fun startScamDetection() {
        Log.d(TAG, "Starting scam detection service")
        val intent = Intent(this, ScamDetectionService::class.java).apply {
            action = ScamDetectionService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopScamDetection() {
        Log.d(TAG, "Stopping scam detection service")
        val intent = Intent(this, ScamDetectionService::class.java).apply {
            action = ScamDetectionService.ACTION_STOP
        }
        startService(intent)
    }

    fun answerCall() {
        PhoneStateHolder.currentCall?.answer(0)
    }

    fun rejectCall() {
        PhoneStateHolder.currentCall?.reject(false, null)
    }

    fun endCall() {
        PhoneStateHolder.currentCall?.disconnect()
    }

    fun toggleSpeaker(on: Boolean) {
        val audioManager = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        audioManager.isSpeakerphoneOn = on
        PhoneStateHolder.isSpeakerphoneActive = on
    }

    fun toggleMute(muted: Boolean) {
        PhoneStateHolder.currentCall?.let { call ->
            // Use telecom API for mute
        }
        val audioManager = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        audioManager.isMicrophoneMute = muted
    }
}
