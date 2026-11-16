# Fire TV Stick 4K (2018): additional setup notes

Tested on AFTMM (`mantis`), Fire OS 6.7.1.1 (NS6711), Android 7.1.2,
SDK 25. These notes describe another owner's session ending 2026-11-12;
they do not replace the earlier units' longer testing in [debloat.txt](debloat.txt).
No reboot-persistence or multi-day claim is made for this session.

## Conservative cleanup

Only the following subset of the existing list was disabled in small batches.
The owner checked physical Home, directional navigation and video playback
between batches and confirmed normal operation after all 13 were off.
Descriptions follow the existing list, not packet-level measurements.

| Package | Purpose recorded in the list |
|---|---|
| `com.amazon.client.metrics.api` | Client metrics API |
| `com.amazon.metrics.api` | Metrics API |
| `com.amazon.tv.fw.metrics` | Fire TV framework metrics |
| `com.amazon.wirelessmetrics.service` | Wireless metrics |
| `com.amazon.perfcollection` | Performance collection |
| `com.amazon.dp.logger` | Logging |
| `com.amazon.ceviche` | Data collection pipelines |
| `com.amazon.tv.developer.dataservice` | Content personalization data |
| `com.amazon.kso.blackbird` | Advertising synchronization |
| `com.amazon.sneakpeek` | Sponsored previews |
| `com.amazon.hybridadidservice` | Advertising ID service |
| `com.amazon.tv.easyupgrade` | Device upgrade promotions |
| `com.amazon.device.sale.service` | Sale campaigns |

Stock Home, Appstore (`com.amazon.venezia`, `com.amazon.tigris`),
`com.amazon.hedwig`, `com.amazon.franktvinput`, `com.amazon.tifobserver` and
`com.amazon.imp` stayed enabled. Voice, casting, input/remote services,
streaming apps and firmware updates were retained. This is a session report,
not a new several-day-tested list. The entire existing list makes broader
choices, including optional apps and services; do not apply it expecting only
the subset above.

Use the existing disable/restore tools with your chosen list and retain its
backup record. The existing list's Apply and Undo commands are in its header.
To recover one reported package individually:

```sh
TV_HOST=<tv ip> python3 tools/tv.py 'pm enable --user 0 com.amazon.client.metrics.api'
```

Substitute only the package being restored. Wait at least 10 seconds after an
enable/disable before any separately agreed reboot.

## Launcher compatibility and remote checks

This repository's reference launcher requires SDK 26 and cannot run on this
SDK 25 stick. Do not lower its minimum SDK or bypass installation checks.
The owner's [Mantis Home source](https://github.com/huaban28/mantis-home) is
a separate GPL-3.0 FLauncher fork, not a build of this repository's launcher.

On this unit, Launcher Manager Mini V2 targeting Mantis Home, using the
**ActivityManager** trigger with **Launch on Boot** enabled, returned to Mantis
after one physical Home press and after switching the television to another
HDMI source and back. The owner verified both on the real remote. Amazon Home
remained enabled. This is one setup's result, not a guarantee for other firmware
or future updates. Third-party helper setup and permissions are outside this
debloat list; see the separate project's documentation. No helper APK is included.

## ADB connection failures

Some new Python ADB connections closed before authentication, including Windows
socket errors 10053/10054. Toggling ADB debugging off and on on the stick restored
access during this session. One authenticated connection could then perform
multiple operations. Avoid rapid reconnect loops; retain a connection for related
operations where the client supports it.

Successful manual app installation does not establish that ADB transport is
healthy. Screenshot requests were omitted during diagnosis, but screenshots
were not proven to cause the failures. UI hierarchy inspection and physical
remote checks were used instead. No OS update or protection bypass was needed.
Stop if permissions or device compatibility refuse an operation.

## App inventory versus home categories

An Appstore cloud/download listing does not necessarily mean the app is installed.
After the owner installed YouTube and Netflix from Appstore, both were found
and opened from Mantis Home. Settings > Applications is the global installed-app
inventory; its TV tab is not a home category. Long OK > **Move to category**
changes home membership; **Add to category** can leave an app in multiple
categories, while **Hide everywhere** is global. Long OK and Reorder were
verified with the physical remote.
