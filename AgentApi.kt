package com.headsetconnector.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Клиент агента на шлеме: телефон спрашивает заряд и медиа. */
object AgentApi {

    data class DeviceInfo(
        val model: String,
        val battery: Int,
        val charging: Boolean,
    )

    data class MediaItem(
        val id: String,
        val name: String,
        val date: Long,
        val size: Long,
    )

    suspend fun device(host: String, pin: String): DeviceInfo = withContext(Dispatchers.IO) {
        val obj = get("http://$host:8788/ping?pin=$pin")
        DeviceInfo(
            model = obj.optString("model", "?"),
            battery = obj.optInt("battery", -1),
            charging = obj.optBoolean("charging", false),
        )
    }

    suspend fun media(host: String, pin: String, type: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val obj = get("http://$host:8788/media?pin=$pin&type=$type")
        val array = obj.optJSONArray("items") ?: JSONArray()
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            MediaItem(
                id = item.optString("id"),
                name = item.optString("name"),
                date = item.optLong("date", 0),
                size = item.optLong("size", 0),
            )
        }
    }

    suspend fun download(host: String, pin: String, id: String): ByteArray = withContext(Dispatchers.IO) {
        val url = URL("http://$host:8788/media/file?pin=$pin&id=" + URLEncoder.encode(id, "UTF-8"))
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 120_000
        connection.inputStream.use { it.readBytes() }.also { connection.disconnect() }
    }

    private fun get(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 6_000
        connection.readTimeout = 8_000
        val text = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        return JSONObject(text)
    }
}
