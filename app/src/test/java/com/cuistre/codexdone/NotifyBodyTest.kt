package com.cuistre.codexdone

import com.cuistre.codexdone.net.NotifyBody
import org.junit.Assert.assertEquals
import org.junit.Test

class NotifyBodyTest {

    @Test
    fun readsJsonTitleAndText() {
        val (title, text) = NotifyBody.parse("""{"title":"Codex 完成 · 手环提醒","text":"已经写完脚本并跑通测试。"}""")
        assertEquals("Codex 完成 · 手环提醒", title)
        assertEquals("已经写完脚本并跑通测试。", text)
    }

    @Test
    fun fallsBackToPlainText() {
        val (title, text) = NotifyBody.parse("Codex 跑完了")
        assertEquals(NotifyBody.DEFAULT_TITLE, title)
        assertEquals("Codex 跑完了", text)
    }

    @Test
    fun acceptsMessageFieldWhenTextMissing() {
        val (title, text) = NotifyBody.parse("""{"message":"来自 message 字段"}""")
        assertEquals(NotifyBody.DEFAULT_TITLE, title)
        assertEquals("来自 message 字段", text)
    }

    @Test
    fun clipsLongTextAndFlattensNewlines() {
        val long = "一".repeat(500) + "\n\n第二段"
        val clipped = NotifyBody.clip(long)
        assertEquals(NotifyBody.MAX_TEXT + 1, clipped.length)
        assertEquals(false, clipped.contains("\n"))
    }
}
