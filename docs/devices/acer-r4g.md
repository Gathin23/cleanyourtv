# Acer R4G 50" (MOKA `R4_GTV`, Google TV 14): the firmware turns packages back on at every boot

Owner report from #1106 by [@madhusoodhans](https://github.com/madhusoodhans) (diagnosis also in #1135), India. Four days of use without a reboot, then a power cut. This is a note,
not a list: on this firmware a disable-only list can't keep the stock Home away, so there is no
`examples/` folder for it.

| Field | Observed value |
|---|---|
| Retail model | Acer R4G 50" |
| Manufacturer / device | MOKA / `R4_GTV` |
| Board | Realtek `rtd2851m`, 2 GB RAM, 32-bit ARM |
| Android | Google TV 14 |
| Build | `UKR1.260308`, security patch `2026-04-05` |
| Fingerprint | `ACER/ACER_NFF/R4_GTV:14/UKR1.260308/5597:user/release-keys` |

The Panasonic 4KTV-3MUP in #1116 is the same MOKA `R4_GTV` platform on the same `UKR1.260308` build
and shows the same behaviour.

## What happens

The owner disabled 31 packages in small batches, then disabled `com.google.android.apps.tv.launcherx`
and `com.google.android.tungsten.setupwraith` so a new launcher could keep Home. It worked until the
first cold boot. After that Home was black while volume, HDMI and the app keys still worked, and
logcat showed setupwraith's `.RecoveryActivity` being started as HOME every ~130 ms:

```
I/ActivityTaskManager: START u0 {act=android.intent.action.MAIN cat=[android.intent.category.HOME] ... cmp=com.google.android.tungsten.setupwraith/.RecoveryActivity (has extras)}
```

## Why

Acer/Realtek's `com.android.tv.settings` is modified. On every boot its `.system.FallbackHome` runs
`SetPmodServiceJob`, which calls `disableOrEnableApk(1)` and re-enables every package on a hard-coded
list of 60, unless a factory `P_MODE` flag is set (that needs root). setupwraith is on the list. Once
it is back, its `.RecoveryActivity` (HOME priority 1) outranks any third-party launcher (priority 0)
and loops trying to bring back the disabled launcherx. Standby and wake do not run the job; only a
cold boot does.

What the shell cannot do here: `pm hide` needs `MANAGE_USERS`, and disabling only `.RecoveryActivity`
is refused (`Shell cannot change component state`).

## What that means for a debloat

- **Keep `launcherx` and `setupwraith` enabled on this TV.** A new launcher can still take the Home
  button with `HOME_KEY_SERVICE=1` and `stock_home_redirect=com.google.android.apps.tv.launcherx`,
  which is how the Panasonic in #1116 runs it (that issue tracks a cold-boot fix for the redirect).
- These 20 are on the firmware's list, so disabling them only lasts until the next cold boot:

  ```
  com.google.android.tungsten.setupwraith
  com.google.android.katniss
  com.google.android.apps.tv.dreamx
  com.google.android.videos
  com.google.android.play.games
  com.google.android.feedback
  com.apps.ota
  com.apps.authota
  com.realsil.ota
  com.google.android.tv.dfuservice
  com.apps.biz.mt
  com.mk.tv.meeting.service
  com.apps.function.audiolink
  com.mk.tv.timeclock
  com.apps.tvmanager
  com.apps.esticker
  com.apps.esticker3
  com.apps.function.gdpr
  com.apps.scast
  com.creative.fastscreen.tv
  ```

- These 11 stayed disabled across cold boots (`launcherx` among them, which is what made Home black
  once setupwraith came back):

  ```
  android.autoinstalls.config.scbc.device
  com.alphainventor.filemanager
  com.android.tv.feedbackconsent
  com.apps.mk.wd
  com.apps.wifihotspot
  com.google.android.apps.tv.launcherx
  com.jio.media.stb.ondemand
  com.mk.tv.systemserver
  com.mxtech.videoplayer.television
  com.realtek.leanbacklauncher.partnercustomizer
  com.yupptv.scope.tvapp
  ```

The owner went further than this project's disable-only rule to keep their own launcher as Home.
Those steps are not documented here.

## A second set on the same firmware (`R3_GTV`): the loop can bootloop the TV

An Acer R3G - the `R3G` model of
[`examples/acer-ar32hdigu2841at`](../../examples/acer-ar32hdigu2841at/README.md), `R3_GTV`,
`rtd2841a`, Google TV 14 on `UKR1.260612` (incremental `2624`) - had `launcherx` and `setupwraith`
disabled the same way and did not stop at a black Home. It bootlooped: `system_server` died every
one to two minutes and the TV restarted itself. `logcat -d -b crash` had

```
FATAL EXCEPTION IN SYSTEM PROCESS: android.display
java.lang.IllegalArgumentException: positionChildAt: container=Task=... is not a child of container=Task=...
    at com.android.server.wm.Task.moveToBack(Task.java:4765)
```

and `logcat -d | grep RecoveryActivity` had

```
E SetupWraith|RecoveryActivity: LauncherX HomeActivity not found on device. Skipping enable.
```

about 20 times a second, with dozens of `type=home` tasks under the home root. The `START` storm is
what crashed `system_server` there: `Task.moveToBack` reached a container it had already detached,
and threw `positionChildAt` on the display thread. ADB stayed reachable throughout, so the way out is
still the one below: enable the stock launcher, watch the count stop growing, then reboot.

Tried on this set before the stock launcher went back on, none of which moved Home:

- `cmd package set-home-activity --user 0 <new launcher>` printed `Success` and
  `cmd role get-role-holders android.app.role.HOME` named it, while `dumpsys activity activities`
  still resumed `com.google.android.apps.tv.launcherx/.home.HomeActivity`. launcherx's preferred
  record (`dumpsys package preferred-activities`, `mAlways=true`) outranked the role.
- `pm disable-user --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity` is refused:
  `java.lang.SecurityException: Shell cannot change component state ...`, as above.
- `pm disable-user --user 0 com.google.android.tungsten.setupwraith` reported `disabled-user`, and
  was back on after the next cold boot (the `SetPmodServiceJob` above).

The configuration this project supports on the platform is the one its folder already gives: keep
`launcherx` and `setupwraith` enabled, and take Home with `HOME_KEY_SERVICE=1` and
`stock_home_redirect=com.google.android.apps.tv.launcherx`. One owner accepted losing the wizard
(and the cast credentials that live in it, [TROUBLESHOOTING](../TROUBLESHOOTING.md)) and used
`pm uninstall -k --user 0 com.google.android.tungsten.setupwraith`; that held across one cold boot
where `pm disable-user` did not. It is outside this project's disable-only rule and one boot is not a
list, so it is a report here, not a recommendation - and the saved Wi-Fi network belongs to the
wizard, so the warning in [PROMPT.md](../../PROMPT.md) applies before either step.

## If Home is already black after a reboot

Check that it is this: `tools/tv.py 'logcat -d | grep RecoveryActivity'`. Then re-enable the stock
launcher right away and do not reboot while `.RecoveryActivity` has the screen (a reboot there
factory-reset a Xiaomi MiTV-MOSR4, see [TROUBLESHOOTING](../TROUBLESHOOTING.md)):

```sh
tools/tv.py 'pm enable --user 0 com.google.android.apps.tv.launcherx'
```
