# Заливка сервера HeadsetConnector на Linux-машину по SSH.
#
# Примеры:
#   .\deploy.ps1 -Server 123.45.67.89 -User ubuntu -Key "$env:USERPROFILE\.ssh\oracle.key"
#   .\deploy.ps1 -Server root@123.45.67.89
#
# Скрипт копирует файлы в /opt/headsetconnector и запускает install-ubuntu.sh,
# который ставит Node.js 22, создаёт systemd-службу и открывает порт.

param(
    [Parameter(Mandatory = $true)][string]$Server,
    [string]$User = "ubuntu",
    [int]$Port = 22,
    [string]$Key = ""
)

$ErrorActionPreference = "Stop"
$here = $PSScriptRoot
$target = if ($Server.Contains("@")) { $Server } else { "$User@$Server" }
$dir = "/opt/headsetconnector"

$sshArgs = @("-p", "$Port")
$scpArgs = @("-P", "$Port")
if ($Key) {
    $sshArgs += @("-i", $Key)
    $scpArgs += @("-i", $Key)
}

Write-Host "Копирую файлы на $target…" -ForegroundColor Cyan
& ssh @sshArgs $target "sudo mkdir -p $dir && sudo chown `$USER`:`$USER` $dir"
if ($LASTEXITCODE -ne 0) { throw "SSH не подключился" }

& scp @scpArgs "$here\server.js" "$here\admin.html" "$here\package.json" "$here\install-ubuntu.sh" "${target}:$dir/"
if ($LASTEXITCODE -ne 0) { throw "Не удалось скопировать файлы" }

Write-Host "Устанавливаю и запускаю службу…" -ForegroundColor Cyan
& ssh @sshArgs $target "cd $dir && sudo bash install-ubuntu.sh"
if ($LASTEXITCODE -ne 0) { throw "Установка не завершилась" }

Write-Host "Готово. Проверьте http://<IP-сервера>:8787/api/ping" -ForegroundColor Green
