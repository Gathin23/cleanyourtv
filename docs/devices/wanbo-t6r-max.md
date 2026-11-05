# Wanbo T6R Max projector (MediaTek MT5862)

Android 9 (SDK 28), firmware `mt5862_bga_1g_tr-userdebug 9 PPR2.180905.006.A1 TVOS-04.16.031.01.12`
(userdebug/dev-keys), about 820 MB of RAM, 1920x1080 at 320 dpi. `ro.product.model` and `ro.product.device` are
both `T6R`, `ro.product.manufacturer` is `MediaTek`; nothing in `getprop` says Wanbo.

This is a one-session report, not a several-day list. Nine packages were disabled, the launcher took Home, and
both held after a reboot.

## Network ADB is not on port 5555

With USB debugging and network debugging on, port 5555 refuses the connection. This firmware starts `adbd` on
**port 5877** (`service.adb.tcp.port=5877`), and nothing on the TV says so. A scan of the projector's TCP ports
found four open ones (1790, 5877, 7100, 8121); only 5877 answered the ADB handshake, the others timed out.

```bash
TV_HOST=<tv ip> TV_PORT=5877 tools/tv.py --probe
```

`TV_PORT` works for every tool here (`disable.sh`, `restore.sh`, `theme.sh`, `launcher/build-and-install.sh`), since
they all go through `tools/tv.py`. Once connected, `getprop service.adb.tcp.port` confirms the port.

`ro.adb.secure=0`: there is no "Allow USB debugging?" prompt, and anyone on the LAN who finds the port gets a shell.
Close it the usual way and check the right port: `tools/adb-closed.py <tv ip> 5877`.

## Home

- Stock launcher: `com.zeasn.whale.saas` (Zeasn Whale, a banner carousel and sponsored rows). The only other HOME
  claimant is `com.android.tv.settings`.
- `launcher/build-and-install.sh` does not know this launcher, so install with `STOCK_LAUNCHER=none`, confirm
  `com.gathin.tvhome` is resumed after Home, then disable `com.zeasn.whale.saas` with `tools/disable.sh`; Home and
  ADB came back after a reboot. Pass `com.zeasn.whale.saas` to `tools/restore.sh` when undoing.
- The preinstalled Kinopoisk TV app is `ru.kinopoisk.zeasn.tv`. The phone app `ru.kinopoisk` from an app store has
  no leanback entry, so it does not show up as a tile or in `dock_apps`.

## Packages disabled (one session)

```text
# Zeasn device portal: starts at boot with a background service
com.zeasn.deviceportal.asdprovider.toptechMtk6681_1
# Stock launcher, after the new one was confirmed as Home
com.zeasn.whale.saas
# Retail demo. On the JMGO G9S (MediaTek m7642) it registers TV_INPUT_BUTTON receivers; the remote's Input
# key was not checked with it off here
fusion.android.tv.demo
# MStar media test program and the factory test app (disabled, never opened)
com.mstar.android.media.tests
com.zoc.factorytest
# Web-URL screensaver, Google partner-row customizer (no Google launcher here), stock browser, sound recorder
com.android.dreams.web
com.google.android.leanbacklauncher.partnercustomizer
com.android.browser
com.android.soundrecorder
```

## Kept on purpose

- Firmware updates: `com.zoc.ota` (Baidu OTA SDK).
- Casting: `com.ecloud.eairplay`, `com.ecloud.emedia` (DLNA), `com.ecloud.eshare.server`.
- Preinstalled apps the owner uses: `ru.rt.video.app.tv` (Wink), `ru.kinopoisk.zeasn.tv`,
  `zeasn.open.technical.tv.store`, `com.jrm.localmm` (USB media player).
- Input and remote: `com.mediatek.wwtv.tvcenter`, `com.mediatek.tvinput`, `com.mediatek.hotkey.dispatcher`,
  `com.zoc.btmisc` (Bluetooth remote key service).

## Wi-Fi name in the launcher

Location is off on this firmware, and Android 9 only gives an app the Wi-Fi network name while it is on, so the
network pill says "Wi-Fi". `settings put secure location_mode 3` brings the name back (there is no network
location provider on the set, so nothing is located); `location_mode 0` undoes it.
