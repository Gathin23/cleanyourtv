# Skyworth 7T864_H5D (MediaTek MT9638, Coocaa OS on Android 10): debloat, Office Mode, and which home redirects survive a reboot

Owner report for a China-market Skyworth 7T864_H5D running Coocaa OS on Android 10. The debloat package
list for this unit lives in [`examples/skyworth-7t864-h5d/debloat.txt`](../../examples/skyworth-7t864-h5d/debloat.txt).

## Observed firmware

| Field | Observed value |
|---|---|
| Model | `7T864_H5D` |
| Product / device | `mt9638` |
| Manufacturer | `skyworth` |
| Android / API | `10` / `29` |
| Build display id | `QQ2A.200305.004.A1 release-keys` — the same build as the 7T861_A23 report, on a different board |
| Application ABIs | `armeabi-v7a` (32-bit userspace) |
| `ro.adb.secure` | `0` |
| `service.adb.tcp.port` | `5555` |
| Stock home | `com.skyworth.officehomepage` (OfficeMode) / `com.tianci.movieplatform` |
| TV inputs & source manager | `com.skyworth.tv` |
| Installed packages | 69, `pm list packages -d` empty out of the box |

Port 5555 opens over Wi-Fi with no pairing prompt (`ro.adb.secure=0`).

Unlike the 7T861_A23, this set has **no `com.skyworth.device`** (the SensorsData telemetry package) and no
`com.skyworth.skymarket`. A list copied from that unit has two lines that quietly do nothing here.

---

## Safety warning: packages to keep

> [!CAUTION]
> **Do not disable any of these:**
> - **`com.skyworth.officehomepage`** and **`com.tianci.movieplatform`**: the Office Mode and Standard
>   Mode launchers. The Coocaa OS boot flow hard-depends on them; disabling either hangs the set at the
>   boot logo with the network stack down, so ADB is gone too. Recovery is a hardware factory reset.
> - **`com.loostone.tuning`**: microphone tuning and audio HAL bridge (`AudioService` timeouts at boot).
> - **`com.tianci.user`**: user authentication, with Binder dependencies across framework daemons.
> - **`com.skyworth.tv`**: HDMI, AV and tuner input switching.
> - **`com.coocaa.teampresent`**: the Office Mode presentation stack.
> - **`com.cibn.tv`**: CIBN broadcast-control platform. On this set it is where the Youku app lives
>   (`com.youku.tv.home.activity.HomeActivity` is inside it), so it is content, not bloat.

---

## Office Mode first

Switching the television to **办公模式 (Office Mode)** in the native settings suppresses video
recommendations, the banner advertisements and background cloud preloading at the source, and does it
without disabling a single package: `pm list packages -d` stays empty. On this unit it also stops the
clock-and-playlist screensaver that Coocaa otherwise shows when the set is left alone.

The property it writes is visible afterwards:

```
persist.service.model.pkg  = com.skyworth.officehomepage
persist.service.model.cls  = com.skyworth.officehomepage.view.HomepageActivity
persist.service.model.name = OfficeMode
```

---

## Debloat result

7 packages disabled, all verified to survive a cold boot (`pm list packages -d` unchanged after a power
cycle): `com.tianci.ad`, `com.tianci.de`, `com.skyworth.performance.monitors`, `com.skyworth.testtime`,
`com.skyworthdigital.stb.dataprovider`, `com.skyworth.microphone`, `com.coocaa.guide` (the EPG guide, which the
shared list keeps as a comment).

Measured on this unit, from the home screen's own readout:

| | Before | After |
|---|---|---|
| Free RAM | 37 % | 74 % |
| Free storage | 43.55 GB | 43.65 GB |
| Disabled packages | 0 | 7 |

The RAM figure is taken right after a reboot, so it is not a like-for-like comparison with a set that has
been running for hours; the point is that a handful of background daemons are gone rather than that the
number doubles.

---

## Replacing Home: what survives a reboot on this firmware

This is the part that cost the most time, and it contradicts the sequence in the
[7T861_A23 note](skyworth-7t861-a23.md) in one important place.

**`persist.service.model.*` is not persistent here.** It is set at boot by `com.tianci.system`, and on this
firmware it was observed to come back **empty** after a power cycle, with `persist.service.homepage.*`
rewritten to `com.tianci.movieplatform` / `com.coocaa.homepage.vast.HomePageActivity`. A launcher started
through those properties therefore only survives until the next cold boot.

**Android's own default-home setting does survive**, and it is enough to boot into a third-party home:

```bash
cmd package set-home-activity com.coocaa.tvhome/com.gathin.tvhome.MainActivity
```

With `persist.service.model.pkg` empty, Coocaa falls back to the standard HOME resolution, which answers
`com.coocaa.tvhome` after that command and keeps answering it across reboots. Note that before the change
the only activity claiming `android.intent.category.HOME` on this set was
`com.android.packageinstaller/.FallbackHome`: the stock Coocaa homes are started through the properties, not
through an intent filter, which is why the standard mechanism is free to take.

The Home **key** is a separate path, and it follows `persist.service.homepage.*`. Pointing that at a
`coocaa-home-shim`-style package
([`VincentYe123/coocaa-home-shim`](https://github.com/VincentYe123/coocaa-home-shim)) works while the value
holds, but the value is the one described above as rewritten at boot, so expect to re-assert it:

```bash
setprop persist.service.homepage.pkg com.ccos.tv.homepage
setprop persist.service.homepage.cls com.ccos.tv.homepage.LaunchActivity
cmd appops set com.ccos.tv.homepage SYSTEM_ALERT_WINDOW allow
```

A launcher installed on this set must be built with the Coocaa application id
(`-PlauncherApplicationId=com.coocaa.tvhome`): the memory manager force-stops third-party packages shortly
after they leave the screen but spares `com.coocaa.*`, and a plain application id is also refused as Home
by some Coocaa builds.

### Disabling the Coocaa screensaver

`com.coocaa.screensaver` is the ad-bearing screensaver ("正在热播", a poster with a "press OK to watch"
prompt). `pm disable-user --user 0 com.coocaa.screensaver` removes it. Two things to know:

- Its package carries `com.coocaa.screensaver.home.ScreenHomeActivity` as well, so keep it in mind when
  the boot flow is being changed; one boot after disabling it, this unit came up on the stock
  `com.tianci.movieplatform` home with the home properties cleared.
- With it disabled and no daydream configured, the set simply stays on whatever is in front when idle:
  no screensaver at all.

### Daydream on this firmware

`screensaver_components` accepts a third-party DreamService and
[Aerial Views](https://github.com/theothernt/AerialViews) is installed and selected here, but it does not
start on its own: the vendor keeps `dumpsys power` at `mStayOn=true` /
`mStayOnWhilePluggedInSetting=1` regardless of `settings put global stay_on_while_plugged_in 0` and
`svc power stayon false`, so the display never times out and `DreamManagerService` is never asked to start
a dream. Treat a custom screensaver on this set as manual-start only unless that lock is addressed.

---

## Practical notes

- Python `adb-shell` over port 5555 works; `adb push` of a large file to
  `/sdcard/Android/data/<package>/files/` runs at about 9-10 MB/s and is far faster than
  `tools/b64push.py` for anything above a few MB.
- `screencap` returns stripes rather than the video layer while the composer stays on the overlay path
  (`hwcomposer: ... Final enableGLCompositor = 0`). `dumpsys media.player` showing the open file, and
  looking at the set, are the reliable checks there.
- Turning USB debugging back off: the toggle lives in the factory menu (本机信息 → `上上下下左右左右` →
  「工厂单键」开 + 「高级设置」→「adbd」= 1), and it is the same menu that has to be revisited if ADB
  stops answering after a boot.
