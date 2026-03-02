package com.github.andock.daemon.os

import java.io.File

/**
 * 进程构建工具函数：根据命令、工作目录和环境变量创建系统进程。
 */
fun Process(
    command: List<String>,
    workingDir: File? = null,
    environment: Map<String, String> = emptyMap(),
    redirectErrorStream: Boolean = true,
): Process {
    val processBuilder = ProcessBuilder(command).apply {
        workingDir?.let { directory(it) }
        environment().putAll(environment)
        redirectErrorStream(redirectErrorStream)
    }
    return processBuilder.start()
}
