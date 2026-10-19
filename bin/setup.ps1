$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
New-Item -ItemType Directory -Path '.tools' -Force | Out-Null
if (!(Test-Path -LiteralPath '.tools\apache-maven-3.9.11')) {
    Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip' -OutFile '.tools\maven.zip'
    Expand-Archive -LiteralPath '.tools\maven.zip' -DestinationPath '.tools' -Force
}
if (!(Get-ChildItem -LiteralPath '.tools' -Directory -Filter 'jdk-*')) {
    Invoke-WebRequest -Uri 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile '.tools\jdk.zip'
    Expand-Archive -LiteralPath '.tools\jdk.zip' -DestinationPath '.tools' -Force
}
& (Join-Path $PSScriptRoot 'maven.ps1') 'dependency:go-offline'
