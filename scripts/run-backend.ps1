param(
    [int]$Port = 8082
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$BackendDir = Join-Path $Root "backend"

$env:PORT = [string]$Port
$env:ENABLE_ADB_REVERSE = "false"

Set-Location $BackendDir
& "C:\Program Files\nodejs\node.exe" "src/server.js"
