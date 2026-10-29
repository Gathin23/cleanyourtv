# OPPO K9 55 (OPPOCNM632): ColorOS TV package-name Home allowlist

Tested on an OPPO K9 55 (`OPPOCNM632`), Android 9 / ColorOS TV. The display build id was not
captured. Stock Home is `com.heytap.tv.launcher/com.oneplus.tv.launcher2.HomeLauncher`.
The package lists for this unit live in
[`examples/oppo-k9-55-oppocnm632/debloat.txt`](../../examples/oppo-k9-55-oppocnm632/debloat.txt)
and
[`examples/oppo-k9-55-oppocnm632/debloat-extra.txt`](../../examples/oppo-k9-55-oppocnm632/debloat-extra.txt).

The allowlist was first written up for a K9 65 (`OPPOCNM652`, ColorOS TV 2.4) in
[aerial-tv's device note](https://github.com/pengchujin/aerial-tv/blob/master/docs/devices/oppo-coloros-tv.md).
The checks below are this 55" set. How to build a launcher that ColorOS will boot is in the
launcher follow-up and in `docs/TROUBLESHOOTING.md` once that change lands.

## The three allowlisted package ids

ColorOS accepts a HOME install only for three package names:

| Package | Role on this set |
|---|---|
| `com.heytap.tv.launcher` | Stock home. Do not build over it, and do not disable it |
| `com.oneplus.tv.launcher2` | The identity that installed and became Home here |
| `com.oppo.tv.launcher2` | Also allowlisted. Not the identity used on this set |

`pm path com.heytap.tv.launcher` already answers: that name is the system launcher. Do not build
over it. Stop if `pm path com.oneplus.tv.launcher2` (or `com.oppo.tv.launcher2`, if you have to use
that one) already names some other application.

The default `com.gathin.tvhome` build was blocked as a HOME install on this set. The same refusal
is quoted on the K9 65 note as
`INSTALL_FAILED_DUPLICATE_PERMISSION: App installation is banned, because of launcher categories`.

Do not run `launcher/build-and-install.sh` here. It always builds and installs `com.gathin.tvhome`,
then points Home at `com.gathin.tvhome/.MainActivity`. Naming `com.heytap.tv.launcher` as
`STOCK_LAUNCHER` would disable the stock home. Leave that package enabled.

## What this set confirmed

Install reported Success for versionName 3.0 / versionCode 9, signed with the debug keystore.
Home was set to `com.oneplus.tv.launcher2/com.gathin.tvhome.MainActivity`. `com.heytap.tv.launcher`
stayed enabled. The owner confirmed the home screen. Android 9 is API 28, above the launcher's
minSdk 26.

The K9 65 note disabled its stock desktop and still booted. That was a different size, a different
product code and a different launcher. This set did not disable `com.heytap.tv.launcher`, and this
list does not either. Several-day use, a cold boot with the stock home disabled, and OTA
persistence were not part of the check.

## Undo

```bash
PY=tools/venv/bin/python
"$PY" -I tools/tv.py 'cmd package set-home-activity --user 0 com.heytap.tv.launcher/com.oneplus.tv.launcher2.HomeLauncher'
"$PY" -I tools/tv.py 'pm uninstall com.oneplus.tv.launcher2'
```

`pm uninstall` here removes the sideloaded launcher only. The debloat lists disable, and
`tools/restore.sh` puts those packages back. The stock home activity on this firmware is
`com.heytap.tv.launcher/com.oneplus.tv.launcher2.HomeLauncher`.
