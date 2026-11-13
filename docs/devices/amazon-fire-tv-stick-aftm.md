# Amazon Fire TV Stick AFTM (montoya): device notes

Hardware and software information collected from one Amazon Fire TV Stick.

## Device identity

| Property | Observed value |
|---|---|
| Manufacturer | Amazon |
| Model | AFTM |
| Product | montoya |
| Device | montoya |
| Board | capri |
| Fire OS | 5.2.9.5 (686800020) |
| Android version | 5.1.1 |
| Android API | 22 |
| Build display ID | LVY48F |
| Build fingerprint | `Amazon/montoya:5.1.1/LVY48F/54.6.8.6_user_686800020:user/release-keys` |
| Build date | 22 March 2023 |

Match the reported model and firmware rather than assuming that other Amazon
Fire TV Sticks with similar retail names use the same packages or behavior.

## Scope and safety

These are device-inspection notes, not a tested debloat list.

The device was inspected using ADB over Wi-Fi. The following information was
collected:

- System package inventory.
- Third-party package inventory.
- Android and Fire OS build properties.
- Paths for selected system applications.

No packages were disabled or uninstalled. No system partition changes,
root access or factory reset were performed as part of this inspection.

The package inventories were saved locally during inspection. They are not
included in this report.

## Selected package observations

The following system applications and APK paths were observed:

| Component | Package | APK path |
|---|---|---|
| Amazon launcher | `com.amazon.tv.launcher` | `/system/priv-app/com.amazon.tv.launcher/com.amazon.tv.launcher.apk` |
| Live TV | `com.amazon.tv.livetv` | `/system/priv-app/com.amazon.tv.livetv/com.amazon.tv.livetv.apk` |

The following additional packages appeared in the third-party package
inventory:

- `com.amazon.firetv.youtube`
- `com.amazon.ftv.glorialist`
- `com.esaba.downloader`
- `com.netflix.ninja`
- `org.smarttube.stable`

Their presence is an inventory observation, not a recommendation to disable
or remove them.

## Platform limitation

The device runs Android 5.1.1. Its shell does not provide the `cmd` command.
An attempt to use `cmd package` therefore returned:

`/system/bin/sh: cmd: not found`

This is an Android platform-version limitation, not evidence that ADB is
disconnected or that the package manager is unavailable.

## Remaining checks

No package-disable testing, reboot-persistence testing, playback regression
testing, remote-control regression testing or firmware-update testing is
claimed here.

A future debloat contribution would require package-by-package inspection,
careful testing, documented protected packages and recovery instructions.
This report intentionally does not provide an untested debloat list.
