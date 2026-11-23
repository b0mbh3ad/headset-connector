$ErrorActionPreference = "Stop"
if (-not $env:PORT) { $env:PORT = "8787" }
Write-Host "HeadsetConnector server на порту $env:PORT" -ForegroundColor Cyan
node "$PSScriptRoot\server.js"
