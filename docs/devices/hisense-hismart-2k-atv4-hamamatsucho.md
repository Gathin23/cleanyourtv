# Hisense HiSmart 2K ATV4 (hamamatsucho): one-session debloat and Home handover notes

Checked on the owner's real TV in a single ADB session on 2026-10-27, followed by one cold reboot.
This is **not a tested debloat list**: no several-day stability test has been completed. Do not copy
the changes below as a proven package list.

## Identify the exact firmware

| Field | Observed value |
|---|---|
| `ro.product.model` | `HiSmart 2K ATV4` |
| `ro.product.device` | `hamamatsucho` |
| `ro.product.manufacturer` | `Hisense` |
| Android / SDK | Android TV 11 / 30 |
| Firmware (`ro.build.display.id`) | `RTO6.250817.001` |
| RAM | 924 MB |
| ABI | armeabi-v7a only |
| ADB | network, port 5555, `ro.adb.secure=1` |

This set is the Android 11 sibling of the Vu Premium FHD 43-inch (`Smart 2K ATV4`,
`examples/vu-premium-fhd-43-hamamatsucho/`, Android 9 firmware `PTO4.200424.001`) on the same
`hamamatsucho` board. The existing `hisense-hismarttv-a4-android11/` list does not fit this firmware:
10 of its 16 packages are absent here (no `com.hisense.evservice`, `service.message`, `tv.b2b`,
VIDAA channels, HiAlexa or `hitvinput`).

## What was disabled in this session

Stock launcher: `com.google.android.tvlauncher`. Live TV app: `com.mediatek.wwtv.tvcenter`.
Eight packages were disabled for user 0, all kept after one cold reboot (~85 s to boot_completed):

| Package | What it is |
|---|---|
| `tv.anoki.acr.controller` | ACR / viewing-habits tracking; priv-app with a boot receiver |
| `fusion.android.tv.demo` | retail/store demo mode |
| `com.google.android.leanbacklauncher.partnercustomizer` | partner promo rows of the stock launcher |
| `com.android.printspooler` | printing framework; no use on a set |
| `com.mstar.android.tv.disclaimercustomization` | store-disclaimer activity |
| `android.autoinstalls.config.hisense.laoshan` | silent partner-app auto-install configuration |
| `com.cvte.startpartnertv` | CVTE partner showcase app (flagged TEST_ONLY) |
| `com.cvte.startpartnertvvod` | CVTE partner VOD storefront |

The unit arrived with several packages already disabled by its owner before the session:
`com.google.android.marvin.talkback`, `com.cltv.fast`, `com.google.android.tvrecommendations`,
`com.google.android.partnersetup`, `com.google.android.feedback`,
`com.google.android.syncadapters.calendar` and `com.netflix.ninja`. Those are owner history, not a
recommendation. After the session `pm list packages -d --user 0` counts 16 disabled packages: the eight
above, the seven owner packages, and the stock launcher.

## The input-driver guard false-positived on four of them

`tools/disable.sh` held `com.mstar.android.tv.disclaimercustomization`,
`android.autoinstalls.config.hisense.laoshan`, `com.cvte.startpartnertv` and
`com.cvte.startpartnertvvod` back as NOCHK: this firmware's `dumpsys package` output does not
contain a `requested permissions:` block, so the guard cannot answer its own check. The deciding
query run on the set was:

```
pm query-services --components -a android.media.tv.TvInputService
```

which returns only `com.mediatek.tvinput` (Component, Composite, HDMI, SCART, Tuner, VGA) and
`com.mediatek.tv.service` - none of the four candidates serves a TV input.
`disclaimercustomization` does hold `android.permission.TV_INPUT_HARDWARE: granted=true`, but it
implements no input; the permission alone is not a reason to keep it. The four were then disabled
with `TVINPUT_ALLOW=1` after that manual check.

## Home handover

Before the handover, Home was `com.google.android.tvlauncher/.MainActivity`, and an owner-installed
`com.spocky.projengmenu` (Projeng Menu) also claimed HOME, so three packages held the category
alongside `com.android.tv.settings`. `launcher/build-and-install.sh` (built from source,
`INPUT_KEY_SERVICE=1`) succeeded in one run: the new launcher started (`Status: ok`),
`pm disable-user --user 0 com.google.android.tvlauncher` ran, and Home resolved to
`com.gathin.tvhome/.MainActivity`. After the cold reboot Home still resolved there, all eight
disabled packages stayed disabled, all seven `TvInputService` providers were intact, and the
`InputKeyService` was bound (after one clear-and-rewrite of `enabled_accessibility_services`, as
described in `docs/TROUBLESHOOTING.md`).

## Validation limits

- One session and one cold reboot. No several-day stability, no antenna scan, no HDMI switching,
  no casting or full physical-remote test after the changes.
- `screencap` over network ADB produced corrupted images on this set; verification was done with
  `dumpsys` and `settings` queries instead of screenshots.
- Turn debugging off and revoke ADB authorisations when maintenance is done.
