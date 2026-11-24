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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.headsetconnector.BuildConfig
import com.headsetconnector.agent.DeviceInfo
import com.headsetconnector.data.ACCENT_OPTIONS
import com.headsetconnector.data.Achievement
import com.headsetconnector.data.Api
import com.headsetconnector.data.HEADSET_OPTIONS
import com.headsetconnector.data.Me
import com.headsetconnector.data.accentColor
import com.headsetconnector.data.accentHexToKey
import com.headsetconnector.data.normalizeAccentHex
import com.headsetconnector.util.ImageTools
import com.headsetconnector.vm.ConnectorViewModel

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ProfileScreen(vm: ConnectorViewModel) {
    val me by vm.session.collectAsStateWithLifecycle()
    val pin by vm.agentPin.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val isHeadset = remember { DeviceInfo.isHeadset() }

    var editing by remember { mutableStateOf(false) }
    var showLogout by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
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

    val badgeLabels = me?.badgeLabels.orEmpty().ifEmpty { listOfNotNull(me?.badgeLabel ?: me?.badge) }
    val accent = accentColor(me?.accent) ?: Accent

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Профиль",
                        color = TextPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                    )
                    Text("Один аккаунт для Pico и Meta Quest", color = TextSecondary, fontSize = 12.sp)
                }
                IconButton(onClick = { editing = true }) {
                    Icon(Icons.Rounded.Edit, "Редактировать профиль", tint = Accent2)
                }
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
                            "$pin",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Hint("Введите этот код в приложении на телефоне — вкладка «Шлем».")
                }
            }
        }

        item {
            CardBox(padding = 0.dp) {
                Box(Modifier.fillMaxWidth().height(132.dp)) {
                    val banner = Api.mediaUrl(me?.banner)
                    if (banner != null) {
                        RemoteImage(banner, Modifier.fillMaxSize())
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                        listOf(accent.copy(alpha = 0.55f), CardHi),
                                    ),
                                ),
                        )
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .clickable { editing = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.PhotoCamera,
                            "Изменить фон",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(78.dp)
                                .clip(CircleShape)
                                .border(2.dp, accent, CircleShape)
                                .padding(3.dp),
                        ) {
                            Avatar(me?.avatar, me?.displayName.orEmpty(), 72.dp)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    me?.displayName.orEmpty(),
                                    color = TextPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                badgeLabels.forEach { badge ->
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        badge,
                                        color = Good,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(Good.copy(alpha = 0.14f))
                                            .padding(horizontal = 7.dp, vertical = 3.dp),
                                    )
                                }
                            }
                            Text("@${me?.username.orEmpty()}", color = Accent2, fontSize = 13.sp)
                            me?.headsetLabel?.let {
                                Spacer(Modifier.height(3.dp))
                                Text(it, color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                    }

                    me?.status?.let { status ->
                        Spacer(Modifier.height(12.dp))
                        Text(status, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    me?.bio?.let { bio ->
                        Spacer(Modifier.height(6.dp))
                        Text(bio, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                    if (me?.status.isNullOrBlank() && me?.bio.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Hint("Добавьте статус и пару слов о себе в редакторе профиля.")
                    }

                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        text = "Редактировать профиль",
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.Edit,
                        onClick = { editing = true },
                    )
                }
            }
        }

        item {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.EmojiEvents, null, tint = Warn, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Достижения", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("${me?.achievements.orEmpty().size}", color = TextSecondary, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
                val achievements = me?.achievements.orEmpty()
                if (achievements.isEmpty()) {
                    Hint("Достижения появятся, когда вы начнёте общаться.")
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        achievements.forEach { AchievementChip(it) }
                    }
                }
            }
        }

        item {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Акцент профиля", color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(accent)
                            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        me?.accent?.uppercase() ?: "по умолчанию",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Шлем", color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.VideogameAsset, null, tint = Accent2, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(me?.headsetLabel ?: "не выбран", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }

        item {
            CardBox {
                Text(
                    "HeadsetConnector ${BuildConfig.VERSION_NAME}",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Hint("Мессенджер для Pico и Meta Quest: друзья, личные чаты и голосовые комнаты.")
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

        item {
            TextButton(onClick = { showDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.DeleteForever, null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Удалить аккаунт", color = TextSecondary, fontSize = 11.sp)
            }
        }
    }

    if (editing) {
        EditProfileDialog(
            me = me,
            onDismiss = { editing = false },
            onPickAvatar = { avatarPicker.launch("image/*") },
            onPickBanner = { bannerPicker.launch("image/*") },
            onSave = { displayName, status, bio, headset, accentHex ->
                vm.saveProfile(displayName, bio, status, accentHex, headset)
                editing = false
            },
        )
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            containerColor = Card,
            shape = RoundedCornerShape(22.dp),
            title = { Text("Выйти?") },
            text = { Text("Аккаунт останется на сервере. На этом устройстве придётся войти заново.") },
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

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            containerColor = Card,
            shape = RoundedCornerShape(22.dp),
            title = { Text("Удалить аккаунт?") },
            text = { Text("Профиль, сообщения, друзья и комнаты будут удалены без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    vm.deleteAccount()
                }) {
                    Text("Удалить навсегда", color = Bad, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
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

@Composable
private fun AchievementChip(achievement: Achievement) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(CardHi)
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(achievement.icon, fontSize = 12.sp)
        Spacer(Modifier.width(5.dp))
        Text(
            achievement.label,
            color = TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun EditProfileDialog(
    me: Me?,
    onDismiss: () -> Unit,
    onPickAvatar: () -> Unit,
    onPickBanner: () -> Unit,
    onSave: (displayName: String, status: String, bio: String, headset: String, accent: String) -> Unit,
) {
    var name by rememberSaveable(me?.id) { mutableStateOf(me?.displayName.orEmpty()) }
    var status by rememberSaveable(me?.id) { mutableStateOf(me?.status.orEmpty()) }
    var bio by rememberSaveable(me?.id) { mutableStateOf(me?.bio.orEmpty()) }
    var headset by rememberSaveable(me?.id) { mutableStateOf(me?.headset.orEmpty()) }
    var accentInput by rememberSaveable(me?.id) {
        mutableStateOf(me?.accent?.takeIf { it.isNotBlank() } ?: "#6c8cff")
    }
    var headsetOpen by remember { mutableStateOf(false) }

    val accentValue = accentColor(accentInput) ?: Accent
    val accentOk = accentInput.isBlank() || normalizeAccentHex(accentInput) != null
    val selectedHeadset = HEADSET_OPTIONS.firstOrNull { it.key == headset }
    val accentKey = accentHexToKey(normalizeAccentHex(accentInput))

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Bg)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Редактировать профиль",
                            color = TextPrimary,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, "Закрыть", tint = TextSecondary)
                        }
                    }
                }

                item {
                    CardBox {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .clickable { onPickAvatar() },
                                contentAlignment = Alignment.BottomEnd,
                            ) {
                                Avatar(me?.avatar, me?.displayName.orEmpty(), 72.dp)
                                Box(
                                    Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Accent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.PhotoCamera,
                                        "Сменить аватар",
                                        tint = Accent.onAccent(),
                                        modifier = Modifier.size(13.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Аватар и фон", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SmallGhost("Аватар", onPickAvatar)
                                    SmallGhost("Фон", onPickBanner)
                                }
                            }
                        }
                    }
                }

                item {
                    CardBox {
                        Field(
                            value = name,
                            onValueChange = { name = it.take(32) },
                            label = "Отображаемое имя",
                            placeholder = "Как вас видят друзья",
                            leading = Icons.Rounded.Edit,
                        )
                        Spacer(Modifier.height(10.dp))
                        Field(
                            value = status,
                            onValueChange = { status = it.take(60) },
                            label = "Статус",
                            placeholder = "Чем занимаетесь",
                            leading = Icons.Rounded.EmojiEvents,
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

                item {
                    CardBox {
                        SectionLabel("ВАШ ШЛЕМ")
                        Spacer(Modifier.height(8.dp))
                        Box {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(15.dp))
                                    .background(CardHi)
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(15.dp))
                                    .clickable { headsetOpen = true }
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.VideogameAsset,
                                    null,
                                    tint = Accent2,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    selectedHeadset?.label ?: "Выберите модель",
                                    color = if (selectedHeadset == null) TextSecondary else TextPrimary,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(Icons.Rounded.ArrowDropDown, null, tint = TextSecondary)
                            }
                            DropdownMenu(
                                expanded = headsetOpen,
                                onDismissRequest = { headsetOpen = false },
                                modifier = Modifier.background(Card),
                            ) {
                                HEADSET_OPTIONS.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                option.label,
                                                color = if (option.key == headset) Accent2 else TextPrimary,
                                            )
                                        },
                                        onClick = {
                                            headset = option.key
                                            headsetOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    CardBox {
                        SectionLabel("АКЦЕНТНЫЙ ЦВЕТ")
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ACCENT_OPTIONS.forEach { (key, hex) ->
                                val color = accentColor(hex) ?: Accent
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
                                        .clickable { accentInput = hex },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (accentKey == key) {
                                        Text("✓", color = color.onAccent(), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(accentValue)
                                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                            )
                            Spacer(Modifier.width(10.dp))
                            Field(
                                value = accentInput,
                                onValueChange = { value ->
                                    accentInput = value.uppercase().filter { it.isDigit() || it == '#' || it in 'A'..'F' }.take(7)
                                },
                                label = "Свой цвет",
                                placeholder = "#6C8CFF",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (!accentOk) {
                            Spacer(Modifier.height(6.dp))
                            Text("Нужен цвет вида #RRGGBB", color = Bad, fontSize = 11.sp)
                        }
                    }
                }

                item {
                    PrimaryButton(
                        text = "Сохранить изменения",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = name.trim().isNotBlank() && headset.isNotBlank() && accentOk,
                        onClick = {
                            val accent = if (accentInput.isBlank()) "" else normalizeAccentHex(accentInput) ?: ""
                            onSave(name.trim(), status.trim(), bio.trim(), headset, accent)
                        },
                    )
                }

                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun SmallGhost(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Accent2,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Accent.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
