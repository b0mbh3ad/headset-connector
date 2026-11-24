package com.headsetconnector.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.headsetconnector.data.HEADSET_OPTIONS
import com.headsetconnector.vm.ConnectorViewModel

private val EMAIL_RE = Regex("^[^\\s@]+@[^\\s@]+\\.[a-zA-Z]{2,}$")

@Composable
fun WelcomeScreen(vm: ConnectorViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()

    var register by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var headset by rememberSaveable { mutableStateOf("") }

    val emailValid = EMAIL_RE.matches(email.trim())

    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.ChatBubble, null, tint = Accent2, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "HeadsetConnector",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                    )
                    Text(
                        "Мост между Pico и Meta Quest",
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        item {
            CardBox {
                SectionLabel(if (register) "РЕГИСТРАЦИЯ" else "ВХОД")
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeTab("Вход", !register, Modifier.weight(1f)) { register = false }
                    ModeTab("Регистрация", register, Modifier.weight(1f)) { register = true }
                }

                Spacer(Modifier.height(12.dp))
                Field(
                    value = username,
                    onValueChange = { value ->
                        username = value.filter { it.isLetterOrDigit() || it == '_' }.take(20)
                    },
                    label = if (register) "Юзернейм" else "Юзернейм или почта",
                    placeholder = if (register) "nickname" else "nickname или почта",
                    leading = Icons.Rounded.AlternateEmail,
                )

                if (register) {
                    Spacer(Modifier.height(10.dp))
                    Field(
                        value = displayName,
                        onValueChange = { displayName = it.take(32) },
                        label = "Как показывать в чатах",
                        placeholder = "Имя",
                        leading = Icons.Rounded.Badge,
                    )
                    Spacer(Modifier.height(10.dp))
                    Field(
                        value = email,
                        onValueChange = { email = it.trim().take(120) },
                        label = "Почта",
                        placeholder = "you@example.com",
                        leading = Icons.Rounded.Email,
                    )
                    if (email.isNotBlank() && !emailValid) {
                        Spacer(Modifier.height(6.dp))
                        Hint("Введите настоящий адрес почты.")
                    }

                    Spacer(Modifier.height(14.dp))
                    SectionLabel("ВАШ ШЛЕМ")
                    Spacer(Modifier.height(8.dp))
                    ChipWrap {
                        HEADSET_OPTIONS.forEach { option ->
                            Chip(
                                text = option.label,
                                selected = headset == option.key,
                                onClick = { headset = option.key },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Field(
                    value = password,
                    onValueChange = { password = it.take(64) },
                    label = "Пароль",
                    placeholder = "минимум 8 символов",
                    password = true,
                    leading = Icons.Rounded.Lock,
                )

                Spacer(Modifier.height(14.dp))
                PrimaryButton(
                    text = if (register) "Создать аккаунт" else "Войти",
                    modifier = Modifier.fillMaxWidth(),
                    loading = busy != null,
                    enabled = if (register) {
                        username.trim().length >= 3 && password.length >= 8 && emailValid && headset.isNotBlank()
                    } else {
                        username.trim().isNotEmpty() && password.length >= 8
                    },
                    onClick = {
                        if (register) {
                            vm.register(username, password, displayName, email, headset)
                        } else {
                            vm.login(username, password)
                        }
                    },
                )

                if (register) {
                    Spacer(Modifier.height(8.dp))
                    Hint("Логин: 3–20 символов, латиница, цифры и _. Пароль от 8 символов.")
                }
            }
        }

        item {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.VideogameAsset, null, tint = Good, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Один аккаунт работает и на Pico, и на Quest — достаточно установить APK на оба шлема.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Accent.copy(alpha = 0.18f) else Card)
            .border(
                1.dp,
                if (selected) Accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) Accent2 else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
