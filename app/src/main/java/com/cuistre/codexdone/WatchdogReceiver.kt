package com.cuistre.codexdone

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 看门狗：国产 ROM 会在息屏/清后台时把前台服务干掉（实测小米 HyperOS 会），
 * 这里每隔一会儿检查一次，发现服务没了就把它拉回来。
 */
class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (NotifyService.running && NotifyService.healthy) return
        if (NotifyService.running) {
            // 进程在、但端口没监听（上一版进程卡着端口、或者绑定失败）——先停再拉一次
            EventLog.add("看门狗：服务在跑但没在监听，重启它")
            runCatching { NotifyService.stop(context) }
            val restart = Intent(context, NotifyService::class.java).setAction(NotifyService.ACTION_START)
            val pending = PendingIntent.getService(
                context,
                3,
                restart,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            runCatching {
                context.getSystemService(AlarmManager::class.java)
                    ?.set(AlarmManager.RTC, System.currentTimeMillis() + 1500, pending)
            }
            return
        }
        EventLog.add("看门狗：服务没在跑，正在重新拉起")
        runCatching { NotifyService.start(context) }
    }
}

object Watchdog {
    private const val INTERVAL_MS = 60_000L
    private const val REQUEST_CODE = 2001

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WatchdogReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun schedule(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching {
            val first = System.currentTimeMillis() + INTERVAL_MS
            manager.setInexactRepeating(AlarmManager.RTC_WAKEUP, first, INTERVAL_MS, pendingIntent(context))
        }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { manager.cancel(pendingIntent(context)) }
    }
}
