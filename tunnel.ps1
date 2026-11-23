# Запускает бесплатный публичный туннель к локальному серверу (Cloudflare Quick Tunnel).
# Аккаунт не нужен, но адрес меняется при каждом запуске.
# Сервер должен быть уже запущен: .\start.ps1

$ErrorActionPreference = "Stop"
$tools = Join-Path $PSScriptRoot "tools"
New-Item -ItemType Directory -Path $tools -Force | Out-Null
$exe = Join-Path $tools "cloudflared.exe"

if (-not (Test-Path $exe)) {
    Write-Host "Скачиваю cloudflared…" -ForegroundColor Yellow
    Invoke-WebRequest `
        -Uri "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe" `
        -OutFile $exe
}

Write-Host "Открываю туннель. Скопируйте адрес https://…trycloudflare.com в приложение." -ForegroundColor Cyan
& $exe tunnel --url "http://localhost:8787" --no-autoupdate
