# JMGO G9S projector (China, `m7642_JmGO_H7A0`, MediaTek m7642)

Android TV 9 (SDK 28), firmware `1.1.52` (`PPR2.180905.006.A1`, userdebug/dev-keys), low-RAM build
(`ro.config.low_ram=true`), 32-bit `armeabi-v7a`, 1920x1080 at 320 dpi, 80 packages installed.
`ro.product.model` is the generic `JmGO`; the board and device string is `m7642_JmGO_H7A0`.

This is a one-session report, not a several-day list. Eight packages were disabled, `tools/check.py`
reported no drift and Home resolved to `com.gathin.tvhome/.MainActivity` before the final ADB-off
reboot. The reboot itself and a few days of use were not observed yet, so the demo package is left
commented out below until the remote's Input key has been checked with it off.

## Home and inputs

- Stock launcher: `com.jmgo.launcher/.home.desktop.activity.JGHomeActivity`.
- HOME also resolves to `com.jmgo.tv/.MainActivity` (the HDMI/AV input app) and
  `com.jmgo.fallbackhome/com.jmgo.settings.FallbackHome` (the firmware's placeholder).
  Because `com.jmgo.tv` answers HOME, the Home guard would count it as a usable fallback. Do **not**
  put `com.jmgo.launcher` in a debloat list: install the replacement first, confirm it owns Home,
  then disable the stock launcher
  (`launcher/build-and-install.ps1 -StockLauncher com.jmgo.launcher`).
- No antenna or tuner. `com.jmgo.tv` only opens HDMI/AV sources; there is no Live TV tuner app to
  point `live_tv_package` at.
- Pass `com.jmgo.launcher` to `tools/restore.sh` / `restore.ps1 -Stock` when undoing.

## Packages disabled (one session)

```text
# Retail demo, but it registers TV_INPUT_BUTTON receivers on this firmware; keep it commented
# until the remote's Input key is tested with it off.
# fusion.android.tv.demo

# MStar media test program, not a user app
com.mstar.android.media.tests

# Google partner-row customizer; no Google launcher on this set, inert
com.google.android.leanbacklauncher.partnercustomizer

# JMGO music backends and local media player (USB/local playback stops with the player off)
com.jmgo.music
com.jmgo.os.music
com.jmgo.player

# JMGO browser and image viewer
com.jmgo.browser
com.jmgo.imagebrowser4k
```

## Kept on purpose

- Casting: `com.jmgo.holacast.x` (HappyCast/AirPlay) and `com.jmgo.hilink`.
- Voice: `com.bajintech.assistant` and `com.jmgo.ai.voice`.
- Phone remote: `com.softwinner.tvremoteserver`.
- App stores: `com.jmgo.appshop` and `com.dangbeimarket`.
- Content: `com.ktcp.video` (Tencent), `com.xiaodianshi.tv.yst` (Bilibili TV), `com.tencent.karaoketv`.
- Optics and remote: `com.jmgo.hippo` (key handling, brightness), `com.jmgo.autokeystone`,
  `com.jmgo.tofautofocus`, `com.jmgo.autocolortemp`, `com.jmgo.a2dpsinkservice` (Bluetooth speaker
  mode), `com.jmgo.remotecontrolota`.
- Firmware updates: `com.jmgo.update`.
- Every `com.mediatek.*` package (TV inputs, the input arbitrator, the hotkey dispatcher) and every
  `com.android.*` system package.

## ADB

- `ro.adb.secure=0`: network ADB on 5555 accepts any host on the LAN without the Allow prompt.
- `adb_enabled=0` alone did not close the port: `service.adb.tcp.port` and `persist.adb.tcp.port`
  were still 5555. Both were set to `-1`, the TV was rebooted, and `tools/adb-closed.py <tv ip>`
  reported the port closed.
- Revoking the stored debugging key needs the TV's own developer-options screen; it cannot be done
  from the shell on this build.
