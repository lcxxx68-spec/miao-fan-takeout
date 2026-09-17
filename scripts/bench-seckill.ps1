# =====================================================================
# miao-fan seckill benchmark runner
#
# Usage:
#   powershell -File scripts\bench-seckill.ps1
#
# The application runs as its own process and the load client runs in a
# separate process. Putting client and server inside the same JVM makes them
# fight for CPU, which makes the latency numbers meaningless.
#
# Comparison: same endpoint, same 200 concurrent users, only the persistence
# strategy changes (sync inside the request vs async via Redis Stream).
#
# IMPORTANT: this file must stay ASCII-only.
# Windows PowerShell reads .ps1 files using the system ANSI code page, so
# non-ASCII text gets mis-decoded and can even swallow line breaks, which
# silently merges the next code line into a comment.
# =====================================================================
param(
    # defaults to the repo this script lives in (its parent folder)
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [int]$Users = 200
)

$ErrorActionPreference = 'Stop'
$script:RootPath = $Root
$script:ModuleDir = Join-Path $Root 'backend\miao-fan-takeout'
$script:ServerDir = Join-Path $script:ModuleDir 'mf-server'
$script:JarPath = Join-Path $script:ServerDir 'target\mf-server-1.0-SNAPSHOT.jar'
$script:AppOutLog = Join-Path $Root '.bench-app.log'
$script:AppErrLog = Join-Path $Root '.bench-app.err.log'

function Start-App([string[]]$extraArgs) {
    $javaArgs = @('-jar', $script:JarPath) + $extraArgs
    $proc = Start-Process -FilePath 'java' -ArgumentList $javaArgs -WorkingDirectory $script:RootPath `
        -RedirectStandardOutput $script:AppOutLog -RedirectStandardError $script:AppErrLog `
        -WindowStyle Hidden -PassThru
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 2
        try {
            $r = Invoke-WebRequest -Uri 'http://localhost:18080/user/shop/status' -UseBasicParsing -TimeoutSec 3
            if ($r.StatusCode -eq 200) { return $proc }
        } catch { }
    }
    Get-Content $script:AppErrLog -Tail 20
    throw 'application failed to start'
}

function Run-Bench([string]$mode, [string[]]$extraArgs) {
    # Write-Host so these lines are not captured as the function's return value
    Write-Host ''
    Write-Host "==== benchmark mode: $mode ===="
    $proc = Start-App $extraArgs
    try {
        # Run from the mf-server module: running from the aggregator would make
        # Maven execute the -Dtest filter in every module, and modules without a
        # matching test fail the whole build.
        Push-Location $script:ServerDir
        $ErrorActionPreference = 'Continue'
        $output = & mvn test '-Dtest=SeckillBenchmarkClient' '-DfailIfNoTests=false' "-Dbench.mode=$mode" 2>&1
        $ErrorActionPreference = 'Stop'
        Pop-Location

        $logFile = Join-Path $script:RootPath ('.bench-mvn-' + $mode + '.log')
        $output | Out-File -LiteralPath $logFile -Encoding utf8

        $bench = $output | Select-String -Pattern 'BENCH mode=' | Select-Object -First 1
        if (-not $bench) {
            Write-Host "---- tail of $logFile ----"
            $output | Select-Object -Last 30 | ForEach-Object { Write-Host $_ }
            throw "benchmark produced no result: $mode"
        }
        $line = $bench.Line.Trim()
        Write-Host $line
        return $line
    } finally {
        if ($proc -and -not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
        Start-Sleep -Seconds 2
    }
}

Write-Output '==== build jar ===='
Push-Location $script:ModuleDir
$ErrorActionPreference = 'Continue'
mvn -q -DskipTests package
$ErrorActionPreference = 'Stop'
Pop-Location

# SQL debug logging is turned off for both runs: synchronous log writes are a
# real bottleneck and would distort the comparison.
$commonArgs = @('--logging.level.com.miaofan.mapper=info')
$asyncLine = Run-Bench 'async' $commonArgs
$syncLine = Run-Bench 'sync' ($commonArgs + '--miaofan.seckill.async-enabled=false')

Write-Output ''
Write-Output '===================== BENCH SUMMARY ====================='
Write-Output $syncLine
Write-Output $asyncLine
Write-Output '========================================================='
