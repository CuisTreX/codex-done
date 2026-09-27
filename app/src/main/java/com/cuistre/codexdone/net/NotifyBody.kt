package com.cuistre.codexdone.net

import org.json.JSONObject

/** 解析电脑端推来的正文：优先按 JSON 取 title/text，取不到就当纯文本。 */
object NotifyBody {
    const val MAX_TEXT = 200
    const val DEFAULT_TITLE = "Codex 完成"

    fun parse(body: String): Pair<String, String> {
        val trimmed = body.trim()
        if (trimmed.startsWith("{")) {
            val json = runCatching { JSONObject(trimmed) }.getOrNull()
            if (json != null) {
                val title = json.optString("title", "").trim().ifBlank { DEFAULT_TITLE }
                val text = json.optString("text", "").ifBlank { json.optString("message", "") }
                return title to clip(text)
            }
        }
        return DEFAULT_TITLE to clip(trimmed)
    }

    fun clip(text: String): String {
        val flat = text.replace(Regex("\\s+"), " ").trim()
        if (flat.length <= MAX_TEXT) return flat
        return flat.substring(0, MAX_TEXT) + "…"
    }
}
