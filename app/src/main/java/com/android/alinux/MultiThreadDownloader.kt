package com.android.alinux

import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong

/**
 * 多线程文件下载器，使用 Java 原生 HttpURLConnection 实现分片并发下载。
 * 支持进度回调；先用 HEAD 请求解析最终 URL 和文件大小，每个分片创建独立连接。
 */
class MultiThreadDownloader(
    private val url: String,
    private val destFile: File,
    private val threadCount: Int = 4,
) {
    fun interface ProgressListener {
        fun onProgress(downloaded: Long, total: Long, speedBytesPerSec: Long)
    }

    fun download(listener: ProgressListener? = null) {
        // 先用 HEAD 解析最终 URL（处理重定向）和内容长度
        val (finalUrl, contentLength) = resolveUrlAndLength(url)

        destFile.parentFile?.mkdirs()

        if (contentLength <= 0) {
            downloadSingleThread(finalUrl, listener)
            return
        }

        // 预分配文件大小
        RandomAccessFile(destFile, "rw").use { it.setLength(contentLength) }

        val chunkSize = contentLength / threadCount
        val downloaded = AtomicLong(0)
        val executor = Executors.newFixedThreadPool(threadCount)
        val startTime = System.currentTimeMillis()

        val futures = mutableListOf<Future<*>>()
        for (i in 0 until threadCount) {
            val start = i * chunkSize
            val end = if (i == threadCount - 1) contentLength - 1 else start + chunkSize - 1
            futures += executor.submit {
                downloadChunk(finalUrl, start, end, downloaded, contentLength, startTime, listener)
            }
        }

        try {
            futures.forEach { it.get() }
        } finally {
            executor.shutdown()
        }

        listener?.onProgress(contentLength, contentLength, 0)
    }

    /** 解析最终跳转 URL 和内容长度（HEAD 请求，不下载 body） */
    private fun resolveUrlAndLength(urlStr: String): Pair<String, Long> {
        var currentUrl = urlStr
        repeat(10) {
            val conn = buildConnection(currentUrl, method = "HEAD")
            conn.instanceFollowRedirects = false
            conn.connect()
            val code = conn.responseCode
            if (code in 301..308) {
                val location = conn.getHeaderField("Location") ?: return currentUrl to -1L
                conn.disconnect()
                currentUrl = location
                return@repeat
            }
            val length = conn.contentLengthLong
            conn.disconnect()
            return currentUrl to length
        }
        return currentUrl to -1L
    }

    private fun downloadChunk(
        urlStr: String,
        start: Long,
        end: Long,
        downloaded: AtomicLong,
        total: Long,
        startTime: Long,
        listener: ProgressListener?,
    ) {
        // 每个分片使用全新连接，在 connect() 之前设置 Range
        val conn = buildConnection(urlStr)
        conn.setRequestProperty("Range", "bytes=$start-$end")
        conn.connect()

        conn.inputStream.use { input ->
            RandomAccessFile(destFile, "rw").use { raf ->
                raf.seek(start)
                val buffer = ByteArray(8 * 1024)
                var bytes: Int
                while (input.read(buffer).also { bytes = it } != -1) {
                    raf.write(buffer, 0, bytes)
                    val total2 = downloaded.addAndGet(bytes.toLong())
                    val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                    val speed = total2 * 1000 / elapsed
                    listener?.onProgress(total2, total, speed)
                }
            }
        }
        conn.disconnect()
    }

    private fun downloadSingleThread(urlStr: String, listener: ProgressListener?) {
        val conn = buildConnection(urlStr)
        conn.connect()
        val total = conn.contentLengthLong
        val startTime = System.currentTimeMillis()
        var downloaded = 0L

        conn.inputStream.use { input ->
            destFile.outputStream().use { output ->
                val buffer = ByteArray(8 * 1024)
                var bytes: Int
                while (input.read(buffer).also { bytes = it } != -1) {
                    output.write(buffer, 0, bytes)
                    downloaded += bytes
                    val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                    val speed = downloaded * 1000 / elapsed
                    listener?.onProgress(downloaded, total, speed)
                }
            }
        }
        conn.disconnect()
    }

    /** 构造一个尚未 connect() 的连接，可安全设置 RequestProperty */
    private fun buildConnection(
        urlStr: String,
        method: String = "GET",
    ): HttpURLConnection {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "ALinux/1.0")
        return conn
    }
}

