# Script de Restauración Total para Haier MatrixTV BE
# Restaura todos los paquetes y el Launcher original de Google TV
$ErrorActionPreference = 'Stop'
$TV_HOST = "192.168.0.20"
$py = "d:\PROYECTOS IA\cleanyourtv\tools\venv\Scripts\python.exe"
$tv = "d:\PROYECTOS IA\cleanyourtv\tools\tv.py"
$env:TV_HOST = $TV_HOST

$packages = @(
    "com.google.android.apps.tv.launcherx",
    "com.google.android.tungsten.setupwraith",
    "com.haierubic.usmartdevice",
    "com.haier.tvhousekeeper",
    "com.haier.emanual",
    "com.haier.oemtabprovider",
    "com.haier.apps",
    "com.mediatek.android.leanbacklauncher.partnercustomizer",
    "fusion.android.tv.demo",
    "com.google.android.feedback",
    "com.android.tv.feedbackconsent",
    "com.google.android.partnersetup",
    "com.google.android.play.games"
)

Write-Host "Rehabilitando paquetes y launcher original en $TV_HOST..." -ForegroundColor Cyan
foreach ($pkg in $packages) {
    Write-Host "Reactivando $pkg..."
    & $py $tv "pm enable $pkg"
}

# Restaurar DNS original
& $py $tv "settings put global private_dns_mode opportunistic"

Write-Host "Restauracion completada. La TV vuelve a su estado original de fabrica." -ForegroundColor Green
