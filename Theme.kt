package com.headsetconnector.ui

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.headsetconnector.data.AppTheme

// Тёмная «нормальная» тема — значения по умолчанию.
val Card = Color(0xFF151B2E)
val Good = Color(0xFF7DE2A8)
val Warn = Color(0xFFFFC46B)
val Bad = Color(0xFFFF7B7B)

// Динамические цвета: их меняет HeadsetTheme в зависимости от выбранной темы.
var Bg by mutableStateOf(Color(0xFF0B1020))
var CardHi by mutableStateOf(Color(0xFF1D2540))
var Accent by mutableStateOf(Color(0xFF6C8CFF))
var Accent2 by mutableStateOf(Color(0xFF9DB2FF))
var TextPrimary by mutableStateOf(Color(0xFFEAEEF9))
var TextSecondary by mutableStateOf(Color(0xFF8B95AD))

@Composable
fun HeadsetTheme(theme: AppTheme = AppTheme.NORMAL, content: @Composable () -> Unit) {
    val context = LocalContext.current

    val materialYou = theme == AppTheme.MATERIAL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val accent: Color
    val accent2: Color
    val bg: Color
    val cardHi: Color
    val textPrimary: Color
    val textSecondary: Color

    if (materialYou) {
        val scheme = dynamicDarkColorScheme(context)
        accent = scheme.primary
        accent2 = scheme.secondary
        bg = Color(0xFF0E1116)
        cardHi = scheme.surfaceVariant
        textPrimary = scheme.onBackground
        textSecondary = scheme.onSurfaceVariant
    } else {
        accent = Color(0xFF6C8CFF)
        accent2 = Color(0xFF9DB2FF)
        bg = Color(0xFF0B1020)
        cardHi = Color(0xFF1D2540)
        textPrimary = Color(0xFFEAEEF9)
        textSecondary = Color(0xFF8B95AD)
    }

    SideEffect {
        Bg = bg
        CardHi = cardHi
        Accent = accent
        Accent2 = accent2
        TextPrimary = textPrimary
        TextSecondary = textSecondary
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            secondary = accent2,
            background = bg,
            onBackground = textPrimary,
            surface = Card,
            onSurface = textPrimary,
            surfaceVariant = cardHi,
            onSurfaceVariant = textSecondary,
            error = Bad,
        ),
        content = content,
    )
}
