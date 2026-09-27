package com.cuistre.codexdone.net

import android.content.Context
import android.util.Log
import com.cuistre.codexdone.EventLog
import com.cuistre.codexdone.NotificationHelper
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 局域网接收端：HTTP 收通知 + UDP 自动发现。
 * 只接受内网来源，并且要带对令牌，否则拒绝。
 */
class NotifyServer(
    private val context: Context,
    private val httpPort: Int,
    private val discoverPort: Int,
    private val token: String,
) {
    private val running = AtomicBoolean(false)
    private val pool = Executors.newFixedThreadPool(4)
    private var serverSocket: ServerSocket? = null
    private var discoverySocket: DatagramSocket? = null

    @Volatile
    var httpListening: Boolean = false
        private set

    @Volatile
    var udpListening: Boolean = false
        private set

    @Volatile
    var lastError: String? = null
        private set

    fun start() {
        running.set(true)
        try {
            serverSocket = ServerSocket(httpPort)
            httpListening = true
            pool.execute { acceptLoop() }
            EventLog.add("开始监听 http://0.0.0.0:$httpPort")
        } catch (e: Exception) {
            httpListening = false
            lastError = "HTTP 端口 $httpPort 启不来：${e.message}"
            EventLog.add(lastError ?: "HTTP 端口启不来")
            Log.e(TAG, "http bind failed", e)
        }
        try {
            discoverySocket = DatagramSocket(discoverPort).apply { broadcast = true }
            udpListening = true
            pool.execute { discoveryLoop() }
            EventLog.add("自动发现已开（UDP $discoverPort）")
        } catch (e: Exception) {
            udpListening = false
            lastError = "UDP 端口 $discoverPort 启不来：${e.message}"
            EventLog.add(lastError ?: "UDP 端口启不来")
            Log.e(TAG, "udp bind failed", e)
        }
    }

    fun stop() {
        running.set(false)
        httpListening = false
        udpListening = false
        runCatching { serverSocket?.close() }
        runCatching { discoverySocket?.close() }
        pool.shutdownNow()
    }

    private fun versionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")

    private fun acceptLoop() {
        while (running.get()) {
            val socket = try {
                serverSocket?.accept() ?: break
            } catch (e: Exception) {
                if (!running.get()) break
                Thread.sleep(120)
                continue
            }
            pool.execute {
                runCatching { handle(socket) }.onFailure { Log.w(TAG, "handle failed", it) }
            }
        }
    }

    private fun discoveryLoop() {
        val buffer = ByteArray(256)
        while (running.get()) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                discoverySocket?.receive(packet) ?: break
            } catch (e: Exception) {
                if (!running.get()) break
                continue
            }
            val message = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
            if (message == DISCOVER_MAGIC) {
                val reply = "$REPLY_PREFIX $httpPort".toByteArray(Charsets.UTF_8)
                runCatching {
                    discoverySocket?.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
                }
            }
        }
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 5000
            val input = BufferedInputStream(client.getInputStream())
            val headBytes = readHead(input) ?: return
            val request = HttpRequestHead.parse(String(headBytes, Charsets.ISO_8859_1)) ?: return
            val length = request.header("content-length")?.toIntOrNull() ?: 0
            val body = if (length > 0) String(readExactly(input, length), Charsets.UTF_8) else ""

            if (!isPrivate(client.inetAddress)) {
                EventLog.add("拒绝外网来源：${client.inetAddress.hostAddress}")
                respond(client, 403, """{"ok":false,"error":"lan only"}""")
                return
            }

            when {
                request.method == "GET" && request.path == "/ping" ->
                    respond(client, 200, """{"ok":true,"app":"$APP_NAME","version":"${versionName()}","port":$httpPort}""")

                request.method == "GET" && request.path == "/info" -> {
                    if (!tokenOk(request)) {
                        respond(client, 401, """{"ok":false,"error":"bad token"}""")
                        return
                    }
                    respond(client, 200, """{"ok":true,"app":"$APP_NAME","version":"${versionName()}","port":$httpPort,"udp":$udpListening}""")
                }

                request.method == "POST" && (request.path == "/notify" || request.path == "/") -> {
                    if (!tokenOk(request)) {
                        EventLog.add("令牌不对，已拒绝 ${client.inetAddress.hostAddress}")
                        respond(client, 401, """{"ok":false,"error":"bad token"}""")
                        return
                    }
                    val (title, text) = NotifyBody.parse(body)
                    NotificationHelper.notifyCodex(context, title, text)
                    EventLog.add("已提醒：$title")
                    respond(client, 200, """{"ok":true}""")
                }

                else -> respond(client, 404, """{"ok":false,"error":"not found"}""")
            }
        }
    }

    private fun tokenOk(request: HttpRequestHead): Boolean {
        val given = request.header("x-cuistre-token") ?: request.query["token"] ?: ""
        return constantTimeEquals(given, token)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val left = a.toByteArray(Charsets.UTF_8)
        val right = b.toByteArray(Charsets.UTF_8)
        if (left.size != right.size) return false
        var diff = 0
        for (index in left.indices) {
            diff = diff or (left[index].toInt() xor right[index].toInt())
        }
        return diff == 0
    }

    private fun isPrivate(address: InetAddress): Boolean =
        address.isSiteLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress

    private fun readHead(input: BufferedInputStream): ByteArray? {
        val buffer = ByteArrayOutputStream()
        var tail = 0
        while (buffer.size() < 8192) {
            val byte = input.read()
            if (byte < 0) return null
            buffer.write(byte)
            tail = ((tail shl 8) or byte) and 0xFFFFFF
            if (tail == 0x0D0A0D0A || (tail and 0xFFFF) == 0x0A0A) return buffer.toByteArray()
        }
        return buffer.toByteArray()
    }

    private fun readExactly(input: BufferedInputStream, length: Int): ByteArray {
        val data = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(data, read, length - read)
            if (count < 0) break
            read += count
        }
        return if (read == length) data else data.copyOf(read)
    }

    private fun respond(socket: Socket, code: Int, json: String) {
        val body = json.toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $code ${reason(code)}\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Connection: close\r\n\r\n"
        runCatching {
            val output = socket.getOutputStream()
            output.write(head.toByteArray(Charsets.ISO_8859_1))
            output.write(body)
            output.flush()
        }
    }

    private fun reason(code: Int): String = when (code) {
        200 -> "OK"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        else -> "Error"
    }

    companion object {
        const val TAG = "CuistreNotify"
        const val DISCOVER_MAGIC = "CUISTRE_DISCOVER"
        const val REPLY_PREFIX = "CUISTRE_HERE"
        const val APP_NAME = "我的codex做完没"
    }
}
