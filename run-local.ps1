param([switch]$Verify)
$ErrorActionPreference = 'Stop'
$repoRoot = $PSScriptRoot
$workspaceRoot = Split-Path (Split-Path $repoRoot -Parent) -Parent
$javaRoot = Join-Path $workspaceRoot 'work/tools/java'
$mavenCommand = Join-Path $workspaceRoot 'work/tools/maven/apache-maven-3.9.9/bin/mvn.cmd'
if (Test-Path $javaRoot) {
    $env:JAVA_HOME = (Get-ChildItem $javaRoot -Directory | Select-Object -First 1).FullName
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
}
Push-Location $repoRoot
try {
    if (Test-Path $mavenCommand) {
        $cache = Join-Path $workspaceRoot 'work/m2'
        if ($Verify) { & $mavenCommand -B -ntp "-Dmaven.repo.local=$cache" verify }
        else { & $mavenCommand "-Dmaven.repo.local=$cache" spring-boot:run }
    } else {
        if ($Verify) { & "$repoRoot/mvnw.cmd" verify }
        else { & "$repoRoot/mvnw.cmd" spring-boot:run }
    }
    if ($LASTEXITCODE -ne 0) { throw "Maven exited with $LASTEXITCODE" }
} finally { Pop-Location }
