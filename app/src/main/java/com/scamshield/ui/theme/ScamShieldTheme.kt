package com.scamshield.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    secondary = Color(0xFF81C784),
    tertiary = Color(0xFFFFB74D),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    error = Color(0xFFEF9A9A)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1565C0),
    secondary = Color(0xFF2E7D32),
    tertiary = Color(0xFFE65100),
    background = Color(0xFFF8F9FA),
    surface = Color(0xFFFFFFFF),
    error = Color(0xFFD32F2F)
)

@Composable
fun ScamShieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography(),
        content = content
    )
}
