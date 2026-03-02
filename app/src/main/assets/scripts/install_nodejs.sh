#!/usr/bin/env bash
set -eu

NODE_VERSION="v24.14.0"
NODE_DIST="node-${NODE_VERSION}-linux-arm64"
NODE_URL="https://nodejs.org/dist/${NODE_VERSION}/${NODE_DIST}.tar.xz"
INSTALL_BASE="/opt"
INSTALL_DIR="${INSTALL_BASE}/${NODE_DIST}"
NODE_HOME="${INSTALL_DIR}"
BASHRC_FILE="/root/.bashrc"
TMP_FILE="/tmp/${NODE_DIST}.tar.xz"

echo "[node] Downloading ${NODE_URL} ..."
curl -fL "${NODE_URL}" -o "${TMP_FILE}"

echo "[node] Installing to ${INSTALL_DIR} ..."
rm -rf "${INSTALL_DIR}"
mkdir -p "${INSTALL_BASE}"
tar -xJf "${TMP_FILE}" -C "${INSTALL_BASE}"
sed -i '/^export NODE_HOME=\/opt\/node-v.*-linux-arm64$/d' "${BASHRC_FILE}" 2>/dev/null || true
sed -i '/^export PATH="\$NODE_HOME\/bin:\$PATH"$/d' "${BASHRC_FILE}" 2>/dev/null || true
cat >> "${BASHRC_FILE}" <<EOF
export NODE_HOME=${NODE_HOME}
export PATH="\$NODE_HOME/bin:\$PATH"
EOF

export NODE_HOME="${NODE_HOME}"
export PATH="$NODE_HOME/bin:$PATH"

echo "[node] Configuring npm libc preference..."
npm config set libc glibc --global
if ! grep -q '^libc=glibc$' /etc/npmrc 2>/dev/null; then
  echo 'libc=glibc' >> /etc/npmrc
fi

if [ ! -e /lib/ld-musl-aarch64.so.1 ] && [ -e /usr/lib/aarch64-linux-musl/libc.so ]; then
  ln -sf /usr/lib/aarch64-linux-musl/libc.so /lib/ld-musl-aarch64.so.1
fi
rm -f "${TMP_FILE}"

echo "[node] Installed: $(node -v)"
echo "[node] Installed npm: $(npm -v)"
