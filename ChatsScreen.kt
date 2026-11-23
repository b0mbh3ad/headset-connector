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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.headsetconnector.data.Chat
import com.headsetconnector.data.Room
import com.headsetconnector.vm.ConnectorViewModel

@Composable
fun ChatsScreen(vm: ConnectorViewModel) {
    val chats by vm.chats.collectAsStateWithLifecycle()
    val rooms by vm.rooms.collectAsStateWithLifecycle()

    var menu by remember { mutableStateOf(false) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var showJoin by rememberSaveable { mutableStateOf(false) }
    var showRooms by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(showRooms) {
        if (showRooms) vm.loadRooms()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Чаты",
                            color = TextPrimary,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                        )
                        Text(
                            "Личные диалоги и комнаты",
                            color = TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    IconButton(onClick = { vm.refreshChats() }) {
                        Icon(Icons.Rounded.Refresh, "Обновить", tint = TextSecondary)
                    }
                }
            }

            if (chats.isEmpty()) {
                item {
                    CardBox {
                        Text(
                            "Пока пусто",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Hint(
                            "Добавьте друга по юзернейму — и появится личный чат. " +
                                "Или создайте комнату и отправьте другу код.",
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostButton(
                                text = "Создать комнату",
                                modifier = Modifier.weight(1f),
                                icon = Icons.Rounded.Groups,
                                tint = Accent2,
                                onClick = { showCreate = true },
                            )
                            GhostButton(
                                text = "По коду",
                                modifier = Modifier.weight(1f),
                                icon = Icons.Rounded.Key,
                                onClick = { showJoin = true },
                            )
                        }
                    }
                }
            } else {
                items(chats, key = { it.id }) { chat ->
                    ChatRow(chat, onClick = { vm.openChat(chat) })
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            FloatingActionButton(
                onClick = { menu = true },
                containerColor = Accent,
                contentColor = Color.White,
            ) {
                Icon(Icons.Rounded.Add, "Создать")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Создать комнату") },
                    leadingIcon = { Icon(Icons.Rounded.Groups, null, tint = Accent2) },
                    onClick = { menu = false; showCreate = true },
                )
                DropdownMenuItem(
                    text = { Text("Войти по коду") },
                    leadingIcon = { Icon(Icons.Rounded.Key, null, tint = Accent2) },
                    onClick = { menu = false; showJoin = true },
                )
                DropdownMenuItem(
                    text = { Text("Публичные комнаты") },
                    leadingIcon = { Icon(Icons.Rounded.Public, null, tint = Accent2) },
                    onClick = { menu = false; showRooms = true },
                )
            }
        }
    }

    if (showCreate) {
        CreateRoomDialog(
            onDismiss = { showCreate = false },
            onCreate = { title, description, isPublic ->
                showCreate = false
                vm.createRoom(title, description, isPublic)
            },
        )
    }

    if (showJoin) {
        JoinCodeDialog(
            onDismiss = { showJoin = false },
            onJoin = { code ->
                showJoin = false
                vm.joinByCode(code)
            },
        )
    }

    if (showRooms) {
        PublicRoomsDialog(
            rooms = rooms,
            onDismiss = { showRooms = false },
            onRefresh = vm::loadRooms,
            onJoin = { room ->
                showRooms = false
                vm.joinRoom(room)
            },
        )
    }
}

@Composable
private fun ChatRow(chat: Chat, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chat.isRoom) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Groups, null, tint = Accent2, modifier = Modifier.size(22.dp))
            }
        } else {
            Avatar(chat.avatar, chat.title, 46.dp)
        }

        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (chat.isRoom) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        if (chat.isPublic) Icons.Rounded.Public else Icons.Rounded.Lock,
                        null,
                        tint = if (chat.isPublic) Good else Warn,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                preview(chat),
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (chat.isRoom && !chat.code.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    "код ${chat.code}",
                    color = Accent2,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatWhen(chat.lastMessage?.createdAt ?: chat.createdAt),
                color = TextSecondary,
                fontSize = 10.sp,
            )
            if (chat.isRoom) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${chat.memberCount} чел.",
                    color = TextSecondary,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

private fun preview(chat: Chat): String {
    val last = chat.lastMessage ?: return "Нет сообщений"
    val body = when {
        last.text.isNotBlank() -> last.text
        last.audioUrl != null -> "Голосовое · ${formatDuration(last.audioDuration)}"
        else -> "Сообщение"
    }
    return if (chat.isRoom) "${last.senderName}: $body" else body
}

@Composable
private fun CreateRoomDialog(onDismiss: () -> Unit, onCreate: (String, String, Boolean) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var isPublic by rememberSaveable { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Card,
        shape = RoundedCornerShape(22.dp),
        title = { Text("Новая комната", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Field(
                    value = title,
                    onValueChange = { title = it.take(40) },
                    label = "Название",
                    placeholder = "Вечерний созвон",
                )
                Spacer(Modifier.height(10.dp))
                Field(
                    value = description,
                    onValueChange = { description = it.take(200) },
                    label = "Описание",
                    placeholder = "О чём эта комната",
                )
                Spacer(Modifier.height(14.dp))
                LabeledSwitch(
                    title = "Публичная",
                    subtitle = if (isPublic) {
                        "Видна всем в списке комнат"
                    } else {
                        "Вход только по коду"
                    },
                    checked = isPublic,
                    onCheckedChange = { isPublic = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title, description, isPublic) }, enabled = title.isNotBlank()) {
                Text("Создать", color = Accent2, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = TextSecondary) }
        },
    )
}

@Composable
private fun JoinCodeDialog(onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Card,
        shape = RoundedCornerShape(22.dp),
        title = { Text("Вход по коду", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Hint("Попросите владельца комнаты прислать 6-значный код.")
                Spacer(Modifier.height(10.dp))
                Field(
                    value = code,
                    onValueChange = { value ->
                        code = value.filter { it.isLetterOrDigit() }.uppercase().take(8)
                    },
                    label = "Код комнаты",
                    placeholder = "AB12CD",
                    leading = Icons.Rounded.Key,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onJoin(code) }, enabled = code.length >= 4) {
                Text("Войти", color = Accent2, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = TextSecondary) }
        },
    )
}

@Composable
private fun PublicRoomsDialog(
    rooms: List<Room>,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onJoin: (Room) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Card,
        shape = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Публичные комнаты",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, "Обновить", tint = TextSecondary)
                }
            }
        },
        text = {
            if (rooms.isEmpty()) {
                Hint("Пока никто не создал публичную комнату. Будьте первым.")
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(rooms, key = { it.id }) { room ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(CardHi)
                                .clickable { onJoin(room) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Groups,
                                null,
                                tint = Accent2,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    room.title,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${room.memberCount} участников",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                )
                            }
                            Text(
                                if (room.isMember) "Открыть" else "Войти",
                                color = Accent2,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть", color = TextSecondary) }
        },
    )
}
