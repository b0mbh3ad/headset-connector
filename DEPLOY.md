# Как поднять сервер без своего ПК

Серверу нужно совсем немного: Node.js 22+, 200 МБ диска и постоянный IP.
Подойдёт самая дешёвая или бесплатная Linux-машина.

## Вариант 1. Oracle Cloud Always Free (бесплатно навсегда)

Это единственный нормальный бесплатный вариант с постоянным диском и IP.
Карта нужна только для проверки личности, Always Free ресурсы не списывают деньги.

1. Зарегистрироваться на <https://www.oracle.com/cloud/free/>.
2. Создать Compute Instance:
   - образ **Ubuntu 24.04**,
   - форма **Ampere A1 (ARM)**, 1 OCPU / 6 ГБ — входит в бесплатный лимит,
   - при создании скачать SSH-ключ (или добавить свой публичный).
3. Открыть порт 8787:
   - в консоли Oracle: **Networking → VCN → Security Lists → Default Security List**,
     добавить Ingress Rule: source `0.0.0.0/0`, protocol TCP, port `8787`;
   - на машине это сделает `install-ubuntu.sh` через `ufw`.
4. С этого ПК залить сервер:

   ```powershell
   cd server
   .\deploy.ps1 -Server <IP-сервера> -User ubuntu -Key "$env:USERPROFILE\Downloads\ssh-key.key"
   ```

5. Проверить: `http://<IP>:8787/api/ping` должен вернуть `{"ok":true,…}`.

6. Задать пароль панели управления (иначе он сгенерируется и попадёт в лог):

   ```bash
   sudo nano /etc/headsetconnector.env
   # ADMIN_PASSWORD=ваш_сложный_пароль
   sudo systemctl restart headsetconnector
   ```

   Панель: `http://<IP>:8787/admin`. Это просто сайт — на своём ПК ничего
   держать не нужно.

В приложении на шлеме ввести этот адрес вручную (кнопка «Найти в сети» ищет
только внутри Wi-Fi).

### Домен и HTTPS

Пароли и сообщения по голому HTTP идут открытым текстом. Для себя любимого
сойдёт, но лучше закрыть HTTPS:

1. Взять бесплатный домен (или поддомен) и направить A-запись на IP.
2. Установить Caddy: <https://caddyserver.com/docs/install>.
3. Взять `Caddyfile.example`, заменить домен, положить в `/etc/caddy/Caddyfile`,
   выполнить `sudo systemctl reload caddy`.
4. Закрыть порт 8787 снаружи: `sudo ufw deny 8787/tcp`.
5. В приложении указать `https://ваш-домен`.

Если домена нет — можно взять бесплатный поддомен на DuckDNS и включить для
Caddy DNS-челлендж.

## Вариант 2. Дешёвый VPS (2–4 $/мес)

Любой хостер (Timeweb, Aeza, Hetzner, Contabo). Дальше то же самое:

```powershell
cd server
.\deploy.ps1 -Server <IP> -User root
```

`install-ubuntu.sh` поставит Node.js, создаст службу `headsetconnector`
с автозапуском и перезапуском при падении. Логи:

```bash
journalctl -u headsetconnector -f
```

## Вариант 3. Docker (где угодно)

```bash
docker compose up -d --build
```

Данные лежат в `./data` рядом с `docker-compose.yml`. Этот же образ работает
на Render/Koyeb/Fly, но у бесплатных тарифов диск не сохраняется — база будет
обнуляться при перезапуске.

## Резервная копия

Вся база и медиа — это папка `data/`. Достаточно скопировать её:

```powershell
scp -r ubuntu@<IP>:/opt/headsetconnector/data .\backup\
```

## Что НЕ подойдёт для 24/7

- **Render / Koyeb free** — контейнер засыпает и диск стирается, аккаунты пропадут.
- **Cloudflare Workers free** — бесплатно и без карты, но сервер придётся
  переписать под D1/KV. Возможно, но это отдельная работа.
- **Старый Android-телефон + Termux** — бесплатно, но нужен постоянный Wi-Fi,
  зарядка и разлоченный экран; как временный вариант годится.

## Быстрый тест без хостинга

Пока VPS нет, можно открыть доступ с этого ПК через бесплатный туннель:

```powershell
cd server
.\tunnel.ps1
```

Скрипт выдаст `https://…trycloudflare.com`. ПК должен быть включён, адрес
меняется при каждом запуске.
