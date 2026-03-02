package com.android.alinux

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.net.NetworkInterface
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class KeepAliveService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var linuxManager: LinuxManager
    private lateinit var appConfig: AppConfig
    private lateinit var logFile: File
    private var sshdProcess: Process? = null

    @Volatile
    private var isStarting = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        appConfig = AppConfig(assets)
        linuxManager = LinuxManager(application, appConfig)
        logFile = File(getExternalFilesDir(""), "log.txt")
        logFile.parentFile?.mkdirs()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (sshdProcess?.isAlive == true || isStarting) return START_STICKY
        isStarting = true
        serviceScope.launch {
            runCatching { startLinuxContainer() }
                .onFailure { appendLog("Exception ERROR: ${it.message}") }
            isStarting = false
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        sshdProcess?.destroy()
        serviceScope.cancel()
    }

    private fun startLinuxContainer() {
        val timestamp =
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        logFile.writeText("=== AndroidUbuntu Debug Log [$timestamp] ===\n")
        appendLog("[log] Output saved to: ${logFile.absolutePath}")
        val log = LinuxManager.ProgressListener { appendLog(it) }

        // 第一步：安装 Ubuntu rootfs（仅首次运行）
        // 检测容器目录中是否存在 /bin/sh，不存在则从网络下载 ubuntu-base 镜像并解压。
        // 后续启动直接跳过，避免重复解压。
        if (!linuxManager.isInstalled(appConfig.containerId)) {
            linuxManager.install(appConfig.containerId, log)
        } else {
            appendLog("Ubuntu rootfs ready at: ${linuxManager.rootfsDir(appConfig.containerId).absolutePath}")
        }

        // 第二步：同步 assets/scripts 到容器 /root/scripts 并授予执行权限
        // 每次启动都同步，确保脚本与 APK 保持最新（同时自动修正 Windows CRLF 换行符）。
        linuxManager.syncScriptsToContainer(appConfig.containerId, listener = log)

        // 第三步：配置容器 DNS
        // 写入 /etc/resolv.conf（替换可能存在的符号链接），使用 Google 公共 DNS。
        // 必须在 apt-get 之前完成，否则容器无法解析域名导致软件安装失败。
        linuxManager.setupDns(appConfig.containerId, log)

        // 第四步：检测并安装必要的软件包（仅首次或二进制缺失时运行）
        // 检查 /usr/sbin/sshd 与 /usr/bin/ssh-keygen 是否存在。
        // 若不存在，运行 init_packages.sh 通过 apt-get 安装 openssh-server 等软件包。
        // 必须等 init_packages.sh 成功后才能继续，否则后续 sshd 无法启动。
        val installed = linuxManager.ensureBinaries(
            containerId = appConfig.containerId,
            binaries = listOf("/usr/sbin/sshd", "/usr/bin/ssh-keygen"),
            scriptAsset = "init_packages.sh",
            listener = log,
        )
        if (!installed) return  // 软件安装失败，终止启动流程

        // 第五步：配置并启动 SSH 服务
        // 运行 setup_sshd.sh，完成以下工作：
        //   1. 生成 SSH host key（/etc/ssh/ssh_host_rsa_key 等，仅首次）
        //   2. 创建 sshd 隔离用户
        //   3. 设置 root 密码并允许 SSH 密码登录
        //   4. 以前台模式启动 sshd（exec /usr/sbin/sshd -D），进程持续运行
        // 通过环境变量传入端口、密码、用户名，避免硬编码。
        appendLog("Configuring and starting sshd...")
        val process = linuxManager.startScript(
            containerId = appConfig.containerId,
            scriptAsset = "setup_sshd.sh",
            env = mapOf(
                "SSH_PORT" to appConfig.sshPort.toString(),
                "SSH_PASSWORD" to appConfig.sshPassword,
                "SSH_USER" to appConfig.user,
            ),
        )
        sshdProcess = process

        // 等待 1000ms，让 sshd 完成初始化后再检查是否存活
        Thread.sleep(1000)
        if (process.isAlive) {
            val ip = getLocalIpAddress()
            appendLog("=== SSH Server Started ===")
            appendLog("  ssh ${appConfig.user}@$ip -p ${appConfig.sshPort}")
            appendLog("  Password: ${appConfig.sshPassword}")
            appendLog("==========================")
        }

        // 持续读取 sshd 的 stdout/stderr 输出并写入日志，直到进程退出
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { appendLog(it) }
        }
        val exitCode = process.waitFor()
        appendLog("[sshd exited with code $exitCode]")
        sshdProcess = null
    }

    private fun getLocalIpAddress(): String {
        return try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .filterNot { it.isLoopbackAddress }
                .filter { it.address.size == 4 }
                .map { it.hostAddress }
                .firstOrNull() ?: "127.0.0.1"
        } catch (_: Exception) {
            "127.0.0.1"
        }
    }

    private fun appendLog(text: String) {
        try {
            logFile.appendText("$text\n")
        } catch (_: Exception) {
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("ALinux 正在运行")
            .setContentText("前台服务已启动，点击返回应用")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ALinux 前台服务",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "保持 ALinux 在后台持续运行"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "alinux_keep_alive"
        private const val NOTIFICATION_ID = 10001
    }
}
