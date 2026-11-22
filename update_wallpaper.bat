@echo off
title Update Mi TV Wallpaper
color 0E
cls
echo =======================================================================
echo                 UPDATE MI TV WALLPAPER / THEME
echo =======================================================================
echo.
set "ADB=C:\Users\Adnan khan\AppData\Local\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe"
set TV_IP=192.168.1.7

set "IMG=%~1"
if "%IMG%"=="" (
    set "IMG=%~dp0..\MiTV_Adnan_Custom_Setup\files\wallpaper.jpg"
)

if not exist "%IMG%" (
    echo [ERROR] Image file nahi mili: %IMG%
    echo Nayi image ko is script ke upar Drag and Drop karein!
    pause
    exit /b 1
)

echo Selected image: %IMG%
echo TV se connect ho raha hai (%TV_IP%:5555)...
"%ADB%" connect %TV_IP%:5555
"%ADB%" -s %TV_IP%:5555 push "%IMG%" /sdcard/Android/data/com.gathin.tvhome/files/wallpaper.jpg
"%ADB%" -s %TV_IP%:5555 shell am force-stop com.gathin.tvhome
"%ADB%" -s %TV_IP%:5555 shell am start -n com.gathin.tvhome/.MainActivity >nul

copy /y "%IMG%" "%~dp0..\MiTV_Adnan_Custom_Setup\files\wallpaper.jpg" >nul

echo.
echo [OK] Wallpaper successfully TV par update ho gaya!
pause
