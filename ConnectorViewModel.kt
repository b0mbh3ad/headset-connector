package com.headsetconnector.vm

import android.app.Application
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.headsetconnector.BuildConfig
import com.headsetconnector.audio.VoicePlayer
import com.headsetconnector.audio.VoiceRecorder
import com.headsetconnector.data.Api
import com.headsetconnector.data.AppTheme
import com.headsetconnector.data.AppUpdate
import com.headsetconnector.data.Chat
import com.headsetconnector.data.FriendsData
import com.headsetconnector.data.Me
import com.headsetconnector.data.Message
import com.headsetconnector.data.Prefs
import com.headsetconnector.data.Room
import com.headsetconnector.data.User
import com.headsetconnector.net.ServerFinder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ConnectorViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = Prefs(application)
    private val recorder = VoiceRecorder(application)
    private val player = VoicePlayer(application)

    val session = MutableStateFlow<Me?>(null)
    val serverUrl = MutableStateFlow("")
    val chats = MutableStateFlow<List<Chat>>(emptyList())
    val friends = MutableStateFlow(FriendsData())
    val rooms = MutableStateFlow<List<Room>>(emptyList())
    val searchResults = MutableStateFlow<List<User>>(emptyList())
    val currentChat = MutableStateFlow<Chat?>(null)
    val messages = MutableStateFlow<List<Message>>(emptyList())
    val busy = MutableStateFlow<String?>(null)
    val message = MutableStateFlow<String?>(null)
    val discovering = MutableStateFlow(false)
    val foundServers = MutableStateFlow<List<String>>(emptyList())
    val searchingUsers = MutableStateFlow(false)
    val recording = MutableStateFlow(false)
    val recordingSeconds = MutableStateFlow(0)
    val playing = MutableStateFlow<String?>(null)
    val updateAvailable = MutableStateFlow<AppUpdate?>(null)
    val theme = MutableStateFlow(if (prefs.theme == "material") AppTheme.MATERIAL else AppTheme.NORMAL)
    val agentPin = MutableStateFlow(prefs.agentPin)

    private var pollJob: Job? = null
    private var chatJob: Job? = null
    private var lastQuery = ""

    init {
        player.onComplete = { playing.value = null }

        // Если APK собран с адресом сервера (gradle -Phc.server=...), он уже
        // прописан — пользователю остаётся только войти или создать аккаунт.
        var url = prefs.baseUrl
        if (url.isBlank() && BuildConfig.DEFAULT_SERVER.isNotBlank()) {
            url = BuildConfig.DEFAULT_SERVER.trimEnd('/')
            prefs.baseUrl = url
        }
        if (url.isNotBlank()) {
            Api.baseUrl = url
            serverUrl.value = url
        }
        val saved = prefs.token
        if (!saved.isNullOrBlank() && url.isNotBlank()) {
            Api.token = saved
            session.value = Me(
                id = prefs.userId,
                username = prefs.username,
                displayName = prefs.displayName,
                avatar = prefs.avatar,
                email = prefs.email,
                headset = prefs.headset,
                headsetLabel = prefs.headsetLabel,
                bio = prefs.bio,
                accent = prefs.accent,
                banner = prefs.banner,
                badge = prefs.badge,
            )
            startPolling()
            viewModelScope.launch {
                runCatching { Api.me() }.onSuccess { saveMe(it) }
            }
        }
    }

    // -------------------------------------------------------------- сервер

    fun setServer(raw: String): Boolean {
        var url = raw.trim()
        if (url.isEmpty()) {
            message.value = "Введите адрес сервера"
            return false
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
        url = url.trimEnd('/')
        Api.baseUrl = url
        prefs.baseUrl = url
        serverUrl.value = url
        return true
    }

    fun discoverServers() {
        if (discovering.value) return
        discovering.value = true
        viewModelScope.launch {
            try {
                val found = ServerFinder.find()
                foundServers.value = found
                if (found.isEmpty()) {
                    message.value = "Сервер не найден. Проверьте, что он запущен и вы в одной Wi-Fi сети."
                } else if (found.size == 1) {
                    setServer(found.first())
                }
            } catch (e: Exception) {
                message.value = e.message ?: "Поиск не удался"
            } finally {
                discovering.value = false
            }
        }
    }

    // -------------------------------------------------------------- аккаунт

    fun login(username: String, password: String) {
        if (busy.value != null) return
        viewModelScope.launch {
            busy.value = "Вход…"
            try {
                val (token, me) = Api.login(username.trim(), password)
                applySession(token, me)
            } catch (e: Exception) {
                message.value = e.message ?: "Не удалось войти"
            } finally {
                busy.value = null
            }
        }
    }

    fun register(username: String, password: String, displayName: String, email: String, headset: String) {
        if (busy.value != null) return
        viewModelScope.launch {
            busy.value = "Создаю аккаунт…"
            try {
                val (token, me) = Api.register(
                    username.trim(),
                    password,
                    displayName.trim().ifBlank { username.trim() },
                    email.trim(),
                    headset,
                )
                applySession(token, me)
            } catch (e: Exception) {
                message.value = e.message ?: "Не удалось зарегистрироваться"
            } finally {
                busy.value = null
            }
        }
    }

    private fun applySession(token: String, me: Me) {
        Api.token = token
        prefs.token = token
        saveMe(me)
        session.value = me
        startPolling()
        refreshAll()
    }

    private fun saveMe(me: Me) {
        prefs.userId = me.id
        prefs.username = me.username
        prefs.displayName = me.displayName
        prefs.avatar = me.avatar
        prefs.email = me.email
        prefs.headset = me.headset
        prefs.headsetLabel = me.headsetLabel
        prefs.bio = me.bio
        prefs.accent = me.accent
        prefs.banner = me.banner
        prefs.badge = me.badge
        session.value = me
    }

    fun logout() {
        pollJob?.cancel()
        chatJob?.cancel()
        recorder.cancel()
        player.stop()
        Api.token = null
        prefs.clearSession()
        session.value = null
        currentChat.value = null
        messages.value = emptyList()
        chats.value = emptyList()
        friends.value = FriendsData()
        rooms.value = emptyList()
        searchResults.value = emptyList()
    }

    fun refreshMe() {
        viewModelScope.launch {
            runCatching { Api.me() }.onSuccess { saveMe(it) }
        }
    }

    fun saveDisplayName(name: String) {
        val value = name.trim()
        if (value.isEmpty()) {
            message.value = "Имя не может быть пустым"
            return
        }
        viewModelScope.launch {
            try {
                saveMe(Api.updateDisplayName(value))
                message.value = "Имя обновлено"
            } catch (e: Exception) {
                message.value = e.message
            }
        }
    }

    fun saveAvatar(base64: String) {
        viewModelScope.launch {
            try {
                saveMe(Api.uploadAvatar(base64))
                message.value = "Аватар обновлён"
            } catch (e: Exception) {
                message.value = e.message
            }
        }
    }

    fun saveBanner(base64: String) {
        viewModelScope.launch {
            try {
                saveMe(Api.uploadBanner(base64))
                message.value = "Баннер обновлён"
            } catch (e: Exception) {
                message.value = e.message
            }
        }
    }

    fun saveProfile(bio: String?, accent: String?, headset: String?) {
        viewModelScope.launch {
            try {
                saveMe(Api.updateProfile(bio = bio, accent = accent, headset = headset))
                message.value = "Профиль обновлён"
            } catch (e: Exception) {
                message.value = e.message
            }
        }
    }

    // ---------------------------------------------------------------- чаты

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching { chats.value = Api.chats() }
                runCatching { friends.value = Api.friends() }
                delay(4_000)
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            runCatching { chats.value = Api.chats() }
            runCatching { friends.value = Api.friends() }
        }
    }

    fun refreshChats() {
        viewModelScope.launch {
            runCatching { chats.value = Api.chats() }
        }
    }

    fun openChat(chat: Chat) {
        currentChat.value = chat
        messages.value = emptyList()
        chatJob?.cancel()
        chatJob = viewModelScope.launch(Dispatchers.IO) {
            var after = 0L
            while (isActive) {
                try {
                    val (fresh, list) = Api.messages(chat.id, after)
                    if (fresh != null && isActive) {
                        currentChat.value = fresh
                        chats.value = chats.value.map { if (it.id == fresh.id) fresh else it }
                    }
                    if (list.isNotEmpty() && isActive) {
                        val merged = (messages.value + list)
                            .associateBy { it.id }
                            .values
                            .sortedBy { it.createdAt }
                        messages.value = merged
                        after = maxOf(after, list.maxOf { it.createdAt })
                        prefs.setLastRead(chat.id, after)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isActive) message.value = e.message
                }
                delay(2_000)
            }
        }
    }

    fun closeChat() {
        chatJob?.cancel()
        chatJob = null
        currentChat.value = null
        messages.value = emptyList()
        recording.value = false
        recorder.cancel()
    }

    fun sendText(text: String) {
        val chat = currentChat.value ?: return
        val value = text.trim()
        if (value.isEmpty()) return
        viewModelScope.launch {
            try {
                val sent = Api.sendText(chat.id, value)
                messages.value = (messages.value + sent).distinctBy { it.id }.sortedBy { it.createdAt }
                refreshChats()
            } catch (e: Exception) {
                message.value = e.message
            }
        }
    }

    // ------------------------------------------------------------- голос

    fun startRecording(): Boolean {
        if (recording.value) return true
        if (!recorder.start()) {
            message.value = "Не удалось включить микрофон"
            return false
        }
        recording.value = true
        recordingSeconds.value = 0
        viewModelScope.launch {
            while (isActive && recording.value) {
                delay(1_000)
                if (!recording.value) break
                recordingSeconds.value += 1
                if (recordingSeconds.value >= 120) {
                    finishRecording()
                    break
                }
            }
        }
        return true
    }

    fun finishRecording() {
        if (!recording.value) return
        recording.value = false
        val result = recorder.stop()
        if (result == null) {
            message.value = "Запись не удалась"
            return
        }
        val (bytes, seconds) = result
        if (seconds < 1) {
            message.value = "Слишком коротко"
            return
        }
        val chat = currentChat.value ?: return
        viewModelScope.launch {
            try {
                val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val sent = Api.sendVoice(chat.id, encoded, seconds)
                messages.value = (messages.value + sent).distinctBy { it.id }.sortedBy { it.createdAt }
                refreshChats()
            } catch (e: Exception) {
                message.value = e.message
            }
        }
    }

    fun cancelRecording() {
        recording.value = false
        recorder.cancel()
    }

    fun togglePlay(url: String) {
        if (playing.value == url) {
            player.stop()
            playing.value = null
            return
        }
        val full = Api.mediaUrl(url) ?: return
        if (player.play(full)) {
            playing.value = url
        } else {
            message.value = "Не удалось воспроизвести"
        }
    }

    // -------------------------------------------------------------- друзья

    fun searchUsers(query: String) {
        lastQuery = query
        if (query.trim().length < 2) {
            searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            searchingUsers.value = true
            try {
                searchResults.value = Api.searchUsers(query.trim())
            } catch (e: Exception) {
                message.value = e.message
            } finally {
                searchingUsers.value = false
            }
        }
    }

    fun addFriend(username: String) {
        viewModelScope.launch {
            try {
                val status = Api.requestFriend(username)
                message.value = if (status == "accepted") "Теперь вы друзья" else "Заявка отправлена"
                friends.value = Api.friends()
                if (lastQuery.trim().length >= 2) {
                    searchResults.value = Api.searchUsers(lastQuery.trim())
                }
            } catch (e: Exception) {
                message.value = e.message
            }
        }
    }

    fun answerFriend(user: User, accept: Boolean) {
        viewModelScope.launch {
            try {
                Api.answerFriend(user.id, accept)
                friends.value = Api.friends()
            } catch (e: Exception) {
                message.value = e.message
            }
        }
    }

    fun removeFriend(user: User) {
        viewModelScope.launch {
            try {
                Api.removeFriend(user.id)
                friends.value = Api.friends()
            } catch (e: Exception) {
                message.value = e.message
            }
        }
    }

    fun openDirect(username: String) {
        viewModelScope.launch {
            try {
                val chat = Api.directChat(username)
                refreshChats()
                openChat(chat)
            } catch (e: Exception) {
                message.value = e.message
            }
        }
    }

    // ------------------------------------------------------------- комнаты

    fun loadRooms() {
        viewModelScope.launch {
            try {
                rooms.value = Api.rooms()
            } catch (e: Exception) {
                message.value = e.message
            }
        }
    }

    fun createRoom(title: String, description: String = "", isPublic: Boolean = true) {
        val value = title.trim()
        if (value.isEmpty()) {
            message.value = "Введите название комнаты"
            return
        }
        viewModelScope.launch {
            try {
                val chat = Api.createRoom(value, description.trim(), isPublic)
                refreshChats()
                openChat(chat)
                message.value = if (chat.code != null) {
                    "Комната создана. Код: ${chat.code}"
                } else {
                    "Публичная комната создана"
                }
            } catch (e: Exception) {
                message.value = e.message
            }
        }
    }

    fun joinByCode(code: String) {
        val value = code.trim().uppercase()
        if (value.length < 4) {
            message.value = "Введите код комнаты"
            return
        }
        viewModelScope.launch {
            try {
                val chat = Api.joinByCode(value)
                refreshChats()
                openChat(chat)
            } catch (e: Exception) {
                message.value = e.message
            }
        }
    }

    fun joinRoom(room: Room) {
        viewModelScope.launch {
            try {
                val chat = Api.joinPublic(room.id)
                refreshChats()
                openChat(chat)
            } catch (e: Exception) {
                message.value = e.message
            }
        }
    }

    fun clearMessage() {
        message.value = null
    }

    // ------------------------------------------------------------ обновление

    fun checkForUpdate() {
        val url = BuildConfig.UPDATE_URL
        if (url.isBlank()) return
        viewModelScope.launch {
            runCatching { Api.fetchUpdate(url) }.onSuccess { latest ->
                if (latest.versionCode > BuildConfig.VERSION_CODE && latest.apkUrl.isNotBlank()) {
                    updateAvailable.value = latest
                }
            }
        }
    }

    fun dismissUpdate() {
        updateAvailable.value = null
    }

    fun setTheme(value: AppTheme) {
        prefs.theme = if (value == AppTheme.MATERIAL) "material" else "normal"
        theme.value = value
    }

    fun notifyLocal(text: String) {
        message.value = text
    }

    override fun onCleared() {
        pollJob?.cancel()
        chatJob?.cancel()
        recorder.cancel()
        player.stop()
        super.onCleared()
    }
}
