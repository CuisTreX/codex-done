package com.cuistre.codexdone

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/** 界面用的“最近收到”列表，最多留 30 条。 */
object EventLog {
    private const val MAX = 30
    private val entries = ArrayDeque<String>()

    @Volatile
    var listener: (() -> Unit)? = null

    @Synchronized
    fun add(line: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        entries.addFirst("$ts  $line")
        while (entries.size > MAX) entries.removeLast()
        listener?.invoke()
    }

    @Synchronized
    fun snapshot(): List<String> = entries.toList()
}
