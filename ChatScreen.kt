package com.headsetconnector.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.headsetconnector.data.Message
import com.headsetconnector.vm.ConnectorViewModel

@Composable
fun ChatScreen(vm: ConnectorViewModel) {
    val chat by vm.currentChat.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val me by vm.session.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle()
    val seconds by vm.recordingSeconds.collectAsStateWithLifecycle()
    val playing by vm.playing.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startRecording()
        else vm.notifyLocal("Без доступа к микрофону голосовые не записать")
    }

    fun startVoice() {
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (allowed) vm.startRecording() else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.closeChat() }) {
                Icon(Icons.Rounded.ArrowBack, "Назад", tint = TextPrimary)
            }
            if (chat?.isRoom == true) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Groups, null, tint = Accent2, modifier = Modifier.size(20.dp))
                }
            } else {
                Avatar(chat?.avatar, chat?.title.orEmpty(), 40.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        chat?.title.orEmpty(),
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (chat?.isRoom == true) {
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            if (chat?.isPublic == true) Icons.Rounded.Public else Icons.Rounded.Lock,
                            null,
                            tint = if (chat?.isPublic == true) Good else Warn,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
                Text(
                    subtitle(chat?.isRoom == true, chat?.memberCount ?: 0, chat?.code),
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = if (chat?.code != null) FontFamily.Monospace else FontFamily.Default,
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.06f)),
        )

        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.Groups,
                                null,
                                tint = TextSecondary,
                                modifier = Modifier.size(30.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Пока тихо", color = TextPrimary, fontSize = 13.sp)
                            Text("Напишите первым", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
            items(messages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    mine = message.senderId == me?.id,
                    showSender = chat?.isRoom == true,
                    playing = playing == message.audioUrl,
                    onPlay = { message.audioUrl?.let(vm::togglePlay) },
                )
            }
        }

        if (recording) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(CardHi)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Bad),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Запись ${formatDuration(seconds)}",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.cancelRecording() }) {
                    Icon(Icons.Rounded.Delete, "Отменить", tint = TextSecondary)
                }
                GhostButton(
                    text = "Отправить",
                    icon = Icons.Rounded.Send,
                    tint = Accent2,
                    onClick = { vm.finishRecording() },
                )
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(4000) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Сообщение", fontSize = 14.sp) },
                    maxLines = 4,
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent.copy(alpha = 0.6f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                        focusedContainerColor = Card,
                        unfocusedContainerColor = Card,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Accent2,
                    ),
                )
                Spacer(Modifier.width(6.dp))
                if (input.isBlank()) {
                    IconButton(
                        onClick = { startVoice() },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Accent.copy(alpha = 0.18f)),
                    ) {
                        Icon(Icons.Rounded.Mic, "Голосовое", tint = Accent2)
                    }
                } else {
                    IconButton(
                        onClick = {
                            vm.sendText(input)
                            input = ""
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Accent),
                    ) {
                        Icon(Icons.Rounded.Send, "Отправить", tint = Color.White)
                    }
                }
            }
        }
    }
}

private fun subtitle(isRoom: Boolean, members: Int, code: String?): String = when {
    !isRoom -> "личный чат"
    code != null -> "$members участников · код $code"
    else -> "$members участников · публичная"
}

@Composable
private fun MessageBubble(
    message: Message,
    mine: Boolean,
    showSender: Boolean,
    playing: Boolean,
    onPlay: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (mine) 16.dp else 4.dp,
                        bottomEnd = if (mine) 4.dp else 16.dp,
                    ),
                )
                .background(if (mine) Accent.copy(alpha = 0.85f) else Card)
                .border(
                    1.dp,
                    Color.White.copy(alpha = if (mine) 0.05f else 0.06f),
                    RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (showSender && !mine) {
                Text(
                    message.senderName,
                    color = Accent2,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
            }

            if (message.text.isNotBlank()) {
                Text(
                    message.text,
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                )
            }

            if (message.audioUrl != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPlay, modifier = Modifier.size(34.dp)) {
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (playing) "Пауза" else "Слушать",
                            tint = Color.White,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Голосовое · ${formatDuration(message.audioDuration)}",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                    )
                }
            }

            Text(
                formatTime(message.createdAt),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}
