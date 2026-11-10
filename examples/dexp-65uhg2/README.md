# DEXP 65UHG2

Sold in Russian retail as the **DEXP 65UHG2**; the set itself reports product `HKC_4K_GTV_PA`, device
`lakeside` and the model string "4K PA Google TV", with HKC as the ODM and CVTE packages alongside.
A Google TV 14 set on a MediaTek m7332 board. The list beside this page is
`debloat.txt`; the header there carries the model string, the firmware build and the quirks found on
the set.

## Before the stock launcher goes

On this firmware `cmd package set-home-activity` from the shell is accepted but does not stick while
`com.google.android.apps.tv.launcherx` is still enabled: Home keeps resolving to launcherx. Disabling
it alone is not enough either, because `com.google.android.tungsten.setupwraith/.RecoveryActivity`
answers HOME with priority 1 and takes over. A third-party HOME filter is capped at priority 0, so
neither claimant can be outranked from the shell.

setupwraith also holds saved Wi-Fi networks, and disabling the package deletes the ones it owns. On
this set the connected network belonged to `android.uid.system:1000` while setupwraith owned a saved
network that was not in use, so this order worked and left the connection, and remote ADB, intact:

1. Install the new launcher and start it: `launcher/build-and-install.ps1 -TvHost <tv ip>` on Windows,
   or `launcher/build-and-install.sh` on Linux and macOS.
2. On the TV, forget the Wi-Fi network and join it again, so the network belongs to the system rather
   than to setupwraith.
3. Check before going on: `adb shell cmd wifi list-networks` names the network in use, and
   `adb shell dumpsys wifi | grep -o 'cname=[^ ]*'` must not name setupwraith for it.
4. Apply the list, which disables setupwraith and then launcherx, and set Home again:
   `adb shell cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity`
5. Reboot once and confirm Home and ADB both come back.

`tools/disable.sh` skips the stock launcher while no other launcher answers HOME, and skips setupwraith
while it owns a saved network, so step 2 is what lets the list apply as written.

## What was checked on the set

- Home opens the new launcher, survives a reboot, and the Home key returns to it.
- The Inputs pill lists HDMI 1, HDMI 2, HDMI 3, Composite and the TV channels; the TV app opens.
- The volume key steps one step per press.
- The remote's hot-key service `com.hkc.globalkey` stays bound and running.
- Nothing that was disabled serves a `TvInputService`.

## What was not checked

- A multi-day soak: the list was applied and verified the same day.
- Casting from a phone and the voice assistant after setupwraith is gone. On other Google TV sets here
  the loss of setupwraith costs the Chromecast credentials, and the Assistant's content search wants
  the stock home, so treat both as possible losses.
- The remote's own buttons, which need a press on the real remote rather than an injected key.

## The gear button

`dumpsys window policy` shows the gear routed away from any app:

```
KEYCODE_NOTIFICATION=com.google.android.apps.tv.launcherx/...remotecontrol.GlobalKeyListener_Receiver
KEYCODE_GUIDE=com.google.android.apps.tv.launcherx/...remotecontrol.GlobalKeyListener_Receiver
```

So once launcherx is disabled the gear does nothing at all: no activity starts and no app is asked.
`getevent -lt` shows the press arriving on the IR receiver as Linux code `0x00f9` (249), and the
framework turns it into `KEYCODE_NOTIFICATION` (83).

The fix is the launcher's optional key helper, and it needs a rebuild, because
`settings_key_scancodes` is read from `config.xml` only:

- install with `HOME_KEY_SERVICE=1` (`-HomeKeyService` on Windows), which binds
  `com.gathin.tvhome/.HomeKeyService`;
- `settings_key_keycodes` `[83]` and `settings_key_scancodes` `[249]` in
  `launcher/app/src/main/res/values/config.xml`;
- `settings_key_panel` `true`, so the press opens the launcher's quick settings panel (TV settings,
  Display, Network, Apps, Launcher settings) rather than the TV's own Settings.

Checked on the set: the real remote's gear opens that panel, `logcat` shows the IR code and then
`START ... com.gathin.tvhome/.QuickSettingsActivity`, and a volume key still steps one step per press.
An injected `input keyevent 83` does **not** reproduce it: the press has to come from the remote, as
`AGENTS.md` says for anything an accessibility helper answers.

### The price, and the choice this set ended with

The helper filters remote keys, and on this firmware that kills the volume auto-repeat. Measured both
ways on the set: with the helper bound, a 4-second hold on Volume Up moved the stream exactly one step;
with `key_filter` off (or the helper unregistered), the same hold ramps.

`consume_volume_keys` does not buy it back here. That switch exists for firmware that applies a volume
key twice and repeats a held one inside the service, but this set's audio path takes the volume keys
before any accessibility service is asked: a 4-second hold with `consume_volume_keys` on left nothing
from `com.gathin.tvhome` in `logcat` and still moved one step, while the switch's own repeat would have
fired every 60 ms after 500 ms.

So it is either the gear button or the volume ramp on this board, not both. The owner kept the gear:
its panel opens over whatever is playing, which is the point of the button — picture, brightness and
sound get touched while a film runs, not from Home. The one-step volume was accepted instead. The
build this folder was written from therefore runs with the key helper on (`HOME_KEY_SERVICE=1`),
`settings_key_panel` on, and 83/249 in `settings_key_keycodes`/`settings_key_scancodes`; `key_filter`
stays on, and a held volume key moves one step.

Anyone who would rather have the ramp can build with `key_filter` false and skip the helper, and then
the gear does nothing — the mKeyMapping above hands it to a launcher that is gone.

Not checked: whether `KEYCODE_GUIDE` (also routed to launcherx) still opens the program guide from
Live TV.

## Bluetooth, and why it cannot have a shortcut

The set has no separate Bluetooth screen. `android.settings.BLUETOOTH_SETTINGS` resolves to nothing,
and accessory pairing lives at the **bottom of the Settings list**, as "Remotes & accessories"
(`com.android.tv.settings/.MainSettings` with its `accessories.AccessoriesFragment`), not in an
activity of its own. That is worth knowing because the gear used to open launcherx's own dashboard,
where Bluetooth was one press away.

Tried here, all on the set, none of them a route to a shortcut:

- `-e ':settings:show_fragment' …AccessoriesFragment` and the `:settings:show_fragment_args` variant
  both open `MainSettings` at the top of the list. The APK carries the fragment name, so the extra is
  simply not read by this build.
- `com.android.tv.settings/.accessories.BluetoothDevicePickerActivity` starts and immediately says
  "This action is not supported": it expects a caller result.
- `com.android.tv.settings/.accessories.BluetoothAccessoryActivity` does not start from the shell.
- `com.android.tv.settings/.accessories.AddAccessoryActivity` does start, from the action
  `com.google.android.intent.action.CONNECT_INPUT`, and shows the pairing scan. A launcher tile or
  top-bar button cannot use it: starting it needs `BLUETOOTH_CONNECT`, which an app without that
  permission does not get, and `logcat` says so — `Permission Denial: starting Intent { act=
  com.google.android.intent.action.CONNECT_INPUT … } from … com.gathin.tvhome … requires
  android.permission.BLUETOOTH_CONNECT`. The launcher has no Bluetooth permission on purpose, so the
  button was removed rather than left dead.

So the shortest path on this set stays «Настройки» → four presses down → «Пульты и аксессуары».

## Undo

```
TV_HOST=<tv ip> tools/restore.sh examples/dexp-65uhg2/debloat.txt com.google.android.apps.tv.launcherx
```
