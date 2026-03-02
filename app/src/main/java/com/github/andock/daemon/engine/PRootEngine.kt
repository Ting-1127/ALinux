package com.github.andock.daemon.engine

import android.app.Application
import com.github.andock.daemon.app.containersDir
import com.github.andock.daemon.images.models.ContainerConfig
import com.github.andock.daemon.os.Process
import com.github.andock.proot.PRoot
import java.io.File

/**
 * PRoot 引擎：负责构建命令行、环境变量，并在容器中启动进程。
 */
class PRootEngine(
    private val appContext: Application,
) {
    val version: String = PRoot.getVersion()

    /** 构建完整的 PRoot 命令行参数 */
    private fun buildCommand(
        container: ContainerConfig,
        rootfsDir: File,
        command: List<String>?,
    ): List<String> {
        val cmd = mutableListOf<String>()
        cmd.add(appContext.binary.absolutePath)
        cmd.add("-0")
        cmd.add("--link2symlink")
        cmd.add("-r")
        cmd.add(rootfsDir.absolutePath)
        cmd.add("-w")
        cmd.add(container.workingDir)

        val mapping = appContext.redirect
        container.binds.forEach { bind ->
            cmd.add("-b")
            val hostPath = mapping.getOrElse(bind.hostPath) { bind.hostPath }
            cmd.add("$hostPath:${bind.containerPath}")
        }

        addEssentialBinds(cmd, rootfsDir)
        cmd.addAll(command ?: buildExecCommand(container))
        return cmd
    }

    /** 构建容器进程的环境变量 */
    private fun buildEnvironment(container: ContainerConfig): Map<String, String> {
        val env = HashMap<String, String>(appContext.environment)
        env["HOME"] = "/root"
        env["USER"] = container.user.ifEmpty { "root" }
        env["HOSTNAME"] = container.hostname
        env.putAll(container.env)
        return env
    }

    private fun startProcess(
        container: ContainerConfig,
        rootfsDir: File,
        command: List<String>? = null,
    ): Result<Process> = runCatching {
        Process(
            command = buildCommand(container, rootfsDir, command),
            workingDir = rootfsDir,
            environment = buildEnvironment(container),
        )
    }

    /**
     * 在指定容器中启动进程。
     * @param containerId 容器 ID（对应 containersDir 下的目录名）
     * @param command 要执行的命令，为空则使用 config 中的 entrypoint/cmd
     * @param config 容器配置（工作目录、用户、主机名等）
     * @return 启动的进程，若 rootfs 不存在则返回失败
     */
    fun startProcess(
        containerId: String,
        command: List<String>? = null,
        config: ContainerConfig = ContainerConfig(),
    ): Result<Process> {
        val rootfsDir = File(appContext.containersDir, containerId)
        if (!rootfsDir.exists()) {
            return Result.failure(IllegalStateException("Container rootfs not found"))
        }
        return startProcess(config, rootfsDir, command)
    }

    companion object {
        /** 根据容器配置构建要执行的命令 */
        private fun buildExecCommand(config: ContainerConfig): List<String> {
            val cmd = mutableListOf<String>()
            cmd.addAll(config.entrypoint ?: emptyList())
            cmd.addAll(config.cmd)
            return cmd.ifEmpty { listOf("/bin/sh") }
        }

        /** 绑定容器运行所需的设备节点、文件系统和网络配置 */
        private fun addEssentialBinds(cmd: MutableList<String>, rootfsDir: File) {
            // 绑定基础设备节点
            listOf("/dev/null", "/dev/zero", "/dev/random", "/dev/urandom", "/dev/ptmx", "/dev/tty").forEach { dev ->
                if (File(dev).exists()) {
                    cmd.add("-b")
                    cmd.add(dev)
                }
            }

            // 绑定 PTY 伪终端（SSH 交互所需）
            if (File("/dev/pts").exists()) {
                cmd.add("-b")
                cmd.add("/dev/pts")
            }

            cmd.add("-b")
            cmd.add("/proc")

            if (File("/sys").exists()) {
                cmd.add("-b")
                cmd.add("/sys")
            }

            // 绑定 Android 系统分区
            listOf("/system", "/vendor").forEach { path ->
                if (File(path).exists()) {
                    cmd.add("-b")
                    cmd.add("$path:$path")
                }
            }

            // 确保 DNS 和 hosts 文件存在
            val resolvConf = File(rootfsDir, "etc/resolv.conf")
            if (!resolvConf.exists()) {
                resolvConf.parentFile?.mkdirs()
                resolvConf.writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
            }

            val hostsFile = File(rootfsDir, "etc/hosts")
            if (!hostsFile.exists()) {
                hostsFile.parentFile?.mkdirs()
                hostsFile.writeText("127.0.0.1 localhost\n::1 localhost\n")
            }
        }
    }
}
