package com.scamshield.ui.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scamshield.telecom.ScamShieldInCallService
import com.scamshield.ui.theme.ScamShieldTheme
import com.scamshield.utils.AlertSeverity
import com.scamshield.utils.PhoneStateHolder
import com.scamshield.utils.ScamAlert
import kotlinx.coroutines.delay

class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isUnknown = intent.getBooleanExtra("is_unknown", false)
        val callerNumber = intent.getStringExtra("caller_number") ?: "Unknown"

        setContent {
            ScamShieldTheme {
                InCallScreen(
                    callerNumber = callerNumber,
                    isUnknown = isUnknown,
                    onEndCall = {
                        ScamShieldInCallService.instance?.endCall()
                        finish()
                    },
                    onToggleSpeaker = { on -> ScamShieldInCallService.instance?.toggleSpeaker(on) },
                    onToggleMute = { muted -> ScamShieldInCallService.instance?.toggleMute(muted) }
                )
            }
        }
    }
}

@Composable
fun InCallScreen(
    callerNumber: String,
    isUnknown: Boolean,
    onEndCall: () -> Unit,
    onToggleSpeaker: (Boolean) -> Unit,
    onToggleMute: (Boolean) -> Unit
) {
    val riskScore by PhoneStateHolder.riskScore.collectAsState()
    val alerts by PhoneStateHolder.scamAlerts.collectAsState()
    val liveTranscript by PhoneStateHolder.liveTranscript.collectAsState()
    val detectedPatterns by PhoneStateHolder.detectedPatterns.collectAsState()

    var isSpeakerOn by remember { mutableStateOf(isUnknown) }
    var isMuted by remember { mutableStateOf(false) }
    var callDurationSecs by remember { mutableIntStateOf(0) }
    var showTranscript by remember { mutableStateOf(false) }

    // Call duration timer
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            callDurationSecs++
        }
    }

    val bgColor = when {
        riskScore >= 0.85f -> Color(0xFFB71C1C)
        riskScore >= 0.65f -> Color(0xFFBF360C)
        riskScore >= 0.40f -> Color(0xFFE65100)
        else -> Color(0xFF1A237E)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        // Caller info
        Text(callerNumber, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            formatDuration(callDurationSecs),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp
        )

        Spacer(Modifier.height(16.dp))

        // Risk gauge
        if (isUnknown) {
            RiskGauge(riskScore = riskScore)
            Spacer(Modifier.height(12.dp))
        }

        // Live alerts list
        if (alerts.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(alerts.reversed()) { alert ->
                    AlertCard(alert = alert)
                }
            }
        } else if (isUnknown) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Shield, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Monitoring call...", color = Color.White.copy(alpha = 0.7f))
                    Text("No scam patterns detected yet", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        // Live transcript toggle
        if (isUnknown && liveTranscript.isNotEmpty()) {
            TextButton(onClick = { showTranscript = !showTranscript }) {
                Text(
                    if (showTranscript) "Hide Transcript" else "Show Live Transcript",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
            AnimatedVisibility(visible = showTranscript) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = liveTranscript,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Call controls row
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Spacer(Modifier.weight(1f))

            // Mute
            CallControlButton(
                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                label = if (isMuted) "Unmute" else "Mute",
                active = isMuted,
                onClick = {
                    isMuted = !isMuted
                    onToggleMute(isMuted)
                }
            )

            // End call
            FloatingActionButton(
                onClick = onEndCall,
                containerColor = Color(0xFFD32F2F),
                modifier = Modifier.size(72.dp)
            ) {
                Icon(Icons.Default.CallEnd, "End", modifier = Modifier.size(32.dp), tint = Color.White)
            }

            // Speaker
            CallControlButton(
                icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                label = "Speaker",
                active = isSpeakerOn,
                onClick = {
                    isSpeakerOn = !isSpeakerOn
                    onToggleSpeaker(isSpeakerOn)
                }
            )

            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun RiskGauge(riskScore: Float) {
    val color = when {
        riskScore >= 0.85f -> Color(0xFFFF1744)
        riskScore >= 0.65f -> Color(0xFFFF6D00)
        riskScore >= 0.40f -> Color(0xFFFFD600)
        else -> Color(0xFF69F0AE)
    }

    val label = when {
        riskScore >= 0.85f -> "⛔ CRITICAL RISK"
        riskScore >= 0.65f -> "🔴 HIGH RISK"
        riskScore >= 0.40f -> "🟡 MEDIUM RISK"
        else -> "🟢 LOW RISK"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("${(riskScore * 100).toInt()}%", color = color, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = riskScore,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = color,
                trackColor = Color.White.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
fun AlertCard(alert: ScamAlert) {
    val bgColor = when (alert.severity) {
        AlertSeverity.CRITICAL -> Color(0xFFFF1744)
        AlertSeverity.HIGH -> Color(0xFFFF6D00)
        AlertSeverity.MEDIUM -> Color(0xFFFFD600)
        AlertSeverity.LOW -> Color(0xFF69F0AE)
    }

    val textColor = when (alert.severity) {
        AlertSeverity.MEDIUM -> Color.Black
        AlertSeverity.LOW -> Color.Black
        else -> Color.White
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, null, tint = textColor, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(alert.pattern.displayName, color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(alert.excerpt, color = textColor.copy(alpha = 0.8f), fontSize = 12.sp)
                Text(alert.severity.label, color = textColor.copy(alpha = 0.7f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(52.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (active) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f)
            )
        ) {
            Icon(icon, null, tint = Color.White)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
