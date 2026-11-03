# XGIMI H3 projector (XK03E, ironman): GMUI on Android 8.0

XGIMI H3 projector, model `XK03E`, device `ironman`, product `Product_ys` (the unit reports the model
string "XGIMI TV"). Runs GMUI, XGIMI's own TV UI — **not** Android TV — on Android 8.0.0 / SDK 26, a
low-RAM build (`ro.config.low_ram=true`), firmware `OPR5.170623.014` / `v1.15.93`, `user` `dev-keys`.
`debloat.txt` in this folder was written and lived with for several days on one real unit; its header
carries the dated `Status:` line.

GMUI is the reason most of this folder exists: it is not Android TV, so the usual settings intents do
not resolve, the built-in cast receiver pulls down an app on first use, and the phone app's remote
control rides on two packages that look like bloat but are not.

## Connect

```bash
export TV_HOST=<projector ip>
```

The firmware ships ADB closed. Two ways in, neither root: open the System info page and long-press
**LEFT** on the remote to reach the factory menu (ADB is on its second page), or keep
`local.z7x.adb` enabled (it is in this list's "kept on purpose" set — it is the enabler this unit was
opened with, and it survives a factory reset as the known-good way back in). ADB is TCP on port 5555.

> Quirk: on this firmware `adb reboot` **powers the unit off**, it does not restart it. Wake it with the
> remote afterwards. The unit also sleeps and drops ADB after a few idle minutes, so run the checks in a
> batch rather than spread out.

## Debloat

```bash
TV_HOST=<projector ip> tools/disable.sh examples/xgimi-h3/debloat.txt
```

(Windows: `tools\disable.ps1 examples\xgimi-h3\debloat.txt`.) Every package is **disabled with
`pm disable-user`**, never uninstalled; `tools/restore.sh` undoes it. The header of `debloat.txt` says
what each group is and what is kept on purpose. The two packages the XGIMI phone app needs
(`com.xgimi.mobilebridgeservice`, `com.xgimi.vcontrol`) are deliberately **not** in the list — see
[Phone remote](#phone-remote) below.

Applied and checked on one unit: launcher, GMUI settings service, the Wi-Fi page, the remote keys
(including the Settings key, keyCode 82) and the cast receiver all still worked, no drop-box crashes.
Reboot-checked across a full power cycle: the disabled packages stay disabled, Home is still the
default HOME, and the cast receiver comes back on its own (~20 s).

## Launcher

```bash
TV_HOST=<projector ip> launcher/build-and-install.sh
```

(Windows: `launcher\build-and-install.ps1 -TvHost <projector ip>`.) The stock launcher
`com.xgimi.home` is disabled by the handover and Home resolves to `com.gathin.tvhome`. If the Home
button or the remote's Settings key needs the accessibility helper on this firmware, install with
`HOME_KEY_SERVICE=1` (the launcher's `HomeKeyService` handles the Settings key, keyCode 82, and the
reboot re-bind described below).

```bash
HOME_KEY_SERVICE=1 TV_HOST=<projector ip> launcher/build-and-install.sh
```

On this firmware the accessibility-helper list came back after a reboot with only the vendor's voice
helper on it, which dropped the remote's Home/Input/app buttons until the helper re-bound. A launcher
fix keeps a wanted helper enabled across a reboot by remembering the uptime it was last unbound
(rather than dropping the record), so a clean reboot is told apart from a deliberate disable — worth
having if you run `HomeKeyService` here.

## Config (GMUI settings service)

GMUI's settings is a **service, not an activity**: the standard `android.settings.SETTINGS` /
`WIFI_SETTINGS` actions resolve to nothing, and the vendor entry only answers its own action
`XGimiSettingAction` on `com.android.newsettings/com.xgimi.newsettings.SettingService`, with the page
to open carried as a string extra named `data` (`main` for the front page,
`Settings://com.xgimi.settings.net/wifi` for Wi-Fi). The launcher's **settings-service** feature (a
separate change, which also adds this folder's `config.json`) reads these keys from the theme overlay
so the header's Settings button, the quick panel's TV-settings row and the Wi-Fi pill start the
service directly instead of landing on a debug entry point the user would have to click through:

```json
{
  "open_on_start_choice": true,
  "settings_service": "com.android.newsettings/com.xgimi.newsettings.SettingService",
  "settings_service_action": "XGimiSettingAction",
  "settings_service_extra": "data",
  "settings_page": "main",
  "settings_page_wifi": "Settings://com.xgimi.settings.net/wifi"
}
```

Push it once the launcher settings-service feature is in:

```bash
TV_HOST=<projector ip> tools/theme.sh examples/xgimi-h3
```

Leave the `settings_*` keys empty on any other firmware and the launcher falls back to the usual
settings intent. `open_on_start_choice` turns on the launcher's "Open on start" row: pick the cast
receiver (LeanCast) there once and it opens after each boot. The receiver's own boot-autostart
foreground service brings it back even if the launcher is not the one to open it.

## Cast

The built-in cast receiver (`com.xgimi.wirelessscreen`, DLNA/SSDP on :1718, shown as "我的H3") quietly
auto-downloads 云视听小电视 (`com.gitvjimi.video`) on first use, so it and the related cast packages
(`com.xgimi.miracast`, `com.xgimi.streammeadiaplayer`) are disabled. The receiver used instead is
**LeanCast** (`io.leancast`, MIT, a pure DLNA/UPnP MediaRenderer) — it boot-autostarts a foreground
service and is device-idle-whitelisted. HPPlay 乐播投屏 (`com.hpplay.happyplay.aw`) is left installed
but idle: only its `.app.MainActivity` fully initializes the SDK, and its `DaemonService` dies within
a minute on GMUI, so it is not a reliable receiver here. Miracast itself is impossible without root
(`wifi_display_on=0`), so phones use the cast button inside video apps (DLNA), which works.

## Phone remote

The XGIMI phone app's remote control rides on two packages that look like telemetry but are the bridge
and the command runner:

- `com.xgimi.mobilebridgeservice` — the phone ↔ projector bridge the app connects through.
- `com.xgimi.vcontrol` — executes the commands the app sends (virtual control). It also carries a
  silent-app-download receiver and a Richinfo push SDK; that is the price of keeping phone remote.

Disabling either makes the app report **"network inconsistent / cannot connect"** even when Wi-Fi is
fine, because the app's connection never reaches the projector. This list keeps both enabled on
purpose. Re-disable them only if you never use the phone app. (`com.xgimi.controllerconnection` and
`com.xgimi.remote` are unrelated — the former is gamepad/sound-controller pairing, the latter the
physical-remote daemon — and are also kept.)

## What stays on

Hardware, input, voice and power services are kept (keystone, ToF autofocus, Bluetooth remote, HDMI
input player, the XGIMI system/UI/window services, SystemUI, the shutdown service). `com.xgimi.screensaver`
is kept too: it owns `SCREEN_ON`/`SLEEP_ON`/`STR_ON`, and the ambient screensaver (`com.xgimi.atmosphere`)
is disabled instead so it cannot seize the foreground and kill third-party launchers. File manager, app
database and factory menus are kept (the factory menus are the documented ADB-entry path). See the
header comments in `debloat.txt` for the full per-package reasoning.

## Undo

```bash
TV_HOST=<projector ip> tools/restore.sh examples/xgimi-h3/debloat.txt
TV_HOST=<projector ip> tools/theme.sh --reset
```

To give the Home button back to the stock launcher, re-enable it
(`pm enable --user 0 com.xgimi.home`) or `pm uninstall com.gathin.tvhome`; either returns Home to
GMUI's launcher. Nothing here is uninstalled, so a full undo is the two commands above plus
re-enabling the stock launcher.

## Tested on this unit

- Launcher is Home; D-pad navigation, the dock and the top bar work with the real remote.
- GMUI settings service reachable from the launcher's Settings button, quick panel and Wi-Fi pill.
- Cast receiver (LeanCast) present and auto-starting; built-in cast disabled.
- Phone remote from the XGIMI mobile app works (with the two bridge packages kept enabled).
- Disabled packages survive a full power cycle; no crashes in the drop box.
