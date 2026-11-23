package com.headsetconnector.agent

import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * Крошечный HTTP-сервер без зависимостей: только GET, JSON и файлы.
 * Хватает для агента на шлеме (телефон спрашивает заряд и медиа).
 */
class MiniHttp(private val port: Int, private val handler: (String, Map<String, String>) -> Response) {

    data class Response(
        val status: Int = 200,
        val contentType: String = "application/json; charset=utf-8",
        val body: ByteArray = ByteArray(0),
    )

    @Volatile
    private var running = false
    private var server: ServerSocket? = null

    fun start() {
        if (running) return
        running = true
        Thread {
            try {
                val socket = ServerSocket(port)
                server = socket
                while (running) {
                    val client = try {
                        socket.accept()
                    } catch (_: Exception) {
                        break
                    }
                    handle(client)
                }
                try { socket.close() } catch (_: Exception) {}
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true }.start()
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) {}
        try { Socket("127.0.0.1", port).close() } catch (_: Exception) {}
    }

    private fun handle(socket: Socket) {
        Thread {
            try {
                socket.soTimeout = 15_000
                val input = socket.getInputStream()
                val requestLine = readLine(input) ?: ""
                val parts = requestLine.split(" ")
                val method = parts.getOrNull(0) ?: ""
                val rawPath = parts.getOrNull(1) ?: "/"
                val path = rawPath.substringBefore("?")
                val query = parseQuery(rawPath.substringAfter("?", ""))
                val response = if (method == "GET") {
                    handler(path, query)
                } else {
                    Response(404, "application/json; charset=utf-8", "{\"error\":\"not found\"}".toByteArray())
                }
                val reason = if (response.status == 200) "OK" else "ERR"
                val head = "HTTP/1.1 ${response.status} $reason\r\n" +
                    "Content-Type: ${response.contentType}\r\n" +
                    "Content-Length: ${response.body.size}\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\n"
                val out = socket.getOutputStream()
                out.write(head.toByteArray())
                out.write(response.body)
                out.flush()
            } catch (_: Exception) {
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }.apply { isDaemon = true }.start()
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
        }
        return sb.toString()
    }

    private fun parseQuery(q: String): Map<String, String> {
        val map = HashMap<String, String>()
        if (q.isEmpty()) return map
        for (pair in q.split("&")) {
            val kv = pair.split("=", limit = 2)
            map[kv[0]] = URLDecoder.decode(kv.getOrElse(1) { "" }, "UTF-8")
        }
        return map
    }
}
