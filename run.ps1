param([int]$Port=8080, [switch]$BuildOnly)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=[System.Text.UTF8Encoding]::new($false)
$OutputEncoding=[Console]::OutputEncoding
Set-Location -LiteralPath $PSScriptRoot
New-Item -ItemType Directory -Path (Join-Path $PSScriptRoot 'work') -Force | Out-Null
try {
    $runLock=[System.IO.File]::Open((Join-Path $PSScriptRoot 'work\run.lock'),[System.IO.FileMode]::OpenOrCreate,[System.IO.FileAccess]::ReadWrite,[System.IO.FileShare]::None)
} catch {
    Write-Host "Another SQLFlow launcher is building or running. Open http://localhost:$Port after startup completes."
    exit 0
}
try {
if ($Port -lt 1 -or $Port -gt 65535) { throw 'Port must be between 1 and 65535.' }
$probe=[System.Net.Sockets.TcpClient]::new()
$portInUse=$false
try { $portInUse=$probe.ConnectAsync('localhost',$Port).Wait(1000) -and $probe.Connected } catch {} finally { $probe.Dispose() }
if ($portInUse) {
    $isSqlFlow=$false
    try { $status=Invoke-RestMethod -Uri "http://localhost:$Port/api/dashboard" -TimeoutSec 3; $isSqlFlow=$status.demo -eq $true -and $null -ne $status.user } catch {}
    if ($isSqlFlow) {
        Write-Host "SQLFlow is already running: http://localhost:$Port"
        Write-Host 'No rebuild was attempted. Stop its terminal with Ctrl+C before rebuilding changed files.'
        exit 0
    }
    throw "Port $Port is in use. Stop that server first or run with -Port 8081."
}
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'maven.ps1') -B -ntp package -DskipTests
if ($LASTEXITCODE -ne 0) { throw 'Build failed. Check the Maven [ERROR] lines above. A running WAR can lock target files on Windows; stop the existing SQLFlow terminal first.' }
if ($BuildOnly) { Write-Host 'Build completed successfully.'; exit 0 }
$jdk=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot '.tools') -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue | Select-Object -First 1
$java=if ($jdk) { Join-Path $jdk.FullName 'bin\java.exe' } else { 'java' }
Write-Host "Starting SQLFlow: http://localhost:$Port"
& $java '-Dfile.encoding=UTF-8' '-jar' (Join-Path $PSScriptRoot 'target\sqlflow-0.0.1-SNAPSHOT.war') "--server.port=$Port"
} finally {
    $runLock.Dispose()
}
