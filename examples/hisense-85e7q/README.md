# Hisense 85E7Q: ADB, and the Home handover on an MT9655 set

The [debloat.txt](debloat.txt) beside this file is the tested list; the firmware behaviour behind it
is in [`docs/devices/hisense-85e7q.md`](../../docs/devices/hisense-85e7q.md). What is here is only
what you have to do before and after applying it.

## Model

| Item | Value |
|---|---|
| Retail model | Hisense 85E7Q, 85-inch, China |
| Android model / platform | `VIDAA_TV` / MediaTek `MT9655` (board `mt5877`) |
| Android | 14 / API 34 |
| Build | `UQ1A.240105.004.A1` / `07.02.70.202605250010` |
| Security patch | `2024-08-05` |
| RAM | 3.7 GB |

`ro.product.model` is `VIDAA_TV` on this family; the retail name is only in `ro.build.description`
(`E7Q-user ...`), so match on the build id before applying the list.

## ADB

The USB debugging switch alone opens **port 5555**, and `ro.adb.secure=0` means the TV never shows
the "Allow USB debugging?" prompt: any device on the LAN gets a shell. There is no separate
wireless-debugging row to use instead.

When the work is done, turn that switch off and confirm the port closes (`tools/adb-closed.py <tv ip>`).
Leave `ro.adb.secure=0` sets off untrusted networks while debugging is on.

## The Home handover

Three things on this firmware have to be right at once, and only the third is a surprise:

1. **`cmd package set-home-activity` does nothing.** It answers `Success` and writes no
   `home_activity` key to `settings list secure` or `settings list global`. Do not build on it.
2. **Home is resolved by `intent-filter android:priority`.** The stock `com.jamdeo.tv.vod` claims `50`,
   the two JuUI second-screen homes `com.keylab.speech.view.vidaa` and `com.hisense.widget.pro` claim
   `-150`, and a launcher that declares nothing is `0`. The value does not appear in `pm` output
   afterwards, so a launcher that already answers Home still reports `priority=0` here.
3. **The main activity needs `android:directBootAware="true"`.** Without it the resolver skips the
   launcher during a cold boot, because the user is still locked at that point, and falls to
   `com.keylab.speech.view.vidaa/com.ju.secondscreen.HomeActivity` — the JuUI second screen with a
   promo row. Home pressed *after* boot works, so this reads as a timing bug and is not one.

`launcher/build-and-install.sh` takes the stock launcher it replaced in the environment:

```
STOCK_LAUNCHER=com.keylab.speech.view.vidaa TV_HOST=<tv ip> launcher/build-and-install.sh
```

The list also disables `com.jamdeo.tv.vod`, the third Home candidate. That is safe here and only
here because keylab and widget.pro stay enabled: two launchers answer Home at every point. Keep both
if you intend to be able to get back to a Home without a computer — they are the recovery path when a
launcher update goes wrong.

## Validation

Single session on **2026-11-02**, on the model and firmware above.

| Feature | Result |
|---|---|
| Cold boot to the replacement Home | Working, 16 s from the power button |
| Home after boot, and from inside apps | Working |
| Physical remote, held volume | Working |
| Inputs panel, every HDMI row switching | Working |
| Wi-Fi, ADB, APK installs from a side-loaded store | Working |
| Live TV / tuner | Not tested (no antenna on this unit) |
| HDMI-CEC, casting from a phone | Not tested |
