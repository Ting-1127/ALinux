package com.android.alinux

import android.app.Application
import android.content.res.AssetManager
import com.github.andock.daemon.app.containersDir
import com.github.andock.daemon.engine.PRootEngine
import com.github.andock.daemon.images.models.ContainerConfig
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Linux 容器生命周期管理器：负责安装、配置和脚本执行。
 */
class LinuxManager(app: Application, private val config: AppConfig) {

    private val assets: AssetManager = app.assets
    private val containersDir: File = app.containersDir
    val engine: PRootEngine = PRootEngine(app)

    /** 容器默认配置（从 AppConfig 读取） */
    private val defaultContainerConfig: ContainerConfig
        get() = ContainerConfig(
            workingDir = config.workDir,
            user = config.user,
            hostname = config.hostname,
        )

    /** 进度回调接口 */
    fun interface ProgressListener {
        fun onProgress(message: String)
    }

    /** 检查容器是否已安装（rootfs 中存在可用的 /bin/sh） */
    fun isInstalled(containerId: String): Boolean {
        val rootfsDir = File(containersDir, containerId)
        return rootfsDir.exists() && File(rootfsDir, "bin/sh").exists()
    }

    /** 获取指定容器的 rootfs 目录 */
    fun rootfsDir(containerId: String): File = File(containersDir, containerId)

    /**
     * 从网络下载 Ubuntu 镜像并解压到 [containersDir]/[containerId]。
     * 若已安装则跳过。通过 [listener] 回调下载和解压进度。
     */
    fun install(containerId: String, listener: ProgressListener? = null) {
        val rootfsDir = rootfsDir(containerId)
        if (File(rootfsDir, "bin/sh").exists()) {
            listener?.onProgress("Rootfs already extracted.")
            return
        }
        rootfsDir.mkdirs()

        val imageUrl = config.imageUrl
        val fileName = imageUrl.substringAfterLast("/")
        val tmpFile = File(containersDir, fileName)

        if (!tmpFile.exists()) {
            listener?.onProgress("Downloading $fileName ...")
            val downloader = MultiThreadDownloader(imageUrl, tmpFile, threadCount = 4)
            var lastReportTime = 0L
            downloader.download { downloaded, total, speed ->
                val now = System.currentTimeMillis()
                if (now - lastReportTime >= 500) {
                    lastReportTime = now
                    val pct = if (total > 0) downloaded * 100 / total else 0
                    val mb = downloaded / 1024 / 1024
                    val totalMb = total / 1024 / 1024
                    val speedKb = speed / 1024
                    listener?.onProgress("[download] $mb/$totalMb MB ($pct%) - $speedKb KB/s")
                }
            }
            listener?.onProgress("Download complete: ${tmpFile.absolutePath}")
        } else {
            listener?.onProgress("Using cached file: ${tmpFile.absolutePath}")
        }

        listener?.onProgress("Extracting $fileName ...")
        extractTar(tmpFile, rootfsDir, listener)
        listener?.onProgress("Extraction complete.")
        tmpFile.delete()
    }

    // ── DNS 配置 ──────────────────────────────────────────────

    /**
     * 为容器配置 DNS：写入 /etc/resolv.conf（替换可能存在的符号链接）。
     * 使用 Google 公共 DNS（8.8.8.8、8.8.4.4）。
     */
    fun setupDns(containerId: String, listener: ProgressListener? = null) {
        val rootfsDir = rootfsDir(containerId)
        val nameservers = listOf("8.8.8.8", "8.8.4.4")
        val resolvConf = File(rootfsDir, "etc/resolv.conf")
        resolvConf.parentFile?.mkdirs()
        if (resolvConf.exists() || Files.isSymbolicLink(resolvConf.toPath())) {
            resolvConf.delete()
        }
        resolvConf.writeText(nameservers.joinToString("\n") { "nameserver $it" } + "\n")
        listener?.onProgress("[dns] resolv.conf set to: ${nameservers.joinToString(", ")}")
    }

    // ── 脚本执行 ──────────────────────────────────────────────

    /**
     * 在容器中通过 PRoot 执行 assets 中的 shell 脚本。
     * 逐行将输出流式传递给 [listener]，返回进程退出码。
     */
    fun runScript(
        containerId: String,
        scriptAsset: String,
        env: Map<String, String> = emptyMap(),
        config: ContainerConfig = defaultContainerConfig,
        listener: ProgressListener? = null,
    ): Int {
        // 规范化换行符：Windows 下创建的文件包含 \r\n，传给 bash -c 会导致解析错误
        val script = assets.open(scriptAsset).bufferedReader().readText()
            .replace("\r\n", "\n").replace("\r", "\n")
        val proc = engine.startProcess(
            containerId = containerId,
            config = config.copy(
                cmd = listOf("/bin/bash", "-c", script),
                env = env,
            ),
        ).getOrThrow()
        proc.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { listener?.onProgress(it) }
        }
        return proc.waitFor()
    }

    /**
     * 在容器中启动长时间运行的脚本（从 assets 加载）。
     * 返回 [Process] 供调用方管理（读取输出、等待、销毁）。
     */
    fun startScript(
        containerId: String,
        scriptAsset: String,
        env: Map<String, String> = emptyMap(),
        config: ContainerConfig = defaultContainerConfig,
    ): Process {
        // 规范化换行符：Windows 下创建的文件包含 \r\n，传给 bash -c 会导致解析错误
        val script = assets.open(scriptAsset).bufferedReader().readText()
            .replace("\r\n", "\n").replace("\r", "\n")
        return engine.startProcess(
            containerId = containerId,
            config = config.copy(
                cmd = listOf("/bin/bash", "-c", script),
                env = env,
            ),
        ).getOrThrow()
    }

    /**
     * 检查 rootfs 中是否存在所需的二进制文件；若缺失则运行 [scriptAsset] 安装。
     * 安装完成后再次验证，返回 true 表示所有二进制均已就绪。
     */
    fun ensureBinaries(
        containerId: String,
        binaries: List<String>,
        scriptAsset: String,
        listener: ProgressListener? = null,
    ): Boolean {
        val rootfsDir = rootfsDir(containerId)
        val missing = binaries.filter { !File(rootfsDir, it.removePrefix("/")).exists() }
        if (missing.isEmpty()) {
            listener?.onProgress("Required binaries already installed.")
            return true
        }
        listener?.onProgress("Missing binaries: ${missing.joinToString()} — installing...")
        val exitCode = runScript(containerId, scriptAsset, listener = listener)
        if (exitCode != 0) {
            listener?.onProgress("[install failed with exit code $exitCode]")
            return false
        }
        val stillMissing = binaries.filter { !File(rootfsDir, it.removePrefix("/")).exists() }
        if (stillMissing.isNotEmpty()) {
            listener?.onProgress("[ERROR] Binaries still missing after install: ${stillMissing.joinToString()}")
            return false
        }
        return true
    }

    /**
     * 将 assets/[scriptsAssetDir] 下所有脚本同步到容器 [targetDir]，并授予可执行权限。
     */
    fun syncScriptsToContainer(
        containerId: String,
        scriptsAssetDir: String = "scripts",
        targetDir: String = "/root/scripts",
        listener: ProgressListener? = null,
    ) {
        val rootfsDir = rootfsDir(containerId)
        val targetRoot = File(rootfsDir, targetDir.removePrefix("/"))
        targetRoot.mkdirs()
        val scriptFiles = mutableListOf<String>()
        collectAssetFilesRecursively(scriptsAssetDir, scriptFiles)
        if (scriptFiles.isEmpty()) {
            listener?.onProgress("No scripts found in assets/$scriptsAssetDir")
            return
        }
        scriptFiles.forEach { assetPath ->
            val relativePath = assetPath.removePrefix("$scriptsAssetDir/")
            val outFile = File(targetRoot, relativePath)
            outFile.parentFile?.mkdirs()
            if (relativePath.endsWith(".sh")) {
                val text = assets.open(assetPath).bufferedReader(StandardCharsets.UTF_8)
                    .use { it.readText() }
                val normalized = text.replace("\r\n", "\n").replace("\r", "\n")
                outFile.writeText(normalized, StandardCharsets.UTF_8)
            } else {
                assets.open(assetPath).use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
            }
            outFile.setReadable(true, false)
            outFile.setWritable(true, true)
            outFile.setExecutable(true, false)
            listener?.onProgress("[scripts] Synced: /root/scripts/$relativePath")
        }
    }

    // ── 私有工具方法 ──────────────────────────────────────────

    private fun collectAssetFilesRecursively(dir: String, files: MutableList<String>) {
        val entries = assets.list(dir).orEmpty()
        if (entries.isEmpty()) {
            runCatching { assets.open(dir).close() }.onSuccess { files.add(dir) }
            return
        }
        entries.forEach { entry ->
            collectAssetFilesRecursively("$dir/$entry", files)
        }
    }

    private fun extractTar(srcFile: File, targetDir: File, listener: ProgressListener?) {
        val rootCanonical = targetDir.canonicalFile
        srcFile.inputStream().use { raw ->
            val src =
                if (srcFile.name.endsWith(".gz")) GzipCompressorInputStream(BufferedInputStream(raw))
                else BufferedInputStream(raw)
            TarArchiveInputStream(src).use { tar ->
                var count = 0
                var entry = tar.nextTarEntry
                while (entry != null) {
                    val out = File(rootCanonical, entry.name).canonicalFile
                    require(
                        out.toPath() == rootCanonical.toPath() || out.toPath()
                            .startsWith(rootCanonical.toPath())
                    ) {
                        "Unsafe tar path: ${entry.name}"
                    }
                    when {
                        entry.isDirectory -> {
                            out.mkdirs()
                            applyMode(out, entry.mode)
                        }

                        entry.isSymbolicLink -> {
                            out.parentFile?.mkdirs()
                            Files.deleteIfExists(out.toPath())
                            Files.createSymbolicLink(out.toPath(), Path.of(entry.linkName))
                        }

                        else -> {
                            out.parentFile?.mkdirs()
                            FileOutputStream(out).use { tar.copyTo(it) }
                            applyMode(out, entry.mode)
                        }
                    }
                    count++
                    if (count % 500 == 0) listener?.onProgress("Extracted $count entries...")
                    entry = tar.nextTarEntry
                }
                listener?.onProgress("Extracted $count entries total.")
            }
        }
    }

    private fun applyMode(file: File, mode: Int) {
        file.setReadable(mode and 0x124 != 0, false)
        file.setWritable(mode and 0x92 != 0, false)
        file.setExecutable(mode and 0x49 != 0, false)
    }
}
