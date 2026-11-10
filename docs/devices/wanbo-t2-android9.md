# Wanbo T2 (Android 9, MediaTek `mt5862`): single-session notes

Observed on one projector in one afternoon, with two restarts, not several days of ordinary use. This is a device
report, **not a validated debloat list**. It is a different board from the
[Android 6 T2 Max](wanbo-t2-max-android6.md) (MStar `bennet`): this one runs Android 9, takes `pm disable-user` and
runs the launcher unchanged.

| Field | Observed value |
|---|---|
| Retail model | Wanbo T2; the projector's own About screen says `T2`, Android reports `SMART Projector` |
| Board | `ro.product.device` `mt5862_bga_1g`, manufacturer `MediaTek`, 4 × Cortex-A53, `armeabi-v7a` only |
| Android / SDK | `9` / `28` |
| Build | `mt5862_bga_1g-userdebug 9 PPR2.180905.006.A1 TVOS-04.16.031.01.12 dev-keys` |
| `ro.build.fingerprint` | `MStar/eng_wwt/eng_wwt:4.4.4/KTU84P/2.0.13:userdebug/test-keys` (not the real build) |
| RAM | 843,520 kB `MemTotal`, about 350 MB available on the stock home screen |
| Display | 1920x1080 at 320 dpi |
| Stock Home | `com.zeasn.whale.open.launcher.technical` (Zeasn "Whale"); `FallbackHome` is the only other HOME |
| Live TV / HDMI | `com.mediatek.wwtv.tvcenter`; one HDMI input, `com.mediatek.tvinput/.hdmi.HDMIInputService/HW5` |

## Connecting

The projector's own settings show an "About Projector" screen with no Build number row. Highlight **Version** and
hold OK for about ten seconds: Developer options slide in from the right. Turn on USB debugging there.

Network ADB then answers on **5555**. `ro.adb.secure` is unset on this userdebug build, so there is no "Allow USB
debugging?" prompt: any device on the LAN gets a shell while the switch is on.

**Do not restart with USB debugging on.** After one restart with it on, the projector came up in standby (it needed
the power button), and the Wi-Fi switch turned itself off again every time it was switched on. Turning USB debugging
off brought Wi-Fi back at once; turning it on again afterwards kept Wi-Fi and ADB working until the next restart. A
second restart with debugging off was normal. The likely cause is a Wi-Fi module on the internal USB bus that loses
its host port while debugging is on at boot (the same pattern as the USB devices in [PROMPT.md](../../PROMPT.md)), but
this was not measured.

`screencap` returns an all-zero frame here (a white PNG), on every screen. `uiautomator dump` still reads the text of
ordinary Android screens, and `dumpsys activity activities | grep mResumedActivity` names what is in front.

## Packages disabled in this session

All with `tools/disable.sh`, in small batches; Home worked after each batch and the set survived both restarts.

| Package | Role on this firmware |
|---|---|
| `com.zeasn.asdprovider.zhiyuan6681` | Zeasn device portal: home-screen content and the firmware update check (`OTA_PULL_CHECK`) |
| `com.zeasn.services.general` | Search, weather and "weekly" for the stock Zeasn home |
| `com.zeasn.mygoogle` | "My Google" shortcut app installed by the Zeasn store |
| `com.google.android.feedback` | Google feedback |
| `com.mstar.android.media.tests` | MStar media test app |
| `com.mediatek.androidbox` | `WFDSinkTest`, a Miracast test receiver |
| `com.memoryclean.chijimlee` | Memory cleaner started at boot |
| `com.chijimlee.cleancacheutil` | Cache cleaner started at boot |

The stock launcher was disabled by `launcher/build-and-install.sh`, not by the list. With the two chijimlee cleaners
disabled, the built-in AirPlay and EShare receivers were running after each restart; before, the AirPlay service was
found stopped. That is one observation, not a measured cause.

Kept, from their components: `com.chijimlee.trapezoid` (keystone and zoom), `com.chijimlee.tyylauncher` (the
remote's global keys, not a Home), `com.mediatek.wwtv.tvcenter`, `com.mediatek.tvinput`,
`com.mediatek.loadingequalizer` (a TV input service that follows the volume), `com.mediatek.autopair`,
`com.mediatek.hotkey.dispatcher`, the `com.ecloud.*` casting receivers, `com.jrm.localmm` (USB player) and the Zeasn
store `zeasn.open.technical.tv.store.zy.mtkwtv`, which is where the installed Netflix updates from.
`fusion.android.tv.demo` (`RetailDisplay`) listens to the Input button, so it was left alone untested. The factory apps
`com.chijimlee.factorymode` and `mediatek.factorymenu.ui` were not opened.

## Launcher

`STOCK_LAUNCHER=com.zeasn.whale.open.launcher.technical launcher/build-and-install.sh` (the launcher built in Docker,
`SKIP_BUILD=1`) installed it, and Home resolved to it after both restarts. On the physical remote Home, Back, Menu,
the volume keys and Mute worked with no key helper enabled. The remote's key layout (`Vendor_3697_Product_0001.kl`)
maps two extra buttons to `MOUSE` (664, the remote's own cursor mode) and `SETTING` (677); the firmware handles both.
The Inputs pill opened the HDMI source with the default `live_tv_package`; a picture on HDMI was not checked.

The installed Netflix (`com.netflix.mediaclient`) and the USB player have only a `LAUNCHER` entry, so they need
`library_apps` (config or theme) to show as tiles.

## Google Play

Google Play Services was `23.03.13` (variant `080306`), and YouTube was still the factory `2.11.08`. Signing in to
Play looped: the web sign-in finished, then `AuthPII` logged `Long live credential not available` and `GetToken`
`BadAuthentication`, and Play asked again or showed "No connection".

## AirPlay

The built-in receiver (`com.ecloud.eairplay`, "Eshare-…") advertises as `AppleTV3,2`, `srcvers=220.68`. An older
iPad mirrored to it. A current iPhone connected and the receiver started its decoder, but the phone sent no video
(about 9 kbit/s on `wlan0`), so the screen stayed blank. A receiver app from Google Play mirrored that iPhone.

## Closing

USB debugging was switched off at the end, and `tools/adb-closed.py <tv ip>` reported 5555 refused.
