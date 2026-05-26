param(
    [int]$Port = 8082
)

$ErrorActionPreference = "Stop"

function Test-CommandExists {
    param([string]$CommandName)
    $null -ne (Get-Command $CommandName -ErrorAction SilentlyContinue)
}

if (-not (Test-CommandExists "cloudflared")) {
    Write-Host ""
    Write-Host "cloudflared가 설치되어 있지 않습니다."
    Write-Host "설치 후 다시 실행하세요:"
    Write-Host "  winget install --id Cloudflare.cloudflared"
    Write-Host ""
    Write-Host "설치가 끝나면 아래 명령을 다시 실행하면 됩니다:"
    Write-Host "  npm run tunnel"
    exit 1
}

Write-Host ""
Write-Host "TripNest 로컬 서버가 켜져 있어야 합니다."
Write-Host "외부 접속 주소가 나오면 Android local.properties의 BACKEND_BASE_URL에 넣으세요."
Write-Host ""
Write-Host "터널 대상: http://127.0.0.1:$Port"
Write-Host ""

cloudflared tunnel --url "http://127.0.0.1:$Port"
