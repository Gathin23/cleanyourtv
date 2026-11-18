# Masjid Times: the next prayer from your mosque's my-masjid.com timetable (the one the My Masjid app uses),
# under the clock on the Clean Your TV home screen.
#
# Usage (from the cleanyourtv folder):
#   powershell -ExecutionPolicy Bypass -File addons\masjid-times\install.ps1 -TvHost 192.168.1.24
# Optional: -City Gravesend -Country "United Kingdom" to search somewhere else, or -Guid <id> to skip the search.
param(
    [string]$TvHost = $env:TV_HOST,
    [string]$City = 'Gravesend',
    [string]$Country = 'United Kingdom',
    [string]$Guid = '',
    [string]$Name = ''
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
Set-Location $PSScriptRoot
$py = '..\..\tools\venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run from the cleanyourtv folder: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$api = 'https://time.my-masjid.com/api'
$configPath = (Resolve-Path 'app\src\main\res\values\config.xml').Path
$config = [IO.File]::ReadAllText($configPath)

function Get-Model([string]$path) {
    $r = Invoke-RestMethod -Uri "$api/$path" -Headers @{ Accept = 'application/json' }
    if ($null -eq $r.model) { throw "my-masjid.com gave no answer for $path" }
    return $r.model
}

# 1. Which mosque: the one already set, the -Guid given, or a pick from the city's list.
if (-not $Guid -and $config -match '<string name="masjid_guid"[^>]*>([^<]+)</string>') { $Guid = $Matches[1].Trim() }
if (-not $Guid) {
    Write-Host "Looking up mosques in $City on my-masjid.com..."
    $countries = @(Get-Model 'Country/GetAllCountries')
    $c = $null
    foreach ($p in @($Country, 'United Kingdom', 'UK', 'England', 'Britain')) {
        $c = $countries | Where-Object { $_.name -like "*$p*" } | Select-Object -First 1
        if ($c) { break }
    }
    if (-not $c) { throw "no country like '$Country' on my-masjid.com" }
    $cities = @(Get-Model "City/GetCitiesByCountryId?CountryId=$($c.id)")
    $ct = $cities | Where-Object { $_.name -like "*$City*" } | Select-Object -First 1
    if (-not $ct) { throw "no city like '$City' in $($c.name) on my-masjid.com" }
    $mosques = @(Get-Model "Masjid/SearchMasjidByLocation?CountryId=$($c.id)&CityId=$($ct.id)")
    if ($mosques.Count -eq 0) { throw "no mosques listed in $($ct.name)" }
    Write-Host ''
    for ($i = 0; $i -lt $mosques.Count; $i++) { Write-Host ('  [{0}] {1}   {2}' -f ($i + 1), $mosques[$i].name, $mosques[$i].address) }
    Write-Host ''
    $pick = [int](Read-Host 'Type the number of your mosque and press Enter')
    if ($pick -lt 1 -or $pick -gt $mosques.Count) { throw 'that number is not on the list' }
    $Guid = $mosques[$pick - 1].guidId
    $Name = $mosques[$pick - 1].name
}

# 2. Check the timetable answers before building anything.
$t = Get-Model "TimingsInfoScreen/GetMasjidTimings?GuidId=$Guid"
if (-not $t.salahTimings) { throw 'my-masjid.com has no timetable for this mosque' }
if (-not $Name) { $Name = $t.masjidDetails.name }
$today = Get-Date
$row = $t.salahTimings | Where-Object { $_.day -eq $today.Day -and $_.month -eq $today.Month } | Select-Object -First 1
Write-Host "Mosque: $Name"
if ($row) {
    Write-Host ("Today:  Fajr {0} ({1})  Zuhr {2} ({3})  Asr {4} ({5})  Maghrib {6} ({7})  Isha {8} ({9})" -f
        $row.fajr, $row.iqamah_Fajr, $row.zuhr, $row.iqamah_Zuhr, $row.asr, $row.iqamah_Asr,
        $row.maghrib, $row.iqamah_Maghrib, $row.isha, $row.iqamah_Isha)
}

$escName = [Security.SecurityElement]::Escape($Name)
$config = $config -replace '(<string name="masjid_guid"[^>]*>)[^<]*(</string>)', ('${1}' + $Guid + '${2}')
$config = $config -replace '(<string name="masjid_name"[^>]*>)[^<]*(</string>)', ('${1}' + $escName.Replace('$', '$$') + '${2}')
[IO.File]::WriteAllText($configPath, $config, (New-Object System.Text.UTF8Encoding $false))

# 3. Build, the same way as the launcher.
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $jbr = Get-ChildItem 'C:\Program Files\Android' -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName 'jbr' } | Where-Object { Test-Path "$_\bin\java.exe" } | Select-Object -First 1
    if (-not $jbr) { throw 'Set JAVA_HOME to a JDK 17+ (Android Studio ships one in its jbr folder)' }
    $env:JAVA_HOME = $jbr
}
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
if (-not (Test-Path local.properties)) { "sdk.dir=$($sdk -replace '\\', '\\')" | Out-File -Encoding ascii local.properties }
Write-Host 'Building (the first time takes a few minutes)...'
.\gradlew.bat assembleRelease -q --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed' }

# 4. Install on the TV.
$apk = 'app\build\outputs\apk\release\app-release.apk'
& $py -I ..\..\tools\b64push.py $apk /data/local/tmp/masjidtimes.apk
if ($LASTEXITCODE -ne 0) { throw 'APK upload failed' }
$out = & $py -I ..\..\tools\tv.py 'pm install -r /data/local/tmp/masjidtimes.apk; rm -f /data/local/tmp/masjidtimes.apk'
Write-Host $out
if ("$out" -notmatch 'Success') { throw 'pm install failed' }

# 5. Tell Home to show it under the clock (a small theme with game_day set; no launcher rebuild).
& powershell -ExecutionPolicy Bypass -File ..\..\tools\theme.ps1 -TvHost $TvHost theme
if ($LASTEXITCODE -ne 0) { throw 'pushing the theme failed' }
Write-Host ''
Write-Host 'Done. Press Home on the remote: the next prayer shows under the clock.'
