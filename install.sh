#!/usr/bin/env bash
# Copyright (c) 2026 Ilham
# All Rights Reserved.
# This from project cleanyourtv by Gathin32 custom by Ilham Asyari
# ==============================================================================
# CleanYourTV - Interactive Installer & Setup Wizard
# Works on macOS, Linux, and Windows (Git Bash / WSL)
# No Agentic AI required - run directly from your Terminal / Command Prompt!
# ==============================================================================

set -e

# Terminal Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

echo -e "${CYAN}${BOLD}"
echo "=========================================================="
echo "   📺 Norma Launcher - Android TV Edition by Ilham Asyari"
echo "=========================================================="
echo -e "${NC}"

# 1. Check for ADB
if ! command -v adb &> /dev/null; then
    # Check default macOS / Android SDK locations
    if [ -f "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
        export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
    elif [ -f "$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" ]; then
        export PATH="$LOCALAPPDATA/Android/Sdk/platform-tools:$PATH"
    else
        echo -e "${RED}[ERROR] ADB (Android Debug Bridge) is not installed or not in PATH.${NC}"
        echo -e "Please install platform-tools: https://developer.android.com/tools/releases/platform-tools"
        exit 1
    fi
fi

echo -e "${GREEN}✓ ADB found: $(which adb)${NC}\n"

# 2. Check for connected TV devices
CONNECTED_DEVICES=$(adb devices | grep -E "device$" | awk '{print $1}' || true)

if [ -n "$CONNECTED_DEVICES" ]; then
    echo -e "${YELLOW}Detected currently connected device(s):${NC}"
    echo "$CONNECTED_DEVICES"
    read -p "Use currently connected device? (Y/n): " USE_EXISTING
    USE_EXISTING=${USE_EXISTING:-Y}
    if [[ "$USE_EXISTING" =~ ^[Yy]$ ]]; then
        TARGET_DEVICE=$(echo "$CONNECTED_DEVICES" | head -n 1)
    fi
fi

# If no target device yet, ask for IP address
if [ -z "$TARGET_DEVICE" ]; then
    echo -e "${BOLD}Connect to your Android TV / Google TV over Wi-Fi:${NC}"
    echo "Make sure Developer Options & USB / Network Debugging are enabled on your TV."
    read -p "Enter your TV IP address (e.g. 192.168.1.150): " TV_IP_INPUT

    # Strip port if user typed it
    TV_IP=$(echo "$TV_IP_INPUT" | sed 's/:5555//')

    if [ -z "$TV_IP" ]; then
        echo -e "${RED}[ERROR] No IP address entered.${NC}"
        exit 1
    fi

    echo -e "\n${BLUE}Connecting to $TV_IP:5555...${NC}"
    adb connect "$TV_IP:5555"
    sleep 1

    if ! adb devices | grep -q "$TV_IP:5555.*device"; then
        echo -e "${RED}[ERROR] Could not connect to $TV_IP:5555.${NC}"
        echo "Tips:"
        echo "1. Verify your TV is turned ON and on the same Wi-Fi network."
        echo "2. Go to TV Settings -> Device Preferences -> Developer Options -> Network Debugging -> Turn ON."
        echo "3. Accept the 'Always allow from this computer' prompt on your TV screen if it appears."
        exit 1
    fi
    TARGET_DEVICE="$TV_IP:5555"
fi

echo -e "${GREEN}✓ Connected to TV: $TARGET_DEVICE${NC}\n"

# 3. Interactive Dock Apps Setup (Multi-Selection)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
echo -e "${CYAN}${BOLD}----------------------------------------------------------"
echo "   📺 Setup Bottom Dock Apps (Multi-Selection)"
echo -e "----------------------------------------------------------${NC}"
echo "Default curated apps:"
echo -e "  ${BOLD}[1]${NC} YouTube       (com.google.android.youtube.tv)"
echo -e "  ${BOLD}[2]${NC} Apple TV      (com.apple.atve.androidtv.appletv)"
echo -e "  ${BOLD}[3]${NC} Netflix       (com.netflix.ninja)"
echo -e "  ${BOLD}[4]${NC} HBO Max       (com.wbd.stream)"
echo -e "  ${BOLD}[5]${NC} Prime Video   (com.amazon.amazonvideo.livingroom)"
echo -e "  ${BOLD}[6]${NC} Disney+       (com.disney.disneyplus)"
echo -e "  ${BOLD}[7]${NC} Vidio         (com.vidio.android.tv)"
echo ""
echo "Select which apps you want on the dock (e.g. 1 3 4 5 7 or 1,2,5)"
read -p "Enter choices [Press ENTER for all 7 apps]: " DOCK_CHOICE
DOCK_CHOICE=${DOCK_CHOICE:-"1 2 3 4 5 6 7"}

python3 - "$SCRIPT_DIR" "$DOCK_CHOICE" << 'EOF'
import sys, os, re, json

script_dir = sys.argv[1]
raw_choices = sys.argv[2]

all_apps = [
    ("YouTube", "com.google.android.youtube.tv"),
    ("Apple TV", "com.apple.atve.androidtv.appletv"),
    ("Netflix", "com.netflix.ninja"),
    ("HBO Max", "com.wbd.stream"),
    ("Prime Video", "com.amazon.amazonvideo.livingroom"),
    ("Disney+", "com.disney.disneyplus"),
    ("Vidio", "com.vidio.android.tv")
]

tokens = re.split(r'[\s,]+', raw_choices.strip())
selected_indices = []
for t in tokens:
    if t.isdigit():
        idx = int(t) - 1
        if 0 <= idx < len(all_apps) and idx not in selected_indices:
            selected_indices.append(idx)

if not selected_indices:
    selected_indices = list(range(len(all_apps)))

selected_apps = [all_apps[i] for i in selected_indices]
cols = len(selected_apps)

print(f"\n\033[0;32m✓ Configured {cols} dock apps (columns = {cols}):\033[0m")
for i, (name, pkg) in enumerate(selected_apps, 1):
    print(f"  {i}. {name}")

# Update config.xml
config_xml_path = os.path.join(script_dir, "launcher/app/src/main/res/values/config.xml")
if os.path.exists(config_xml_path):
    with open(config_xml_path, "r", encoding="utf-8") as f:
        content = f.read()

    items_xml = "\n".join([f"        <item>{pkg}</item>" for _, pkg in selected_apps])
    new_dock_block = f"<string-array name=\"dock_apps\">\n{items_xml}\n    </string-array>"
    content = re.sub(r'<string-array name=\"dock_apps\">.*?</string-array>', new_dock_block, content, flags=re.DOTALL)
    content = re.sub(r'<integer name=\"columns\">\d+</integer>', f'<integer name=\"columns\">{cols}</integer>', content)

    with open(config_xml_path, "w", encoding="utf-8") as f:
        f.write(content)

# Write cyt_dock_config.json for instant TV push
tmp_json_path = os.path.join(script_dir, "launcher/app/build/cyt_dock_config.json")
os.makedirs(os.path.dirname(tmp_json_path), exist_ok=True)
config_json_data = {
    "dock_apps": [pkg for _, pkg in selected_apps],
    "columns": cols
}
with open(tmp_json_path, "w", encoding="utf-8") as f:
    json.dump(config_json_data, f, indent=2)
EOF

echo ""

# 4. Choose APK: Pre-built or Build from source
PREBUILT_APK="$SCRIPT_DIR/launcher/app/build/outputs/apk/release/norma-tv-launcher.apk"
if [ ! -f "$PREBUILT_APK" ] && [ -f "$SCRIPT_DIR/norma-tv-launcher.apk" ]; then
    PREBUILT_APK="$SCRIPT_DIR/norma-tv-launcher.apk"
elif [ ! -f "$PREBUILT_APK" ] && [ -f "$SCRIPT_DIR/launcher/app/build/outputs/apk/debug/norma-tv-launcher.apk" ]; then
    PREBUILT_APK="$SCRIPT_DIR/launcher/app/build/outputs/apk/debug/norma-tv-launcher.apk"
elif [ ! -f "$PREBUILT_APK" ] && [ -f "$SCRIPT_DIR/launcher/app/build/outputs/apk/release/app-deblouting-ps5.apk" ]; then
    PREBUILT_APK="$SCRIPT_DIR/launcher/app/build/outputs/apk/release/app-deblouting-ps5.apk"
fi

APK_PATH=""
if [ -f "$PREBUILT_APK" ]; then
    echo -e "${BOLD}How would you like to install?${NC}"
    echo "1) Fast Install: Use existing pre-built APK ($(du -h "$PREBUILT_APK" | cut -f1)) [Default]"
    echo "2) Build from Source: Recompile launcher with Gradle (bakes new dock into APK)"
    read -p "Enter choice [1 or 2]: " BUILD_CHOICE
    BUILD_CHOICE=${BUILD_CHOICE:-1}

    if [ "$BUILD_CHOICE" == "2" ]; then
        echo -e "\n${BLUE}Building APK with Gradle...${NC}"
        cd "$SCRIPT_DIR/launcher"
        JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}" ./gradlew assembleRelease
        cd "$SCRIPT_DIR"
        APK_PATH="$SCRIPT_DIR/launcher/app/build/outputs/apk/release/norma-tv-launcher.apk"
    else
        APK_PATH="$PREBUILT_APK"
    fi
else
    echo -e "${BLUE}No pre-built APK found. Building from source with Gradle...${NC}"
    cd "$SCRIPT_DIR/launcher"
    JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}" ./gradlew assembleRelease
    cd "$SCRIPT_DIR"
    APK_PATH="$SCRIPT_DIR/launcher/app/build/outputs/apk/release/norma-tv-launcher.apk"
fi

# 5. Install onto TV
if [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}[ERROR] APK file not found at $APK_PATH${NC}"
    exit 1
fi

echo -e "\n${BLUE}Installing Norma Launcher onto $TARGET_DEVICE...${NC}"
adb -s "$TARGET_DEVICE" install -r "$APK_PATH"

# Apply dock configuration to TV
CONFIG_JSON_PATH="$SCRIPT_DIR/launcher/app/build/cyt_dock_config.json"
if [ -f "$CONFIG_JSON_PATH" ]; then
    adb -s "$TARGET_DEVICE" push "$CONFIG_JSON_PATH" /data/local/tmp/config.json >/dev/null 2>&1 || true
    adb -s "$TARGET_DEVICE" shell "mkdir -p /sdcard/Android/data/com.norma.tvlauncher/files 2>/dev/null; cp /data/local/tmp/config.json /sdcard/Android/data/com.norma.tvlauncher/files/config.json 2>/dev/null || content write --uri content://com.norma.tvlauncher.theme/config.json < /data/local/tmp/config.json; rm -f /data/local/tmp/config.json" >/dev/null 2>&1 || true
fi

# 5. Grant Permissions, System Lock & Launch
echo -e "${BLUE}Configuring Norma Launcher permissions and defaults...${NC}"
adb -s "$TARGET_DEVICE" shell appops set com.norma.tvlauncher GET_USAGE_STATS allow || true
adb -s "$TARGET_DEVICE" shell settings put system sound_effects_enabled 0 2>/dev/null || true

# Lock as default home launcher
echo -e "${BLUE}Locking Norma Launcher as default home launcher...${NC}"
adb -s "$TARGET_DEVICE" shell cmd package set-home-activity --user 0 com.norma.tvlauncher/.MainActivity 2>/dev/null || true

# Configure default screensaver
echo -e "${BLUE}Configuring Norma screensaver...${NC}"
adb -s "$TARGET_DEVICE" shell settings put secure screensaver_components com.norma.tvlauncher/.ClockDream 2>/dev/null || true
adb -s "$TARGET_DEVICE" shell settings put secure screensaver_enabled 1 2>/dev/null || true

# Enable accessibility service for remote Home Key intercept
echo -e "${BLUE}Configuring remote Home key handler...${NC}"
adb -s "$TARGET_DEVICE" shell settings put secure enabled_accessibility_services com.norma.tvlauncher/.HomeKeyService 2>/dev/null || true
adb -s "$TARGET_DEVICE" shell settings put secure accessibility_enabled 1 2>/dev/null || true

# 6. Screensaver Photos Setup (Optional)
HAS_PHOTOS=$(find "$SCRIPT_DIR/screensaver" -maxdepth 1 -type f \( -iname "*.jpg" -o -iname "*.jpeg" -o -iname "*.png" -o -iname "*.heic" -o -iname "*.webp" \) 2>/dev/null | head -n 1 || true)

if [ -n "$HAS_PHOTOS" ]; then
    echo -e "\n${YELLOW}Detected personal photos in screensaver/ folder.${NC}"
    read -p "Sync screensaver photos to TV (/sdcard/Screensaver/)? (Y/n): " SYNC_SCREENSAVER
    SYNC_SCREENSAVER=${SYNC_SCREENSAVER:-Y}
    if [[ "$SYNC_SCREENSAVER" =~ ^[Yy]$ ]]; then
        echo -e "${BLUE}Optimizing and syncing screensaver photos for TV...${NC}"
        TV_H=$(echo "$TARGET_DEVICE" | cut -d: -f1)
        TV_P=$(echo "$TARGET_DEVICE" | cut -d: -f2)
        TV_HOST="$TV_H" TV_PORT="${TV_P:-5555}" python3 "$SCRIPT_DIR/tools/sync-screensaver.py" || true
    fi
else
    echo -e "\n${BLUE}ℹ Using default screensaver: Islamic Prayer Countdown (toggleable to Clocks in Settings).${NC}"
fi

echo -e "\n${BLUE}Starting Norma Launcher on your TV...${NC}"
adb -s "$TARGET_DEVICE" shell am force-stop com.norma.tvlauncher 2>/dev/null || true
adb -s "$TARGET_DEVICE" shell am start -n com.norma.tvlauncher/.MainActivity

echo -e "\n${GREEN}${BOLD}=========================================================="
echo "   🎉 Installation Complete!"
echo "   Norma Launcher is now running as your default TV launcher."
echo "==========================================================${NC}"
