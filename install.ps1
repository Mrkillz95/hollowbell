# Installs the newest Hollowbell jar from release\ into Minecraft.
# Run it from anywhere: powershell -ExecutionPolicy Bypass -File C:\Users\jeria\Hollowbell\install.ps1
$ErrorActionPreference = 'Stop'
$repo = $PSScriptRoot
$mc = Join-Path $env:APPDATA '.minecraft'
$targets = @((Join-Path $mc 'mods'), (Join-Path $mc 'mountain-server\mods'))

# Releases live on this branch, not main.
git -C $repo fetch origin claude/amazing-faraday-iutdpo | Out-Host
git -C $repo checkout claude/amazing-faraday-iutdpo | Out-Host
git -C $repo pull --ff-only origin claude/amazing-faraday-iutdpo | Out-Host

$jar = Get-ChildItem (Join-Path $repo 'release') -Filter 'hollowbell-*.jar' -ErrorAction SilentlyContinue |
    Sort-Object { [version]($_.BaseName -replace '^hollowbell-', '') } | Select-Object -Last 1
if (-not $jar) { Write-Host 'No Hollowbell jar in release\ yet.'; exit 1 }
# If the all-in-one jar (all five giants in one) is in use, leave it: it already has this mod inside.
foreach ($t in $targets) {
    if ((Test-Path $t) -and (Get-ChildItem $t -Filter 'giants-all-*.jar')) {
        Write-Host 'You use the all-in-one giants jar, so nothing to do here. Run install-all.ps1 in the Hollowbell folder instead.'; exit 0
    }
}

foreach ($t in $targets) {
    if (-not (Test-Path $t)) { continue }
    # Only one Hollowbell jar can be in a mods folder at a time.
    Get-ChildItem $t -Filter 'hollowbell-*.jar' | Where-Object Name -ne $jar.Name | Remove-Item -Confirm:$false
    Copy-Item $jar.FullName $t -Force
    Write-Host "Installed $($jar.Name) in $t"
}
$readme = Join-Path $repo 'release\Hollowbell README.md'
if (Test-Path $readme) { Copy-Item $readme (Join-Path $mc 'mods') -Force }
Write-Host 'Done. Open the launcher, pick fabric-loader-0.19.5-1.21.1 and press Play.'
