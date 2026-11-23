package com.headsetconnector.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        cancel()
        val target = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        return try {
            val media = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            media.setAudioSource(MediaRecorder.AudioSource.MIC)
            media.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            media.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            media.setAudioSamplingRate(44_100)
            media.setAudioEncodingBitRate(64_000)
            media.setAudioChannels(1)
            media.setOutputFile(target.absolutePath)
            media.prepare()
            media.start()
            recorder = media
            file = target
            startedAt = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            runCatching { recorder?.release() }
            recorder = null
            file?.delete()
            file = null
            false
        }
    }

    /** Возвращает байты записи и длительность в секундах. */
    fun stop(): Pair<ByteArray, Int>? {
        val media = recorder ?: return null
        recorder = null
        val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtLeast(1)
        val target = file
        file = null
        return try {
            media.stop()
            media.release()
            val bytes = target?.readBytes()
            target?.delete()
            if (bytes == null || bytes.isEmpty()) null else bytes to seconds
        } catch (e: Exception) {
            runCatching { media.release() }
            target?.delete()
            null
        }
    }

    fun cancel() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        file?.delete()
        file = null
    }
}
