# Hisense VIDAA_TV / AMLOGICT963 (China, Android 9): Home handover

This is an owner-observed launcher note, **not a tested debloat list**. The
retail model could not be established from the TV's software; use the device
and build below to decide whether another set is comparable. The CleanYourTV
reference launcher was not installed on this set.

| Field | Observed value |
|---|---|
| Android model / device | `VIDAA_TV` / `AMLOGICT963` |
| Android / API | `9` / `28` |
| Build fingerprint | `Hisense/VIDAA_TV/AT963:9/PPR1.180610.011/07.02.70.02:user/release-keys` |
| TV software version | `V0011.00.01L.P0926` (About screen) |
| Application ABIs | `armeabi-v7a,armeabi` (32-bit only) |
| ADB authorization | `ro.adb.secure=1`; the owner accepted the TV's debugging prompt |

## Home with the stock launcher still enabled

The stock 聚好看 package `com.jamdeo.tv.vod` remained enabled. A third-party
launcher was selected as Home. On 2026-10-24, these read-only checks confirmed
that the stock package was enabled and that Home resolved to the replacement
launcher's activity:

```text
adb shell pm list packages -e com.jamdeo.tv.vod
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

After a reboot, the owner confirmed that the TV arrived at the replacement
launcher and that pressing the **physical remote's Home key** left it there.
The same physical Home check was repeated. The 聚好看 branding still appeared briefly during
startup; that sighting alone does not establish whether the stock app was
opened or the firmware displayed a branded boot image.

This differs from the [Hisense 65D62QH on MT9653 / Android 11](hisense-65d62qh.md),
where leaving `com.jamdeo.tv.vod` enabled reclaimed Home. That set's package
list and launcher procedure should not be copied to this Android 9 build.
The result here establishes a preferred-Home handover with a third-party
launcher; it does not establish that the CleanYourTV launcher, the input panel,
voice, casting or HDMI-CEC work the same way.

No vendor package was evaluated for a debloat list in this session. In
particular, there is no evidence here to disable `com.jamdeo.tv.vod` or to
recommend packages from another Hisense board. This note omits the unit's
serial number, chip ID, network addresses and screenshots of its About page.
