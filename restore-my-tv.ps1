# CleanYourTV Restore Script for SEI Robotics OTH-100 (Hathway Play Box)
# Re-enables all debloated packages, resets private DNS, and puts back the stock launcher.

param(
    [string]$TvHost = "192.168.0.121"
)

$ErrorActionPreference = 'Continue'
Write-Host "Connecting to TV at $TvHost:5555..."
adb connect "$TvHost:5555"

$disabledByCleanYourTV = @(
    "com.seirobotics.printlog",
    "com.google.android.feedback",
    "com.google.android.tv.bugreportsender",
    "com.nes.saleservice",
    "com.nes.datashare",
    "com.nes.skywayclient",
    "android.autoinstalls.config.sei",
    "com.google.android.tvrecommendations",
    "com.google.android.leanbacklauncher.recommendations",
    "com.google.android.katniss",
    "com.google.android.speech.pumpkin",
    "com.google.android.apps.mediashell",
    "com.google.android.backdrop",
    "com.nes.update",
    "com.nes.remoteota.six",
    "com.android.smspush",
    "com.android.wallpapercropper",
    "com.android.printspooler",
    "com.droidlogic.FileBrower",
    "com.droidlogic.videoplayer",
    "com.google.android.onetimeinitializer",
    "com.android.onetimeinitializer",
    "com.google.android.tvtutorials",
    "flar2.homebutton",
    "app.lumoslabs.chillio",
    "com.shubh.anililitv",
    "com.google.android.partnersetup",
    "com.google.android.leanbacklauncher.partnercustomizer",
    "com.google.android.tvsetup.partnercustomizer",
    "com.google.android.tts",
    "com.alphainventor.filemanager",
    "com.google.android.syncadapters.contacts",
    "com.google.android.syncadapters.calendar",
    "app.lumoslabs.chillhub",
    "com.google.android.tvlauncher"
)

Write-Host "Re-enabling packages..."
foreach ($pkg in $disabledByCleanYourTV) {
    adb -s "$TvHost:5555" shell "pm enable --user 0 $pkg"
}

Write-Host "Restoring stock launcher as Home..."
adb -s "$TvHost:5555" shell "cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity"

Write-Host "Resetting Private DNS to opportunistic..."
adb -s "$TvHost:5555" shell "settings put global private_dns_mode opportunistic"

Write-Host "Done! Verification:"
adb -s "$TvHost:5555" shell "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
