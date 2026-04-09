package com.scamshield.ui.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scamshield.ui.theme.ScamShieldTheme
import com.scamshield.utils.AlertSeverity
import com.scamshield.utils.PhoneStateHolder
import com.scamshield.utils.ScamAlert
import com.scamshield.utils.ScamPattern

class PostCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Snapshot call data before clearing
        val callerNumber = PhoneStateHolder.callerNumber
        val finalRisk = PhoneStateHolder.riskScore.value
        val alerts = PhoneStateHolder.scamAlerts.value.toList()
        val patterns = PhoneStateHolder.detectedPatterns.value.toSet()

        // Clear all ephemeral data immediately
        PhoneStateHolder.clearAll()

        setContent {
            ScamShieldTheme {
                PostCallScreen(
                    callerNumber = callerNumber,
                    finalRiskScore = finalRisk,
                    alerts = alerts,
                    patterns = patterns,
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCallScreen(
    callerNumber: String,
    finalRiskScore: Float,
    alerts: List<ScamAlert>,
    patterns: Set<ScamPattern>,
    onDismiss: () -> Unit
) {
    val overallSeverity = when {
        finalRiskScore >= 0.85f -> AlertSeverity.CRITICAL
        finalRiskScore >= 0.65f -> AlertSeverity.HIGH
        finalRiskScore >= 0.40f -> AlertSeverity.MEDIUM
        else -> AlertSeverity.LOW
    }

    val headerColor = when (overallSeverity) {
        AlertSeverity.CRITICAL -> Color(0xFFB71C1C)
        AlertSeverity.HIGH -> Color(0xFFBF360C)
        AlertSeverity.MEDIUM -> Color(0xFFE65100)
        AlertSeverity.LOW -> Color(0xFF1B5E20)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Call Summary") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = headerColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            when (overallSeverity) {
                                AlertSeverity.CRITICAL, AlertSeverity.HIGH -> Icons.Default.GppBad
                                AlertSeverity.MEDIUM -> Icons.Default.GppMaybe
                                AlertSeverity.LOW -> Icons.Default.GppGood
                            },
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(overallSeverity.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(callerNumber, color = Color.White.copy(alpha = 0.8f))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Risk Score: ${(finalRiskScore * 100).toInt()}%",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // Privacy confirmation
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "All audio and transcripts have been permanently discarded. Nothing was stored or transmitted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF1B5E20)
                        )
                    }
                }
            }

            // Detected patterns
            if (patterns.isNotEmpty()) {
                item {
                    Text("Detected Scam Patterns", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                items(patterns.toList()) { pattern ->
                    PatternCard(pattern = pattern)
                }
            } else if (finalRiskScore < 0.4f) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("No Scam Patterns Detected", fontWeight = FontWeight.SemiBold)
                                Text("This call appeared safe", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            // Alerts timeline
            if (alerts.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text("Alert Timeline", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                items(alerts) { alert ->
                    AlertTimelineCard(alert = alert)
                }
            }

            // Advice
            if (overallSeverity == AlertSeverity.HIGH || overallSeverity == AlertSeverity.CRITICAL) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, tint = Color(0xFFE65100))
                                Spacer(Modifier.width(8.dp))
                                Text("What to do", fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                            }
                            Spacer(Modifier.height(8.dp))
                            listOf(
                                "Do NOT share OTPs, PINs, or passwords with anyone",
                                "Do NOT transfer money on a caller's request",
                                "Call your bank directly using the number on their official website",
                                "Report scam calls to cybercrime.gov.in or dial 1930",
                                "No government agency will ever demand immediate payment over phone"
                            ).forEachIndexed { i, tip ->
                                Text("${i + 1}. $tip", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
fun PatternCard(pattern: ScamPattern) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFCE4EC))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = Color(0xFFC62828), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(pattern.displayName, fontWeight = FontWeight.SemiBold, color = Color(0xFFC62828))
            }
            Spacer(Modifier.height(4.dp))
            Text(pattern.description, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
        }
    }
}

@Composable
fun AlertTimelineCard(alert: ScamAlert) {
    val color = when (alert.severity) {
        AlertSeverity.CRITICAL -> Color(0xFFD32F2F)
        AlertSeverity.HIGH -> Color(0xFFE64A19)
        AlertSeverity.MEDIUM -> Color(0xFFF57C00)
        AlertSeverity.LOW -> Color(0xFF388E3C)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color, CircleShape)
            )
        }
        Spacer(Modifier.width(12.dp))
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(alert.pattern.displayName, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Text(alert.excerpt, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Text(alert.severity.label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private val CircleShape = RoundedCornerShape(50)
