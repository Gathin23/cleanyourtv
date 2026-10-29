# Build the launcher or an add-on inside Docker, so this computer needs no JDK or Android SDK.
# Usage: tools\docker-build.ps1 [-Project launcher | addons\<name>] [-Tasks <gradle task>, ...]
#   tools\docker-build.ps1
#   tools\docker-build.ps1 -Project addons\weather
#   tools\docker-build.ps1 -Project launcher -Tasks testReleaseUnitTest,lintRelease,assembleRelease
# The APK lands where a native build puts it (<project>\app\build\outputs\apk\release\).
# Install the launcher with: $env:SKIP_BUILD = '1'; launcher\build-and-install.ps1
# Guide, signing key included: tools/docker-build.md
param(
    [string]$Project = 'launcher',
    [string[]]$Tasks = @('assembleRelease')
)
$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Project = $Project.TrimEnd('\', '/') -replace '\\', '/'
$Image = if ($env:IMAGE) { $env:IMAGE } else { 'cleanyourtv-android-build' }
$Volume = if ($env:GRADLE_VOLUME) { $env:GRADLE_VOLUME } else { 'cleanyourtv-gradle' }
# Where the debug signing key lives inside the container; see tools/docker-build.md.
$KeyDir = if ($env:KEY_DIR) { $env:KEY_DIR } else { '/gradle/.android' }

if (-not (Test-Path (Join-Path $Root "$Project/settings.gradle")) -and
    -not (Test-Path (Join-Path $Root "$Project/settings.gradle.kts"))) {
    throw "$Project is not a Gradle project in this repository (try launcher or addons/<name>)."
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'docker is not installed or not on PATH.' }

docker image inspect $Image *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Building the $Image image once (JDK 17, Android SDK 34, build-tools 34.0.0; a few minutes)..."
    docker build -t $Image (Join-Path $Root 'tools/docker')
    if ($LASTEXITCODE -ne 0) { throw 'docker build failed' }
}

$keyArgs = @()
if ($env:HOST_KEY_DIR) {
    $keyArgs = @('-v', "$($env:HOST_KEY_DIR):/key")
    $KeyDir = '/key'
}

# The whole repository is mounted: an add-on's gradlew may call ../../launcher/gradlew.
docker run --rm @keyArgs -v "${Root}:/repo" -v "${Volume}:/gradle" -w "/repo/$Project" `
    -e "ANDROID_USER_HOME=$KeyDir" $Image sh ./gradlew --no-daemon --console=plain @Tasks
if ($LASTEXITCODE -ne 0) { throw 'gradle failed in the container' }

Get-ChildItem (Join-Path $Root "$Project/app/build/outputs/apk") -Recurse -Filter *.apk -ErrorAction SilentlyContinue |
    ForEach-Object { Write-Host "APK: $($_.FullName.Substring($Root.Length + 1)) ($($_.Length) B)" }
