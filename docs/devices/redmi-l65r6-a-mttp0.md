# Redmi TV L65R6-A (MiTV-MTTP0, hugo), China, Android 9

From @xxb26553663-star's report in #1063: a same-day session on one set, six packages disabled. Not a
several-day list; full-reboot and OTA persistence are not verified yet. This is **not** the
[`redmi-mitv-mftp0`](../../examples/redmi-mitv-mftp0/debloat.txt) (`alita`) set.

| Field | Observed value |
|---|---|
| Retail model | Redmi TV L65R6-A |
| System model / codename | `MiTV-MTTP0` / `hugo` |
| Android / API | Android 9 / 28, China-market firmware |
| Build | `21.4.9.2585` (incremental `2585`), MiTV OS 2.9.4 |
| Stock Home | `com.mitv.tvhome/.MainActivityUserMode` (PatchWall 3.50.1621), left enabled |

## Disabled

One `pm disable-user --user 0` per package, each read back:

```text
com.miui.tv.analytics
com.miui.analytics
com.xiaomi.statistic
com.xm.webcontent
com.mitv.shoplugin
com.xiaomi.mitv.shop
```

Kept: the stock Home, TV and input services, voice and casting, the app store, the firmware updater and the
streaming apps. `com.mitv.care` was already disabled before the session and is not part of this set.

## Home: the allowlist identity

The ordinary `com.gathin.tvhome` build installed and opened, but `set-home-activity` printed `Success` and Home
still resolved to PatchWall: the same package-name HOME allowlist as the MiTV-AETP0 and MiTV-MTEQ0
([`xiaomi-mitv-aetp0-dofus.md`](xiaomi-mitv-aetp0-dofus.md),
[`xiaomi-mitv-mteq0-baymax.md`](xiaomi-mitv-mteq0-baymax.md)). A build from the same source with
`-PlauncherApplicationId=com.ts.launcher` became the resolved Home, with PatchWall still installed and enabled.

- Check `pm path com.ts.launcher` prints nothing before installing under that identity.
- Install it with `STOCK_LAUNCHER=none` (or by hand as in the MTEQ0 note): the default
  `launcher/build-and-install.sh` disables the stock launcher, which this report did not do.

## Inputs and boot ad

- The Inputs menu listed HDMI 1/2/3 and Composite. HDMI 1 opened Xiaomi's external-source activity, and Home
  came back to the replacement launcher.
- `settings put system boot_ad_switch 0` read back as `0`, and the owner saw no startup ad after turning the TV
  off and on (an owner power cycle, not a verified full reboot). The same key works on other China Xiaomi builds
  ([`xiaomi-l43m7-ea-antl0.md`](xiaomi-l43m7-ea-antl0.md)); undo with `settings delete system boot_ad_switch`.

The owner confirmed the picture, the physical remote, HDMI and normal viewing during staged checks.
