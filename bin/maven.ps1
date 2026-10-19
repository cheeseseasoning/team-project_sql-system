param([Parameter(ValueFromRemainingArguments=$true)][string[]]$MavenArguments)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=[System.Text.UTF8Encoding]::new($false)
$OutputEncoding=[Console]::OutputEncoding
Set-Location -LiteralPath $PSScriptRoot
$jdk=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot '.tools') -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($jdk) { $env:JAVA_HOME=$jdk.FullName }
$localMaven=Join-Path $PSScriptRoot '.tools\apache-maven-3.9.11\bin\mvn.cmd'
if (Test-Path -LiteralPath $localMaven) { & $localMaven "-Dmaven.repo.local=$PSScriptRoot\.m2" @MavenArguments }
elseif (Get-Command mvn -ErrorAction SilentlyContinue) { & mvn @MavenArguments }
else { throw 'Install JDK 17+ and Maven, or run setup.ps1 first.' }
exit $LASTEXITCODE
