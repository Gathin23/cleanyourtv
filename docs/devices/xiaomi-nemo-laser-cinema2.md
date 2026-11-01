# Xiaomi Laser Cinema 2 4K (`nemo`): the launcher installs, Home stays with PatchWall

Report for [#2415](https://github.com/Gathin23/cleanyourtv/issues/2415), one unit, same-day checks plus
a reboot. This is a device note, not a debloat list anyone should copy: the packages below are one
owner's choices on one firmware build. The debloat half of a run works here; replacing Home does not,
and the reason is in the firmware rather than in the launcher.

## Device

| Field | Observed value |
|---|---|
| Retail model | Xiaomi Laser Cinema 2 4K / `XMJGYY01FM` (China market, Formovie) |
| `ro.product.manufacturer` / `ro.product.device` | `Formovie` / `nemo` |
| Mi Home model | `fengmi.projector.l206j` |
| `ro.board.platform` (as reported) | `m7642` |
| Android / SDK | 9 / 28 |
| Firmware | `2.1.3.4134` |
| Build fingerprint | `Formovie/nemo/nemo:9/PPR2.180905.006.A1/2.1.3.4134:user/release-keys` |
| Stock Home | `com.mitv.tvhome.pub/com.mitv.tvhome.MainActivityUserMode` (PatchWall) |
| Launcher tested | versionName 3.1, versionCode 10, from `095b71ad93d99ea28d94d97b06385709d354b795`, built locally with `-PlauncherApplicationId=com.ts.launcher` |

## The firmware drops non-system HOME candidates

The launcher installs, opens and runs. It declares `MAIN` / `HOME` / `DEFAULT`, `pm list packages`
shows it installed and enabled, and its application flags read `[ HAS_CODE ALLOW_CLEAR_USER_DATA ]`
with no `SYSTEM` bit. The package manager then refuses to treat it as a Home app:

```text
$ adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
2 activities found:
  Activity #0:
    priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
    com.mitv.tvhome.pub/com.mitv.tvhome.MainActivityUserMode
  Activity #1:
    priority=-1000 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
    com.android.tv.settings/.system.FallbackHome

$ adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME -n com.ts.launcher/com.gathin.tvhome.MainActivity
No activity found

$ adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -n com.ts.launcher/com.gathin.tvhome.MainActivity
priority=0 preferredOrder=0 match=0x0 specificIndex=-1 isDefault=false
com.ts.launcher/com.gathin.tvhome.MainActivity

$ adb shell getprop ro.formovie.launcher_type
Patchwall
```

Reading this set's `services.jar` (dexdump and JADX) found the equivalent of the following in
`PackageManagerService.applyPostResolutionFilter()`, with branches and null checks that do not matter
here left out:

```text
static final boolean LAUNCHER_IS_ANDROIDTV =
    SystemProperties.get("ro.formovie.launcher_type", "AndroidTV")
        .equals("AndroidTV");

if (intent.hasCategory("android.intent.category.HOME")
        && !LAUNCHER_IS_ANDROIDTV
        && !info.activityInfo.applicationInfo.isSystemApp()) {
    resolveInfos.remove(i);
}
```

`framework.jar` confirms `isSystemApp()` tests `ApplicationInfo.FLAG_SYSTEM`. This particular HOME branch
consults neither a package-name allowlist nor a Settings key, and the flag is read once when the class
loads; on this unit the property is `Patchwall`, and even a shell `setprop` to the same value is refused.
The `cannot be home on user 0` answer from `cmd package set-home-activity` comes later, in
`setHomeActivity()`, once the requested component has already been dropped from the HOME candidates.

`services.jar` SHA-256, for identifying this exact firmware artifact:
`70e9cf977f2e9f7608ad0a646953ae1d13825981b9b5241a8bc1bc7db4c3d292`.

What that means for a run on this set:

- Keep `com.mitv.tvhome.pub` enabled. With PatchWall out of the candidate set only
  `com.android.tv.settings/.system.FallbackHome` is left, and the Home guard in `tools/disable.sh` names
  Home's owner for exactly this reason.
- This is **not** the package-name allowlist recorded for MiTV-AETP0 in
  [`xiaomi-mitv-aetp0-dofus.md`](xiaomi-mitv-aetp0-dofus.md) and in
  [the troubleshooting page](../TROUBLESHOOTING.md#working-on-the-tv): a filter on `FLAG_SYSTEM` is not a
  filter on a package name, so the alternate application ID used above (`com.ts.launcher`, the identity
  that allowlist accepted) is not a HOME candidate on this build either. The same output without
  `-c android.intent.category.HOME` is what makes the difference visible.
- Nothing here is a launcher fault: the APK declares HOME and the request is refused before the choice
  reaches the launcher.

## What was tried locally, and why it is not a regression report

The launcher configuration was customized for this set, and the owner later modified `HomeKeyService` in
a device-local build (`stockHomeStays()` allowed on Android 9, and the explicit intent used while stock
HOME stayed active omitted `CATEGORY_HOME`). That avoided the failed HOME launch, but the redirect still
did not reliably reactivate when PatchWall came back to the foreground, and the accessibility helper is
disabled again.

These helper experiments are not an upstream regression and are not a fix for this firmware: the central
finding above stands on the package-manager queries and on the firmware's own code alone. No root, no
framework modification and no system-app installation was used, and there is no verified workaround to
recommend from these tests.

## Debloat on the same unit

Twenty packages were disabled with `pm disable-user --user 0`, never uninstalled, with
`com.mitv.tvhome.pub` left enabled. The disabled flags persisted after a reboot;

```text
com.miui.analytics
com.formovie.devicestat
com.mstar.android.media.tests
com.xm.webcontent
com.duokan.videodaily
com.xiaomi.mitv.shoppub
com.xiaomi.smarthome.tv
cn.formovie.fengos.iot
com.xiaomi.mitv.appstore.common
com.xiaomi.mitv.pay
com.xiaomi.mitv.payment
com.xiaomi.mibox.gamecenter
com.xiaomi.gamecenter.sdk.service.mibox
com.xiaomi.tv.common.gallery
cn.formovie.fengos.fdp.browser
cn.formovie.fengos.usermanual
com.formovie.userguide
com.mitv.mivideoplayer
com.android.printspooler
com.fengmi.testsuite
```

Scope of that list: same-day checks plus one reboot, and `com.formovie.devicestat` still ran as a
persistent system process afterwards. It is not a several-day validated list, and only the owner of the
set can say which of these they still want off after living with it.

## Test limits

- The latest `main` was not tested on this set; the build above is from `095b71ad`.
- Settings pages, input/source switching and sideloading were reported as behaving normally; the physical
  remote's buttons were not checked against the launcher.
- No OTA was observed, so whether an update changes `ro.formovie.launcher_type` (and with it the HOME
  filter) is unknown.
- The existing Formovie records are other products on other platforms — `MiProjM05` (`eva`, Amlogic,
  [#928](https://github.com/Gathin23/cleanyourtv/pull/928)) and `Theater`/`hanlla` (Android TV 14,
  [#1932](https://github.com/Gathin23/cleanyourtv/pull/1932)) — and none of them validates this
  `nemo`/PatchWall build.
