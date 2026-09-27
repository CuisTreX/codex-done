package com.cuistre.codexdone

import android.app.Service
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import com.cuistre.codexdone.net.NotifyServer

class NotifyService : Service() {

    private var server: NotifyServer? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            EventLog.add("已停止接收")
            shutdown()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(SERVICE_NOTIFICATION_ID, NotificationHelper.buildServiceNotification(this))
        startServer()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        shutdown()
        super.onDestroy()
    }

    /** 从最近任务里划掉 App 时，系统会顺带杀掉服务 —— 一秒后自己爬起来。 */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (Prefs.autoStart(this)) {
            runCatching {
                val restart = Intent(applicationContext, NotifyService::class.java).setAction(ACTION_START)
                val pending = PendingIntent.getService(
                    applicationContext,
                    1,
                    restart,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                getSystemService(AlarmManager::class.java)?.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, pending)
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startServer() {
        if (server != null) return
        lastAddress = NetInfo.lanIp()
        val instance = NotifyServer(this, HTTP_PORT, DISCOVER_PORT, Prefs.token(this))
        instance.start()
        server = instance
        healthy = instance.httpListening
        acquireWifiLock()
    }

    private fun shutdown() {
        server?.stop()
        server = null
        healthy = false
        runCatching { wifiLock?.release() }
        wifiLock = null
    }

    private fun acquireWifiLock() {
        runCatching {
            val manager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            val lock = manager.createWifiLock(mode, "cuistre:codexdone")
            lock.setReferenceCounted(false)
            lock.acquire()
            wifiLock = lock
        }
    }

    companion object {
        const val HTTP_PORT = 8765
        const val DISCOVER_PORT = 8766
        const val SERVICE_NOTIFICATION_ID = 1001
        const val ACTION_START = "com.cuistre.codexdone.START"
        const val ACTION_STOP = "com.cuistre.codexdone.STOP"

        @Volatile
        var running: Boolean = false
            private set

        /** 服务在跑 ≠ 真的在监听：端口被占时 running=true 但 healthy=false。 */
        @Volatile
        var healthy: Boolean = false
            private set

        @Volatile
        var lastAddress: String? = null
            private set

        fun start(context: Context) {
            val intent = Intent(context, NotifyService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, NotifyService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }
    }
}
