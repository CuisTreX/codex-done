package com.cuistre.codexdone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机后自动把接收服务拉起来（可在系统里关掉自启动）。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != "android.intent.action.QUICKBOOT_POWERON") return
        if (!Prefs.autoStart(context)) return
        runCatching { NotifyService.start(context) }
        Watchdog.schedule(context)
    }
}
