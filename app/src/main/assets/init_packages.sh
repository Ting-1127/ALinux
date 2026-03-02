#!/bin/bash
export DEBIAN_FRONTEND=noninteractive
dpkg --configure -a 2>/dev/null || true
apt-get -f install -y 2>/dev/null || true
apt-get update -y

# --no-install-recommends avoids pulling in systemd/python deps that fail in PRoot
# post-install scripts may fail (systemd, ca-certificates) — that's expected in PRoot
apt-get install -y --no-install-recommends \
    openssh-server openssh-client \
    vim git curl wget \
    xz-utils tar zip unzip \
    python3 python3-pip \
    ca-certificates musl \
    net-tools iproute2 iputils-ping dnsutils \
    || true

# Force-configure in case post-install scripts left packages half-configured
dpkg --configure -a 2>/dev/null || true
echo '[init] package installation finished.'
