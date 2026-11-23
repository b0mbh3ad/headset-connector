package com.headsetconnector.data

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
    "orange" to "#fb8c00",
    "amber" to "#fdd835",
    "green" to "#66bb6a",
    "teal" to "#26a69a",
    "blue" to "#42a5f5",
    "indigo" to "#5c6bc0",
    "purple" to "#ab47bc",
    "pink" to "#ec407a",
)

fun accentHexToKey(hex: String?): String? =
    ACCENT_OPTIONS.firstOrNull { it.second.equals(hex, ignoreCase = true) }?.first
