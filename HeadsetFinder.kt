package com.headsetconnector.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.net.URLEncoder
import java.util.Collections

/**
 * Ищет шлем (агента на порту 8788) в локальной сети.
 * Телефон вводит только 6-значный код — сканируем сеть и находим того,
 * кто отвечает на этот код. IP запоминается автоматически, вводить его не нужно.
 */
object HeadsetFinder {

    private const val AGENT_PORT = 8788

    suspend fun findAgent(pin: String, port: Int = AGENT_PORT): String? = withContext(Dispatchers.IO) {
        val local = ServerFinder.localIpv4() ?: return@withContext null
        val base = local.substringBeforeLast('.', "")
        if (base.isBlank()) return@withContext null

        val semaphore = Semaphore(64)
        val found = Collections.synchronizedList(mutableListOf<String>())

        coroutineScope {
            (1..254).map { host ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        if (pingAgent("$base.$host", port, pin)) found.add("$base.$host")
                    }
                }
            }.awaitAll()
        }
        found.firstOrNull()
    }


    fun pingAgent(ip: String, port: Int, pin: String, timeoutMs: Int = 1500): Boolean = runCatching {
        val url = URL("http://$ip:$port/ping?pin=" + URLEncoder.encode(pin, "UTF-8"))
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        body.contains("\"headset\":true")
    }.getOrNull() ?: false
}
