package com.github.andock.common

import android.content.Context
import java.io.File

/** 获取应用的原生库目录路径 */
val Context.nativeLibDir: File
    get() = File(requireNotNull(applicationInfo.nativeLibraryDir))
