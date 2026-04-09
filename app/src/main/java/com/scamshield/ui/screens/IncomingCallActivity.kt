package com.scamshield.ui.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scamshield.telecom.ScamShieldInCallService
import com.scamshield.ui.theme.ScamShieldTheme

class IncomingCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callerNumber = intent.getStringExtra("caller_number") ?: "Unknown"
        val isUnknown = intent.getBooleanExtra("is_unknown", false)

        setContent {
            ScamShieldTheme {
                IncomingCallScreen(
                    callerNumber = callerNumber,
                    isUnknown = isUnknown,
                    onAnswer = {
                        ScamShieldInCallService.instance?.answerCall()
                        finish()
                    },
                    onDecline = {
                        ScamShieldInCallService.instance?.rejectCall()
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun IncomingCallScreen(
    callerNumber: String,
    isUnknown: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit
) {
    val bgGradient = if (isUnknown) {
        Brush.verticalGradient(listOf(Color(0xFF1A237E), Color(0xFF311B92)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF1B5E20), Color(0xFF2E7D32)))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            Text("Incoming Call", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)

            // Avatar
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    tint = Color.White
                )
            }

            Text(
                text = callerNumber,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            if (isUnknown) {
                Card(
                    shape = RoundedCornerShape(50),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFF6F00))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("Unknown Caller", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }

                Text(
                    text = "ScamShield will monitor this call\nand alert you if scam patterns are detected",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            } else {
                Card(
                    shape = RoundedCornerShape(50),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("Known Contact", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            // Answer / Decline buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(80.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Decline
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = onDecline,
                        containerColor = Color(0xFFD32F2F),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(Icons.Default.CallEnd, "Decline", modifier = Modifier.size(32.dp), tint = Color.White)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Decline", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }

                // Answer
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = onAnswer,
                        containerColor = Color(0xFF2E7D32),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(Icons.Default.Call, "Answer", modifier = Modifier.size(32.dp), tint = Color.White)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Answer", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }
        }
    }
}
