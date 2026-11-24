package com.headsetconnector.ui

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.headsetconnector.data.AgentApi
import com.headsetconnector.data.AgentApi.DeviceInfo
import com.headsetconnector.data.AgentApi.MediaItem
import com.headsetconnector.net.HeadsetFinder
import kotlinx.coroutines.launch

@Composable
fun HeadsetScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var host by remember { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }
    var device by remember { mutableStateOf<DeviceInfo?>(null) }
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var mode by remember { mutableStateOf("images") }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadMedia() {
        val h = host
        val p = pin
        if (h.isBlank() || p.isBlank()) return
        scope.launch {
            busy = "media"
            try {
                items = AgentApi.media(h, p, mode)
            } catch (e: Exception) {
                items = emptyList()
                error = "Не удалось получить медиа"
            } finally {
                busy = null
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text("Шлем", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Подключите шлем, где запущен HeadsetConnector", color = TextSecondary, fontSize = 12.sp)
                Text(
                    "Введите 6-значный код с экрана шлема — приложение само найдёт его в этой сети",
                    color = TextSecondary,
                    fontSize = 11.sp,
                )
            }
        }

        item {
            CardBox {
                SectionLabel("ПОДКЛЮЧЕНИЕ")
                Spacer(Modifier.height(8.dp))
                Field(
                    value = pin,
                    onValueChange = { value -> pin = value.filter { it.isDigit() }.take(6) },
                    label = "Код со шлема",
                    placeholder = "6 цифр",
                    number = true,
                    leading = Icons.Rounded.Lock,
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = if (busy == "device") "Ищу шлем…" else "Подключить",
                    modifier = Modifier.fillMaxWidth(),
                    loading = busy == "device",
                    enabled = pin.length >= 4,
                    onClick = {
                        scope.launch {
                            busy = "device"
                            error = null
                            try {
                                val ip = HeadsetFinder.findAgent(pin)
                                if (ip == null) {
                                    error = "Шлем не найден в этой сети. Убедитесь, что он включён."
                                } else {
                                    host = ip
                                    device = AgentApi.device(ip, pin)
                                    items = emptyList()
                                }
                            } catch (e: Exception) {
                                error = "Не удалось подключиться. Проверьте код."
                                device = null
                            } finally {
                                busy = null
                            }
                        }
                    },
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Hint(it)
                }
            }
        }

        device?.let { d ->
            item {
                CardBox {
                    SectionLabel("СОСТОЯНИЕ")
                    Spacer(Modifier.height(8.dp))
                    Text(d.model, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    val level = if (d.battery >= 0) "${d.battery}%" else "?"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.VideogameAsset, null, tint = Good, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Заряд: $level" + if (d.charging) " · заряжается" else "",
                            color = TextPrimary,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            item {
                CardBox {
                    SectionLabel("ФОТО И ВИДЕО С ШЛЕМА")
                    Spacer(Modifier.height(8.dp))
                    ChipWrap {
                        Chip("Фото", mode == "images") { mode = "images"; loadMedia() }
                        Chip("Видео", mode == "videos") { mode = "videos"; loadMedia() }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (busy == "media") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Accent2, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Hint("Загружаю…")
                        }
                    } else if (items.isEmpty()) {
                        Hint("Ничего не найдено. Нажмите «Фото» или «Видео».")
                    } else {
                        items.take(60).forEach { item ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.PhotoLibrary, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(formatWhen(item.date), color = TextSecondary, fontSize = 10.sp)
                                }
                                GhostButton(
                                    text = "Скачать",
                                    modifier = Modifier.height(36.dp),
                                    onClick = {
                                        scope.launch {
                                            try {
                                                val bytes = AgentApi.download(host, pin, item.id)
                                                saveToDownloads(context, bytes, item.name)
                                            } catch (e: Exception) {
                                                error = "Не удалось скачать ${item.name}"
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun saveToDownloads(context: android.content.Context, bytes: ByteArray, name: String) {
    if (Build.VERSION.SDK_INT >= 29) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        }
    }
}
