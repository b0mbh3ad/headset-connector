package com.headsetconnector.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ApiException(message: String) : IOException(message)

object Api {

    @Volatile
    var baseUrl: String = ""

    @Volatile
    var token: String? = null

    fun mediaUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return baseUrl.trimEnd('/') + path
    }

    suspend fun call(
        method: String,
        path: String,
        body: JSONObject? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) throw ApiException("Сервер не настроен")

        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 25_000
            setRequestProperty("Accept", "application/json")
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
        }

        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(Charsets.UTF_8))
            }
        }

        val code = try {
            connection.responseCode
        } catch (e: Exception) {
            connection.disconnect()
            throw ApiException("Нет связи с сервером. Проверьте адрес и Wi-Fi.")
        }

        val text = runCatching {
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }.getOrDefault("")

        connection.disconnect()

        val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
        if (code !in 200..299) {
            throw ApiException(json.optString("error").ifBlank { "Ошибка сервера ($code)" })
        }
        json
    }

    // ------------------------------------------------------------- сервис

    suspend fun ping(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(url.trimEnd('/') + "/api/ping").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 700
                readTimeout = 700
            }
            val code = connection.responseCode
            val text = connection.inputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            code == 200 && text.contains("HeadsetConnector")
        }.getOrDefault(false)
    }

    // ------------------------------------------------------------- аккаунт

    suspend fun register(
        username: String,
        password: String,
        displayName: String,
        email: String,
        headset: String,
    ): Pair<String, Me> {
        val json = call(
            "POST",
            "/api/register",
            JSONObject()
                .put("username", username)
                .put("password", password)
                .put("displayName", displayName)
                .put("email", email)
                .put("headset", headset),
        )
        return json.getString("token") to parseMe(json.getJSONObject("user"))
    }

    suspend fun login(username: String, password: String): Pair<String, Me> {
        val json = call(
            "POST",
            "/api/login",
            JSONObject().put("username", username).put("password", password),
        )
        return json.getString("token") to parseMe(json.getJSONObject("user"))
    }

    suspend fun me(): Me = parseMe(call("GET", "/api/me").getJSONObject("user"))

    suspend fun fetchUpdate(url: String): AppUpdate = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
        }
        val text = runCatching {
            connection.inputStream.bufferedReader().use { it.readText() }
        }.getOrElse {
            connection.disconnect()
            throw ApiException("Не удалось проверить обновление")
        }
        connection.disconnect()
        parseAppUpdate(JSONObject(text))
    }

    suspend fun updateProfile(
        displayName: String? = null,
        bio: String? = null,
        accent: String? = null,
        headset: String? = null,
    ): Me {
        val body = JSONObject()
        displayName?.let { body.put("displayName", it) }
        bio?.let { body.put("bio", it) }
        accent?.let { body.put("accent", it) }
        headset?.let { body.put("headset", it) }
        return parseMe(call("PATCH", "/api/me", body).getJSONObject("user"))
    }

    suspend fun updateDisplayName(name: String): Me = updateProfile(displayName = name)

    suspend fun uploadAvatar(base64: String, mime: String = "image/jpeg"): Me = parseMe(
        call(
            "POST",
            "/api/me/avatar",
            JSONObject().put("data", base64).put("mime", mime),
        ).getJSONObject("user"),
    )

    suspend fun uploadBanner(base64: String, mime: String = "image/jpeg"): Me = parseMe(
        call(
            "POST",
            "/api/me/banner",
            JSONObject().put("data", base64).put("mime", mime),
        ).getJSONObject("user"),
    )

    // --------------------------------------------------------------- люди

    suspend fun searchUsers(query: String): List<User> {
        val json = call("GET", "/api/users/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}")
        val array = json.optJSONArray("users") ?: JSONArray()
        return (0 until array.length()).map { parseUser(array.getJSONObject(it)) }
    }

    suspend fun friends(): FriendsData {
        val json = call("GET", "/api/friends")
        fun list(name: String): List<User> {
            val array = json.optJSONArray(name) ?: JSONArray()
            return (0 until array.length()).map { parseUser(array.getJSONObject(it)) }
        }
        return FriendsData(list("friends"), list("incoming"), list("outgoing"))
    }

    suspend fun requestFriend(username: String): String = call(
        "POST",
        "/api/friends/request",
        JSONObject().put("username", username),
    ).optString("status", "pending")

    suspend fun answerFriend(userId: String, accept: Boolean) = call(
        "POST",
        "/api/friends/answer",
        JSONObject().put("userId", userId).put("accept", accept),
    )

    suspend fun removeFriend(userId: String) = call("DELETE", "/api/friends/$userId")

    // --------------------------------------------------------------- чаты

    suspend fun chats(): List<Chat> {
        val array = call("GET", "/api/chats").optJSONArray("chats") ?: JSONArray()
        return (0 until array.length()).map { parseChat(array.getJSONObject(it)) }
    }

    suspend fun directChat(username: String): Chat = parseChat(
        call("POST", "/api/chats/dm", JSONObject().put("username", username)).getJSONObject("chat"),
    )

    suspend fun createRoom(title: String, description: String = "", isPublic: Boolean = true): Chat = parseChat(
        call(
            "POST",
            "/api/chats/room",
            JSONObject().put("title", title).put("description", description).put("isPublic", isPublic),
        ).getJSONObject("chat"),
    )

    suspend fun joinByCode(code: String): Chat = parseChat(
        call("POST", "/api/chats/join", JSONObject().put("code", code)).getJSONObject("chat"),
    )

    suspend fun joinPublic(chatId: String): Chat = parseChat(
        call("POST", "/api/chats/$chatId/join").getJSONObject("chat"),
    )

    suspend fun rooms(): List<Room> {
        val array = call("GET", "/api/rooms").optJSONArray("rooms") ?: JSONArray()
        return (0 until array.length()).map { index ->
            val o = array.getJSONObject(index)
            Room(
                id = o.optString("id"),
                title = o.optString("title"),
                memberCount = o.optInt("memberCount"),
                isMember = o.optBoolean("isMember"),
                lastMessage = o.optJSONObject("lastMessage")?.let(::parseMessage),
            )
        }
    }

    // ------------------------------------------------------------ сообщения

    suspend fun messages(chatId: String, after: Long): Pair<Chat?, List<Message>> {
        val json = call("GET", "/api/chats/$chatId/messages?after=$after")
        val array = json.optJSONArray("messages") ?: JSONArray()
        val messages = (0 until array.length()).map { parseMessage(array.getJSONObject(it)) }
        val chat = json.optJSONObject("chat")?.let(::parseChat)
        return chat to messages
    }

    suspend fun sendText(chatId: String, text: String): Message = parseMessage(
        call("POST", "/api/chats/$chatId/messages", JSONObject().put("text", text))
            .getJSONObject("message"),
    )

    suspend fun sendVoice(chatId: String, base64: String, seconds: Int): Message = parseMessage(
        call(
            "POST",
            "/api/chats/$chatId/messages",
            JSONObject().put(
                "audio",
                JSONObject()
                    .put("data", base64)
                    .put("mime", "audio/mp4")
                    .put("duration", seconds),
            ),
        ).getJSONObject("message"),
    )
}
