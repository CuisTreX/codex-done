package com.cuistre.codexdone

import com.cuistre.codexdone.net.HttpRequestHead
import com.cuistre.codexdone.net.Token
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpRequestHeadTest {

    @Test
    fun parsesPostWithHeaders() {
        val head = HttpRequestHead.parse(
            "POST /notify HTTP/1.1\r\nHost: 192.168.1.5:8765\r\nX-Cuistre-Token: abc123\r\nContent-Length: 42\r\n\r\n"
        )
        assertEquals("POST", head?.method)
        assertEquals("/notify", head?.path)
        assertEquals("abc123", head?.header("x-cuistre-token"))
        assertEquals("42", head?.header("content-length"))
    }

    @Test
    fun parsesQueryToken() {
        val head = HttpRequestHead.parse("POST /notify?token=dead%20beef&x=1 HTTP/1.1\n\n")
        assertEquals("/notify", head?.path)
        assertEquals("dead beef", head?.query?.get("token"))
        assertEquals("1", head?.query?.get("x"))
    }

    @Test
    fun rejectsGarbage() {
        assertNull(HttpRequestHead.parse(""))
        assertNull(HttpRequestHead.parse("SOMETHINGWEIRD"))
    }

    @Test
    fun tokenIsHexAndLongEnough() {
        val token = Token.random()
        assertEquals(24, token.length)
        assertTrue(token.all { it in "0123456789abcdef" })
    }
}
