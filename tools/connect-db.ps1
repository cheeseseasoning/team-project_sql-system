param(
    [Parameter(Mandatory = $true)]
    [string]$KeyPath,

    [string]$SshUser = "ubuntu",
    [string]$SshHost = "13.124.37.144",
    [int]$SshPort = 22,

    [string]$OracleHost = "127.0.0.1",
    [string]$MySqlHost = "127.0.0.1"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $KeyPath -PathType Leaf)) {
    throw "SSH 키 파일을 찾을 수 없습니다: $KeyPath"
}

$resolvedKeyPath = (Resolve-Path -LiteralPath $KeyPath).Path

$sshArguments = @(
    "-i", $resolvedKeyPath,
    "-p", "$SshPort",
    "-N",
    "-o", "ExitOnForwardFailure=yes",
    "-o", "ServerAliveInterval=30",
    "-o", "ServerAliveCountMax=3",
    "-L", "127.0.0.1:11521:${OracleHost}:1521",
    "-L", "127.0.0.1:13306:${MySqlHost}:3306",
    "${SshUser}@${SshHost}"
)

Write-Host "DB SSH 터널을 연결합니다."
Write-Host "Oracle: localhost:11521"
Write-Host "MySQL : localhost:13306"
Write-Host "연결된 동안 이 창을 열어 두세요. 종료: Ctrl+C"

& ssh @sshArguments

if ($LASTEXITCODE -ne 0) {
    throw "SSH 연결이 종료되었습니다. 위 오류 메시지를 확인하세요."
}