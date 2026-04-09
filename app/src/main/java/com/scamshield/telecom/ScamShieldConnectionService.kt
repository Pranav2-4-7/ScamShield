package com.scamshield.telecom

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.util.Log

class ScamShieldConnectionService : ConnectionService() {

    companion object {
        private const val TAG = "ScamShieldConnectionSvc"
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.d(TAG, "Creating outgoing connection")
        return ScamShieldConnection()
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.d(TAG, "Creating incoming connection")
        return ScamShieldConnection()
    }
}
