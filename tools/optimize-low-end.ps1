# Low-End Android TV System Optimization Script
# Enforces background process limits, AppOps restrictions, animation scaling, and AOT compilation.
# Usage: powershell -File .\tools\optimize-low-end.ps1 -TvHost <TV_IP>

param(
    [string]$TvHost = $env:TV_HOST
)

if (-not $TvHost) {
    Write-Error "Please specify -TvHost <IP> or set TV_HOST environment variable."
    exit 1
}

$env:TV_HOST = $TvHost
$py = "$PSScriptRoot\venv\Scripts\python.exe"
if (-not (Test-Path $py)) {
    $py = "python"
}

Write-Host "Applying low-end TV optimizations to $TvHost..." -ForegroundColor Cyan

# 1. Background process limit (1 cached process)
Write-Host "Enforcing background process limit = 1..."
& $py -I "$PSScriptRoot\tv.py" "settings put global standard_background_process_limit 1" | Out-Null

# 2. Window animation scales for responsive navigation
Write-Host "Setting snappy UI animation scales (0.5x)..."
& $py -I "$PSScriptRoot\tv.py" "settings put global window_animation_scale 0.5; settings put global transition_animation_scale 0.5; settings put global animator_duration_scale 0.5" | Out-Null

# 3. Restrict Katniss hotword polling and Sling background execution
Write-Host "Restricting background polling for Katniss and Sling..."
& $py -I "$PSScriptRoot\tv.py" "cmd appops set com.google.android.katniss RUN_IN_BACKGROUND ignore; cmd appops set com.google.android.katniss RUN_ANY_IN_BACKGROUND ignore; cmd appops set com.sling RUN_IN_BACKGROUND ignore" | Out-Null

# 4. AOT native compile launcher and heavy streaming apps if installed
$packages = @("com.gathin.tvhome", "com.sling", "org.smarttube.stable", "com.google.android.youtube.tv", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom")
foreach ($pkg in $packages) {
    Write-Host "Checking $pkg for ahead-of-time (AOT) speed compilation..."
    $installed = & $py -I "$PSScriptRoot\tv.py" "pm list packages $pkg"
    if ($installed -match $pkg) {
        Write-Host "Compiling $pkg to native ARM machine code..." -ForegroundColor Yellow
        & $py -I "$PSScriptRoot\tv.py" "cmd package compile -m speed -f $pkg" | Out-Null
    }
}

Write-Host "Low-end TV optimizations applied successfully!" -ForegroundColor Green
