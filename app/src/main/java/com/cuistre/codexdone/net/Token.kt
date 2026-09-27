package com.cuistre.codexdone.net

import java.security.SecureRandom

object Token {
    private val random = SecureRandom()

    fun random(bytes: Int = 12): String {
        val buffer = ByteArray(bytes)
        random.nextBytes(buffer)
        val builder = StringBuilder(bytes * 2)
        for (b in buffer) {
            val value = b.toInt() and 0xFF
            builder.append(HEX[value ushr 4])
            builder.append(HEX[value and 0x0F])
        }
        return builder.toString()
    }

    private const val HEX = "0123456789abcdef"
}
