# TCL / FFalcon MT9655 China (Android 14): debloat works, launcher and sideloading do not

Checked on a real TV in one session on 2026-10-21, using upstream
`Gathin23/cleanyourtv` at the commit cloned that day (launcher 2.4 built from source).
The debloat list lives in [`examples/tcl-mt9655-cn-android14/`](../../examples/tcl-mt9655-cn-android14/debloat.txt).

## Identify the firmware, not the brand

| Field | Observed value |
|---|---|
| Retail brand | IFFALCON (雷鸟) / TCL, reported in Settings as `TCL Smart TV Pro` |
| `ro.product.manufacturer` | `TCL` |
| `ro.product.model` | `Smart TV Pro` (generic, shared with other TCL sets) |
| `ro.product.name` | `MT9655_4K_CN` |
| `ro.product.device` | `MT9655` |
| `ro.board.platform` / `ro.hardware` | `merak` / `mtktv` |
| Android / SDK | `14` / `34` |
| `ro.build.display.id` | `tcl9655_cn_an64-user 14 UTT2.250416.001 AU01 release-keys` |
| `ro.build.version.incremental` | `AU01` |
| Security patch | `2025-09-05` |
| `ro.product.cpu.abilist` | `arm64-v8a,armeabi-v7a,armeabi` |
| RAM | 3.7 GB (3,704,512 K) |
| `ro.adb.secure` | `1` |

This is the generation after the MT9653 sets already covered by
[`ffalcon-65s585c-pro`](../../examples/ffalcon-65s585c-pro/debloat.txt) and
[`tcl-q10g-pro`](../../examples/tcl-q10g-pro/debloat.txt). The package list is close to those, but
**the ADB access method is completely different**, so the "Device info + UP/DOWN/LEFT/RIGHT"
procedure from those notes does not apply.

## ADB: TLS on a random port, no pairing UI, USB is the only way in

The only switch is `通用设置 → 更多 → ADB`. There is no Developer options screen at all: the AOSP
settings package is not installed (`com.android.settings` is absent, the stock settings app is
`com.tcl.setting` and declares only `MainActivity`, `SceneActivity`, `StartOtherAppActivity` and
`SlicePermissionActivity`), and `android.settings.APPLICATION_DEVELOPMENT_SETTINGS` has no handler.

With the toggle on:

- adbd listens on a **random high port that changes on every toggle** (40769, 35879, 41629 and
  46493 were seen on the same unit in one session);
- the port answers an ADB `CNXN` with `STLS` and never speaks plaintext, so the ADB-over-TLS
  upgrade is mandatory;
- `adb connect <ip>:<port>` fails and the daemon trace shows
  `Post-handshake SSL_peek failed [SSLV3_ALERT_CERTIFICATE_UNKNOWN]`, i.e. the TV does not know the
  host key;
- mDNS advertises `_adb-tls-connect._tcp` (`adb-<serial>-xxxx`) but **never**
  `_adb-tls-pairing._tcp`, so `adb pair` has no pairing service to talk to;
- 5555 is closed, and a full 1–65535 scan finds no second ADB listener.

Switching Wi-Fi brings up the stock Android dialog `要允许通过此网络进行无线调试吗` with the SSID and
BSSID and an "always allow on this network" checkbox. Allowing it does not change the TLS
behaviour, but it does show the wireless-debugging framework is present with the pairing UI removed.

**The working path is USB**, and the port choice matters:

1. dual male USB-A cable, PC side in a **USB 3.0** port, TV side in a **USB 2.0** port;
2. ADB toggle on, then `adb devices`: Windows enumerates `USB\VID_18D1&PID_4EE7` bound to WinUSB
   (`Class_FF / SubClass_42 / Prot_01`, the ADB interface) with a serial equal to the mDNS instance;
3. the TV shows the standard "Allow USB debugging" prompt; accept it (tick "always allow");
4. `adb tcpip 5555`, then `adb connect <ip>:5555` for wireless work.

Caveats: the USB path only exists while the ADB toggle stays on, and `adb tcpip 5555` does not
survive a reboot — plug the cable in and run it again. A cable that is charge-only, or a TV-side
port that is not the USB 2.0 one, makes the TV enumerate nothing at all, which is easy to mistake
for "this firmware has no USB ADB".

## Sideloading is closed, and the check is in the package manager

Every APK is refused over adb, including apps that do not declare HOME:

```
adb install / pm install / cmd package install
  -> INSTALL_FAILED_VERIFICATION_FAILURE: Install not allowed for file:///data/app/vmdl*.tmp
```

Spoofing the installer package (`-i com.tcl.guard`, `com.tcl.appmarket2`,
`com.android.packageinstaller`) makes no difference. The line `PackageManager: Install not allowed`
is logged by `system_server`, so this is not only the `com.android.packageinstaller` verifier
(`verifier: signature not match ...`) that can be worked around by driving the installer UI.

The only channel that installs ordinary APKs is a USB stick through 电视卫士 (`com.tcl.guard`).
An activity launcher installed that way (`de.szalkowski.activitylauncher`, sideloaded from USB)
was used to confirm there is no hidden development screen.

## The launcher cannot be replaced

Building `launcher/` from source works (`./gradlew assembleRelease` with JDK 17 and SDK 34,
394 KB APK), but installing it fails at the verifier:

```
verifier: signature not match for tcl com.gathin.tvhome, will not be installed !
```

Driving the installer UI over adb gets further than `adb install` — `file://` URIs are refused on
Android 14, but a MediaStore content URI does open the "应用未被认证，是否继续安装" dialog:

```
am start -a android.intent.action.VIEW -t application/vnd.android.package-archive \
  -d content://media/external/file/<id> --grant-read-uri-permission \
  -n com.android.packageinstaller/.InstallStart
```

Pressing 继续 then fails with the same signature error. The rule is readable:

```
content query --uri content://com.tcl.providers.config/all | grep InstallConfig
{"enable":"true","strategies":[
  {"name":"safeStrategy","enable":"true","priority":"0"},
  {"name":"blackListStrategy","enable":"true","packages":[],"priority":"1"},
  {"name":"thridStrategy","enable":"false","packages":["com.dangbeimarket","com.shafa.market","com.ant.store.appstore"],"priority":"2"},
  {"name":"launcherStrategy","enable":"true","packages":[],"priority":"3"},
  {"name":"overDueStrategy","enable":"true","packages":[],"priority":"4"},
  {"name":"pmStrategy","enable":"true","priority":"5"}]}
```

`launcherStrategy` is enabled with an **empty allowlist**, so a HOME package is only accepted if it
is signed with `com.tcl.cyberui`'s key. The row is read-only from the shell:

- `content update` on it returns 0 and silently ignores the write (verified by rewriting the
  `Version` row and reading the old value back);
- the `content` CLI splits `--bind` on every `:`, so a JSON value cannot be passed at all;
- `/data/user_de/0/com.tcl.providers.config/databases/` is not readable as `shell`.

So on this generation the honest summary is: **debloat only**. Replacing Home needs a TCL-signed
build, root, or a firmware downgrade.

The stock launcher (`com.tcl.cyberui`) also renders its own home-screen cards and promo banner from
inside the app, so those cannot be removed by disabling packages either — `com.tcl.atomic.servicecenter`
being disabled does not stop the launcher's own feed.

## What the debloat does change

27 packages disabled with `pm disable-user` - the tested set had `com.tcl.t_solo` off as well, 28 in
all, and it is opt-in here because it is the Bluetooth music app - one full reboot afterwards: Home,
the source panel, Settings, the app store, 电视卫士, Media Center and Multi-screen still worked, the
crash buffer was empty, and free RAM went from 1.97 GB to 1.15 GB of 3.7 GB. The boot ad, telemetry
upload, push connection, home recommendation service, shop, OTA and the bundled content entries stay
off across the reboot.
