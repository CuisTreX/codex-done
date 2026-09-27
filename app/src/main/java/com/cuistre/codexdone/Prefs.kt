package com.cuistre.codexdone

import android.content.Context
import android.content.SharedPreferences
import com.cuistre.codexdone.net.Token

object Prefs {
    private const val FILE = "cuistre_codexdone"
    private const val KEY_TOKEN = "token"
    private const val KEY_AUTOSTART = "autostart"

    private fun sp(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 首次调用时生成一个随机令牌，电脑端要用同一个。 */
    fun token(context: Context): String {
        val store = sp(context)
        val existing = store.getString(KEY_TOKEN, null)
        if (!existing.isNullOrBlank()) return existing
        val fresh = Token.random()
        store.edit().putString(KEY_TOKEN, fresh).apply()
        return fresh
    }

    fun regenToken(context: Context): String {
        val fresh = Token.random()
        sp(context).edit().putString(KEY_TOKEN, fresh).apply()
        return fresh
    }

    fun autoStart(context: Context): Boolean = sp(context).getBoolean(KEY_AUTOSTART, true)
}
