# TCL C6KS, G10 (Google TV 12): app buttons go through launcherx, and a log reader brings them back with the volume ramp

One unit, one session on 2026-11-01: TCL C6KS ("Smart TV Pro"), device `G10`, product `G10_4K_GB`, Android 12
(SDK 31), build `STT2.230203.001` (AS14), software `V8-R75PT01-LF1V500`, Bluetooth remote `BT_RC813A_B8`,
debloated with [`examples/tcl-g10-google-tv`](../../examples/tcl-g10-google-tv/debloat.txt).

## The symptom

With launcherx disabled, the remote's Netflix, YouTube, Prime Video and TCL Channel buttons and the gear
do nothing, from Home and from inside apps. Input, ☰ and Home keep working. The launcher's key helpers
bring the buttons back, but with any key-filtering helper bound, a held volume key steps once per press,
the trade measured on a G09 in [`tcl-55p71b-g09-key-repeat.md`](tcl-55p71b-g09-key-repeat.md). The owner
confirmed it on the real remote here too, with `HomeKeyService` alone.

## The buttons

Read from `logcat` (`WindowManager: handleKeyBeforeQueueing ... ACTION_DOWN`) while the owner pressed each
button on the real remote:

| Button | keyCode | scanCode | With launcherx disabled |
|---|---|---|---|
| Netflix | 4062 | 242 | dead |
| YouTube | 4065 | 245 | dead |
| Prime Video | 4099 | 752 | dead |
| TCL Channel | 4020 | 227 | dead |
| WeTV | 4142 | 747 | works (opens WeTV) |
| Vidio | 4157 | 743 | dead (Vidio was disabled) |
| ☰ | 4514 | 139 | works |
| Gear | `KEYCODE_NOTIFICATION` (83) | 233 | dead |

The ☰ button is 4514 here, the code a 55Q6CS remote in the G10 list sends for Input, so check the label on
the button before mapping a code by another unit's table.

## Where the presses go

Injected with `input keyevent 4065` over Settings, launcherx disabled:

```text
I TclPhoneWindowManagerEx: mTclShortCutKeyManager.handleShortCutKey
D WindowManager: handleKeyBeforeDispatching keyCode=4065 disable
```

and nothing opens. The only other trace is a broadcast to TCL's analytics package, which fires on every key
(`dumpsys activity broadcasts history`):

```text
Intent { act=tcl.sys.intent.action.USERSTATE_KEYDOWN flg=0x11000010 pkg=com.tcl.ttvs (has extras) }
  extras: Bundle[{keycode=4020, type=IR}]
```

It is addressed to `com.tcl.ttvs`, so no other app can receive it.

With launcherx enabled again, the same injected 4065 over Settings opens YouTube, started from launcherx's
uid. Re-enabling `com.tcl.partnercustomizer` with launcherx still disabled did not. The strings in
`/system/framework/tcl-service.jar` (world-readable; `TclShortCutKeyManager` lives there) name
`com.google.global_button.ACTION_LAUNCH_APP`,
`com.google.android.apps.tv.launcherx.globalbutton.GlobalButtonLaunch_Receiver` and launcherx's
`DeviceModeContentProvider`. That suggests TCL hands the app buttons to launcherx's global-button receiver.
It is read from the strings, not from decompiled code. `dumpsys window policy` lists only `KEYCODE_GUIDE`,
`KEYCODE_PROFILE_SWITCH` and `KEYCODE_TCL_USB_MENU` as global keys for launcherx, so these buttons are not
ordinary global keys.

## What did not work

- **Keep launcherx for the buttons and make the new launcher Home.** The buttons work and held volume
  ramps (`key_filter` false, `stock_home_redirect` set to launcherx), but an enabled launcherx takes Home
  whatever `set-home-activity` says, and the redirect shows Google TV's home for a moment on every Home
  press. The owner rejected that.
- **Disable only launcherx's Home screen.** `pm disable-user` and `pm disable` on the component are refused:
  `SecurityException: Shell cannot change component state for com.google.android.apps.tv.launcherx/...HomeActivity`.
- **A foreground service that starts at boot.** With `APP_AUTO_START` allowed and written, TclAppBoot still
  refuses it:
  `[TclAppBoot]:forbid startForeground for ServiceRecord{... KeyService}` with `reason = 'default_borbid'`.
  The service record survives, but not as a foreground service.

## What worked: a non-filtering service that reads the key log

`WindowManager` logs every key-down at debug level on this build, real and injected, with launcherx
disabled:

```text
D WindowManager: handleKeyBeforeQueueing event=KeyEvent { action=ACTION_DOWN, keyCode=4065, scanCode=245, ... repeatCount=0, ... }
```

A small sideloaded app reads that log and opens the mapped app. Nothing is filtered, so held volume ramps.
The owner checked every button and the held volume on the real remote, and it survived a reboot. The app
is not in this repository. The parts that matter, for anyone building one:

- **An accessibility service that asks for no key filtering.** No `flagRequestFilterKeyEvents`; `dumpsys
  accessibility` shows it bound with `capabilities=0`. The system binds it again after boot, which
  TclAppBoot allowed where the foreground service was refused. It needs no window events
  (`typeAnnouncement` only).
- **`READ_LOGS`**, granted over ADB: `pm grant <package> android.permission.READ_LOGS`. Without it `logcat`
  shows an app only its own lines. Android 12 asked for no consent here.
- **The reader:** `logcat -v brief -T 1 -s WindowManager:D`, matching
  `handleKeyBeforeQueueing.*action=ACTION_DOWN, keyCode=([A-Z_0-9]+),.*repeatCount=0,`. Vendor keys print
  as numbers and known ones as names (`KEYCODE_NOTIFICATION` for the gear). `-T 1` starts at the newest
  line, so a restarted reader does not replay old presses.
- **Opening apps from the background:** `appops set <package> SYSTEM_ALERT_WINDOW allow`, as
  [`addons/wake-app`](../../addons/wake-app/build-and-install.sh) does.
- **`APP_AUTO_START`** allowed and saved with `appops write-settings`, as for the launcher's helpers.
- **Enable the service a few seconds after `pm install -r`.** Written straight after the install,
  `enabled_accessibility_services` came back empty: Android dropped the not-yet-registered service.

Because nothing is consumed, a mapped button still does its own job too. That costs nothing for the dead
buttons above. Mapping a working one (☰, Input) would do both.

The same idea, reading the window manager's key lines with `READ_LOGS`, was noted for a BeyondTV set in
[`tcl-beyondtv-rt2851.md`](tcl-beyondtv-rt2851.md). This is the first unit where it was built and used.

## Limits

One unit, one remote, one day. The launch adds a little delay, unmeasured but not noticed by the owner. The
key lines are debug output of TCL's window manager, so a firmware update could drop them. If the buttons
stop after an update, look for `handleKeyBeforeQueueing` in `logcat -s WindowManager:D` first.
