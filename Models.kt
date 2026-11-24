package com.headsetconnector.data

import org.json.JSONArray
import org.json.JSONObject

data class Achievement(
    val key: String,
    val label: String,
    val icon: String,
)

data class User(
    val id: String,
    val username: String,
    val displayName: String,
    val avatar: String?,
    val relation: String = "none",
    val headset: String? = null,
    val headsetLabel: String? = null,
    val bio: String? = null,
    val status: String? = null,
    val accent: String? = null,
    val banner: String? = null,
    val createdAt: Long = 0,
    val badge: String? = null,
    val badgeLabel: String? = null,
    val badges: List<String> = emptyList(),
    val badgeLabels: List<String> = emptyList(),
    val achievements: List<Achievement> = emptyList(),
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
    val status: String? = null,
    val accent: String? = null,
    val banner: String? = null,
    val createdAt: Long = 0,
    val badge: String? = null,
    val badgeLabel: String? = null,
    val badges: List<String> = emptyList(),
    val badgeLabels: List<String> = emptyList(),
    val achievements: List<Achievement> = emptyList(),
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
    val description: String? = null,
    val ownerId: String? = null,
    val ownerName: String? = null,
    val members: List<User> = emptyList(),
    val memberCount: Int,
    val lastMessage: Message?,
    val createdAt: Long,
    val headsetLabel: String? = null,
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

/** Превращает "0.4" или "v0.4" в сравнимое число (0.4 → 4, 1.10 → 110). */
fun versionNumber(version: String): Int {
    val parts = version.removePrefix("v").removePrefix("V").trim().split(".")
        .mapNotNull { it.toIntOrNull() }
    if (parts.isEmpty()) return 0
    return parts[0] * 100 + (parts.getOrElse(1) { 0 })
}

fun parseUser(o: JSONObject): User = User(
    id = o.optString("id"),
    username = o.optString("username"),
    displayName = o.optString("displayName").ifBlank { o.optString("username") },
    avatar = o.optString("avatar").takeIf { it.isNotBlank() && it != "null" },
    relation = o.optString("relation", "none"),
    headset = o.optString("headset").takeIf { it.isNotBlank() && it != "null" },
    headsetLabel = o.optString("headsetLabel").takeIf { it.isNotBlank() && it != "null" },
    bio = o.optString("bio").takeIf { it.isNotBlank() && it != "null" },
    status = o.optString("status").takeIf { it.isNotBlank() && it != "null" },
    accent = o.optString("accent").takeIf { it.isNotBlank() && it != "null" },
    banner = o.optString("banner").takeIf { it.isNotBlank() && it != "null" },
    createdAt = o.optLong("createdAt"),
    badge = o.optString("badge").takeIf { it.isNotBlank() && it != "null" },
    badgeLabel = o.optString("badgeLabel").takeIf { it.isNotBlank() && it != "null" },
    badges = parseStringList(o.optJSONArray("badges")),
    badgeLabels = parseStringList(o.optJSONArray("badgeLabels")),
    achievements = parseAchievements(o.optJSONArray("achievements")),
)

fun parseAchievements(array: JSONArray?): List<Achievement> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        Achievement(
            key = item.optString("key"),
            label = item.optString("label"),
            icon = item.optString("icon"),
        )
    }
}

private fun parseStringList(array: JSONArray?): List<String> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        array.optString(index).takeIf { it.isNotBlank() && it != "null" }
    }
}

fun parseMe(o: JSONObject): Me = Me(
    id = o.optString("id"),
    username = o.optString("username"),
    displayName = o.optString("displayName").ifBlank { o.optString("username") },
    avatar = o.optString("avatar").takeIf { it.isNotBlank() && it != "null" },
    email = o.optString("email").takeIf { it.isNotBlank() && it != "null" },
    headset = o.optString("headset").takeIf { it.isNotBlank() && it != "null" },
    headsetLabel = o.optString("headsetLabel").takeIf { it.isNotBlank() && it != "null" },
    bio = o.optString("bio").takeIf { it.isNotBlank() && it != "null" },
    status = o.optString("status").takeIf { it.isNotBlank() && it != "null" },
    accent = o.optString("accent").takeIf { it.isNotBlank() && it != "null" },
    banner = o.optString("banner").takeIf { it.isNotBlank() && it != "null" },
    createdAt = o.optLong("createdAt"),
    badge = o.optString("badge").takeIf { it.isNotBlank() && it != "null" },
    badgeLabel = o.optString("badgeLabel").takeIf { it.isNotBlank() && it != "null" },
    badges = parseStringList(o.optJSONArray("badges")),
    badgeLabels = parseStringList(o.optJSONArray("badgeLabels")),
    achievements = parseAchievements(o.optJSONArray("achievements")),
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
    title = o.optString("title").ifBlank { "Chat" },
    avatar = o.optString("avatar").takeIf { it.isNotBlank() && it != "null" },
    isPublic = o.optBoolean("isPublic"),
    code = o.optString("code").takeIf { it.isNotBlank() && it != "null" },
    description = o.optString("description").takeIf { it.isNotBlank() && it != "null" },
    ownerId = o.optString("ownerId").takeIf { it.isNotBlank() && it != "null" },
    ownerName = o.optString("ownerName").takeIf { it.isNotBlank() && it != "null" },
    members = o.optJSONArray("members")?.let { array ->
        (0 until array.length()).map { parseUser(array.getJSONObject(it)) }
    } ?: emptyList(),
    memberCount = o.optInt("memberCount"),
    lastMessage = o.optJSONObject("lastMessage")?.let(::parseMessage),
    createdAt = o.optLong("createdAt"),
    headsetLabel = o.optString("headsetLabel").takeIf { it.isNotBlank() && it != "null" },
)
