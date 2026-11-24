package com.headsetconnector.voice

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.headsetconnector.data.Api
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.TimeUnit

/**
 * Состояние голосовой комнаты для интерфейса.
 * active — мы уже в комнате и звук идёт, connecting — подключаемся.
 */
data class VoiceState(
    val roomId: String? = null,
    val roomTitle: String = "",
    val connecting: Boolean = false,
    val active: Boolean = false,
    val muted: Boolean = false,
    val peers: List<String> = emptyList(),
    val error: String? = null,
)

/**
 * Живой голос в комнате: WebRTC mesh (до ~6 человек) и сигналинг через наш сервер.
 * Звук идёт напрямую между устройствами, сервер только знакомит их.
 */
object VoiceManager {
    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var session: VoiceSession? = null

    fun join(context: Context, roomId: String, roomTitle: String, myId: String?) {
        leave()
        val token = Api.token ?: run {
            _state.value = VoiceState(error = "Need account")
            return
        }
        if (myId.isNullOrBlank()) {
            _state.value = VoiceState(error = "Profile is loading")
            return
        }
        if (Api.baseUrl.isBlank()) {
            _state.value = VoiceState(error = "Server not configured")
            return
        }
        session = VoiceSession(
            context.applicationContext,
            roomId,
            roomTitle,
            myId,
            token,
            onUpdate = { _state.value = it },
        ).also { it.start() }
    }

    fun leave() {
        session?.close()
        session = null
        _state.value = VoiceState()
    }

    fun toggleMute() {
        session?.toggleMute()
    }
}

private class VoiceSession(
    private val context: Context,
    private val roomId: String,
    private val roomTitle: String,
    private val myId: String?,
    private val token: String,
    private val onUpdate: (VoiceState) -> Unit,
) {
    private var state = VoiceState(roomId = roomId, roomTitle = roomTitle, connecting = true)
    private var closed = false

    private val peers = mutableMapOf<String, PeerConnection>()
    private val names = mutableMapOf<String, String>()
    private val pendingIce = mutableMapOf<String, MutableList<IceCandidate>>()
    private val remoteTracks = mutableMapOf<String, AudioTrack?>()

    private var ws: WebSocket? = null
    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var factory: PeerConnectionFactory? = null
    private var audioModule: JavaAudioDeviceModule? = null
    private var audioSource: AudioSource? = null
    private var localTrack: AudioTrack? = null
    private var audioManager: AudioManager? = null

    private fun push(change: (VoiceState) -> VoiceState) {
        state = change(state)
        onUpdate(state)
    }

    fun start() {
        try {
            initAudio()
        } catch (e: Exception) {
            Log.e(TAG, "audio init failed", e)
            push { it.copy(connecting = false, error = "Failed to enable mic") }
            return
        }

        val url = wsUrl(Api.baseUrl)
        ws = http.newWebSocket(Request.Builder().url(url).build(), listener)
    }

    fun toggleMute() {
        val track = localTrack ?: return
        val muted = !state.muted
        track.setEnabled(!muted)
        push { it.copy(muted = muted) }
    }

    fun close() {
        if (closed) return
        closed = true
        runCatching { ws?.send(JSONObject().put("type", "leave").toString()) }
        runCatching { ws?.close(1000, "bye") }
        ws = null
        peers.keys.toList().forEach { dropPeer(it) }
        runCatching { localTrack?.dispose() }
        runCatching { audioSource?.dispose() }
        runCatching { factory?.dispose() }
        runCatching { audioModule?.release() }
        localTrack = null
        audioSource = null
        factory = null
        audioModule = null
        runCatching {
            audioManager?.let {
                it.isSpeakerphoneOn = false
                it.mode = AudioManager.MODE_NORMAL
            }
        }
        audioManager = null
        http.dispatcher.executorService.shutdown()
    }

    // ------------------------------------------------------------- WebRTC

    private fun initAudio() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions(),
        )
        audioModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioModule)
            .createPeerConnectionFactory()

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }
        audioSource = factory?.createAudioSource(constraints)
        localTrack = factory?.createAudioTrack("hc-mic", audioSource)

        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        runCatching {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager?.isSpeakerphoneOn = true
        }
    }

    private fun rtcConfig(): PeerConnection.RTCConfiguration {
        val ice = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            // Публичный TURN-релей на случай строгого NAT: без него звонок
            // между разными сетями может не подняться.
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer(),
        )
        return PeerConnection.RTCConfiguration(ice).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
    }

    private fun ensurePeer(peerId: String, initiator: Boolean): PeerConnection? {
        peers[peerId]?.let { return it }
        val pc = factory?.createPeerConnection(rtcConfig(), observer(peerId)) ?: return null
        peers[peerId] = pc
        pendingIce[peerId] = mutableListOf()
        localTrack?.let { pc.addTrack(it, listOf(STREAM_ID)) }
        if (initiator) sendOffer(peerId, pc)
        return pc
    }

    private fun dropPeer(peerId: String) {
        peers.remove(peerId)?.let {
            runCatching { it.close() }
            runCatching { it.dispose() }
        }
        pendingIce.remove(peerId)
        remoteTracks.remove(peerId)
        names.remove(peerId)
        push { it.copy(peers = names.values.toList()) }
    }

    private fun observer(peerId: String) = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            val msg = JSONObject()
                .put("type", "ice")
                .put("to", peerId)
                .put("candidate", candidate.sdp)
                .put("sdpMid", candidate.sdpMid ?: JSONObject.NULL)
                .put("sdpMLineIndex", candidate.sdpMLineIndex)
            send(msg)
        }

        override fun onTrack(transceiver: RtpTransceiver) {
            val track = transceiver.receiver?.track()
            if (track is AudioTrack) {
                track.setEnabled(true)
                remoteTracks[peerId] = track
            }
        }

        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
            val track = receiver.track()
            if (track is AudioTrack) {
                track.setEnabled(true)
                remoteTracks[peerId] = track
            }
        }

        override fun onSignalingChange(newState: PeerConnection.SignalingState) {}
        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
        override fun onAddStream(stream: MediaStream) {
            stream.audioTracks.firstOrNull()?.let {
                it.setEnabled(true)
                remoteTracks[peerId] = it
            }
        }

        override fun onRemoveStream(stream: MediaStream) {}
        override fun onDataChannel(dataChannel: DataChannel) {}
        override fun onRenegotiationNeeded() {}
    }

    private fun sendOffer(peerId: String, pc: PeerConnection) {
        pc.createOffer(
            object : SimpleSdpObserver() {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    pc.setLocalDescription(
                        object : SimpleSdpObserver() {
                            override fun onSetSuccess() {
                                sendOfferSdp(peerId, sdp)
                            }
                        },
                        sdp,
                    )
                }
            },
            MediaConstraints(),
        )
    }

    private fun sendOfferSdp(peerId: String, sdp: SessionDescription) {
        send(
            JSONObject()
                .put("type", "offer")
                .put("to", peerId)
                .put("sdp", sdp.description),
        )
    }

    private fun handleOffer(from: String, sdp: String) {
        val pc = ensurePeer(from, initiator = false) ?: return
        pc.setRemoteDescription(
            object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    pc.createAnswer(
                        object : SimpleSdpObserver() {
                            override fun onCreateSuccess(answer: SessionDescription) {
                                pc.setLocalDescription(
                                    object : SimpleSdpObserver() {
                                        override fun onSetSuccess() {
                                            send(
                                                JSONObject()
                                                    .put("type", "answer")
                                                    .put("to", from)
                                                    .put("sdp", answer.description),
                                            )
                                        }
                                    },
                                    answer,
                                )
                            }
                        },
                        MediaConstraints(),
                    )
                    flushIce(from, pc)
                }
            },
            SessionDescription(SessionDescription.Type.OFFER, sdp),
        )
    }

    private fun handleAnswer(from: String, sdp: String) {
        val pc = peers[from] ?: return
        pc.setRemoteDescription(
            object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    flushIce(from, pc)
                }
            },
            SessionDescription(SessionDescription.Type.ANSWER, sdp),
        )
    }

    private fun handleIce(from: String, msg: JSONObject) {
        val pc = peers[from] ?: return
        val candidate = IceCandidate(
            if (msg.isNull("sdpMid")) null else msg.optString("sdpMid"),
            msg.optInt("sdpMLineIndex", 0),
            msg.optString("candidate"),
        )
        if (pc.remoteDescription == null) {
            pendingIce.getOrPut(from) { mutableListOf() }.add(candidate)
        } else {
            pc.addIceCandidate(candidate)
        }
    }

    private fun flushIce(peerId: String, pc: PeerConnection) {
        pendingIce.remove(peerId)?.forEach { pc.addIceCandidate(it) }
    }

    // -------------------------------------------------------------- сокет

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            send(
                JSONObject()
                    .put("type", "join")
                    .put("token", token)
                    .put("roomId", roomId),
            )
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (msg.optString("type")) {
                "joined" -> {
                    val users = msg.optJSONArray("users") ?: JSONArray()
                    val known = mutableListOf<String>()
                    for (i in 0 until users.length()) {
                        val user = users.optJSONObject(i) ?: continue
                        val id = user.optString("id")
                        if (id.isBlank() || id == myId) continue
                        names[id] = user.optString("displayName").ifBlank { user.optString("username") }
                        known.add(id)
                    }
                    push {
                        it.copy(
                            connecting = false,
                            active = true,
                            peers = names.values.toList(),
                            error = null,
                        )
                    }
                    // Новый участник сам звонит тем, кто уже в комнате.
                    known.forEach { peerId -> ensurePeer(peerId, initiator = true) }
                }

                "peer-joined" -> {
                    val user = msg.optJSONObject("user") ?: return
                    val id = user.optString("id")
                    if (id.isBlank() || id == myId) return
                    names[id] = user.optString("displayName").ifBlank { user.optString("username") }
                    push { it.copy(peers = names.values.toList()) }
                }

                "peer-left" -> {
                    val id = msg.optString("userId")
                    if (id.isNotBlank()) dropPeer(id)
                }

                "offer" -> {
                    val from = msg.optString("from")
                    if (from.isNotBlank()) handleOffer(from, msg.optString("sdp"))
                }

                "answer" -> {
                    val from = msg.optString("from")
                    if (from.isNotBlank()) handleAnswer(from, msg.optString("sdp"))
                }

                "ice" -> {
                    val from = msg.optString("from")
                    if (from.isNotBlank()) handleIce(from, msg)
                }

                "error" -> {
                    push {
                        it.copy(
                            connecting = false,
                            active = false,
                            error = msg.optString("error").ifBlank { "Voice room error" },
                        )
                    }
                    close()
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (closed) return
            Log.w(TAG, "ws failure", t)
            push {
                it.copy(
                    connecting = false,
                    active = false,
                    error = "Lost connection to room",
                )
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (closed) return
            push { it.copy(connecting = false, active = false) }
        }
    }

    private fun send(obj: JSONObject) {
        runCatching { ws?.send(obj.toString()) }
    }

    companion object {
        private const val TAG = "VoiceSession"
        private const val STREAM_ID = "hc-stream"

        private fun wsUrl(baseUrl: String): String {
            val root = baseUrl.trimEnd('/')
            return when {
                root.startsWith("https://") -> "wss://" + root.removePrefix("https://") + "/ws"
                root.startsWith("http://") -> "ws://" + root.removePrefix("http://") + "/ws"
                else -> "wss://$root/ws"
            }
        }
    }
}

private open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String) {
        Log.w("VoiceSession", "sdp create failed: $error")
    }

    override fun onSetFailure(error: String) {
        Log.w("VoiceSession", "sdp set failed: $error")
    }
}
