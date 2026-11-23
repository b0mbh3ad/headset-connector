# Заливка HeadsetConnector на бесплатный хостинг alwaysdata.
#
# Один раз:
#   1. Зарегистрироваться (без карты): https://www.alwaysdata.com/en/register/?p=2012
#   2. Запустить: .\deploy-alwaysdata.ps1 -Account ВАШ_ЛОГИН
#   3. Вставить напечатанный ключ в панели:
#      Remote access > SSH/SFTP > пользователь > SSH keys > Add a SSH key
#   4. Нажать Enter в скрипте — он зальёт файлы и напечатает адрес сайта.
#
# Повторный запуск обновляет сервер (файлы перезаписываются, база не трогается).

param(
    [Parameter(Mandatory = $true)][string]$Account,
    [string]$Key = "$env:USERPROFILE\.ssh\headsetconnector",
    [string]$AdminPassword = "",
    [switch]$KeyInstalled
)

$ErrorActionPreference = "Stop"

$Account = $Account.Trim().ToLowerInvariant()
if ($Account -notmatch '^[a-z0-9][a-z0-9_-]{1,30}$') {
    throw "Логин alwaysdata может содержать только латиницу, цифры, _ и -"
}

$here = Split-Path $PSScriptRoot -Parent
$hostName = "ssh-$Account.alwaysdata.net"
$target = "$Account@$hostName"
$publicUrl = "https://$Account.alwaysdata.net"

function New-Password {
    $chars = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    -join (1..16 | ForEach-Object { $chars[(Get-Random -Maximum $chars.Length)] })
}

if (-not (Get-Command ssh -ErrorAction SilentlyContinue)) {
    throw "Не найден ssh. Включите «Клиент OpenSSH» в компонентах Windows."
}

if (-not (Test-Path $Key)) {
    Write-Host "Создаю SSH-ключ для alwaysdata…" -ForegroundColor Cyan
    $sshDir = Split-Path $Key -Parent
    if (-not (Test-Path $sshDir)) { New-Item -ItemType Directory -Path $sshDir | Out-Null }
    & ssh-keygen -t ed25519 -f $Key -N '""' -C "headsetconnector" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "ssh-keygen не смог создать ключ" }
}

$pub = Get-Content "$Key.pub" -Raw
if (-not $KeyInstalled) {
    Write-Host ""
    Write-Host "=== Шаг 1. Добавьте ключ в панели alwaysdata ===" -ForegroundColor Yellow
    Write-Host "Откройте https://admin.alwaysdata.com → Remote access → SSH/SFTP"
    Write-Host "→ ваш пользователь → SSH keys → Add a SSH key и вставьте строку:"
    Write-Host ""
    Write-Host $pub.Trim() -ForegroundColor Green
    Write-Host ""
    Read-Host "Когда ключ добавлен, нажмите Enter"
} else {
    Write-Host "Использую ключ $Key (ключ уже добавлен в панели)." -ForegroundColor DarkGray
}

$sshArgs = @(
    "-i", $Key,
    "-o", "IdentitiesOnly=yes",
    "-o", "StrictHostKeyChecking=accept-new",
    "-o", "ConnectTimeout=20"
)

Write-Host "Проверяю связь с $hostName…" -ForegroundColor Cyan
& ssh @sshArgs $target "echo ok"
if ($LASTEXITCODE -ne 0) {
    throw "SSH не пустил. Проверьте, что ключ добавлен и логин «$Account» верный."
}

& ssh @sshArgs $target "mkdir -p hc/data && chmod 700 hc hc/data"
if ($LASTEXITCODE -ne 0) { throw "Не удалось создать папку ~/hc" }

if (-not $AdminPassword) { $AdminPassword = New-Password }

$envFile = Join-Path $env:TEMP "hc-alwaysdata.env"
$envText = "# Пароль панели /admin. Измените и перезапустите сайт в панели." + "`n" +
           "ADMIN_PASSWORD=$AdminPassword" + "`n" +
           "PUBLIC_URL=$publicUrl" + "`n"
[System.IO.File]::WriteAllText($envFile, $envText, (New-Object System.Text.UTF8Encoding($false)))

Write-Host "Копирую файлы…" -ForegroundColor Cyan
& scp @sshArgs "$here\server.js" "$here\admin.html" "$here\package.json" $envFile "${target}:hc/"
if ($LASTEXITCODE -ne 0) { throw "Не удалось скопировать файлы" }

# APK и описание версии — для автообновления приложения. Если их нет рядом
# со скриптом, пропускаем (сервер просто не отдаёт обновления).
if (Test-Path "$PSScriptRoot\app.apk") {
    & scp @sshArgs "$PSScriptRoot\app.apk" "${target}:hc/app.apk"
    if ($LASTEXITCODE -ne 0) { throw "Не удалось скопировать app.apk" }
}
if (Test-Path "$PSScriptRoot\version.json") {
    & scp @sshArgs "$PSScriptRoot\version.json" "${target}:hc/version.json"
    if ($LASTEXITCODE -ne 0) { throw "Не удалось скопировать version.json" }
}

& ssh @sshArgs $target "mv hc/hc-alwaysdata.env hc/.env && chmod 600 hc/.env && ls -la hc"
if ($LASTEXITCODE -ne 0) { throw "Файлы скопированы, но .env не переименовался" }

Write-Host ""
Write-Host "=== Шаг 2. Создайте сайт в панели (один раз) ===" -ForegroundColor Yellow
Write-Host "https://admin.alwaysdata.com → Web → Sites → Add a site"
Write-Host ""
Write-Host "  Type ............. Node.js"
Write-Host "  Addresses ........ $Account.alwaysdata.net"
Write-Host "  Command .......... node /home/$Account/hc/server.js"
Write-Host "  Node.js version .. 24"
Write-Host ""
Write-Host "Сохраните. Через минуту проверьте в браузере:" -ForegroundColor Cyan
Write-Host "  $publicUrl/api/ping"
Write-Host "  $publicUrl/admin"
Write-Host ""
Write-Host "Пароль панели: $AdminPassword" -ForegroundColor Green
Write-Host "Адрес для приложения: $publicUrl" -ForegroundColor Green
Write-Host ""
Write-Host "Этот же скрипт запускайте снова для обновления сервера." -ForegroundColor DarkGray
