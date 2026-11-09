#!/usr/bin/env bash
# One-time setup for a fresh Ubuntu 24.04 server, run as root. Safe to re-run: it keeps an existing
# deploy/.env, so the database password and site address survive.
set -euo pipefail

APP_DIR=/opt/stepfree
export DEBIAN_FRONTEND=noninteractive

apt-get update
apt-get -y upgrade
apt-get -y install ca-certificates curl git openssl ufw unattended-upgrades

command -v docker >/dev/null || curl -fsSL https://get.docker.com | sh

# Headroom for image builds, which use far more memory than the running app.
if ! swapon --show | grep -q .; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

ufw default deny incoming
ufw default allow outgoing
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp
ufw --force enable

cat > /etc/ssh/sshd_config.d/10-key-only.conf <<'CONF'
PasswordAuthentication no
PermitRootLogin prohibit-password
CONF
systemctl reload ssh

if [ -d "$APP_DIR/.git" ]; then
  git -C "$APP_DIR" pull --ff-only
else
  git clone https://github.com/Adebozz/stepfree.git "$APP_DIR"
fi
cd "$APP_DIR/deploy"

if [ ! -f .env ]; then
  ip=$(ip -4 route get 1.1.1.1 | awk '{print $7; exit}')
  umask 077
  cat > .env <<ENV
SITE_ADDRESS=stepfree.${ip//./-}.sslip.io
DB_PASSWORD=$(openssl rand -hex 32)
TFL_APP_KEY=
ENV
fi

docker compose up -d --build

chmod +x backup.sh
echo "30 3 * * * root $APP_DIR/deploy/backup.sh" > /etc/cron.d/stepfree-backup

grep '^SITE_ADDRESS=' .env
