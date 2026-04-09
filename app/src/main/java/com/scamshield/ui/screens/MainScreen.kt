package com.scamshield.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scamshield.ui.viewmodel.AppSettings
import com.scamshield.ui.viewmodel.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: MainUiState,
    onRequestDefaultDialer: () -> Unit,
    onRequestPermissions: () -> Unit,
    onDialNumber: (String) -> Unit,
    onSettingsChanged: (AppSettings) -> Unit
) {
    var dialpadNumber by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("ScamShield", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Dialpad, null) },
                    label = { Text("Dialpad") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Shield, null) },
                    label = { Text("Status") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> DialpadTab(
                number = dialpadNumber,
                onNumberChange = { dialpadNumber = it },
                onDial = { onDialNumber(dialpadNumber) },
                modifier = Modifier.padding(padding)
            )
            1 -> StatusTab(
                uiState = uiState,
                onRequestDefaultDialer = onRequestDefaultDialer,
                onRequestPermissions = onRequestPermissions,
                modifier = Modifier.padding(padding)
            )
            2 -> SettingsTab(
                settings = uiState.settings,
                onSettingsChanged = onSettingsChanged,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
fun DialpadTab(
    number: String,
    onNumberChange: (String) -> Unit,
    onDial: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = number,
            onValueChange = onNumberChange,
            label = { Text("Phone Number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (number.isNotEmpty()) {
                    IconButton(onClick = {
                        if (number.isNotEmpty()) onNumberChange(number.dropLast(1))
                    }) {
                        Icon(Icons.Default.Backspace, "Delete")
                    }
                }
            }
        )

        Spacer(Modifier.height(24.dp))

        // Dialpad grid
        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("*", "0", "#")
        )

        keys.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                row.forEach { key ->
                    FilledTonalButton(
                        onClick = { onNumberChange(number + key) },
                        modifier = Modifier.size(80.dp),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(key, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        FloatingActionButton(
            onClick = { if (number.isNotEmpty()) onDial() },
            containerColor = Color(0xFF2E7D32),
            modifier = Modifier.size(70.dp)
        ) {
            Icon(Icons.Default.Call, "Call", modifier = Modifier.size(32.dp), tint = Color.White)
        }
    }
}

@Composable
fun StatusTab(
    uiState: MainUiState,
    onRequestDefaultDialer: () -> Unit,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Protection Status", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        StatusCard(
            title = "Default Dialer",
            description = if (uiState.isDefaultDialer) "ScamShield is your default dialer" else "Set ScamShield as default dialer for full protection",
            isOk = uiState.isDefaultDialer,
            actionLabel = if (!uiState.isDefaultDialer) "Set as Default" else null,
            onAction = onRequestDefaultDialer
        )

        StatusCard(
            title = "Permissions",
            description = if (uiState.hasRequiredPermissions) "All required permissions granted" else "Some permissions are missing",
            isOk = uiState.hasRequiredPermissions,
            actionLabel = if (!uiState.hasRequiredPermissions) "Grant Permissions" else null,
            onAction = onRequestPermissions
        )

        StatusCard(
            title = "On-Device AI",
            description = "All detection runs locally. No data leaves your device.",
            isOk = true,
            actionLabel = null,
            onAction = {}
        )

        StatusCard(
            title = "Data Privacy",
            description = "Zero data retention. Audio discarded after each chunk.",
            isOk = true,
            actionLabel = null,
            onAction = {}
        )

        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("How it works", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                listOf(
                    "📞 Incoming call detected",
                    "🔍 Unknown number check",
                    "🔊 Auto speakerphone (unknown callers)",
                    "🎙️ Mic captures live audio",
                    "🗣️ On-device speech-to-text",
                    "🤖 AI scam pattern detection",
                    "⚠️ Real-time alert if scam detected",
                    "🗑️ All data discarded after call"
                ).forEach { step ->
                    Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
fun StatusCard(
    title: String,
    description: String,
    isOk: Boolean,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isOk) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isOk) Color(0xFF2E7D32) else Color(0xFFE65100),
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            if (actionLabel != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun SettingsTab(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        SettingsToggle(
            title = "Auto Speakerphone",
            subtitle = "Automatically enable speaker for unknown callers",
            checked = settings.autoSpeakerphone,
            onCheckedChange = { onSettingsChanged(settings.copy(autoSpeakerphone = it)) }
        )

        SettingsToggle(
            title = "Vibration Alerts",
            subtitle = "Vibrate when scam pattern detected",
            checked = settings.vibrationAlerts,
            onCheckedChange = { onSettingsChanged(settings.copy(vibrationAlerts = it)) }
        )

        SettingsToggle(
            title = "Live Transcript (Debug)",
            subtitle = "Show live transcription during call. Ephemeral only.",
            checked = settings.showLiveTranscript,
            onCheckedChange = { onSettingsChanged(settings.copy(showLiveTranscript = it)) }
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Detection Sensitivity", fontWeight = FontWeight.SemiBold)
                Text(
                    "Current: ${
                        when {
                            settings.sensitivityLevel >= 0.8f -> "High"
                            settings.sensitivityLevel >= 0.5f -> "Medium"
                            else -> "Low"
                        }
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Slider(
                    value = settings.sensitivityLevel,
                    onValueChange = { onSettingsChanged(settings.copy(sensitivityLevel = it)) },
                    valueRange = 0.3f..0.9f,
                    steps = 5
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Fewer alerts", style = MaterialTheme.typography.labelSmall)
                    Text("More alerts", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5))
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = Color(0xFF6A1B9A))
                    Spacer(Modifier.width(8.dp))
                    Text("Privacy Guarantee", fontWeight = FontWeight.SemiBold, color = Color(0xFF6A1B9A))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "ScamShield processes all audio on-device only. No recordings are made. No data is transmitted. All transcripts are discarded immediately after analysis.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
