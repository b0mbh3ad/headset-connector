package com.headsetconnector.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// Классическая тёмная тема. Никакого Material You и динамических обоев:
// фон и карточки всегда одинаковые, меняется только акцент пользователя.
val Card = Color(0xFF151B2E)
val Good = Color(0xFF7DE2A8)
val Warn = Color(0xFFFFC46B)
val Bad = Color(0xFFFF7B7B)

var Bg by mutableStateOf(Color(0xFF0B1020))
var CardHi by mutableStateOf(Color(0xFF1D2540))
var Accent by mutableStateOf(Color(0xFF6C8CFF))
var Accent2 by mutableStateOf(Color(0xFF9DB2FF))
var TextPrimary by mutableStateOf(Color(0xFFEAEEF9))
var TextSecondary by mutableStateOf(Color(0xFF8B95AD))

/** Цвет текста/иконки поверх акцентной заливки — всегда читаемый. */
fun Color.onAccent(): Color {
    val luminance = 0.2126f * red + 0.7152f * green + 0.0722f * blue
    return if (luminance > 0.55f) Color(0xFF11151C) else Color.White
}

@Composable
fun HeadsetTheme(
    accent: Color = Accent,
    content: @Composable () -> Unit,
) {
    val accent2 = lerp(accent, Color.White, 0.42f)
    val onPrimary = accent.onAccent()

    SideEffect {
        Bg = Color(0xFF0B1020)
        CardHi = Color(0xFF1D2540)
        Accent = accent
        Accent2 = accent2
        TextPrimary = Color(0xFFEAEEF9)
        TextSecondary = Color(0xFF8B95AD)
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent,
            onPrimary = onPrimary,
            secondary = accent2,
            onSecondary = onPrimary,
            background = Bg,
            onBackground = TextPrimary,
            surface = Card,
            onSurface = TextPrimary,
            surfaceVariant = CardHi,
            onSurfaceVariant = TextSecondary,
            error = Bad,
            onError = Color.White,
        ),
        content = content,
    )
}
