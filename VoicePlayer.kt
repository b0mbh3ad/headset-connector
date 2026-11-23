package com.headsetconnector.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

class VoicePlayer(private val context: Context) {

    private var player: MediaPlayer? = null

    var onComplete: (() -> Unit)? = null

    fun play(url: String): Boolean {
        stop()
        val media = MediaPlayer()
        return try {
            media.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            media.setDataSource(url)
            media.setOnCompletionListener {
                stop()
                onComplete?.invoke()
            }
            media.setOnErrorListener { _, _, _ ->
                stop()
                onComplete?.invoke()
                true
            }
            media.prepareAsync()
            media.setOnPreparedListener { it.start() }
            player = media
            true
        } catch (e: Exception) {
            runCatching { media.release() }
            false
        }
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }
}
