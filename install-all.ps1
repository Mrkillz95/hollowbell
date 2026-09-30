# Installs the newest all-in-one giants jar (all five bosses in one jar) into Minecraft.
# It takes out the five single jars, because the all-in-one jar has them inside.
# Run it from anywhere: powershell -ExecutionPolicy Bypass -File C:\Users\jeria\Hollowbell\install-all.ps1
$ErrorActionPreference = 'Stop'
$repo = $PSScriptRoot
$branch = 'claude/amazing-faraday-iutdpo'
$mc = Join-Path $env:APPDATA '.minecraft'
$targets = @((Join-Path $mc 'mods'), (Join-Path $mc 'mountain-server\mods'))

git -C $repo fetch origin $branch | Out-Host
git -C $repo checkout $branch | Out-Host
git -C $repo pull --ff-only origin $branch | Out-Host

$jar = Get-ChildItem (Join-Path $repo 'release') -Filter 'giants-all-*.jar' -ErrorAction SilentlyContinue |
    Sort-Object { [version]($_.BaseName -replace '^giants-all-', '') } | Select-Object -Last 1
if (-not $jar) { Write-Host 'No all-in-one jar in release\ yet.'; exit 1 }

$singles = @('pitchgut-*.jar', 'furrowmaw-*.jar', 'fire-ice-cerberus-*.jar', 'hollowbell-*.jar', 'lanternwillow-*.jar')
foreach ($t in $targets) {
    if (-not (Test-Path $t)) { continue }
    foreach ($f in $singles) { Get-ChildItem $t -Filter $f | Remove-Item -Confirm:$false }
    Get-ChildItem $t -Filter 'giants-all-*.jar' | Where-Object Name -ne $jar.Name | Remove-Item -Confirm:$false
    Copy-Item $jar.FullName $t -Force
    Write-Host "Installed $($jar.Name) in $t"
}
$readme = Join-Path $repo 'release\Giants All-in-one README.md'
if (Test-Path $readme) { Copy-Item $readme (Join-Path $mc 'mods') -Force }
Write-Host 'Done. Open the launcher, pick fabric-loader-0.19.5-1.21.1 and press Play.'
