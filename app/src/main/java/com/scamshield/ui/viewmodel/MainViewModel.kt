package com.scamshield.ui.viewmodel

import android.app.Application
import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.telecom.TelecomManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scamshield.utils.PhoneStateHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    init {
        checkDefaultDialerStatus()
    }

    private fun checkDefaultDialerStatus() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val isDefault = isDefaultDialer(context)
            val hasPermissions = hasRequiredPermissions(context)
            _uiState.value = _uiState.value.copy(
                isDefaultDialer = isDefault,
                hasRequiredPermissions = hasPermissions
            )
        }
    }

    private fun isDefaultDialer(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) ?: false
        } else {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecomManager.defaultDialerPackage == context.packageName
        }
    }

    private fun hasRequiredPermissions(context: Context): Boolean {
        val permissions = listOf(
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_CONTACTS,
        )
        return permissions.all {
            context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    fun updateSettings(settings: AppSettings) {
        _uiState.value = _uiState.value.copy(settings = settings)
    }

    fun refresh() {
        checkDefaultDialerStatus()
    }
}

data class MainUiState(
    val isDefaultDialer: Boolean = false,
    val hasRequiredPermissions: Boolean = false,
    val settings: AppSettings = AppSettings()
)

data class AppSettings(
    val autoSpeakerphone: Boolean = true,
    val vibrationAlerts: Boolean = true,
    val showLiveTranscript: Boolean = false, // Hidden by default for privacy
    val sensitivityLevel: Float = 0.65f
)
