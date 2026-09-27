# 我的codex做完没

作者：**Cuistre** ｜ 包名：`com.cuistre.codexdone` ｜ 版本：1.0.1

一个只干一件事的安卓 App：**Codex 在电脑上跑完任务，让手机弹通知、让小米手环震一下。**
不经过任何第三方推送服务 —— 电脑和手机在同一个 WiFi 下直连，消息只在自己家里走。

## 它怎么工作

```
Codex 任务结束
   ↓ notify 钩子（F:\CodeX\01-开发项目\DEV-2026-017-Codex手环提醒）
电脑上的 cuistre-notify.ps1
   ↓ 先 UDP 广播找手机（8766），再 HTTP POST 通知（8765，带令牌）
本 App 收到 → 弹一条高优先级通知
   ↓ 小米运动健康把「我的codex做完没」的通知转给手环
小米手环 9 震动
```

## 安装

APK 在 `outputs\` 里，装 release 那个：

1. 把 `outputs\codexdone-release.apk` 传到手机（数据线、微信文件传输助手都行）。
2. 手机上点开安装；小米可能会提示「未知来源」，按提示允许一次。
3. 打开 App，首屏会请求通知权限，同意。
4. 点 **开始接收**：顶部变成「接收服务：运行中」，下面显示监听地址和令牌。

## 首次配置（三步）

1. **权限**：App 里三个按钮点一遍 —— 通知权限、电池优化白名单、自启动设置（HyperOS 上这三项不开，服务会被系统杀掉）。
2. **配对**：点 **复制电脑端配置片段**，把内容发给电脑，粘进
   `F:\CodeX\01-开发项目\DEV-2026-017-Codex手环提醒\config.json` 的 `"cuistre"` 段（`channel` 设成 `"cuistre"`）。
3. **装钩子并测试**（在电脑上）：

```powershell
cd 'F:\CodeX\01-开发项目\DEV-2026-017-Codex手环提醒'
pwsh -NoProfile -File .\install.ps1
pwsh -NoProfile -File .\cuistre-notify.ps1 -Test
```

手机应该立刻弹通知，手环跟着震一下。

## 让手环震动的最后一步

小米运动健康 → 设备 → 小米手环 9 → **应用通知** → 把「我的codex做完没」勾上；手环别开勿扰、别开睡眠模式。

## 协议（想自己接别的程序时看）

| 接口 | 说明 |
| --- | --- |
| `GET /ping` | 探活，不需要令牌，返回 `{"ok":true,"app":"我的codex做完没"}` |
| `GET /info` | 需要令牌，返回端口等信息 |
| `POST /notify` | 需要令牌；正文 `{"title":"...","text":"..."}`，也接受纯文本 |

- 令牌两种传法：请求头 `X-Cuistre-Token: <令牌>`，或 `?token=<令牌>`。
- 只接受内网来源（10./172.16-31./192.168./127./169.254.），外网来源直接 403。
- 自动发现：向 UDP 8766 广播 `CUISTRE_DISCOVER`，App 回 `CUISTRE_HERE <端口>`。
- 正文超过 200 字会被截断，避免通知太长。

## 省电与后台

- 服务是**前台服务**（有常驻通知），配合 WiFi 锁，屏幕关掉也能收。
- HyperOS 必须做三件事：通知权限、电池优化白名单、「无限制」后台 + 允许自启动；再在**最近任务里给 App 上锁**（下拉卡片点锁）。
- 手机重启后会自动拉起（可在系统里关掉自启动权限来禁用它）。
- **1.0.1 起有两层保活**：从最近任务划掉 App 时服务会自动重启（`onTaskRemoved`）；万一还是被系统清掉，
  看门狗每分钟检查一次并把它拉回来。被清掉的那段时间 App 里会显示「未启动」，一到两分钟内会自己恢复。

## 开发

```powershell
# 编译 debug + release，产物自动复制到 outputs\
pwsh -NoProfile -File tools\build.ps1

# 跑单元测试（HTTP 解析、正文裁剪、令牌格式）
pwsh -NoProfile -File tools\run-tests.ps1
```

- 工具链：JDK 11（`D:\Programs\jdk-11`）、Android SDK（`D:\Programs\android-sdk`）、Gradle 7.6.4、AGP 7.4.2、Kotlin 1.8.22。
- 零第三方依赖：只用 Android 原生 API（没有 AndroidX / Compose），所以 release 包体很小（约 50 KB）。
- 项目路径含中文，`gradle.properties` 里必须保留 `android.overridePathCheck=true`；跑测试要用 `tools\run-tests.ps1`（它建 ASCII Junction，否则测试 worker 加载不了类）。

## 已知限制

- 电脑和手机必须在**同一个 WiFi**，且路由器没开「AP 隔离」。
- 手机换 WiFi、IP 变了不用管 —— 电脑端会先广播自动发现；实在发现不了会退回上次记下的地址（`logs\phone-host.txt`）。
- 令牌重置后，电脑端 `config.json` 里的 `cuistre.token` 要同步更新。
