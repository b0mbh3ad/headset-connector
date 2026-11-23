package com.headsetconnector.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.headsetconnector.MainActivity
import com.headsetconnector.R
import org.json.JSONObject

/**
 * Агент внутри приложения: работает на шлеме, без root. Поднимает локальный
 * HTTP-сервер, к которому подключается телефон (заряд, фото/видео с шлема).
 */
class AgentService : Service() {

    companion object {
        const val PORT = 8788
        const val CHANNEL = "headsetconnector_agent"
        @Volatile var running = false

        fun start(context: Context) {
            val intent = Intent(context, AgentService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private var http: MiniHttp? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())
        val app = applicationContext
        val pin = currentPin()
        http = MiniHttp(PORT) { path, query ->
            if (query["pin"] != pin) {
                MiniHttp.Response(403, "application/json", "{\"error\":\"неверный код\"}".toByteArray())
            } else {
                when (path) {
                    "/ping" -> json(
                        mapOf(
                            "app" to "HeadsetConnector",
                            "model" to DeviceInfo.model(),
                            "battery" to DeviceInfo.battery(app),
                            "charging" to DeviceInfo.charging(app),
                            "headset" to true,
                        ),
                    )
                    "/media" -> MediaProvider.list(app, query["type"])
                    "/media/file" -> MediaProvider.file(app, query["id"])
                    else -> MiniHttp.Response(404, "application/json", "{\"error\":\"not found\"}".toByteArray())
                }
            }
        }
        http?.start()
        running = true
    }

    private fun currentPin(): String {
        val sp = getSharedPreferences("headsetconnector", Context.MODE_PRIVATE)
        var pin = sp.getString("agent_pin", null)
        if (pin == null) {
            pin = (100000..999999).random().toString()
            sp.edit().putString("agent_pin", pin).apply()
        }
        return pin
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        http?.stop()
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun json(map: Map<String, Any?>): MiniHttp.Response {
        val obj = JSONObject()
        map.forEach { (key, value) -> obj.put(key, value) }
        return MiniHttp.Response(200, "application/json; charset=utf-8", obj.toString().toByteArray())
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Агент HeadsetConnector", NotificationManager.IMPORTANCE_LOW),
        )
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("HeadsetConnector")
            .setContentText("Агент запущен — телефон может подключиться")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }
}
