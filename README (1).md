# Сервер на бесплатном хостинге alwaysdata

**Уже развёрнуто:** аккаунт `headsetconnector`, адрес
`https://headsetconnector.alwaysdata.net`, файлы в `~/hc`, пароль панели — в
`~/hc/.env`.

Обновление после правок в `server.js`/`admin.html`:

```powershell
cd server\alwaysdata
.\deploy-alwaysdata.ps1 -Account headsetconnector -KeyInstalled
```

Ниже — как поднять такой сервер с нуля (для другого аккаунта или если
alwaysdata когда-нибудь попросит переехать).

Этот вариант даёт **постоянный адрес в интернете**:

```
https://ВАШ_ЛОГИН.alwaysdata.net
```

Друзьям не нужно ничего, кроме APK: ни VPN, ни туннелей, ни регистрации.
Хостинг бесплатный, без карты. Ограничения бесплатного тарифа: 1 ГБ диска,
256 МБ памяти — для мессенджера на несколько десятков человек хватает.

## Что нужно сделать (один раз)

### 1. Регистрация

<https://www.alwaysdata.com/en/register/?p=2012>

Нужны только почта и пароль. Карта не нужна. Логин, который вы выберете, станет
адресом сервера: например логин `headsetconnector` → адрес
`https://headsetconnector.alwaysdata.net`.

### 2. Заливка сервера

На своём ПК откройте PowerShell в папке `server\alwaysdata`:

```powershell
.\deploy-alwaysdata.ps1 -Account ВАШ_ЛОГИН
```

Скрипт создаст SSH-ключ и покажет его. Скопируйте ключ в панели:

**https://admin.alwaysdata.com → Remote access → SSH/SFTP → ваш пользователь →
SSH keys → Add a SSH key**

Вернитесь в PowerShell и нажмите Enter. Скрипт зальёт файлы в `~/hc` и
напечатает пароль панели.

### 3. Создание сайта

**Web → Sites → Add a site** и заполните:

| Поле | Значение |
|---|---|
| Type | `Node.js` |
| Addresses | `ВАШ_ЛОГИН.alwaysdata.net` |
| Command | `node /home/ВАШ_ЛОГИН/hc/server.js` |
| Node.js version | `24` |

Сохраните. Через минуту:

- `https://ВАШ_ЛОГИН.alwaysdata.net/api/ping` → `{"ok":true,…}`
- `https://ВАШ_ЛОГИН.alwaysdata.net/admin` → панель (пароль напечатал скрипт)

### 4. APK с готовым адресом

Чтобы друзьям не вводить адрес руками, приложение можно собрать с ним внутри:

```powershell
cd ..\..\..
$env:JAVA_HOME="$env:USERPROFILE\.jdks\jbr-21.0.11"
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain `
  "-Phc.server=https://ВАШ_ЛОГИН.alwaysdata.net"
```

Готовый APK: `app\build\outputs\apk\debug\app-debug.apk`. При первом запуске
останется только войти или создать аккаунт.

## Обслуживание

- Обновить сервер: снова `.\deploy-alwaysdata.ps1 -Account ВАШ_ЛОГИН`
  (база и медиа в `~/hc/data` не трогаются).
- Сменить пароль панели: `~/hc/.env`, строка `ADMIN_PASSWORD=...`,
  затем **Web → Sites → ваш сайт → Restart**.
- Бэкап: панель alwaysdata → **Backups**, либо скачать `~/hc/data` по SFTP.
- Место кончилось (1 ГБ): удаляйте старые комнаты и медиа в панели `/admin`.

## Если что-то не так

| Симптом | Причина |
|---|---|
| `Permission denied (publickey)` | ключ не добавлен в панели или другой логин |
| Сайт отдаёт 502/503 | неверная команда сайта — должно быть `node /home/ЛОГИН/hc/server.js` |
| `api/ping` работает, `/admin` пусто | не скопировался `admin.html` — запустите скрипт ещё раз |
| Долгий первый ответ | бесплатный сайт засыпает после 30 минут тишины и поднимается при первом запросе (5–10 секунд) |
| Кончилось место | почистите комнаты и медиа в `/admin` |
