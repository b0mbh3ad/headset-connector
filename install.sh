#!/data/data/com.termux/files/usr/bin/bash
# Установка сервера HeadsetConnector на Android через Termux.
# Запускать из папки, куда распакован архив:  bash install.sh

set -e

APP_DIR="$HOME/headsetconnector"
SRC_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "HeadsetConnector — установка на телефон"
echo

if ! command -v pkg >/dev/null 2>&1; then
  echo "Это не Termux. Откройте Termux и запустите скрипт там."
  exit 1
fi

if ! command -v node >/dev/null 2>&1; then
  echo "Устанавливаю Node.js (это займёт пару минут)…"
  pkg install -y nodejs
fi

NODE_VERSION="$(node -v 2>/dev/null || echo 'нет')"
echo "Node.js: $NODE_VERSION"

SQLITE_FLAG=""
if node -e "require('node:sqlite')" >/dev/null 2>&1; then
  SQLITE_FLAG=""
elif node --experimental-sqlite -e "require('node:sqlite')" >/dev/null 2>&1; then
  SQLITE_FLAG="--experimental-sqlite"
else
  echo
  echo "Ошибка: в этом Node.js нет модуля node:sqlite (нужен 22.5+)."
  echo "Обновите: pkg install nodejs"
  exit 1
fi

pkg install -y termux-tools >/dev/null 2>&1 || true

mkdir -p "$APP_DIR"
cp "$SRC_DIR/server.js" "$APP_DIR/server.js"
cp "$SRC_DIR/admin.html" "$APP_DIR/admin.html"
cp "$SRC_DIR/package.json" "$APP_DIR/package.json"
mkdir -p "$APP_DIR/data"

cat > "$APP_DIR/start.sh" <<EOF
#!/data/data/com.termux/files/usr/bin/bash
termux-wake-lock 2>/dev/null || true
cd "$APP_DIR"
[ -f admin.env ] && . ./admin.env
export PORT=8787
export DATA_DIR="$APP_DIR/data"
exec node $SQLITE_FLAG server.js
EOF
chmod +x "$APP_DIR/start.sh"

mkdir -p "$HOME/.termux/boot"
cat > "$HOME/.termux/boot/headsetconnector.sh" <<EOF
#!/data/data/com.termux/files/usr/bin/bash
termux-wake-lock 2>/dev/null || true
sleep 8
cd "$APP_DIR"
[ -f admin.env ] && . ./admin.env
export PORT=8787
export DATA_DIR="$APP_DIR/data"
nohup node $SQLITE_FLAG server.js >> "$APP_DIR/server.log" 2>&1 &
EOF
chmod +x "$HOME/.termux/boot/headsetconnector.sh"

IP="$(ip -4 addr show wlan0 2>/dev/null | grep -o 'inet [0-9.]*' | head -1 | cut -d' ' -f2)"
if [ -z "$IP" ]; then
  IP="$(ip -4 addr 2>/dev/null | grep -o 'inet 192[0-9.]*' | head -1 | cut -d' ' -f2)"
fi
if [ -z "$IP" ]; then
  IP="$(getprop dhcp.wlan0.ipaddress 2>/dev/null || true)"
fi

echo
echo "Готово."
echo "  Папка:  $APP_DIR"
echo "  Запуск: bash $APP_DIR/start.sh"
if [ -n "$IP" ]; then
  echo "  Адрес:  http://$IP:8787"
  echo "  Панель: http://$IP:8787/admin"
fi
echo
echo "Пароль панели: при первом запуске сервер напечатает его в консоль."
echo "Чтобы задать свой, создайте $APP_DIR/admin.env со строкой:"
echo "  export ADMIN_PASSWORD=ваш_пароль"
echo
echo "Автозапуск: установите Termux:Boot и откройте его один раз."
echo "И отключите оптимизацию батареи для Termux."
