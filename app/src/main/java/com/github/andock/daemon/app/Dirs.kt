package com.github.andock.daemon.app

import android.content.Context
import java.io.File

/** 容器目录（存放所有容器 rootfs） */
val Context.containersDir: File
    get() = File(dataDir, DIR_CONTAINERS)

/** Unix Socket 文件路径 */
val Context.socketFile: File
    get() = File(cacheDir, DOCKER_SOCK)

private const val DOCKER_SOCK = "docker.sock"
private const val DIR_CONTAINERS = "containers"
