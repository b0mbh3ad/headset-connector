# HeadsetConnector — сервер

Мессенджер-мост между Pico и Meta Quest. Этот сервер — один файл на Node.js,
без внешних зависимостей. База — SQLite (встроенный модуль `node:sqlite`),
медиа (аватарки, голосовые) лежат рядом в `data/media`.

## Запуск

```powershell
node server.js
```

Сервер напечатает адреса вида `http://192.168.1.10:8787` — этот адрес вводится
в приложении на шлеме или находится кнопкой «Найти в сети».

Порт и папку данных можно менять:

```powershell
$env:PORT = "9000"
$env:DATA_DIR = "D:\headsetconnector"
node server.js
```

Ещё можно положить рядом файл `.env`:

```
ADMIN_PASSWORD=пароль_панели
PUBLIC_URL=https://имя.alwaysdata.net
```

## Где хостить

| Куда | Плюсы | Инструкция |
|---|---|---|
| alwaysdata Free | бесплатно, без карты, HTTPS, адрес `https://логин.alwaysdata.net` | [`alwaysdata/README.md`](alwaysdata/README.md) |
| Oracle Cloud Always Free | 200 ГБ, свой IP, но нужна карта | [`DEPLOY.md`](DEPLOY.md) |
| VPS / Docker | полный контроль, 2–4 $/мес | [`DEPLOY.md`](DEPLOY.md) |
| Старый Android + Termux | бесплатно, но работает только пока телефон включён | [`termux/README.md`](termux/README.md) |

## Проверка

```powershell
node test-api.js
```

Тест поднимает двух пользователей, проверяет друзей, личку, комнаты,
голосовое сообщение и аватарку. Ничего не удаляет — тестовые аккаунты
остаются в базе.

Проверить живой сервер и сразу убрать мусор после тестов:

```powershell
node test-api.js https://headsetconnector.alwaysdata.net
node test-admin.js https://headsetconnector.alwaysdata.net ПАРОЛЬ_ПАНЕЛИ
node cleanup-test-users.js https://headsetconnector.alwaysdata.net ПАРОЛЬ_ПАНЕЛИ
```

## Панель управления

`http://<сервер>:8787/admin` — веб-панель. Пароль: `ADMIN_PASSWORD` или
сгенерированный при первом запуске (печатается в консоль/журнал).

API панели (заголовок `X-Admin-Token`):

| Метод | Путь | Что делает |
|---|---|---|
| POST | `/api/admin/login` | `{password}` → токен на 12 часов |
| POST | `/api/admin/logout` | выход |
| GET | `/api/admin/overview` | статистика и последние регистрации |
| GET | `/api/admin/users?q=` | список/поиск пользователей |
| POST | `/api/admin/users/{id}/ban` | блокировка + сброс сессий |
| POST | `/api/admin/users/{id}/unban` | разблокировка |
| POST | `/api/admin/users/{id}/reset` | новый пароль |
| DELETE | `/api/admin/users/{id}` | удалить пользователя |
| GET | `/api/admin/rooms` | список комнат |
| DELETE | `/api/admin/rooms/{id}` | удалить комнату |

Проверка панели:

```powershell
$env:ADMIN_PASSWORD="admin123"
node server.js
# в другом окне:
node test-admin.js
```

## API

Все запросы — JSON. Авторизация: `Authorization: Bearer <token>`.

| Метод | Путь | Что делает |
|---|---|---|
| GET | `/api/ping` | проверка сервера и поиск в сети |
| POST | `/api/register` | `{username, password, displayName}` → токен |
| POST | `/api/login` | вход |
| GET | `/api/me` | текущий профиль |
| PATCH | `/api/me` | смена отображаемого имени |
| POST | `/api/me/avatar` | аватар, `{data: base64, mime}` |
| GET | `/api/users/search?q=` | поиск людей по юзернейму/имени |
| GET | `/api/friends` | друзья, входящие, исходящие |
| POST | `/api/friends/request` | `{username}` |
| POST | `/api/friends/answer` | `{userId, accept}` |
| DELETE | `/api/friends/{id}` | удалить из друзей |
| GET | `/api/chats` | список чатов |
| POST | `/api/chats/dm` | `{username}` — личный чат (только с другом) |
| POST | `/api/chats/room` | `{title, isPublic}` — комната |
| POST | `/api/chats/join` | `{code}` — вход в закрытую комнату |
| POST | `/api/chats/{id}/join` | вход в публичную комнату |
| GET | `/api/rooms` | публичные комнаты |
| GET | `/api/chats/{id}/messages?after=` | сообщения после метки времени |
| POST | `/api/chats/{id}/messages` | `{text}` или `{audio:{data,mime,duration}}` |
| GET | `/media/{file}` | аватарки и голосовые |

## Хостинг

Сервер можно завернуть в Docker:

```bash
docker build -t headsetconnector .
docker run -p 8787:8787 -v hc-data:/data -e DATA_DIR=/data headsetconnector
```

Варианты размещения:

- **Этот ПК + Cloudflare Tunnel** — бесплатно и сразу: `.\tunnel.ps1` выдаст
  публичный `https://…trycloudflare.com`. ПК должен быть включён.
- **Бесплатный VPS (Oracle Cloud Always Free)** — постоянный адрес, нужен
  аккаунт и карта для верификации.
- **Render / Koyeb free** — бесплатно, но диск не сохраняется: база обнулится
  при перезапуске. Для демо годится, для реальных пользователей — нет.
