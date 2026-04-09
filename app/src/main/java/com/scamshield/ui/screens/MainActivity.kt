package com.scamshield.ui.screens

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scamshield.ui.theme.ScamShieldTheme
import com.scamshield.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val requestDefaultDialer = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "Default dialer result: ${result.resultCode}")
    }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d(TAG, "Permissions result: $permissions")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ScamShieldTheme {
                val viewModel: MainViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()

                MainScreen(
                    uiState = uiState,
                    onRequestDefaultDialer = { requestToBeDefaultDialer() },
                    onRequestPermissions = { requestNecessaryPermissions() },
                    onDialNumber = { number -> dialNumber(number) },
                    onSettingsChanged = viewModel::updateSettings
                )
            }
        }

        // Handle intent if launched via tel: URI
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_DIAL || intent?.action == Intent.ACTION_VIEW) {
            val number = intent.data?.schemeSpecificPart
            if (!number.isNullOrBlank()) {
                Log.d(TAG, "Launched with number: $number")
            }
        }
    }

    private fun requestToBeDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true &&
                !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
            ) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                requestDefaultDialer.launch(intent)
            }
        } else {
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            }
            requestDefaultDialer.launch(intent)
        }
    }

    private fun requestNecessaryPermissions() {
        val permissions = mutableListOf(
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.CALL_PHONE,
            android.Manifest.permission.ANSWER_PHONE_CALLS,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissions.launch(permissions.toTypedArray())
    }

    private fun dialNumber(number: String) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$number")
        }
        startActivity(intent)
    }
}
