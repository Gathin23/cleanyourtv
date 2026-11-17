# Sony K-55XR35M2 (BRAVIA 3, `BRAVIA CT1`): one session, not a list

One session on 2026-11-09. This is not an `examples/` entry and not a list to apply.
The packages below were disabled with `pm disable-user --user 0`, survived one reboot,
and have not been lived with for days. The physical remote's app, Input and Settings
buttons were not pressed, and a cast from a phone was not tried.

Android calls this set `BRAVIA CT1`. The retail name on the set is K-55XR35M2, sold as
BRAVIA 3. It is not the [BRAVIA 9 II](../../examples/sony-bravia-9-ii/debloat.txt)
(`BRAVIA_AE3`, `UKN4`) and not the [BRAVIA 8](sony-bravia-4k-ae2-eu-remote-keys.md)
(`BRAVIA_AE2`, `UKN4`). Copying either list onto this chassis would aim Live TV at a
package this firmware does not use.

## Identify the firmware

| Field | Observed value |
|---|---|
| Retail model | Sony K-55XR35M2 (BRAVIA 3) |
| Manufacturer | `Sony` |
| Android model | `BRAVIA CT1` |
| Device / product | `BRAVIA_CT1` / `BRAVIA_CT1_BR23_M_A` |
| Chassis | `bravia3_gr6tp` (`vendor.mtk.hbbtv.chassisname`) |
| HbbTV family | `com.sony.LE.G200` |
| SoC | MediaTek MT9690 (`ro.boot.vendor.cf.hardware`, `ro.hardware.vulkan`) |
| CPU ABI | `armeabi-v7a` only |
| Android / API | 14 / 34, `ro.product.first_api_level=34` |
| Build | `UKN5.250921.001`, incremental `614020073`, `user/release-keys` |
| Security patch | 2026-04-05 |
| `ro.adb.secure` | `1` |

The serial number, Android ID, MAC addresses and the network name are left out.

## Stock Home

Stock Home was `com.google.android.apps.tv.launcherx`. `com.google.android.tungsten.setupwraith`
also claimed HOME, at a higher priority than `launcherx`, through its `RecoveryActivity`.
`cmd package set-home-activity` printed `Success` and changed nothing while `launcherx` was
still enabled.

The saved network in use was owned by `android.uid.system`, checked more than once, so
disabling `setupwraith` did not delete it. That is the opposite of the BRAVIA 9 II note,
where a network saved during the first-run wizard belongs to `setupwraith` and Android
deletes it when that package is disabled. Read `dumpsys wifi` for `cname=` on the set in
front of you. This unit's answer is not a reason to skip that check.

Order that stuck: disable `setupwraith` while `launcherx` still owned Home, then disable
`launcherx`, then `set-home-activity`. `resolve-activity` for HOME and the focused app
were both `com.gathin.tvhome/.MainActivity`. After a reboot the screen was Android's
`FallbackHome` for a few seconds, then the new launcher. It did not open Live TV, so
`boot_to_live_tv` was left unset. No key helper was turned on.

`launcher/build-and-install.sh` is what should do this handover. On this firmware it does
not, until `launcherx` is no longer enabled.

## Live TV and inputs

Live TV is `com.mediatek.tv.oneworld.tvcenter`. It is not `com.sony.dtv.tvlin` (BRAVIA 9 II)
and not `com.sony.dtv.tvx` (older Sony lists). HDMI is `com.mediatek.external`. The input
menu is `com.sony.dtv.smartui`. The DVB tuner left enabled was
`com.mediatek.dtv.tvinput.dvbtuner`.

Those packages were identified and kept. Picture, audio and a tuned channel were not
checked in this session.

## What was disabled

Twenty-three packages, all with `pm disable-user --user 0`, none uninstalled. After the
reboot the disabled set was the same 27 packages as before it: these 23, plus four that
were already disabled when the session started
(`com.mediatek.dtv.tvinput.isdbtuner`, `com.mediatek.dtv.tvinput.atsctuner`,
`com.mediatek.opapp`, `com.sony.dtv.b2b.vendorprotocol`). The firmware did not turn any
of them back on. Nothing here was re-enabled and retested, because nothing had been
lived with yet.

Telemetry and promos:

- `tv.samba.ssm`, `tv.samba.ssm.ui`, `com.sony.dtv.sambacontrol`
- `com.google.android.feedback`, `com.android.tv.feedbackconsent`, `com.android.adservices.api`
- `com.sony.dtv.promos`, `com.sony.dtv.sonyselect`, `com.sony.dtv.sonytab`
- `android.autoinstalls.config.google.gtvpai`

Retail demo and help:

- `com.mediatek.tv.retaildemo`, `com.sony.dtv.demosupport`, `com.sony.dtv.multiscreendemo`
- `com.sony.dtv.smarthelp`, `com.sony.dtv.helpguidecontents`

Home, so the new launcher could take it:

- `com.google.android.tungsten.setupwraith`
- `com.google.android.apps.tv.launcherx`

Off because this owner does not use them, not because the chassis requires it:
`com.crunchyroll.crunchyroid`, `com.sonypicturescore`, `com.google.android.play.games`,
`com.sonyliv`, `com.graymatrix.did` (ZEE5), `com.playstation.remoteplay.tv`.

Left enabled on purpose: Netflix, Prime Video, YouTube, YouTube Music, JioHotstar,
Spotify, voice, the Chromecast and AirPlay packages, screen mirroring, and Sony's
firmware updater. Playback of each was not rechecked after the disables.

## Debugging

Wireless debugging listens on its own TLS port (`adb mdns services` lists
`_adb-tls-connect._tcp`). That port changes. `adb tcpip 5555` is what opened the plain
port for the tools. `adb_enabled` was already `0` while port 5555 still answered,
because wireless debugging was on and `service.adb.tcp.port` was `5555`.

Turning wireless debugging off made both that TLS port and 5555 refuse a connection.
The shell dropped as they closed, so the saved authorization was not revoked from here,
and the set was not rebooted after that close. An earlier reboot, with wireless
debugging still on, had left 5555 closed until `adb tcpip 5555` was run again, and the
TLS port came back. This note does not claim either port stays closed across a boot
once debugging has been switched off.

## What this session did not check

The remote's Netflix, Prime Video, YouTube, Input and Settings buttons, held volume,
HDMI picture, the tuner, and a cast from a phone. On the BRAVIA 8 those app buttons and
Settings are delivered only to `launcherx`, so with that package disabled they may do
nothing here too. That was not measured on this set. A key helper was not enabled,
because on other Sony builds it costs the held-volume repeat.
