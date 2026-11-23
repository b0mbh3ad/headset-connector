package com.headsetconnector.net

import com.headsetconnector.data.Api
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/** Ищет сервер HeadsetConnector в локальной сети по порту 8787. */
object ServerFinder {

    suspend fun find(port: Int = 8787): List<String> = withContext(Dispatchers.IO) {
        val local = localIpv4() ?: return@withContext emptyList()
        val base = local.substringBeforeLast('.', "")
        if (base.isBlank()) return@withContext emptyList()

        val semaphore = Semaphore(64)
        val found = Collections.synchronizedList(mutableListOf<String>())

        coroutineScope {
            (1..254).map { host ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val ip = "$base.$host"
                        val url = "http://$ip:$port"
                        if (Api.ping(url)) found.add(url)
                    }
                }
            }.awaitAll()
        }
        found.sortedBy { it.substringAfter("http://").substringBefore(":") }
    }

    fun localIpv4(): String? {
        val addresses = mutableListOf<Pair<String, String>>()
        for (networkInterface in Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!networkInterface.isUp || networkInterface.isLoopback) continue
            for (address in Collections.list(networkInterface.inetAddresses)) {
                val host = address.hostAddress ?: continue
                if (address is Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress) {
                    addresses.add(networkInterface.name to host)
                }
            }
        }
        // На телефоне может быть VPN: сначала ищем обычный Wi-Fi.
        return addresses.firstOrNull { it.first.startsWith("wlan") }?.second
            ?: addresses.firstOrNull { it.first.startsWith("en") }?.second
            ?: addresses.firstOrNull()?.second
    }
}
