#!/usr/bin/env bash
# Установка HeadsetConnector на Ubuntu/Debian: Node.js, systemd, автозапуск.
# Запускать из папки с server.js:  sudo bash install-ubuntu.sh

set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")" && pwd)"
PORT="${PORT:-8787}"

if ! command -v node >/dev/null 2>&1; then
  echo "Устанавливаю Node.js 22…"
  curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
  sudo apt-get install -y nodejs
fi

NODE_MAJOR="$(node -p 'process.versions.node.split(".")[0]')"
if [ "$NODE_MAJOR" -lt 22 ]; then
  echo "Нужен Node.js 22 или новее, сейчас $(node --version)" >&2
  exit 1
fi

sudo useradd -r -s /usr/sbin/nologin headsetconnector 2>/dev/null || true
sudo mkdir -p "$APP_DIR/data"
sudo chown -R headsetconnector:headsetconnector "$APP_DIR"

# Пароль админ-панели: если файла нет — создаём. Пустой означает
# «сгенерировать при первом запуске и напечатать в лог».
if [ ! -f /etc/headsetconnector.env ]; then
  sudo tee /etc/headsetconnector.env >/dev/null <<'EOF'
# Пароль для /admin. Раскомментируйте и задайте свой.
# ADMIN_PASSWORD=придумайте_сложный_пароль
EOF
  sudo chmod 600 /etc/headsetconnector.env
fi

sudo tee /etc/systemd/system/headsetconnector.service >/dev/null <<EOF
[Unit]
Description=HeadsetConnector messenger server
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=headsetconnector
WorkingDirectory=$APP_DIR
Environment=PORT=$PORT
Environment=DATA_DIR=$APP_DIR/data
Environment=TRUST_PROXY=1
EnvironmentFile=-/etc/headsetconnector.env
ExecStart=$(command -v node) $APP_DIR/server.js
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now headsetconnector

if command -v ufw >/dev/null 2>&1; then
  sudo ufw allow "$PORT"/tcp || true
fi

echo
echo "Готово. Сервер: http://$(hostname -I | awk '{print $1}'):$PORT"
echo "Логи: journalctl -u headsetconnector -f"
echo "Если ставите за Caddy/nginx — включите HTTPS, иначе пароли идут открытым текстом."
