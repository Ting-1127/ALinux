package com.android.alinux

import android.content.res.AssetManager
import java.util.Properties

/**
 * 应用全局配置，从 assets/alinux.conf 加载。
 * 所有容器和 SSH 相关的常量统一在此管理。
 */
class AppConfig(assets: AssetManager) {

    private val props = Properties().apply {
        assets.open(CONFIG_FILE).use { load(it) }
    }

    /** 容器 ID（对应 containersDir 下的目录名） */
    val containerId: String = props.getProperty("container.id", "ubuntu")
    /** 容器主机名 */
    val hostname: String = props.getProperty("container.hostname", "ubuntu-android")
    /** 容器默认用户 */
    val user: String = props.getProperty("container.user", "root")
    /** 容器工作目录 */
    val workDir: String = props.getProperty("container.workdir", "/root")
    /** SSH 服务端口 */
    val sshPort: Int = props.getProperty("ssh.port", "2224").toInt()
    /** SSH 登录密码 */
    val sshPassword: String = props.getProperty("ssh.password", "root")
    /** Ubuntu 镜像下载地址 */
    val imageUrl: String = props.getProperty(
        "image.url",
        "https://mirrors.aliyun.com/ubuntu-cdimage/ubuntu-base/releases/24.04.4/release/ubuntu-base-24.04.4-base-arm64.tar.gz",
    )

    companion object {
        private const val CONFIG_FILE = "alinux.conf"
    }
}
