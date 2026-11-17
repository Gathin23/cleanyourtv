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
$Root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$Project = $Project.TrimEnd('\', '/') -replace '\\', '/'
$Image = if ($env:IMAGE) { $env:IMAGE } else { 'cleanyourtv-android-build' }
$Volume = if ($env:GRADLE_VOLUME) { $env:GRADLE_VOLUME } else { 'cleanyourtv-gradle' }
# Where the debug signing key lives inside the container; see tools/docker-build.md.
$KeyDir = if ($env:KEY_DIR) { $env:KEY_DIR } else { '/gradle/.android' }

if (-not (Test-Path -LiteralPath (Join-Path $Root "$Project/settings.gradle")) -and
    -not (Test-Path -LiteralPath (Join-Path $Root "$Project/settings.gradle.kts"))) {
    throw "$Project is not a Gradle project in this repository (try launcher or addons/<name>)."
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'docker is not installed or not on PATH.' }

$keyArgs = @()
if ($env:HOST_KEY_DIR) {
    # As in the shell twin: docker bind-mounts an absolute path that is there, and nothing else - a relative one
    # it reads as a volume name, and a missing one it creates empty - so /key would hold no key and the build
    # would make a new one. See "The signing key" in tools/docker-build.md.
    if (-not [System.IO.Path]::IsPathRooted($env:HOST_KEY_DIR)) {
        throw "HOST_KEY_DIR=$($env:HOST_KEY_DIR) is not an absolute path: Docker reads a relative one as a volume name, and the build would make a new debug key. Pass the folder your native build uses, usually the .android folder in your home directory ('~/.android' in quotes is not expanded)."
    }
    if (-not (Test-Path -LiteralPath $env:HOST_KEY_DIR -PathType Container)) {
        throw "HOST_KEY_DIR=$($env:HOST_KEY_DIR) is not a folder on this computer: Docker would not mount your native key there, the build would make a new debug key, and an install over the app already on the TV would fail with INSTALL_FAILED_UPDATE_INCOMPATIBLE."
    }
    $keyArgs = @('-v', "$($env:HOST_KEY_DIR):/key")
    $KeyDir = '/key'
}

# Windows PowerShell 5.1 turns a native command's stderr into an error record, and 'Stop' ends the script on
# it: on a first run the image does not exist yet, so this probe writes "No such image" to stderr - the case
# the line below is there to handle - and the build is never reached. Ask docker with 'Continue' and judge
# the exit code.
# Always x86-64, as in the shell twin: the Linux AAPT2 the Android Gradle plugin downloads is x86-64 only, so an
# arm64 image (Windows on ARM) stops at :app:mergeReleaseResources (#2941). On an x86-64 computer it changes nothing.
$Platform = 'linux/amd64'
$ErrorActionPreference = 'Continue'
docker image inspect $Image *> $null
$Missing = $LASTEXITCODE -ne 0
$ErrorActionPreference = 'Stop'
if ($Missing) {
    Write-Host "Building the $Image image once (JDK 17, Android SDK 34, build-tools 34.0.0; a few minutes)..."
    docker build --platform $Platform -t $Image (Join-Path $Root 'tools/docker')
    if ($LASTEXITCODE -ne 0) { throw 'docker build failed' }
}

# The whole repository is mounted: an add-on's gradlew may call ../../launcher/gradlew.
docker run --rm --platform $Platform @keyArgs -v "${Root}:/repo" -v "${Volume}:/gradle" -w "/repo/$Project" `
    -e "ANDROID_USER_HOME=$KeyDir" $Image sh ./gradlew --no-daemon --console=plain @Tasks
if ($LASTEXITCODE -ne 0) { throw 'gradle failed in the container' }

Get-ChildItem -LiteralPath (Join-Path $Root "$Project/app/build/outputs/apk") -Recurse -Filter *.apk -ErrorAction SilentlyContinue |
    ForEach-Object { Write-Host "APK: $($_.FullName.Substring($Root.Length + 1)) ($($_.Length) B)" }
