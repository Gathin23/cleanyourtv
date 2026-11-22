@echo off
title CleanYourTV - Build & Deploy to Mi TV
color 0B
cls
echo =======================================================================
echo            CLEANYOURTV - BUILD & DEPLOY TO MI TV
echo =======================================================================
echo.

set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.21.102-hotspot"
set "ANDROID_HOME=C:\Users\Adnan khan\AppData\Local\Android\Sdk"
set "ADB=C:\Users\Adnan khan\AppData\Local\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe"
set TV_IP=192.168.1.7

echo [1/4] CleanYourTV Launcher build ho raha hai (gradlew assembleRelease)...
cd /d "%~dp0launcher"
call gradlew.bat assembleRelease

if errorlevel 1 (
    color 0C
    echo [ERROR] Build fail ho gaya!
    pause
    exit /b 1
)

echo [OK] Build successful!

echo.
echo [2/4] Backup folder me naya APK copy ho raha hai...
copy /y "%~dp0launcher\app\build\outputs\apk\release\app-release.apk" "%~dp0..\MiTV_Adnan_Custom_Setup\files\tvhome_mr_adnan.apk" >nul

echo.
echo [3/4] TV se connect karke install ho raha hai (%TV_IP%:5555)...
"%ADB%" connect %TV_IP%:5555
"%ADB%" -s %TV_IP%:5555 push "%~dp0launcher\app\build\outputs\apk\release\app-release.apk" /data/local/tmp/tvhome.apk >nul
"%ADB%" -s %TV_IP%:5555 shell pm install -r /data/local/tmp/tvhome.apk
"%ADB%" -s %TV_IP%:5555 shell rm -f /data/local/tmp/tvhome.apk
"%ADB%" -s %TV_IP%:5555 shell settings put secure enabled_accessibility_services "com.gathin.tvhome/.HomeKeyService:com.gathin.tvhome/.InputKeyService"
"%ADB%" -s %TV_IP%:5555 shell settings put secure accessibility_enabled 1
"%ADB%" -s %TV_IP%:5555 shell cmd package compile -m speed -f com.gathin.tvhome >nul 2>&1

echo.
echo [4/4] Launcher restart ho raha hai...
"%ADB%" -s %TV_IP%:5555 shell am force-stop com.gathin.tvhome
"%ADB%" -s %TV_IP%:5555 shell am start -n com.gathin.tvhome/.MainActivity >nul

echo.
echo =======================================================================
echo          NEW BUILD TV PAR SUCCESSFULLY UPDATE HO GAYA!
echo =======================================================================
pause
