package com.github.andock.proot

/**
 * PRoot JNI 接口：加载原生 proot_ext 库并提供版本查询。
 */
object PRoot {

    init {
        System.loadLibrary("proot_ext")
    }

    /** 获取 PRoot 版本号 */
    @JvmStatic
    external fun getVersion(): String
}