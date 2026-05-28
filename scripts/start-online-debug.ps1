param(
    [int]$Port = 8082
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$BackendDir = Join-Path $Root "backend"
$LocalProperties = Join-Path $Root "local.properties"
$ExpoEnvFiles = @(
    (Join-Path $Root "mobile\.env"),
    (Join-Path $Root "tripnest-expo\tripnest-expo\.env")
)
$TunnelLog = Join-Path $Root "tunnel.err.log"
$Cloudflared = Get-Command cloudflared -ErrorAction SilentlyContinue

if (-not $Cloudflared) {
    $WingetCloudflared = Join-Path $env:LOCALAPPDATA "Microsoft\WinGet\Packages\Cloudflare.cloudflared_Microsoft.Winget.Source_8wekyb3d8bbwe\cloudflared.exe"
    if (Test-Path $WingetCloudflared) {
        $Cloudflared = [pscustomobject]@{ Source = $WingetCloudflared }
    }
}

if (-not $Cloudflared) {
    throw "cloudflared가 없습니다. winget install --id Cloudflare.cloudflared 로 설치하세요."
}

function Test-Health {
    try {
        Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:$Port/api/health" -TimeoutSec 3 | Out-Null
        return $true
    } catch {
        return $false
    }
}

function Set-KeyValueFileValue {
    param(
        [string]$Path,
        [hashtable]$Values
    )

    $dir = Split-Path -Parent $Path
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir | Out-Null
    }

    $lines = @()
    if (Test-Path $Path) {
        $lines = @(Get-Content $Path)
    }

    foreach ($key in $Values.Keys) {
        $value = $Values[$key]
        $found = $false
        $lines = @($lines | ForEach-Object {
            if ($_ -match "^$([regex]::Escape($key))=") {
                $found = $true
                "$key=$value"
            } else {
                $_
            }
        })

        if (-not $found) {
            $lines += "$key=$value"
        }
    }

    Set-Content -LiteralPath $Path -Value $lines
}

if (-not (Test-Health)) {
    $backendScript = Join-Path $Root "scripts\run-backend.ps1"
    Start-Process -FilePath "powershell.exe" -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-File", "`"$backendScript`"", "-Port", $Port -WindowStyle Minimized

    for ($i = 0; $i -lt 15; $i++) {
        Start-Sleep -Seconds 1
        if (Test-Health) {
            break
        }
    }
}

if (-not (Test-Health)) {
    throw "백엔드가 http://127.0.0.1:$Port 에서 응답하지 않습니다."
}

Remove-Item -LiteralPath $TunnelLog -ErrorAction SilentlyContinue
$tunnelCommand = "cd /d `"$Root`" && `"$($Cloudflared.Source)`" tunnel --url http://127.0.0.1:$Port > tunnel.out.log 2> tunnel.err.log"
Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "start", "`"TripNest Tunnel`"", "/MIN", "cmd.exe", "/k", $tunnelCommand -WindowStyle Hidden

$TunnelUrl = ""
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 1
    if (Test-Path $TunnelLog) {
        $content = Get-Content $TunnelLog -Raw
        $match = [regex]::Match($content, "https://[a-z0-9-]+\.trycloudflare\.com")
        if ($match.Success) {
            $TunnelUrl = $match.Value
            break
        }
    }
}

if (-not $TunnelUrl) {
    throw "Cloudflare Tunnel 주소를 가져오지 못했습니다. tunnel.err.log를 확인하세요."
}

Set-KeyValueFileValue -Path $LocalProperties -Values @{
    "BACKEND_BASE_URL" = $TunnelUrl
    "BACKEND_FALLBACK_URL" = $TunnelUrl
}

foreach ($envFile in $ExpoEnvFiles) {
    Set-KeyValueFileValue -Path $envFile -Values @{
        "EXPO_PUBLIC_BACKEND_BASE_URL" = $TunnelUrl
        "EXPO_PUBLIC_BACKEND_FALLBACK_URL" = $TunnelUrl
    }
}

Write-Host "TripNest backend: http://127.0.0.1:$Port"
Write-Host "TripNest online URL: $TunnelUrl"
Write-Host "local.properties updated."
Write-Host "Expo .env files updated."

Set-Location $Root
& .\gradlew.bat assembleDebug
