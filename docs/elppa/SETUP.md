# Build and try elppA TV

This is the fork's cinematic launcher, not the upstream cleanup procedure. Follow this guide instead of `PROMPT.md`, `build-and-install.sh` or the debloat lists. No packages need to be disabled. [Phase 1 plan and evidence](PHASE-1.md).

## 1. Build locally

Use JDK 17 and Android SDK platform 34 / build-tools 34.0.0. Set `JAVA_HOME` and `ANDROID_HOME`, or configure SDK location in Android Studio. Open `launcher/` as the Android project and select the **elppa** module.

From PowerShell at the repository root:

```powershell
cd launcher
.\gradlew.bat --no-daemon :elppa:testReleaseUnitTest :elppa:lintRelease :elppa:assembleRelease
cd ..
```

On Linux/macOS use `./gradlew` from `launcher/`. Output: `launcher/elppa/build/outputs/apk/release/elppa-release.apk`.

Package: `io.github.sanskarixd.elppa`. Activity class: `com.gathin.tvhome.ElppaHomeActivity`. The package/class prefix difference is intentional because the app shares upstream helpers and resources. The original `:app` remains separately buildable.

This Phase 1 release uses your local Android debug signing key. Keep that key for subsequent updates; builds from different machines will not replace each other without matching signatures. Establish a private release key before publishing. APKs and signing keys are not committed.

## 2. Connect to your Sony X75

Install Google's [SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools) and Python 3. Enable developer options on the TV by selecting its Android build entry repeatedly; then enable the debugging option exposed by the Sony firmware. Put the computer and TV on the same trusted network.

If the TV exposes Network/ADB debugging on port 5555:

```powershell
adb connect <tv-ip>:5555
adb devices
```

Approve the computer's RSA fingerprint on the TV. Do not assume this model has Android 11's Wireless debugging pairing screen. If that screen exists on your firmware, use its displayed pairing and connection ports instead:

```powershell
adb pair <tv-ip>:<pair-port>
adb connect <tv-ip>:<connect-port>
adb devices
```

Use the exact authorised endpoint from `adb devices` wherever `<endpoint>` appears below. An `unauthorized` entry needs approval on the TV; `offline` is not a usable connection. If the firmware exposes no network debugging, stop and inspect its supported connection method. Do not open factory menus.

```powershell
python tools/elppa.py --serial <endpoint> probe
python tools/elppa.py --serial <endpoint> install --apk launcher/elppa/build/outputs/apk/release/elppa-release.apk
```

The install command opens elppA as an app. It does **not** request a new Home assignment. Back should return to the previous screen. Keep the stock launcher enabled.

## 3. Connect content and test the screen

Open **Settings → Content access**. Android TV's listings permission allows the launcher to query rows exposed by its TV provider. Some firmware may require the explicit ADB grant:

```powershell
python tools/elppa.py --serial <endpoint> grant-content
```

Check **Content diagnostics** for permission and row counts. A successful grant does not force Netflix, Prime Video or JioHotstar to share data. Recommendations use accessible preview rows; Continue Watching uses app-published Watch Next rows. Local dismiss/source choices do not modify the apps' history.

For visual testing when no rows are published, turn on **Settings → Preview mode**. This shows labelled fictional titles using bundled NASA photos; it does not imply a streaming subscription or real watch progress. Turn it off to return to app data.

- D-pad left/right moves within each row; up/down moves between controls, apps and media.
- Select opens an app or title. A title link is scoped to its source app; unsupported links fall back to opening that app.
- Hold Select on an app to reorder, pin, hide or open App info. **All apps** includes hidden/sideloaded launchable apps.
- Hold Select on a title to dismiss it locally; Settings can restore dismissed titles.
- **Recommendation sources** controls both recommendations and Watch Next visibility.
- **TV settings**, network and Inputs open platform actions. A missing vendor action displays an unavailable message or the settings fallback.
- The physical voice button stays under the system's control. If firmware passes SEARCH to the launcher, it requests the stock assistant. There is no custom voice recognition or replacement search results UI in this phase.
- Google Home's phone remote depends on the TV's existing Remote Service; elppA doesn't replace it.

## 4. Make it Home only after the app works

```powershell
python tools/elppa.py --serial <endpoint> make-home
```

This writes a private recovery folder under `backup/`, records the current Home, launches elppA, requests Home selection and verifies the resolved activity. No launcher or service is disabled. If the TV refuses Home selection, the tool attempts to restore the saved Home and reports the failure.

Keep the printed `restore.json` path. Immediately verify the **physical** Home and mic buttons, Google Home phone D-pad/typing, Sony picture/sound settings, HDMI/Input, volume, opening an OTT title, standby/wake and reboot. ADB-injected keys cannot prove that these integrations work on your remote.

Restore the original Home:

```powershell
python tools/elppa.py --serial <endpoint> restore --record backup/<record-folder>/restore.json
```

The record is tied to the original ADB endpoint. If the TV's address changes, verify the device and reconnect it using the original endpoint before this command; otherwise select the original Home in the TV's default-app settings. Do not blindly reuse a recovery file for another TV.

After testing, disable wireless/network debugging and revoke the computer's authorisation. Follow the original repo's [closing ADB checks](../../PROMPT.md) if you enabled port 5555; the launcher does not need an ongoing ADB connection.

## What Phase 1 does not promise

- Google's proprietary personalised recommendations or account watch history.
- Every provider's Continue Watching row or precise playback resume position.
- Faster performance on the X75 until measured on that TV.
- Interception of firmware-owned voice keys, app passwords, subscriptions or DRM playback.

The launcher only displays provider-shared artwork/metadata and opens installed apps. No analytics, account login, remote control service or background playback service is added.
