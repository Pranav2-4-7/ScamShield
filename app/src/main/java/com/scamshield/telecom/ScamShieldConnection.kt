package com.scamshield.telecom

import android.telecom.Connection
import android.telecom.DisconnectCause
import android.util.Log

class ScamShieldConnection : Connection() {

    companion object {
        private const val TAG = "ScamShieldConnection"
    }

    init {
        connectionCapabilities = CAPABILITY_HOLD or
                CAPABILITY_SUPPORT_HOLD or
                CAPABILITY_MUTE or
                CAPABILITY_RESPOND_VIA_TEXT
    }

    override fun onAnswer() {
        super.onAnswer()
        Log.d(TAG, "Connection answered")
        setActive()
    }

    override fun onReject() {
        super.onReject()
        Log.d(TAG, "Connection rejected")
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        destroy()
    }

    override fun onDisconnect() {
        super.onDisconnect()
        Log.d(TAG, "Connection disconnected")
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
    }

    override fun onHold() {
        super.onHold()
        setOnHold()
    }

    override fun onUnhold() {
        super.onUnhold()
        setActive()
    }
}
