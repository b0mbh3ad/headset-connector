package com.headsetconnector.data

import androidx.compose.ui.graphics.Color

data class HeadsetOption(val key: String, val label: String)

// Список поддерживаемых шлемов. Старые модели сознательно не добавляем.
val HEADSET_OPTIONS = listOf(
    HeadsetOption("quest1", "Quest 1"),
    HeadsetOption("quest2", "Quest 2"),
    HeadsetOption("quest3", "Quest 3"),
    HeadsetOption("questpro", "Quest Pro"),
    HeadsetOption("piconeo3", "Pico Neo 3"),
    HeadsetOption("pico4", "Pico 4"),
    HeadsetOption("pico4ultra", "Pico 4 Ultra"),
    HeadsetOption("pico4pro", "Pico 4 Pro"),
)

// Акцентные цвета профиля. Ключ отправляется на сервер, значение — hex.
val ACCENT_OPTIONS = listOf(
    "red" to "#ef5350",
    "coral" to "#ff7043",
    "orange" to "#fb8c00",
    "amber" to "#fdd835",
    "lime" to "#9ccc65",
    "green" to "#66bb6a",
    "mint" to "#4dd0a8",
    "teal" to "#26a69a",
    "cyan" to "#26c6da",
    "blue" to "#42a5f5",
    "indigo" to "#5c6bc0",
    "violet" to "#7e57c2",
    "purple" to "#ab47bc",
    "pink" to "#ec407a",
    "rose" to "#f06292",
)

fun accentHexToKey(hex: String?): String? =
    ACCENT_OPTIONS.firstOrNull { it.second.equals(hex, ignoreCase = true) }?.first

/** Приводит ввод пользователя к #rrggbb. Пустая строка допустима (сброс). */
fun normalizeAccentHex(input: String?): String? {
    val clean = (input ?: "").trim().removePrefix("#")
    if (clean.length != 6) return null
    if (!clean.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return "#" + clean.lowercase()
}

/** Hex акцента профиля → Compose Color (поддерживает любой #rrggbb). */
fun accentColor(hex: String?): Color? {
    val value = normalizeAccentHex(hex) ?: return null
    val clean = value.removePrefix("#")
    val r = clean.substring(0, 2).toInt(16) / 255f
    val g = clean.substring(2, 4).toInt(16) / 255f
    val b = clean.substring(4, 6).toInt(16) / 255f
    return Color(r, g, b)
}
