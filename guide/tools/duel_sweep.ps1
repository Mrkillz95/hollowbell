# Runs the giant duel harness over all 28 pairs, a few pairs per server (one server runs out of memory after about
# 20 full-size duels), through the shared Gradle slots. Every run appends to build/duels/<Out>.csv.
# -Parallel N: N shards at once, each in its own copy of this project (build/shards/<i>, so their Gradle builds, run
# folders and worlds never meet) and its own slot; their CSVs are merged into build/duels/<Out>*.csv at the end.
# A server that writes no row for -StallMinutes is taken to be hung (it happened: a giant's line-of-sight check to a
# robot whose position went NaN loaded chunks without end): it is stopped, the duel it was on is noted in
# build/duels/<Out>-hung.txt, and the sweep goes on with the next group.
# usage: powershell -NoProfile -File tools\duel_sweep.ps1 [-Out base] [-Runs 4] [-Seconds 300] [-Tune @file]
#        [-PerServer 3] [-Pairs "P-G,C-H"] [-StallMinutes 8] [-Fix grudge] [-Escape] [-Parallel 4] [-Heap 4G]
# (-Tune @file: the file is read from build/duels, copied into each shard)
param([string]$Out = 'base', [int]$Runs = 4, [int]$Seconds = 300, [string]$Tune = '', [int]$PerServer = 3,
    [string]$Pairs = '', [int]$StallMinutes = 8, [string]$Fix = '', [switch]$Escape, [int]$Parallel = 1,
    [string]$Heap = '4G', [string]$Guide = '')
$self = $PSCommandPath
$main = Split-Path -Parent $PSScriptRoot
if (-not $Guide) { $Guide = $main }
$withslot = 'C:\Users\jeria\giants\slots\withslot.ps1'
$csv = Join-Path $Guide "build\duels\$Out.csv"
$codes = 'P', 'F', 'C', 'H', 'W', 'WB', 'S', 'G'
$all = if ($Pairs) { $Pairs -split ',' } else {
    for ($i = 0; $i -lt $codes.Count; $i++) { for ($j = $i + 1; $j -lt $codes.Count; $j++) { "$($codes[$i])-$($codes[$j])" } }
}

if ($Parallel -gt 1) {
    $jobs = @()
    for ($s = 0; $s -lt $Parallel; $s++) {
        $mine = @(for ($k = $s; $k -lt $all.Count; $k += $Parallel) { $all[$k] })
        if ($mine.Count -eq 0) { continue }
        $copy = Join-Path $main "build\shards\$s"
        New-Item -ItemType Directory -Force $copy | Out-Null
        foreach ($f in 'build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat', 'LICENSE') {
            Copy-Item (Join-Path $main $f) $copy -Force
        }
        foreach ($d in 'gradle', 'src', 'run-mods') {
            robocopy (Join-Path $main $d) (Join-Path $copy $d) /MIR /NFL /NDL /NJH /NJS /NP | Out-Null
        }
        New-Item -ItemType Directory -Force (Join-Path $copy 'build\duels') | Out-Null
        if ($Tune -like '@*') { Copy-Item (Join-Path $main "build\duels\$($Tune.Substring(1))") (Join-Path $copy 'build\duels') -Force }
        foreach ($f in "$Out.csv", "$Out-moves.csv", "$Out-hung.txt") { Remove-Item (Join-Path $copy "build\duels\$f") -ErrorAction SilentlyContinue }
        $a = @('-NoProfile', '-File', $self, '-Guide', $copy, '-Out', $Out, '-Runs', $Runs, '-Seconds', $Seconds,
            '-PerServer', $PerServer, '-Pairs', ($mine -join ','), '-StallMinutes', $StallMinutes, '-Heap', $Heap)
        if ($Tune) { $a += '-Tune', $Tune }
        if ($Fix) { $a += '-Fix', $Fix }
        if ($Escape) { $a += '-Escape' }
        Write-Host "duel_sweep: shard $s ($($mine -join ','))"
        $jobs += Start-Process powershell -ArgumentList $a -NoNewWindow -PassThru `
            -RedirectStandardOutput (Join-Path $env:TEMP "duel_shard_${Out}_$s.log")
        Start-Sleep -Seconds 20
    }
    $jobs | ForEach-Object { $_.WaitForExit() }
    # merge: one header, every shard's rows
    New-Item -ItemType Directory -Force (Join-Path $main 'build\duels') | Out-Null
    foreach ($f in "$Out.csv", "$Out-moves.csv", "$Out-hung.txt") {
        $dest = Join-Path $main "build\duels\$f"
        $parts = 0..($Parallel - 1) | ForEach-Object { Join-Path $main "build\shards\$_\build\duels\$f" } | Where-Object { Test-Path $_ }
        $first = -not (Test-Path $dest)
        foreach ($p in $parts) {
            $lines = @(Get-Content $p)
            if ($f -like '*.csv' -and -not $first) { $lines = $lines | Select-Object -Skip 1 }
            Add-Content $dest $lines -Encoding utf8
            $first = $false
        }
    }
    Write-Host "duel_sweep: merged into build\duels\$Out.csv"
    exit 0
}

function Rows { if (Test-Path $csv) { @(Get-Content $csv).Count } else { 0 } }
for ($k = 0; $k -lt $all.Count; $k += $PerServer) {
    $group = ($all[$k..([Math]::Min($k + $PerServer, $all.Count) - 1)]) -join ','
    Write-Host "duel_sweep: $group"
    $a = @('-NoProfile', '-File', $withslot, $Guide, 'runDuels', '-Pduels', "-Pduel_pairs=$group", "-Pduel_runs=$Runs",
        "-Pduel_seconds=$Seconds", "-Pduel_out=$Out", "-Pduel_tag=$Out", "-PduelHeap=$Heap", '--console=plain')
    if ($Tune) { $a += "-Pduel_tune=$Tune" }
    if ($Fix) { $a += "-Pduel_fix=$Fix" }
    if ($Escape) { $a += '-Pduel_escape=true' }
    $log = Join-Path $env:TEMP "duel_sweep_${Out}_$(Split-Path -Leaf $Guide).log"
    $p = Start-Process powershell -ArgumentList $a -NoNewWindow -PassThru -RedirectStandardOutput $log
    $rows = Rows; $since = Get-Date
    while (-not $p.HasExited) {
        Start-Sleep -Seconds 20
        $now = Rows
        if ($now -ne $rows) { $rows = $now; $since = Get-Date; continue }
        if (((Get-Date) - $since).TotalMinutes -lt $StallMinutes) { continue }
        # (this project copy's own server only: its launch config sits under its folder)
        $server = Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
            Where-Object { $_.CommandLine -like "*$Guide\.gradle\loom-cache*" -and $_.CommandLine -match 'KnotServer' }
        $mc = Join-Path $Guide 'build\duels\logs\latest.log'
        $batch = (Select-String -Path $mc -Pattern "Running test batch '([^']+)'" | Select-Object -Last 1).Matches.Groups[1].Value
        Add-Content (Join-Path $Guide "build\duels\$Out-hung.txt") "$(Get-Date -Format o) $batch"
        Write-Host "  hung on $batch, stopping the server"
        $server | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
        $since = Get-Date
    }
    Select-String -Path $log -Pattern 'required tests|BUILD|OutOfMemory' | ForEach-Object { Write-Host "  $($_.Line)" }
}
