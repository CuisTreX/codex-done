package com.cuistre.codexdone

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvAddress: TextView
    private lateinit var tvToken: TextView
    private lateinit var tvPerm: TextView
    private lateinit var tvLog: TextView
    private lateinit var tvHint: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvAddress = findViewById(R.id.tvAddress)
        tvToken = findViewById(R.id.tvToken)
        tvPerm = findViewById(R.id.tvPerm)
        tvLog = findViewById(R.id.tvLog)
        tvHint = findViewById(R.id.tvHint)

        NotificationHelper.ensureChannels(this)

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            NotifyService.start(this)
            Watchdog.schedule(this)
            toast("已开始接收")
            handler.postDelayed({ render() }, 400)
        }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            NotifyService.stop(this)
            Watchdog.cancel(this)
            toast("已停止接收")
            handler.postDelayed({ render() }, 400)
        }
        findViewById<Button>(R.id.btnTest).setOnClickListener {
            NotificationHelper.notifyCodex(this, "Cuistre 测试", "看到这条、手环也震了，说明通知链路是通的。")
            EventLog.add("手动发了一条测试通知")
            render()
        }
        findViewById<Button>(R.id.btnCopyConfig).setOnClickListener {
            copyToClipboard(configSnippet(), "配置片段已复制，粘到电脑端 config.json 里")
        }
        findViewById<Button>(R.id.btnCopyAddress).setOnClickListener {
            copyToClipboard(address(), "监听地址已复制")
        }
        findViewById<Button>(R.id.btnResetToken).setOnClickListener { confirmResetToken() }
        findViewById<Button>(R.id.btnNotifPerm).setOnClickListener { ensureNotificationPermission() }
        findViewById<Button>(R.id.btnBattery).setOnClickListener { requestBatteryWhitelist() }
        findViewById<Button>(R.id.btnAutostart).setOnClickListener { openAutostart() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        EventLog.listener = { runOnUiThread { renderLog() } }
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
        EventLog.listener = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) {
            toast(if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) "通知权限已开" else "通知权限被拒绝，手环收不到提醒")
            render()
        }
    }

    private fun render() {
        val running = NotifyService.running
        val healthy = NotifyService.healthy
        when {
            healthy -> {
                tvStatus.text = "接收服务：运行中"
                tvStatus.setTextColor(getColor(R.color.ink))
                tvHint.visibility = android.view.View.GONE
            }
            running -> {
                tvStatus.text = "接收服务：在跑，但没在监听"
                tvStatus.setTextColor(getColor(R.color.orange))
                tvHint.text = "8765 端口没绑上，多半是上一次的进程还卡着：去 系统设置 → 应用 → 我的codex做完没 → 强行停止，再重开本 App 并点「开始接收」。"
                tvHint.visibility = android.view.View.VISIBLE
            }
            else -> {
                tvStatus.text = "接收服务：未启动"
                tvStatus.setTextColor(getColor(R.color.orange))
                tvHint.text = "点上面的「开始接收」；如果点完还是这个状态，就强行停止本 App 再重开。"
                tvHint.visibility = android.view.View.VISIBLE
            }
        }
        tvAddress.text = "监听地址：${address()}"
        tvToken.text = "令牌：${Prefs.token(this)}"
        tvPerm.text = "通知权限：${if (notificationsAllowed()) "已开" else "未开"}    电池白名单：${if (batteryWhitelisted()) "已加" else "未加"}"
        renderLog()
    }

    private fun renderLog() {
        val lines = EventLog.snapshot()
        tvLog.text = if (lines.isEmpty()) "还没有收到任何请求。" else lines.take(6).joinToString("\n")
    }

    private fun address(): String {
        val ip = NotifyService.lastAddress ?: NetInfo.lanIp()
        return if (ip == null) "手机没连 WiFi" else "http://$ip:${NotifyService.HTTP_PORT}"
    }

    private fun configSnippet(): String {
        val ip = NotifyService.lastAddress ?: NetInfo.lanIp() ?: ""
        return buildString {
            append("  \"cuistre\": {\n")
            append("    \"host\": \"$ip\",\n")
            append("    \"port\": ${NotifyService.HTTP_PORT},\n")
            append("    \"token\": \"${Prefs.token(this@MainActivity)}\",\n")
            append("    \"autoDiscover\": true,\n")
            append("    \"discoverPort\": ${NotifyService.DISCOVER_PORT}\n")
            append("  },")
        }
    }

    private fun notificationsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun batteryWhitelisted(): Boolean {
        val power = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return power.isIgnoringBatteryOptimizations(packageName)
    }

    private fun copyToClipboard(text: String, message: String) {
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("cuistre", text))
        toast(message)
    }

    private fun confirmResetToken() {
        AlertDialog.Builder(this)
            .setTitle("重置令牌？")
            .setMessage("重置后需要把新令牌填回电脑端 config.json，否则推送会被拒绝。")
            .setPositiveButton("重置") { _, _ ->
                Prefs.regenToken(this)
                render()
                toast("令牌已重置")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsAllowed()) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            return
        }
        startSafely(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        ) { openAppDetails() }
    }

    private fun requestBatteryWhitelist() {
        startSafely(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:$packageName"))
        ) { openAppDetails() }
    }

    private fun openAutostart() {
        val candidates = listOf(
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.powercenter.PowerSettings",
            ),
        )
        for (intent in candidates) {
            if (startSafely(intent) { }) return
        }
        openAppDetails()
    }

    private fun openAppDetails() {
        startSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$packageName"))) { }
    }

    /** 跳系统页面：能跳就返回 true，跳不动就执行 fallback。 */
    private fun startSafely(intent: Intent, fallback: () -> Unit): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(intent)
            true
        } catch (e: Exception) {
            fallback()
            false
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val REQUEST_NOTIFICATIONS = 1001
    }
}
