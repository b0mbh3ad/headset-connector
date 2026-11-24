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
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.headsetconnector.data.Chat
import com.headsetconnector.data.Message
import com.headsetconnector.vm.ConnectorViewModel
import com.headsetconnector.voice.VoiceManager

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
    var showRoomPanel by remember { mutableStateOf(false) }
    val voice by VoiceManager.state.collectAsStateWithLifecycle()

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startRecording()
        else vm.notifyLocal("No mic access — cannot record voice")
    }

    val voicePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val activeChat = chat
        if (granted && activeChat != null) {
            VoiceManager.join(context, activeChat.id, activeChat.title, me?.id)
        } else if (!granted) {
            vm.notifyLocal("No mic — you can still write in the text chat")
        }
    }

    fun joinVoice() {
        val activeChat = chat ?: return
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (allowed) VoiceManager.join(context, activeChat.id, activeChat.title, me?.id)
        else voicePermission.launch(Manifest.permission.RECORD_AUDIO)
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
            IconButton(onClick = { VoiceManager.leave(); vm.closeChat() }) {
                Icon(Icons.Rounded.ArrowBack, "Back", tint = TextPrimary)
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
                chat?.headsetLabel?.let { headset ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        headset,
                        color = Accent2,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (chat?.isRoom == true) {
                IconButton(onClick = { showRoomPanel = true }) {
                    Icon(Icons.Rounded.Groups, "Members", tint = Accent2)
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.06f)),
        )

        if (chat?.isRoom == true) {
            VoiceBar(
                active = voice.active && voice.roomId == chat?.id,
                connecting = voice.connecting && voice.roomId == chat?.id,
                muted = voice.muted,
                count = if (voice.active && voice.roomId == chat?.id) voice.peers.size + 1 else 0,
                peers = if (voice.roomId == chat?.id) voice.peers else emptyList(),
                error = if (voice.roomId == chat?.id) voice.error else null,
                otherRoom = voice.roomId != null && voice.roomId != chat?.id,
                onJoin = { joinVoice() },
                onLeave = { VoiceManager.leave() },
                onMute = { VoiceManager.toggleMute() },
                isRoom = chat?.isRoom == true,
            )
        }

        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (chat?.isRoom == true) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.Mic,
                                null,
                                tint = Accent2,
                                modifier = Modifier.size(30.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Voice-only chat",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Texts go to private chats",
                                color = TextSecondary,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            } else if (messages.isEmpty()) {
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
                            Text("Quiet for now", color = TextPrimary, fontSize = 13.sp)
                            Text("Be the first", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
            if (chat?.isRoom != true) {
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
        }

        if (chat?.isRoom != true && recording) {
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
                    "Recording ${formatDuration(seconds)}",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.cancelRecording() }) {
                    Icon(Icons.Rounded.Delete, "Cancel", tint = TextSecondary)
                }
                GhostButton(
                    text = "Send",
                    icon = Icons.Rounded.Send,
                    tint = Accent2,
                    onClick = { vm.finishRecording() },
                )
            }
        } else if (chat?.isRoom != true) {
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
                    placeholder = { Text("Message", fontSize = 14.sp) },
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
                        Icon(Icons.Rounded.Mic, "Voice message", tint = Accent2)
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
                        Icon(Icons.Rounded.Send, "Send", tint = Color.White)
                    }
                }
            }
        }
    }

    val currentChatValue = chat
    if (showRoomPanel && currentChatValue != null) {
        RoomPanelDialog(
            chat = currentChatValue,
            myId = me?.id,
            onDismiss = { showRoomPanel = false },
            onTransfer = { id -> showRoomPanel = false; vm.transferOwner(id) },
            onKick = { id -> vm.kickMember(id) },
            onLeave = { showRoomPanel = false; vm.leaveRoom() },
        )
    }
}

@Composable
private fun RoomPanelDialog(
    chat: Chat,
    myId: String?,
    onDismiss: () -> Unit,
    onTransfer: (String) -> Unit,
    onKick: (String) -> Unit,
    onLeave: () -> Unit,
) {
    val isOwner = chat.ownerId != null && chat.ownerId == myId
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CardBox(
            padding = 18.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            SectionLabel("MEMBERS · ${chat.memberCount}")
            Spacer(Modifier.height(6.dp))
            chat.description?.let {
                Text(it, color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
            }
            if (isOwner && chat.code != null) {
                Text("Code: ${chat.code}", color = Accent2, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(4.dp))
            chat.members.forEach { member ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(member.avatar, member.displayName, 36.dp)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            member.displayName,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (member.id == chat.ownerId) "owner" else "@${member.username}",
                            color = TextSecondary,
                            fontSize = 10.sp,
                        )
                        member.headsetLabel?.let { headset ->
                            Spacer(Modifier.height(2.dp))
                            Text(
                                headset,
                                color = Accent2,
                                fontSize = 10.sp,
                            )
                        }
                    }
                    if (isOwner && member.id != myId) {
                        TextButton(onClick = { onTransfer(member.id) }) {
                            Text("Transfer", color = Accent2, fontSize = 11.sp)
                        }
                        TextButton(onClick = { onKick(member.id) }) {
                            Text("Kick", color = Bad, fontSize = 11.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            GhostButton(
                text = "Leave room",
                modifier = Modifier.fillMaxWidth(),
                tint = Bad,
                onClick = { VoiceManager.leave(); onLeave() },
            )
        }
    }
}

@Composable
private fun VoiceBar(
    active: Boolean,
    connecting: Boolean,
    muted: Boolean,
    count: Int,
    peers: List<String>,
    error: String?,
    otherRoom: Boolean,
    isRoom: Boolean,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onMute: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (active) Good.copy(alpha = 0.10f) else CardHi.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (active) Icons.Rounded.Call else Icons.Rounded.CallEnd,
            null,
            tint = if (active) Good else Accent2,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    error != null -> error
                    connecting -> "Connecting to voice…"
                    active -> "In voice: $count · ${peers.joinToString(", ").ifBlank { "only you" }}"
                    otherRoom -> "You are in another voice room"
                    else -> "Voice room"
                },
                color = when {
                    error != null -> Bad
                    active -> Good
                    else -> TextPrimary
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!active && error == null) {
                Text(
                    if (isRoom) "A mic is needed to speak in the room" else "No mic? Write in the text chat",
                    color = TextSecondary,
                    fontSize = 10.sp,
                )
            }
        }
        if (active) {
            IconButton(onClick = onMute) {
                Icon(
                    if (muted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    if (muted) "Unmute" else "Mute",
                    tint = if (muted) Bad else TextPrimary,
                )
            }
            IconButton(onClick = onLeave) {
                Icon(Icons.Rounded.CallEnd, "Leave voice", tint = Bad)
            }
        } else {
            GhostButton(
                text = if (connecting) "Cancel" else "Join",
                icon = Icons.Rounded.Call,
                tint = Accent2,
                onClick = if (connecting) onLeave else onJoin,
            )
        }
    }
}

private fun subtitle(isRoom: Boolean, members: Int, code: String?): String = when {
    !isRoom -> "private chat"
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
                            if (playing) "Pause" else "Listen",
                            tint = Color.White,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Voice message · ${formatDuration(message.audioDuration)}",
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
