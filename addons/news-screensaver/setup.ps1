# Windows version of setup.sh: make the news screensaver the TV's screensaver, and set its feeds, its seconds per
# headline and how long the TV waits before it starts. Only what you pass is changed: without a feeds file and
# without feeds.txt in this folder the feeds on the TV stay as they are. Install the app first with
# build-and-install.ps1.
#
# Usage: addons\news-screensaver\setup.ps1 -TvHost 192.168.1.50                    # feeds.txt from this folder
#        addons\news-screensaver\setup.ps1 -TvHost 192.168.1.50 D:\my-feeds.txt    # any feeds file
#        addons\news-screensaver\setup.ps1 -TvHost 192.168.1.50 -Seconds 20        # 20 s per headline
#        addons\news-screensaver\setup.ps1 -TvHost 192.168.1.50 -IdleMinutes 10    # start it after 10 idle minutes
#        addons\news-screensaver\setup.ps1 -TvHost 192.168.1.50 -Start             # and show it now
#        addons\news-screensaver\setup.ps1 -TvHost 192.168.1.50 -DefaultFeeds      # back to the built-in feed
#        addons\news-screensaver\setup.ps1 -TvHost 192.168.1.50 -Off               # previous screensaver back
param(
    [Parameter(Position = 0)][string]$Feeds,
    [int]$Seconds = 0,
    [int]$IdleMinutes = 0,
    [switch]$Start,
    [switch]$DefaultFeeds,
    [switch]$Off,
    [string]$TvHost = $env:TV_HOST
)
$ErrorActionPreference = 'Stop'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
if (-not $Feeds -and (Test-Path (Join-Path $PSScriptRoot 'feeds.txt'))) { $Feeds = Join-Path $PSScriptRoot 'feeds.txt' }
if ($Feeds) {
    if (-not (Test-Path $Feeds -PathType Leaf)) { throw "no feeds file $Feeds (copy feeds.example.txt to feeds.txt and edit it)" }
    $Feeds = (Resolve-Path $Feeds).Path
}
Set-Location (Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) 'tools')
$py = 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
$pkg = 'com.gathin.newsdream'
$dream = "$pkg/.NewsDream"
$uri = "content://$pkg.files"
$tmp = '/data/local/tmp/cyt-news'
# The screensaver the TV had before the first run, so -Off can put it back.
$before = '/data/local/tmp/cyt-news-before'
function Tv([string]$cmd) { & $py -I tv.py $cmd; if ($LASTEXITCODE) { throw "tv.py failed: $cmd" } }

if ($IdleMinutes -lt 0 -or $IdleMinutes -gt 1440) { throw '-IdleMinutes takes 1 to 1440' }
if ($Seconds -ne 0 -and ($Seconds -lt 5 -or $Seconds -gt 600)) { throw '-Seconds takes 5 to 600' }
if (-not ((Tv "pm path $pkg; true") -match 'package:')) { throw 'the news screensaver is not on the TV: run addons\news-screensaver\build-and-install.ps1 first' }

if ($Off) {
    Tv "if [ -f $before ]; then { read c; read e; } < $before; if [ `"`$c`" = null ]; then settings delete secure screensaver_components >/dev/null; else settings put secure screensaver_components `"`$c`"; fi; [ `"`$e`" = null ] || settings put secure screensaver_enabled `"`$e`"; rm -f $before; fi; echo screensaver: `$(settings get secure screensaver_components)"
    exit 0
}

if ($DefaultFeeds) {
    Tv "content delete --uri $uri/feeds.txt" | Out-Null
    Write-Host 'feeds: the built-in default'
} elseif ($Feeds) {
    $list = [System.Collections.Generic.List[string]]::new()
    $urls = @{}
    foreach ($line in Get-Content $Feeds -Encoding UTF8) {
        $line = $line.Trim()
        if (-not $line -or $line.StartsWith('#')) { continue }
        # "Name | https://..." or just the address; the last bar splits them.
        $bar = $line.LastIndexOf('|')
        $name = if ($bar -ge 0) { $line.Substring(0, $bar).Trim() } else { '' }
        $url = $line.Substring($bar + 1).Trim()
        if ($url -notmatch '^(?i)https://[^\s/]+\S*$' -or $url.Length -gt 500) { throw "not an https:// feed address: $line" }
        if ($name.Length -gt 40) { throw "name over 40 characters: $name" }
        if ($urls.ContainsKey($url)) { continue }
        $urls[$url] = $true
        $list.Add($(if ($name) { "$name | $url" } else { $url }))
    }
    if ($list.Count -gt 10) { throw "$($list.Count) feeds; the screensaver takes at most 10" }
    if ($list.Count -eq 0) { Write-Warning "no feeds in ${Feeds}: the screensaver will say so" }
    $clean = New-TemporaryFile
    try {
        [System.IO.File]::WriteAllText($clean.FullName, (($list | ForEach-Object { "$_`n" }) -join ''))
        & $py -I b64push.py $clean.FullName "$tmp-feeds.txt" | Out-Null
        if ($LASTEXITCODE) { throw 'sending the feeds failed' }
    } finally { Remove-Item $clean -Force }
    Tv "content write --uri $uri/feeds.txt < $tmp-feeds.txt; rm -f $tmp-feeds.txt" | Out-Null
    Write-Host "feeds: $($list.Count) from $Feeds"
}

if ($Seconds) { Tv "printf '%s' $Seconds > $tmp-seconds.txt; content write --uri $uri/seconds.txt < $tmp-seconds.txt; rm -f $tmp-seconds.txt" | Out-Null }
Tv "[ -f $before ] || { settings get secure screensaver_components; settings get secure screensaver_enabled; } > $before; settings put secure screensaver_components $dream; settings put secure screensaver_enabled 1" | Out-Null
if ($IdleMinutes) { Tv "settings put system screen_off_timeout $($IdleMinutes * 60000)" | Out-Null }
Write-Host 'On the TV now:'
Tv "f=`$(content read --uri $uri/feeds.txt 2>/dev/null); s=`$(content read --uri $uri/seconds.txt 2>/dev/null); echo `"`${f:-(the built-in default feed)}`" | sed 's/^/  feed: /'; echo `"  seconds per headline: `${s:-15}`"; echo `"  screen_off_timeout: `$(( `$(settings get system screen_off_timeout) / 60000 )) min`""
Write-Host 'Screensaver: News. Undo with: addons\news-screensaver\setup.ps1 -Off'
if ($Start) { Tv 'am start -n com.android.systemui/.Somnambulator >/dev/null; sleep 3; dumpsys dreams | grep mCurrentDreamName' }
