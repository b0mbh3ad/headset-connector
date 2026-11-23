package com.headsetconnector.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.headsetconnector.agent.DeviceInfo
import com.headsetconnector.data.ACCENT_OPTIONS
import com.headsetconnector.data.Api
import com.headsetconnector.data.AppTheme
import com.headsetconnector.data.HEADSET_OPTIONS
import com.headsetconnector.data.accentHexToKey
import com.headsetconnector.util.ImageTools
import com.headsetconnector.vm.ConnectorViewModel

@Composable
fun ProfileScreen(vm: ConnectorViewModel) {
    val me by vm.session.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val pin by vm.agentPin.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isHeadset = remember { DeviceInfo.isHeadset() }

    var name by rememberSaveable(me?.displayName) { mutableStateOf(me?.displayName.orEmpty()) }
    var bio by rememberSaveable(me?.bio) { mutableStateOf(me?.bio.orEmpty()) }
    var headsetKey by rememberSaveable(me?.headset) { mutableStateOf(me?.headset.orEmpty()) }
    var accentHex by rememberSaveable(me?.accent) { mutableStateOf(me?.accent.orEmpty()) }
    var showLogout by remember { mutableStateOf(false) }
    var cropSource by remember { mutableStateOf<Bitmap?>(null) }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val decoded = ImageTools.decode(context, uri)
            if (decoded != null) cropSource = decoded else vm.notifyLocal("Не удалось прочитать картинку")
        }
    }

    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val encoded = ImageTools.bannerBase64(context, uri)
            if (encoded != null) vm.saveBanner(encoded) else vm.notifyLocal("Не удалось прочитать картинку")
        }
    }

    val accentKey = accentHexToKey(accentHex)
    val profileChanged = name.trim() != me?.displayName ||
        bio.trim() != me?.bio.orEmpty() ||
        headsetKey != me?.headset ||
        accentHex != me?.accent.orEmpty()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    "Профиль",
                    color = TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                )
                Text("Один аккаунт для Pico и Meta Quest", color = TextSecondary, fontSize = 12.sp)
            }
        }

        if (isHeadset) {
            item {
                CardBox {
                    SectionLabel("ПОДКЛЮЧЕНИЕ С ТЕЛЕФОНА")
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.VideogameAsset, null, tint = Good, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Код для телефона:  ${pin}",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Hint("Откройте приложение на телефоне → вкладка «Шлем» → введите IP шлема и этот код.")
                }
            }
        }

        item {
            CardBox(padding = 0.dp) {
                Box(Modifier.fillMaxWidth().height(120.dp)) {
                    val banner = Api.mediaUrl(me?.banner)
                    if (banner != null) {
                        RemoteImage(banner, Modifier.fillMaxSize())
                    } else {
                        Box(Modifier.fillMaxSize().background(CardHi))
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { bannerPicker.launch("image/*") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.PhotoCamera,
                            "Сменить баннер",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .clickable { avatarPicker.launch("image/*") },
                            contentAlignment = Alignment.BottomEnd,
                        ) {
                            Avatar(me?.avatar, me?.displayName.orEmpty(), 76.dp)
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Accent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.PhotoCamera,
                                    "Сменить аватар",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    me?.displayName.orEmpty(),
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                me?.badge?.let { badge ->
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(Good.copy(alpha = 0.16f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                    ) {
                                        Text(badge, color = Good, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            Text("@${me?.username.orEmpty()}", color = Accent2, fontSize = 13.sp)
                            me?.headsetLabel?.let {
                                Spacer(Modifier.height(3.dp))
                                Text(it, color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                    me?.email?.let { email ->
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Email, null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(email, color = TextSecondary, fontSize = 12.sp)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Field(
                        value = name,
                        onValueChange = { name = it.take(32) },
                        label = "Отображаемое имя",
                        placeholder = "Как вас видят друзья",
                        leading = Icons.Rounded.Edit,
                    )
                    Spacer(Modifier.height(10.dp))
                    Field(
                        value = bio,
                        onValueChange = { bio = it.take(200) },
                        label = "О себе",
                        placeholder = "Пара слов о вас",
                        leading = Icons.Rounded.Badge,
                    )
                }
            }
        }

        item {
            CardBox {
                SectionLabel("ВАШ ШЛЕМ")
                Spacer(Modifier.height(8.dp))
                ChipWrap {
                    HEADSET_OPTIONS.forEach { option ->
                        Chip(
                            text = option.label,
                            selected = headsetKey == option.key,
                            onClick = { headsetKey = option.key },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                SectionLabel("АКЦЕНТНЫЙ ЦВЕТ")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ACCENT_OPTIONS.forEach { (key, hex) ->
                        val color = parseColor(hex) ?: Accent
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (accentKey == key) {
                                        Modifier.border(2.dp, Color.White, CircleShape)
                                    } else {
                                        Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                    },
                                )
                                .clickable { accentHex = hex },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (accentKey == key) {
                                Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            PrimaryButton(
                text = "Сохранить профиль",
                modifier = Modifier.fillMaxWidth(),
                enabled = profileChanged && headsetKey.isNotBlank(),
                onClick = {
                    vm.saveProfile(bio.trim(), accentHex, headsetKey)
                },
            )
        }

        item {
            CardBox {
                SectionLabel("ТЕМА")
                Spacer(Modifier.height(8.dp))
                ChipWrap {
                    Chip("Нормальная", theme == AppTheme.NORMAL) { vm.setTheme(AppTheme.NORMAL) }
                    Chip("Material You", theme == AppTheme.MATERIAL) { vm.setTheme(AppTheme.MATERIAL) }
                }
                Spacer(Modifier.height(6.dp))
                Hint("Material You подстраивает цвета под обои (Android 12+).")
            }
        }

        item {
            CardBox {
                Text(
                    "HeadsetConnector 0.4",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Hint(
                    "Мессенджер, который работает и на Pico, и на Meta Quest. " +
                        "Аккаунты, друзья, личные чаты, комнаты по коду, голосовые сообщения.",
                )
            }
        }

        item {
            GhostButton(
                text = "Выйти из аккаунта",
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Logout,
                tint = Bad,
                onClick = { showLogout = true },
            )
        }
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            containerColor = Card,
            shape = RoundedCornerShape(22.dp),
            title = { Text("Выйти?") },
            text = { Text("Аккаунт останется на сервере, но на этом устройстве придётся войти заново.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogout = false
                    vm.logout()
                }) {
                    Text("Выйти", color = Bad, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogout = false }) {
                    Text("Отмена", color = TextSecondary)
                }
            },
        )
    }

    cropSource?.let { source ->
        Dialog(
            onDismissRequest = { cropSource = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            CropScreen(
                source = source,
                onConfirm = { base64 ->
                    cropSource = null
                    vm.saveAvatar(base64)
                },
                onCancel = { cropSource = null },
            )
        }
    }
}
