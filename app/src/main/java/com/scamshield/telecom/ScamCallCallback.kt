package com.scamshield.telecom

import android.telecom.Call
import android.util.Log

class ScamCallCallback(
    private val service: ScamShieldInCallService,
    private val call: Call
) : Call.Callback() {

    companion object {
        private const val TAG = "ScamCallCallback"
    }

    override fun onStateChanged(call: Call, state: Int) {
        super.onStateChanged(call, state)
        Log.d(TAG, "Call state changed: $state")

        when (state) {
            Call.STATE_ACTIVE -> service.onCallAnswered(call)
            Call.STATE_DISCONNECTED -> {
                Log.d(TAG, "Call disconnected")
            }
            Call.STATE_RINGING -> {
                Log.d(TAG, "Call ringing")
            }
            Call.STATE_HOLDING -> {
                Log.d(TAG, "Call on hold")
            }
        }
    }

    override fun onDetailsChanged(call: Call, details: Call.Details) {
        super.onDetailsChanged(call, details)
        Log.d(TAG, "Call details changed")
    }
}
