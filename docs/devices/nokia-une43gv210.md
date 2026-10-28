# Nokia UNE43GV210: Plume launcher and package notes

Observed on one Nokia UNE43GV210 in a single session. This is a device report,
not a general debloat list for other Nokia or Vestel televisions.

| Field | Observed value |
|---|---|
| Retail model | Nokia UNE43GV210 |
| Android / SDK | `11` / `30` |
| Android device | `yeoksam` |
| Firmware build | `RTOC.251019.001` |
| Board/platform | Vestel Cosmos170 / MediaTek TV stack |

## Plume configuration

Plume Launcher `1.6` was installed from the [official GitHub release](https://github.com/aradhyas8/Plume_Launcher/releases/tag/v1.6).
The downloaded APK was 469,939 bytes with SHA-256:

```text
919d78c2f8bc125edab935fcbcda445eec5180c4e9a2a0e39ba0d9cd72837c27
```

The package is `dev.plume`. The TV-listings permission
`android.permission.READ_TV_LISTINGS` was granted so Plume can read Watch Next
and channel/listing data. The additional
`com.android.providers.tv.permission.READ_EPG_DATA` permission is protected on
this firmware and cannot be granted to the app; this is an Android restriction,
not a failed installation. Channel playback and tuner reception were not
independently tested.

The stock launcher was initially kept enabled while Plume was tested. Android's
preferred-Home command alone did not change the physical Home result on this
Vestel firmware, so the stock launcher was then disabled reversibly:

```text
pm disable-user --user 0 com.google.android.tvlauncher
```

After that change and a reboot, Home resolved to:

```text
dev.plume/.MainActivity
```

The original launcher remains installed and can be restored with the local
recovery list:

```bash
TV_HOST=<tv ip> tools/restore.sh work/nokia-une43gv210-plume-home.txt
```

`com.google.android.tungsten.setupwraith` was left enabled. Plume's Accessibility
service was not enabled. The dark Plume theme was visible after reboot, with the
clock, weather and app row working. App-button, HDMI/input, standby/wake and
several-day stability checks remain unverified.

AT4K remains installed but is not the Home app.

## NRK Radio compatibility workaround

The reusable app-specific procedure is documented in
[docs/apps/nrk-radio.md](../apps/nrk-radio.md).

Google Play reported that NRK Radio was not compatible with this TV. The app's
official Play listing targets ordinary Android devices rather than Android TV,
but a sideload was tested successfully.

| Field | Observed value |
|---|---|
| Package | `no.nrk.mobil.radio` |
| Installed version | `2026.10.1` / version code `106278` |
| TV architecture | `armeabi-v7a` |
| App minimum SDK | `29` |
| TV SDK | `30` |
| Install result | Installed and launched successfully |

The ARMv7 split package was installed with the base APK plus the
`config.armeabi_v7a` and `config.mdpi` splits. The app opened to its Norwegian
home screen with Home, Explore, Live and Login sections visible. It may not
appear in Plume's TV-optimized app row because it is a phone/tablet app, but it
has a launcher activity:

```text
no.nrk.mobil.radio/.MainActivitySwitch
```

The temporary APK files were removed from the TV after installation. Network
ADB was disabled and port 5555 was verified closed. This workaround may stop
working after an app update, and the app's remote navigation was not fully
tested.
