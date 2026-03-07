#!/bin/sh
apk update
apk add openssh ca-certificates curl
> /etc/motd
echo '[init] Alpine installation finished.'
