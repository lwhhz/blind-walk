<#
    Build helper.

    Why this exists:
    When the project lives under a path containing non-ASCII characters (Windows),
    Gradle's test worker fails to load test classes:

        java.lang.ClassNotFoundException: com.blindwalk.app.domain.GeoTest

    Cause: Gradle writes the long classpath into an @argfile (UTF-8), but the JDK 21
    Windows launcher reads that file using the system ANSI codepage, so non-ASCII
    path segments are decoded incorrectly and the classpath points nowhere.

    Workaround: `subst` a free drive letter to the project's parent directory so that
    Gradle only ever sees an ASCII path. Sources do not move; build outputs stay in
    the project directory.

    Gradle version: the project now needs Gradle 9.x (AGP 9.x + Compose 1.12 +
    backdrop 2.0.1). A local distribution is used when present:
        C:\Gradle\gradle-9.8.2
    otherwise it falls back to `gradle` on PATH.

    Usage:
      .\build.ps1                      # assembleDebug
      .\build.ps1 testDebugUnitTest    # unit tests
      .\build.ps1 assembleRelease

    NOTE: keep this file pure ASCII as well, so Windows PowerShell 5.1 parses it
    correctly regardless of the active codepage.
#>
param([string]$Task = "assembleDebug")

$ErrorActionPreference = "Stop"

$androidDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$parentDir = Split-Path -Parent $androidDir
$gradleArgs = @("--console=plain", $Task)

# JAVA_HOME must be the JDK root, not ...\bin\java.exe
$javaExe = (Get-Command java -ErrorAction SilentlyContinue).Source
if ($javaExe) {
    $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $javaExe)
}

$localGradle = "C:\Gradle\gradle-9.8.2\bin\gradle.bat"
if (Test-Path $localGradle) {
    $gradleCmd = $localGradle
} else {
    $gradleCmd = "gradle"
}

$hasNonAscii = $androidDir -match '[^\x00-\x7F]'
if (-not $hasNonAscii) {
    & $gradleCmd -p $androidDir @gradleArgs
    exit $LASTEXITCODE
}

$used = @(Get-PSDrive -PSProvider FileSystem | ForEach-Object { $_.Name })
$free = @("B", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z") |
    Where-Object { $used -notcontains $_ } |
    Select-Object -First 1

if (-not $free) { throw "No free drive letter available for subst." }

$drive = "${free}:"
Write-Host "[build] Non-ASCII project path detected; mapping $drive to the project root" -ForegroundColor Yellow
cmd /c "subst $drive `"$parentDir`"" | Out-Null

try {
    & $gradleCmd -p "$drive\android" @gradleArgs
    $code = $LASTEXITCODE
} finally {
    cmd /c "subst $drive /D" | Out-Null
}

exit $code