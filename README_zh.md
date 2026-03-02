# ALinux

[English](README.md) 

在 Android 应用内运行 **Linux userspace（免 Root）** 的最小化工程模板。

ALinux 基于 **PRoot + rootfs** 启动/管理一个可用的 Linux 运行环境，面向 **App 开发者“复制源码直接集成”** 的场景设计。

> 项目使用某个发行版 rootfs 仅作演示；理论上只要是 **arm64** 架构的 Linux rootfs，都可以用相同方式启动运行。

---

## 适用人群

- 想在自己的 Android App 内“跑起来 Linux 环境”的开发者
- 希望直接 **复制源码** 集成，而不是引入复杂依赖/完整终端 App
- 后续功能将基于已运行的 Linux 环境实现（工具链、CLI、守护进程、自动化任务等）

---

## 它是什么（以及不是什么）

### 它是

- **最小必要**的 Linux 启动底座（已移除非必要内容，便于抄进你的项目）
- 基于 PRoot 的 userspace 隔离与进程启动
- rootfs 的准备（下载/导入/解压）+ 启动（进入 shell/执行命令）的工程化骨架

### 它不是

- 一个完整的“终端/发行版管理器”应用（例如带大量 UI、插件体系等）
- 也不绑定任何特定发行版：你可以替换成自己的 rootfs

---

## 支持范围（概念）

- 架构：**arm64**
- rootfs：任意 arm64 Linux rootfs（发行版不限）
- 启动方式：PRoot（免 root）

---

## 快速开始（复制源码集成）

1. Clone 本仓库并在 Android Studio 打开
2. 确保目标设备为 **arm64**
3. 准备一个 rootfs（下载或你自己构建），放到 App 可访问目录
4. 运行 App，执行启动流程（PRoot 启动 `/bin/sh` 或 `/bin/bash`）

---

## 典型集成方式（建议）

你可以把本项目当成“可复制的最小模块”，常见做法：

- 将 `app/` 中与 Linux 启动相关的代码/资源 **直接拷贝** 到你的工程
- 将 rootfs 下载/导入逻辑替换成你的业务逻辑（例如内置、分包、在线更新、企业内网分发）
- 把“启动后执行命令/脚本”的入口封装成你自己的 API（如 `start() / exec() / stop()`）

---

## 使用场景

- 在 App 内提供可复用 CLI/脚本环境（你在 rootfs 内放什么就能跑什么）
- 运行 Linux sidecar 进程/服务（后台任务、构建/转换、自动化等）
- 研究/教学：Android userspace Linux 环境实践

---

## 参考

- https://github.com/termux/termux-app
- https://github.com/termux/proot
- https://github.com/termux/proot-distro/releases
- https://wiki.termux.com/wiki/PRoot
- https://github.com/LukeXeon/adocker
- https://github.com/LukeXeon/proot

---