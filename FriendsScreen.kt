package com.headsetconnector.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.headsetconnector.data.User
import com.headsetconnector.vm.ConnectorViewModel
import kotlinx.coroutines.delay

@Composable
fun FriendsScreen(vm: ConnectorViewModel) {
    val data by vm.friends.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val searching by vm.searchingUsers.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var removeTarget by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(query) {
        delay(400)
        vm.searchUsers(query)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Друзья",
                        color = TextPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                    )
                    Text(
                        "Добавляйте по юзернейму — он одинаковый на Pico и Quest",
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                }
                IconButton(onClick = { vm.refreshAll() }) {
                    Icon(Icons.Rounded.Refresh, "Обновить", tint = TextSecondary)
                }
            }
        }

        if (data.incoming.isNotEmpty()) {
            item {
                CardBox {
                    SectionLabel("ЗАЯВКИ")
                    Spacer(Modifier.height(8.dp))
                    data.incoming.forEach { user ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(user.avatar, user.displayName, 40.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    user.displayName,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text("@${user.username}", color = TextSecondary, fontSize = 11.sp)
                            }
                            IconButton(onClick = { vm.answerFriend(user, true) }) {
                                Icon(Icons.Rounded.Check, "Принять", tint = Good)
                            }
                            IconButton(onClick = { vm.answerFriend(user, false) }) {
                                Icon(Icons.Rounded.Close, "Отклонить", tint = Bad)
                            }
                        }
                    }
                }
            }
        }

        item {
            CardBox {
                SectionLabel("НАЙТИ ЧЕЛОВЕКА")
                Spacer(Modifier.height(8.dp))
                Field(
                    value = query,
                    onValueChange = { value -> query = value.take(20) },
                    label = "Юзернейм",
                    placeholder = "nickname",
                    leading = Icons.Rounded.Search,
                )
                if (query.trim().length >= 2) {
                    Spacer(Modifier.height(10.dp))
                    if (searching) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Accent2,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Hint("Ищу…")
                        }
                    } else if (results.isEmpty()) {
                        Hint("Никого не нашли. Юзернейм чувствителен к регистру? Нет, но проверьте раскладку.")
                    } else {
                        results.forEach { user ->
                            SearchRow(user, onAdd = { vm.addFriend(user.username) }, onChat = {
                                vm.openDirect(user.username)
                            })
                        }
                    }
                }
            }
        }

        item {
            CardBox {
                SectionLabel("МОИ ДРУЗЬЯ · ${data.friends.size}")
                Spacer(Modifier.height(8.dp))
                if (data.friends.isEmpty()) {
                    Hint("Пока никого. Найдите человека выше и отправьте заявку.")
                } else {
                    data.friends.forEach { user ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(user.avatar, user.displayName, 42.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    user.displayName,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text("@${user.username}", color = TextSecondary, fontSize = 11.sp)
                                user.headsetLabel?.let {
                                    Text(it, color = Accent2, fontSize = 10.sp)
                                }
                            }
                            IconButton(onClick = { vm.openDirect(user.username) }) {
                                Icon(Icons.Rounded.ChatBubble, "Написать", tint = Accent2)
                            }
                            IconButton(onClick = { removeTarget = user }) {
                                Icon(
                                    Icons.Rounded.PersonRemove,
                                    "Удалить",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (data.outgoing.isNotEmpty()) {
            item {
                CardBox {
                    SectionLabel("ОТПРАВЛЕННЫЕ ЗАЯВКИ")
                    Spacer(Modifier.height(8.dp))
                    data.outgoing.forEach { user ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(user.avatar, user.displayName, 32.dp)
                            Spacer(Modifier.width(9.dp))
                            Text(
                                user.displayName,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text("ждём ответа", color = TextSecondary, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }

    removeTarget?.let { user ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            containerColor = Card,
            shape = RoundedCornerShape(22.dp),
            title = { Text("Удалить из друзей?") },
            text = { Text("${user.displayName} пропадёт из списка. Диалог останется.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeFriend(user)
                    removeTarget = null
                }) {
                    Text("Удалить", color = Bad, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) {
                    Text("Отмена", color = TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun SearchRow(user: User, onAdd: () -> Unit, onChat: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(user.avatar, user.displayName, 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                user.displayName,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("@${user.username}", color = TextSecondary, fontSize = 11.sp)
            user.headsetLabel?.let {
                Text(it, color = Accent2, fontSize = 10.sp)
            }
        }
        when (user.relation) {
            "friends" -> IconButton(onClick = onChat) {
                Icon(Icons.Rounded.ChatBubble, "Написать", tint = Accent2)
            }

            "outgoing" -> Text("отправлено", color = TextSecondary, fontSize = 10.sp)

            "incoming" -> Text("хочет дружить", color = Warn, fontSize = 10.sp)

            else -> IconButton(onClick = onAdd) {
                Icon(Icons.Rounded.PersonAdd, "Добавить", tint = Good)
            }
        }
    }
}
