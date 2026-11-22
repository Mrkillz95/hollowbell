# Giants: one-step setup for testers.
# Installs Fabric for Minecraft 1.21.1, Fabric API and the newest Giants all-in-one jar, and adds a "Giants" profile
# to the Minecraft Launcher. Other mods in the mods folder are left alone (old Giants jars and old Fabric API are swapped).
# Run in PowerShell:
#   irm https://raw.githubusercontent.com/Mrkillz95/hollowbell/claude/amazing-faraday-iutdpo/install-giants.ps1 | iex
param([string]$MinecraftDir = (Join-Path $env:APPDATA '.minecraft'))
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$mcVersion = '1.21.1'
$loader = '0.19.5'
$id = "fabric-loader-$loader-$mcVersion"
$ua = @{ 'User-Agent' = 'giants-installer' }

Write-Host "== Giants setup for Minecraft $mcVersion ==" -ForegroundColor Yellow
if (Get-Process -Name javaw -ErrorAction SilentlyContinue) {
    Write-Host 'Minecraft looks like it is running. Close it first, then run this again.' -ForegroundColor Red; return
}
if (-not (Test-Path $MinecraftDir)) {
    Write-Host "No Minecraft folder at $MinecraftDir. Install the Minecraft Launcher, open it once, then run this again." -ForegroundColor Red; return
}

# 1. Fabric loader (the same files the official Fabric installer writes; no Java needed)
Write-Host '1/4 Fabric loader...'
$verDir = Join-Path $MinecraftDir "versions\$id"
New-Item -ItemType Directory -Force $verDir | Out-Null
Invoke-WebRequest -UseBasicParsing -Headers $ua "https://meta.fabricmc.net/v2/versions/loader/$mcVersion/$loader/profile/json" -OutFile (Join-Path $verDir "$id.json")
$emptyJar = Join-Path $verDir "$id.jar"
if (-not (Test-Path $emptyJar)) { [IO.File]::WriteAllBytes($emptyJar, [byte[]]@()) }

# 2. A "Giants" profile in the launcher (normal launcher and the Microsoft Store one)
Write-Host '2/4 Launcher profile...'
$now = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
$profileFiles = @('launcher_profiles.json', 'launcher_profiles_microsoft_store.json') |
    ForEach-Object { Join-Path $MinecraftDir $_ } | Where-Object { Test-Path $_ }
if (-not $profileFiles) { $profileFiles = @(Join-Path $MinecraftDir 'launcher_profiles.json'); '{"profiles":{}}' | Set-Content $profileFiles[0] -Encoding ASCII }
foreach ($pf in $profileFiles) {
    $json = Get-Content $pf -Raw | ConvertFrom-Json
    if (-not $json.profiles) { $json | Add-Member -NotePropertyName profiles -NotePropertyValue ([pscustomobject]@{}) -Force }
    $entry = [pscustomobject]@{ name = 'Giants'; type = 'custom'; lastVersionId = $id; icon = 'Furnace'; created = $now; lastUsed = $now }
    $json.profiles | Add-Member -NotePropertyName 'giants' -NotePropertyValue $entry -Force
    Copy-Item $pf "$pf.bak" -Force
    [IO.File]::WriteAllText($pf, ($json | ConvertTo-Json -Depth 20), (New-Object Text.UTF8Encoding($false)))
}

# 3. Fabric API (newest for 1.21.1, from Modrinth)
Write-Host '3/4 Fabric API...'
$mods = Join-Path $MinecraftDir 'mods'
New-Item -ItemType Directory -Force $mods | Out-Null
$q = [uri]::EscapeDataString("[`"$mcVersion`"]"); $l = [uri]::EscapeDataString('["fabric"]')
$fapi = (Invoke-RestMethod -Headers $ua "https://api.modrinth.com/v2/project/fabric-api/version?game_versions=$q&loaders=$l")[0]
$file = $fapi.files | Where-Object primary | Select-Object -First 1
if (-not $file) { $file = $fapi.files[0] }
Get-ChildItem $mods -Filter 'fabric-api-*.jar' -ErrorAction SilentlyContinue | Remove-Item -Force
Invoke-WebRequest -UseBasicParsing -Headers $ua $file.url -OutFile (Join-Path $mods $file.filename)

# 4. The newest Giants all-in-one jar (from GitHub releases)
Write-Host '4/4 Giants...'
$all = Invoke-RestMethod -Headers $ua 'https://api.github.com/repos/Mrkillz95/hollowbell/releases?per_page=100'
$rel = $all | ForEach-Object { $_ } | Where-Object { $_.tag_name -like 'all-in-one-*' } |
    Sort-Object { [version]($_.tag_name -replace '^all-in-one-', '') } | Select-Object -Last 1
$asset = $rel.assets | Where-Object { $_.name -like 'giants-all-*.jar' } | Select-Object -First 1
$old = @('giants-all-*.jar', 'pitchgut-*.jar', 'furrowmaw-*.jar', 'fire-ice-cerberus-*.jar', 'hollowbell-*.jar', 'lanternwillow-*.jar',
    'wreckback-*.jar', 'swarmforge-*.jar', 'gloamwick-*.jar', 'hearthstride-*.jar', 'tickmoor-*.jar')
foreach ($f in $old) { Get-ChildItem $mods -Filter $f -ErrorAction SilentlyContinue | Remove-Item -Force }
Write-Host "   downloading $($asset.name) ($([math]::Round($asset.size / 1MB)) MB)..."
Invoke-WebRequest -UseBasicParsing -Headers $ua $asset.browser_download_url -OutFile (Join-Path $mods $asset.name)

Write-Host ''
Write-Host "Done: Fabric $loader, $($file.filename) and $($asset.name) are installed." -ForegroundColor Green
Write-Host 'Open the Minecraft Launcher, pick the "Giants" profile (next to the Play button) and press Play.'
Write-Host 'The first start takes a minute while the launcher downloads Minecraft 1.21.1.'
