# ALinux

[中文](README_zh.md)

A minimal Android project template to run a **Linux userspace (no root)** inside your app.

ALinux boots and manages a Linux runtime using **PRoot + a rootfs**, designed specifically for **app developers who prefer copying source code** into their own projects.

> A distro rootfs may be used for demonstration, but ALinux is **not distro-specific**. In general, any **arm64 Linux rootfs** can be started in the same way.

---

## Who is this for?

- Android app developers who want a Linux runtime inside their apps
- People who prefer **copy-paste integration** over heavy dependencies or a full terminal app
- Teams building features on top of a running Linux environment (toolchains, CLIs, daemons, automation, etc.)

---

## What it is (and what it is not)

### It is

- A **minimal, source-copy-friendly** Linux boot foundation (non-essential parts removed)
- Userspace isolation + process launching via PRoot
- A practical skeleton for rootfs provisioning (download/import/extract) and boot (shell/command execution)

### It is not

- A full “terminal / distro manager” application with lots of UI features
- Bound to any specific distribution — you can swap in your own rootfs

---

## Conceptual support

- Architecture: **arm64**
- rootfs: any arm64 Linux rootfs
- Boot method: **PRoot** (no root)

---

## Quick start (copy-source integration)

1. Clone this repository and open it in Android Studio
2. Use an **arm64** device
3. Prepare a rootfs (download or self-built) and place it under an app-accessible directory
4. Run the app and trigger the boot flow (PRoot launching `/bin/sh` or `/bin/bash`)

---

## Recommended integration approach

Treat this repo as a “minimal module you can copy”:

- Copy the Linux-boot related code/assets from `app/` into your project
- Replace rootfs provisioning with your own strategy (bundled, split APK, OTA update, enterprise distribution, etc.)
- Wrap the “boot + exec” entry points into your own API (e.g. `start() / exec() / stop()`)

---

## Use cases

- Ship a reusable CLI/script environment inside your app (whatever you install into the rootfs is available)
- Run Linux sidecar processes/daemons (background tasks, build/transform pipelines, automation)
- Research/education: running Linux userspace on Android

---

## References

- https://github.com/termux/termux-app
- https://github.com/termux/proot
- https://github.com/termux/proot-distro/releases
- https://wiki.termux.com/wiki/PRoot
- https://github.com/LukeXeon/adocker
- https://github.com/LukeXeon/proot

---