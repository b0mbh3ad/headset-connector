package com.headsetconnector.data

import org.json.JSONObject

data class User(
    val id: String,
    val username: String,
    val displayName: String,
    val avatar: String?,
    val relation: String = "none",
    val headset: String? = null,
    val headsetLabel: String? = null,
    val badge: String? = null,
)

data class Me(
    val id: String,
    val username: String,
    val displayName: String,
    val avatar: String?,
    val email: String? = null,
    val headset: String? = null,
    val headsetLabel: String? = null,
    val bio: String? = null,
    val accent: String? = null,
    val banner: String? = null,
    val badge: String? = null,
)

data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val senderAvatar: String?,
    val text: String,
    val audioUrl: String?,
    val audioDuration: Int,
    val createdAt: Long,
)

data class Chat(
    val id: String,
    val type: String,
    val title: String,
    val avatar: String?,
    val isPublic: Boolean,
    val code: String?,
    val memberCount: Int,
    val lastMessage: Message?,
    val createdAt: Long,
) {
    val isRoom: Boolean get() = type == "room"
}

data class Room(
    val id: String,
    val title: String,
    val memberCount: Int,
    val isMember: Boolean,
    val lastMessage: Message?,
)

data class FriendsData(
    val friends: List<User> = emptyList(),
    val incoming: List<User> = emptyList(),
    val outgoing: List<User> = emptyList(),
)

data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
)

fun parseAppUpdate(o: JSONObject): AppUpdate = AppUpdate(
    versionCode = o.optInt("versionCode", 0),
    versionName = o.optString("versionName"),
    apkUrl = o.optString("apkUrl").ifBlank { o.optString("url") },
)

fun parseUser(o: JSONObject): User = User(
    id = o.optString("id"),
    username = o.optString("username"),
    displayName = o.optString("displayName").ifBlank { o.optString("username") },
    avatar = o.optString("avatar").takeIf { it.isNotBlank() && it != "null" },
    relation = o.optString("relation", "none"),
    headset = o.optString("headset").takeIf { it.isNotBlank() && it != "null" },
    headsetLabel = o.optString("headsetLabel").takeIf { it.isNotBlank() && it != "null" },
    badge = o.optString("badge").takeIf { it.isNotBlank() && it != "null" },
)

fun parseMe(o: JSONObject): Me = Me(
    id = o.optString("id"),
    username = o.optString("username"),
    displayName = o.optString("displayName").ifBlank { o.optString("username") },
    avatar = o.optString("avatar").takeIf { it.isNotBlank() && it != "null" },
    email = o.optString("email").takeIf { it.isNotBlank() && it != "null" },
    headset = o.optString("headset").takeIf { it.isNotBlank() && it != "null" },
    headsetLabel = o.optString("headsetLabel").takeIf { it.isNotBlank() && it != "null" },
    bio = o.optString("bio").takeIf { it.isNotBlank() && it != "null" },
    accent = o.optString("accent").takeIf { it.isNotBlank() && it != "null" },
    banner = o.optString("banner").takeIf { it.isNotBlank() && it != "null" },
    badge = o.optString("badge").takeIf { it.isNotBlank() && it != "null" },
)

fun parseMessage(o: JSONObject): Message = Message(
    id = o.optString("id"),
    chatId = o.optString("chatId"),
    senderId = o.optString("senderId"),
    senderName = o.optString("senderName"),
    senderAvatar = o.optString("senderAvatar").takeIf { it.isNotBlank() && it != "null" },
    text = o.optString("text").takeIf { it.isNotBlank() && it != "null" }.orEmpty(),
    audioUrl = o.optString("audioUrl").takeIf { it.isNotBlank() && it != "null" },
    audioDuration = o.optInt("audioDuration", 0),
    createdAt = o.optLong("createdAt"),
)

fun parseChat(o: JSONObject): Chat = Chat(
    id = o.optString("id"),
    type = o.optString("type"),
    title = o.optString("title").ifBlank { "Чат" },
    avatar = o.optString("avatar").takeIf { it.isNotBlank() && it != "null" },
    isPublic = o.optBoolean("isPublic"),
    code = o.optString("code").takeIf { it.isNotBlank() && it != "null" },
    memberCount = o.optInt("memberCount"),
    lastMessage = o.optJSONObject("lastMessage")?.let(::parseMessage),
    createdAt = o.optLong("createdAt"),
)
