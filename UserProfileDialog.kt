package com.headsetconnector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.headsetconnector.data.Achievement
import com.headsetconnector.data.Api
import com.headsetconnector.data.User
import com.headsetconnector.data.accentColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Карточка чужого профиля: оформление, статус, шлем, достижения и действия.
 * Открывается из списка друзей, заявок и поиска — поэтому профиль не теряется.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun UserProfileDialog(
    user: User,
    onDismiss: () -> Unit,
    onChat: () -> Unit,
    onAddFriend: () -> Unit,
    onRemoveFriend: () -> Unit,
    onAnswerFriend: (Boolean) -> Unit,
) {
    val accent = accentColor(user.accent) ?: Accent
    val badgeLabels = user.badgeLabels.ifEmpty { listOfNotNull(user.badgeLabel ?: user.badge) }
    val memberSince = formatMemberDate(user.createdAt)

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
                            "Профиль",
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
                    CardBox(padding = 0.dp) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(126.dp),
                        ) {
                            val banner = Api.mediaUrl(user.banner)
                            if (banner != null) {
                                RemoteImage(banner, Modifier.fillMaxSize())
                            } else {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                listOf(accent.copy(alpha = 0.55f), CardHi),
                                            ),
                                        ),
                                )
                            }
                        }
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(74.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, accent, CircleShape)
                                        .padding(3.dp),
                                ) {
                                    Avatar(user.avatar, user.displayName, 68.dp)
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        user.displayName,
                                        color = TextPrimary,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text("@${user.username}", color = accent, fontSize = 13.sp)
                                    user.headsetLabel?.let { label ->
                                        Spacer(Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Rounded.VideogameAsset,
                                                null,
                                                tint = TextSecondary,
                                                modifier = Modifier.size(13.dp),
                                            )
                                            Spacer(Modifier.width(5.dp))
                                            Text(label, color = TextSecondary, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            if (badgeLabels.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    badgeLabels.forEach { label ->
                                        Text(
                                            label,
                                            color = Good,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(Good.copy(alpha = 0.14f))
                                                .padding(horizontal = 8.dp, vertical = 3.dp),
                                        )
                                    }
                                }
                            }

                            user.status?.let { status ->
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    status,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            user.bio?.let { bio ->
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    bio,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                )
                            }
                            if (memberSince.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "В HeadsetConnector с $memberSince",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }

                item {
                    CardBox {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.EmojiEvents,
                                null,
                                tint = Warn,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Достижения",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${user.achievements.size}",
                                color = TextSecondary,
                                fontSize = 11.sp,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        if (user.achievements.isEmpty()) {
                            Hint("Пока без достижений.")
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                user.achievements.forEach { AchievementChip(it) }
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        when (user.relation) {
                            "friends" -> {
                                PrimaryButton(
                                    text = "Написать сообщение",
                                    modifier = Modifier.fillMaxWidth(),
                                    icon = Icons.Rounded.ChatBubble,
                                    onClick = onChat,
                                )
                                GhostButton(
                                    text = "Удалить из друзей",
                                    modifier = Modifier.fillMaxWidth(),
                                    icon = Icons.Rounded.PersonRemove,
                                    tint = Bad,
                                    onClick = onRemoveFriend,
                                )
                            }

                            "outgoing" -> {
                                PrimaryButton(
                                    text = "Заявка отправлена",
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = false,
                                    onClick = {},
                                )
                            }

                            "incoming" -> {
                                PrimaryButton(
                                    text = "Принять заявку",
                                    modifier = Modifier.fillMaxWidth(),
                                    icon = Icons.Rounded.PersonAdd,
                                    onClick = { onAnswerFriend(true) },
                                )
                                GhostButton(
                                    text = "Отклонить",
                                    modifier = Modifier.fillMaxWidth(),
                                    tint = TextSecondary,
                                    onClick = { onAnswerFriend(false) },
                                )
                            }

                            else -> PrimaryButton(
                                text = "Добавить в друзья",
                                modifier = Modifier.fillMaxWidth(),
                                icon = Icons.Rounded.PersonAdd,
                                onClick = onAddFriend,
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }
            }
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

private fun formatMemberDate(timestamp: Long): String {
    if (timestamp <= 0) return ""
    return SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(timestamp))
}
