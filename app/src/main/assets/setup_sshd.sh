#!/bin/bash
# Required env vars: SSH_PORT, SSH_PASSWORD, SSH_USER

if [ ! -f /etc/ssh/ssh_host_rsa_key ]; then
    echo '[setup] Generating SSH host keys...'
    mkdir -p /etc/ssh
    /usr/bin/ssh-keygen -A
fi

if ! id sshd >/dev/null 2>&1; then
    echo '[setup] Creating sshd privilege separation user...'
    mkdir -p /var/empty
    groupadd -r -g 74 sshd 2>/dev/null || true
    useradd -r -g sshd -u 74 -d /var/empty -s /usr/sbin/nologin sshd 2>/dev/null || true
fi

if ! grep -q "^${SSH_USER}:[^!*]" /etc/shadow 2>/dev/null; then
    echo "[setup] Setting ${SSH_USER} password..."
    echo "${SSH_USER}:${SSH_PASSWORD}" | chpasswd
fi

CONF=/etc/ssh/sshd_config
sed -i '/^#*PermitRootLogin/d; /^#*PasswordAuthentication/d' "$CONF"
echo 'PermitRootLogin yes' >> "$CONF"
echo 'PasswordAuthentication yes' >> "$CONF"
mkdir -p /var/run/sshd /run/sshd

if [ ! -x /usr/sbin/sshd ]; then
    echo '[setup] ERROR: /usr/sbin/sshd is missing after install.'
    exit 1
fi

echo "[setup] Starting sshd on port ${SSH_PORT}..."
exec /usr/sbin/sshd -D -e -p ${SSH_PORT}
