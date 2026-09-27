package com.cuistre.codexdone.net

import java.net.URLDecoder

/**
 * 纯 Kotlin 的 HTTP 请求头解析，不依赖任何 Android API，方便单元测试。
 * 只解析请求行和头部；正文由调用方按 Content-Length 读出来。
 */
class HttpRequestHead(
    val method: String,
    val target: String,
    private val headers: Map<String, String>,
) {
    val path: String get() = target.substringBefore('?')

    val query: Map<String, String>
        get() {
            val raw = target.substringAfter('?', "")
            if (raw.isEmpty()) return emptyMap()
            val map = LinkedHashMap<String, String>()
            for (pair in raw.split('&')) {
                if (pair.isEmpty()) continue
                val key = pair.substringBefore('=').urlDecode()
                val value = pair.substringAfter('=', "").urlDecode()
                map[key] = value
            }
            return map
        }

    fun header(name: String): String? = headers[name.lowercase()]

    companion object {
        fun parse(head: String): HttpRequestHead? {
            val lines = head.split("\r\n", "\n")
            val requestLine = lines.firstOrNull { it.isNotBlank() }?.trim() ?: return null
            val parts = requestLine.split(" ")
            if (parts.size < 2) return null
            val headers = LinkedHashMap<String, String>()
            for (index in 1 until lines.size) {
                val line = lines[index]
                if (line.isBlank()) continue
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val name = line.substring(0, colon).trim().lowercase()
                    val value = line.substring(colon + 1).trim()
                    headers[name] = value
                }
            }
            return HttpRequestHead(parts[0].uppercase(), parts[1].trim(), headers)
        }
    }
}

private fun String.urlDecode(): String = try {
    URLDecoder.decode(this, "UTF-8")
} catch (e: Exception) {
    this
}
