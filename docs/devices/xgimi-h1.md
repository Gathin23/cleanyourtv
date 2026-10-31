# XGIMI H1 (MStar `guava`, GMUI V1.5.3, Android 5.1.1): `pm disable-user` refused, `pm hide` works

This is an owner report from one projector, **not a debloat list for the repo tools**. The adb shell cannot
disable packages on this firmware (next section), so there is deliberately no `examples/` entry. What follows is
what plain ADB shows on this unit, so another owner of a GMUI projector can judge for themselves. The firmware
is from 2017 and gets no updates.

| Field | Observed value |
|---|---|
| Retail model | XGIMI H1 (About screen: Machine model `H1`, System version `GMUI V1.5.3`) |
| Android model / device | `XGIMI TV` / `guava`, `ro.product.brand=MStar` |
| Fingerprint | `MStar/aosp_guava_en/guava:5.1.1/LMY47V/V1.5.3:userdebug/test-keys` |
| Android / API | 5.1.1 / 22, `armeabi-v7a` |
| RAM / storage | 2 GB (`MemTotal` 1997736 kB) / 10.5 GB `/data` |
| Stock home | `com.xgimi.launcher` (`com.xgimi.mslauncher.LauncherActivity`) |
| ADB | Network ADB on 5555 out of the box, `ro.adb.secure=1` (the "Allow USB debugging?" prompt appears) |

## `pm disable-user` is refused, `pm hide` works

From the adb shell, `pm disable-user <pkg>` and `pm disable <component>` fail for every package:

```text
Error: java.lang.SecurityException: Permission Denial: attempt to change component state from pid=..., uid=2000, package uid=...
```

`pm hide <pkg>` and `pm unhide <pkg>` do work from the same shell (checked on `com.lee.gallery3d`, then put back),
as on the [Whaley WTV55K1](whaley-wtv55k1.md) and the [Skyworth 55G6](skyworth-9r52-g6.md). The repo's
`tools/disable.sh` and `tools/restore.sh` only disable and enable, so a list file for this projector could not be
applied or undone with them. The packages below were not run through `pm hide` one by one; this note maps them, it
does not report a hidden set.

`launcher/build-and-install.sh` cannot help here either: the reference launcher needs Android 8.0 (minSdk 26).
A third-party launcher with minSdk 21, installed with `adb install`, took Home through Android's "Select a Home
app" chooser ("Always"); the stock launcher stayed enabled as a fallback.

## What the packages do

Every XGIMI and MStar app below runs as `android.uid.system`, so its permission list is the whole system uid's and
says nothing about the package itself. What the package declares does:

| Package | What it declares |
|---|---|
| `com.gimi.adservice` | `GimiAdBootBroadcastReciver`, `com.gimi.access.AppInstallBroadcast` and `com.xgimi.tongji.TongjiReceiver` ("tongji" is "statistics") |
| `com.xgimi.vcontrol` | Phone remote: `NettyService`, `WebService`, `DownloadService`, `AppInstalledBroadcastRecevier` |
| `com.xgimi.filefly` | File push: `UcontrolService` and `ApkInstallerService` |
| `com.xgimi.wirelessscreen` | Miracast/AirPin receiver with its own `UpgradeService` |
| `com.waxrain.airplaydmr` | WaxPlay AirPlay/DLNA receiver (in `/data/app`), seven TCP ports |
| `com.android.mcast` | MStar `MMCastDemo` receiver |
| `cm.aptoidetv.pt.xgimi_market` | Aptoide TV store |
| `cn.wps.moffice_i18n_TV` | WPS Office, carries `com.appsflyer.MultipleInstallBroadcastReceiver` |
| `com.android.chrome` 59, `com.google.android.youtube.tv` 1.3.11 | 2017 preloads |
| `com.google.android.gms` 10.2.98, `com.google.android.gsf`, `com.google.android.gsf.login`, `com.android.vending` 6.3.16 | 2017 Google stack; no current apps for API 22 |
| `com.android.exchange`, `com.android.inputmethod.pinyin`, `com.xgimi.doubanfm`, `com.xgimi.xgimimusicparseservice`, `com.lee.gallery3d`, `com.mstar.android.pppoe` | Mail sync, Pinyin keyboard, XGIMI music and gallery, PPPoE dialer |
| `com.xgimi.minitvfactory`, `mstar.factorymenu.ui` | Factory menus (never opened); `minitvfactory` has a boot receiver |
| `com.mstar.tv.service`, `com.mstar.tv.tvplayer.ui`, `com.mstar.android.providers.tv`, `com.mstar.miscsetting`, `com.mstar.tvsetting` | TV middleware, input source, hotkeys: keep |
| `com.xgimi.launcher`, `com.xgimi.appmanager`, `com.xgimi.bootwizard`, `com.xgimi.inputmethod`, `com.xgimi.filemanager`, `com.xgimi.gimiplayer` | Stock home, app list, first-run wizard, keyboard, USB file browser and player |

A system-uid app held a plain-HTTP connection to `47.103.94.254:80` (Alibaba Cloud), read from `/proc/net/tcp`.
Which of the system-uid packages made it was not narrowed down.

## Network listeners

| Port | Owner (uid from `/proc/net`, process from `ps` and the init `.rc` files) |
|---|---|
| TCP 139, 445 | root (`smbd`) |
| TCP 554 | root (`livecap`, an init service) |
| TCP 15735, 15737; UDP 16735, 16737 | root (`ucontrolMDNS`, an init service) |
| TCP 16740, 16741 | system uid (XGIMI phone-remote and file-push apps) |
| TCP 5000, 7000, 7100, 7700, 49153, 55560, 55570 | `com.waxrain.airplaydmr` |

### Samba runs as root from a world-writable folder

`/system/bin/debug.sh` (init service `debug-sh`, no `user`, so root) starts `smbd` and `nmbd` from
`/data/data/com.xgimi.filemanager/samba/` at every boot and restarts them whenever a file named `restart` appears
there. On this unit that folder and the script it runs were world-writable (`ls -l` shows `rwxrwxrwx`), and
`smb.conf` uses `security = SHARE` with a guest account. Disabling or hiding FileManager does not stop it, because
init starts it, not the app. Without root there is no way to switch it off from the projector: keep this
projector on a network of its own, and off the internet if it only needs the LAN.
