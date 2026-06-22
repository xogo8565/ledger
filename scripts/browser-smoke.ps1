param(
    [string]$FrontendUrl = "http://host.docker.internal:8081",
    [string]$BackendUrl = "http://host.docker.internal:8080",
    [string]$PlaywrightImage = "mcr.microsoft.com/playwright:v1.49.0-noble"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$screenshotDir = Join-Path $repoRoot "data\test-artifacts"
New-Item -ItemType Directory -Force -Path $screenshotDir | Out-Null

docker run --rm `
    -v "${repoRoot}:/work" `
    -w /work `
    -e "FRONTEND_URL=$FrontendUrl" `
    -e "BACKEND_URL=$BackendUrl" `
    -e "SCREENSHOT_PATH=/work/data/test-artifacts/browser-smoke-home.png" `
    -e "SCREENSHOT_DIR=/work/data/test-artifacts" `
    $PlaywrightImage `
    /bin/sh -lc "mkdir -p /tmp/browser-smoke && cp /work/scripts/browser-smoke.mjs /tmp/browser-smoke/browser-smoke.mjs && cd /tmp/browser-smoke && npm init -y >/dev/null && npm install playwright@1.49.0 --no-save >/dev/null && node browser-smoke.mjs"
