param(
    [string]$HostName = $env:OCI_HOST,
    [string]$User = $env:OCI_USER,
    [string]$KeyPath = $env:OCI_SSH_KEY_PATH,
    [string]$AppDir = $env:OCI_APP_DIR,
    [switch]$SkipSsh
)

$ErrorActionPreference = "Stop"

function Add-Check {
    param(
        [System.Collections.Generic.List[object]]$Checks,
        [string]$Name,
        [bool]$Passed,
        [string]$Detail
    )

    $Checks.Add([pscustomobject]@{
        name = $Name
        passed = $Passed
        detail = $Detail
    }) | Out-Null
}

function Assert-File {
    param(
        [System.Collections.Generic.List[object]]$Checks,
        [string]$Name,
        [string]$Path
    )

    Add-Check $Checks $Name (Test-Path -LiteralPath $Path -PathType Leaf) $Path
}

function Assert-Directory {
    param(
        [System.Collections.Generic.List[object]]$Checks,
        [string]$Name,
        [string]$Path
    )

    Add-Check $Checks $Name (Test-Path -LiteralPath $Path -PathType Container) $Path
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$checks = [System.Collections.Generic.List[object]]::new()

Assert-File $checks "GitHub Actions OCI workflow" (Join-Path $repoRoot ".github\workflows\deploy-oci.yml")
Assert-File $checks "Docker Compose file" (Join-Path $repoRoot "docker-compose.yml")
Assert-File $checks "Environment example" (Join-Path $repoRoot ".env.example")
Assert-Directory $checks "Persistent data directory" (Join-Path $repoRoot "data")

$compose = Get-Content -Raw -Encoding utf8 (Join-Path $repoRoot "docker-compose.yml")
Add-Check $checks "Backend GHCR image configured" ($compose -match "ghcr\.io/.+/comfortable-ledger-backend:latest") "docker-compose.yml backend image"
Add-Check $checks "Frontend GHCR image configured" ($compose -match "ghcr\.io/.+/comfortable-ledger-frontend:latest") "docker-compose.yml frontend image"

$workflow = Get-Content -Raw -Encoding utf8 (Join-Path $repoRoot ".github\workflows\deploy-oci.yml")
Add-Check $checks "Workflow supports manual dispatch" ($workflow -match "workflow_dispatch") ".github/workflows/deploy-oci.yml"
Add-Check $checks "Workflow validates OCI secrets" ($workflow -match "Validate deployment secrets") ".github/workflows/deploy-oci.yml"
Add-Check $checks "Workflow pulls images on server" ($workflow -match "docker compose pull") ".github/workflows/deploy-oci.yml"
Add-Check $checks "Workflow health checks backend" ($workflow -match "localhost:8080/api/bootstrap") ".github/workflows/deploy-oci.yml"
Add-Check $checks "Workflow health checks frontend" ($workflow -match "localhost:8081") ".github/workflows/deploy-oci.yml"

$sshConfigured = -not $SkipSsh -and $HostName -and $User -and $KeyPath -and $AppDir
if (-not $sshConfigured) {
    Add-Check $checks "SSH readiness check skipped" $true "Provide HostName/User/KeyPath/AppDir or env OCI_HOST/OCI_USER/OCI_SSH_KEY_PATH/OCI_APP_DIR to check server prerequisites."
} else {
    Add-Check $checks "SSH key file exists" (Test-Path -LiteralPath $KeyPath -PathType Leaf) $KeyPath

    $remoteScript = @'
set -eu
printf 'user=%s\n' "$(id -un)"
command -v git >/dev/null && printf 'git=ok\n'
command -v docker >/dev/null && printf 'docker=ok\n'
docker compose version >/dev/null && printf 'docker_compose=ok\n'
test -d "$OCI_APP_DIR" && printf 'app_dir=ok\n'
test -f "$OCI_APP_DIR/.env" && printf 'env_file=ok\n'
test -d "$OCI_APP_DIR/data" && printf 'data_dir=ok\n'
test -d "$OCI_APP_DIR/.git" && printf 'git_repo=ok\n'
'@

    $remoteOutput = $remoteScript | & ssh -o BatchMode=yes -o StrictHostKeyChecking=accept-new -i $KeyPath "$User@$HostName" "OCI_APP_DIR='$AppDir' sh -s" 2>&1
    $remoteText = ($remoteOutput -join "`n")
    Add-Check $checks "SSH connection" ($LASTEXITCODE -eq 0) "$User@$HostName"
    Add-Check $checks "Remote git installed" ($remoteText -match "git=ok") "git"
    Add-Check $checks "Remote Docker installed" ($remoteText -match "docker=ok") "docker"
    Add-Check $checks "Remote Docker Compose installed" ($remoteText -match "docker_compose=ok") "docker compose"
    Add-Check $checks "Remote app directory exists" ($remoteText -match "app_dir=ok") $AppDir
    Add-Check $checks "Remote .env exists" ($remoteText -match "env_file=ok") "$AppDir/.env"
    Add-Check $checks "Remote data directory exists" ($remoteText -match "data_dir=ok") "$AppDir/data"
    Add-Check $checks "Remote app directory is git repository" ($remoteText -match "git_repo=ok") "$AppDir/.git"
}

$failed = @($checks | Where-Object { -not $_.passed })
$checks | Format-Table -AutoSize

if ($failed.Count -gt 0) {
    throw "OCI deploy readiness failed: $($failed.Count) check(s) failed."
}

"OCI deploy readiness checks passed."
