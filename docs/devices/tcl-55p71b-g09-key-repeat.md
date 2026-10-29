# TCL 55P71B Pro (G09, Google TV 12): a bound key helper costs remote key auto-repeat

Owner report: [#763](https://github.com/Gathin23/cleanyourtv/issues/763). One unit, one session: TCL
55P71B Pro "Smart TV Pro", device `G09`, Android 12 (Google TV), firmware `STT2.230203.001.AS23`,
Amlogic.

## The symptom

Hold volume up or down and the volume moves one step, on the home screen and inside an app. Pressing
the button that many times gives that many steps. The remote is not necessarily the problem and neither
is the debloat: the same remote ramps normally again as soon as no key-filtering accessibility service is
bound, and where the repeats go is one `getevent -lt` away - if the kernel still prints them while the
button is held, they are being dropped above the driver.

## The bisect

The owner enabled the launcher's two optional helpers one at a time and then together:

| `enabled_accessibility_services` | holding volume up/down |
| --- | --- |
| both `HomeKeyService` and `InputKeyService` | one step per press |
| `HomeKeyService` alone | one step per press |
| `InputKeyService` alone | one step per press |
| `""`, with `accessibility_enabled 0` | ramps continuously |

All or nothing. This is not one helper mishandling `VOLUME_UP`: the dump while broken lists both as
`Service[label=Home button, capabilities=8]` and `Service[label=Home, capabilities=8]`, and capability
8 is `CAPABILITY_CAN_FILTER_KEY_EVENTS`. Any bound service that asks to filter key events puts this
firmware's input path in a mode where the first press arrives and the repeats that follow are dropped.

Read the state without changing anything:

```sh
tools/tv.py 'settings get secure enabled_accessibility_services'
tools/tv.py 'settings get secure accessibility_enabled'
tools/tv.py 'dumpsys accessibility'
```

To get the repeat back for a test, and to put the helpers back afterwards:

```sh
# keep the string the first read printed; the empty list also unbinds TalkBack and any other service
tools/tv.py "settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0"
tools/tv.py "settings put secure enabled_accessibility_services com.gathin.tvhome/com.gathin.tvhome.HomeKeyService:com.gathin.tvhome/com.gathin.tvhome.InputKeyService; settings put secure accessibility_enabled 1"
```

That second line is the launcher's two helpers, not "the value": write back what the read printed.
`theme.sh` already re-puts the list after a theme push, `am force-stop com.gathin.tvhome` drops it on
its own (see [TROUBLESHOOTING](../TROUBLESHOOTING.md)), and re-running `launcher/build-and-install.sh`
without `INPUT_KEY_SERVICE=1`/`HOME_KEY_SERVICE=1` leaves the helpers off.

## The launcher is not the one eating the repeats

Both helpers return `false` for keys they do not map, so a volume key never reaches launcher code and
is not consumed there: `InputKeyService.onKeyEvent` passes anything that is not in `app_keys`,
`input_key_keycodes`, `KEYCODE_TV` or `KEYCODE_TV_INPUT`, and `HomeKeyService` passes everything except
`KEYCODE_ALL_APPS` and the settings keycodes/scancodes. The repeat is lost before the app sees the
event. What the report does not contain is an `onKeyEvent` line for a held volume key, so "the
platform's filter drops the repeat" is the reporter's reading of the bisect, not a captured log. A
future report that logs the repeat event arriving at a helper and being passed through would move the
blame back into the launcher.

## What the helpers buy, and what this costs

The buttons a helper exists for on this remote:

| Button | What arrives | Helper |
| --- | --- | --- |
| Gear (TV settings) | `KEYCODE_NOTIFICATION` 83, scancode 561 | `settings_key_keycodes` / `settings_key_scancodes`, `HOME_KEY_SERVICE=1` |
| Netflix | vendor keycode 4062, scancode 472 | `app_keys`, `INPUT_KEY_SERVICE=1` |
| Prime Video | vendor keycode 4099 | `app_keys` |
| YouTube | vendor keycode 4065 | `app_keys` |
| Input | `KEYCODE_TV` / `KEYCODE_TV_INPUT` | `InputKeyService`, and TCL draws that panel itself on this set |

Vendor keycodes reach nothing on their own: `com.tcl.ttvs`'s `KeyDownBehaviorReceiver` logs
`keyString=4062 keyCode=4062 type=IR` and acts on nothing. These buttons were answered by the stock
home, and the [G09 example list](../../examples/tcl-g09-google-tv/debloat.txt) disables launcherx, so
after the cleanup only a helper can answer them. That is where the trade comes from: on this firmware
the buttons and the volume ramp cannot both be had, because both need the same filter capability.

## The same trade on a Xiaomi MediaTek set

A second owner reported it on [#944](https://github.com/Gathin23/cleanyourtv/issues/944): Xiaomi
MiTV-MOSR4 (`irobot`, MediaTek), Google TV / Android 11 (SDK 30), firmware `RTM5.220609.003.1265`.
Holding volume up with `InputKeyService` and `HomeKeyService` bound moves it one step, and
`InputKeyService` alone is still one step; `dumpsys accessibility` lists the bound service with
`capabilities=8`. Same symptom, same capability, second vendor and second Android version.

Two things differ from the bisect above, and both are worth keeping. The empty-list control was not run:
on this set clearing the list unhooks the buttons the helpers were installed for - with launcherx
disabled, its Input, Settings, YouTube and Netflix buttons reach only
`com.xiaomi.android.tvsetup.partnercustomizer/.GlobalKeyReceiver`, which starts launcherx activities and
crashes (the [MOSR4 note](xiaomi-mitv-mosr4.md) has that part, and its install line passes both helpers).
So "the ramp returns with no service bound" was measured on the G09 alone until the Mi TV 4A below ran that
control too. And the build string in
the issue is not the one that note carries (`RTM5.220609.003/1585`, written on another unit of the same
model), so treat the two as separate units until both fingerprints are read: the model, the firmware
family and the behaviour are what they agree on.

That is still enough to unhook the mechanism from "this firmware": the volume keys take the early
question from the window policy on Android 11 and 12, on two vendors, whenever a bound service holds
`CAPABILITY_CAN_FILTER_KEY_EVENTS`. The trade and the choices below are the same on both sets. The
reporter's own suggestion - a helper that synthesizes the repeat with `adjustSuggestedStreamVolume` while
a volume key is held - is launcher code, and an off-by-default switch at best; it is not in this note.

## Where the repeat goes on a Hisense set with a CEC soundbar

A third set, with a log of the held key: Hisense 75U75N (`hengshan`, MediaTek), Google TV / Android 12,
firmware `STT2.230310.002`, US market, one unit, one evening. Sound goes over HDMI eARC to a soundbar with
HDMI-CEC system audio on, so each volume step leaves the TV as a CEC `<User Control Pressed>` volume key.

| `enabled_accessibility_services` | holding volume up |
| --- | --- |
| `HomeKeyService` alone (`capabilities=8` in `dumpsys accessibility`) | one step per press |
| `""`, with `accessibility_enabled 0` | ramps continuously |

`InputKeyService` was not tried on this set. With the helper bound, `logcat` for one press held for 2.7 s:

- `InputDispatcher: notifyKey` shows the down and, 2.7 s later, the up, and nothing between. The remote sends
  no repeats of its own; the dispatcher makes them while a key it has dispatched stays down.
- `WindowManager: interceptKeyTi keyCode=24 down=true` comes twice, the first time with
  `interceptDelayTime=144`, the second 145 ms later.
- `HdmiCecLocalDeviceTv` then sends one volume step, and the CEC history in `dumpsys hdmi_control` holds one
  `<User Control Pressed> 05:44:41` and its release for the whole hold.

That pattern fits Android 12's accessibility key filter: with a service asking to filter keys, volume keys
go to the window policy's `interceptKeyBeforeDispatching` before any service sees them, and the second call is
that check retried after the delay it asked for. On this firmware the policy answers by sending the CEC
step itself and reporting the key consumed, so the event ends inside the filter. No service receives it, and
the dispatcher never gets the held key it would make repeats from. A test build whose helper stepped the
volume itself while a volume key was held sent no second CEC step, so on this set the helper never saw the
key either. That is a log of the framework's side, read against the AOSP filter; it still has no
`onKeyEvent` line from a helper, which is what the "launcher is not the one eating the repeats" section asks
for.

What the helpers would buy on this set, from `dumpsys window policy` and `logcat`:

| Button | What arrives | Without a helper |
| --- | --- | --- |
| Gear | `KEYCODE_NOTIFICATION` 83, scancode 598 | a global key for launcherx's `GlobalKeyListener_Receiver`, so nothing once it is disabled |
| YouTube | `KEYCODE_BUTTON_3` 190 | the same global key route |
| Prime Video, Disney+, Tubi | 295, 607, 606 | the policy consumes them and Hisense's `com.vt.tv.smartsense` asks launcherx to open the app with `com.google.global_button.ACTION_LAUNCH_APP`, addressed to launcherx; another app registered for that action received nothing |
| Netflix, Input | | work, through Hisense and MediaTek receivers that stay enabled |

Keeping launcherx enabled while another launcher holds Home did not work here: re-enabled, it took Home back
(`cmd package resolve-activity` named it although `android.app.role.HOME` was given to the new launcher), and
the shell may not disable its home activity alone (`SecurityException` from `pm disable-user` on the
component). The owner kept the ramp and dropped the five buttons.

## A second Hisense set, without a soundbar

A Hisense SmartTV 4K FFM (`songshan`, MediaTek, Android 11, `RTT2.220118.001`, the second unit in
[`examples/hisense-smarttv-4k-ffm-android11`](../../examples/hisense-smarttv-4k-ffm-android11/debloat.txt))
does the same with `InputKeyService` alone bound: held volume up arrives as one press, and the firmware's
own key log (`WindowManager: interceptKeyTi keyCode=24 down=true repeatCount=0`) shows no repeat after
it. The empty-list control was not run there either. The owner kept the helper for the YouTube, Prime
Video, Disney+, Peacock and gear buttons and lives with one step per press.

## A TCL G08 on Android 12, and `consume_volume_keys`

A TCL 75Q7C (G08, Google TV 12, `STT2.221228.001 AS36`, the second unit in
[`examples/tcl-g08-eu-android12`](../../examples/tcl-g08-eu-android12/README.md)) does the same with both helpers
bound: a two-second hold of volume up logs one `handleKeyBeforeQueueing ... ACTION_DOWN ... KEYCODE_VOLUME_UP
... repeatCount=0`, the `ACTION_UP` two seconds later, and one volume change (`setStreamVolumeIndex`) in between.
`consume_volume_keys` true did not bring the ramp back there: the launcher's own repeat (`VolumeKeys`) never
stepped the volume, so the helpers do not seem to receive the volume key on this build, and the switch, made for
the G03's double step, has nothing to repeat. The empty-list control was not run. The owner kept the helpers for
the gear, YouTube and TCL buttons.

A TCL 75Q7CXB (G08, Google TV 14, `UTT2.250416.001 AU02`), launcherx and setupwraith disabled with this
launcher as Home, loses the ramp the same way, and with `HomeKeyService` alone as well: with both helpers bound,
then with `enabled_accessibility_services` set to `HomeKeyService` only (`dumpsys accessibility` showing one bound
service), the owner's held volume up moved one step per press each time. That one-helper run also kept the app
buttons in `app_keys` and the gear in `settings_key_keycodes` working. The 55Q6C in
[`examples/tcl-q7c-google-tv`](../../examples/tcl-q7c-google-tv/README.md), on the same build, kept ramping
with `HomeKeyService` bound, but it kept launcherx enabled and went through `stock_home_redirect`; so on this
build one bound helper is enough to cost the ramp once launcherx is gone. The empty-list control was not run
here either.

## The path in the Android 12 source, and a Philips set measured both ways

The Hisense reading above holds line by line in AOSP `android12-release`, and a fourth vendor measures the
same way. With an input filter installed, `InputDispatcher::notifyKey` hands every key to the filter before
anything is queued. For a key, `AccessibilityInputFilter` goes to `KeyboardInterceptor.onKeyEvent`, whose
`getEventDelay` asks the window policy first for Volume Up and Down only
(`mPolicy.interceptKeyBeforeDispatching(null, event, policyFlags)`), and returns without passing the event on
when the answer is negative. On a TV (`mUseTvRouting`) `PhoneWindowManager.interceptKeyBeforeDispatching`
answers a volume key with `dispatchDirectAudioEvent(event)` and `key_consumed`, which is `-1`. So the key
takes its one volume step inside that check and is dropped there: no accessibility service is offered it,
and it never reaches `InputDispatcher::dispatchKeyLocked`, the only place that records a held key
(`mKeyRepeatState.lastKeyEntry`) and synthesizes its repeats every `getKeyRepeatDelay()`, 50 ms. That is
why `onKeyEvent` has nothing to log, and why a helper cannot repeat the key itself either. Source:
[KeyboardInterceptor.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/accessibility/java/com/android/server/accessibility/KeyboardInterceptor.java),
[PhoneWindowManager.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/core/java/com/android/server/policy/PhoneWindowManager.java),
[InputDispatcher.cpp](https://android.googlesource.com/platform/frameworks/native/+/refs/heads/android12-release/services/inputflinger/dispatcher/InputDispatcher.cpp).

Measured on a Philips 65OLED804/12 (TPM191E, Android TV 12, `STT2.230526.001`, the fifth unit in
[`examples/philips-tpm191e`](../../examples/philips-tpm191e/debloat.txt)), sound over HDMI ARC to a home
cinema system, with `getevent -lt` on the remote and the volume event log in `dumpsys audio`. On a TV,
`AudioService.handleVolumeKey` logs one `adjustSuggestedStreamVolume` for the down (`VOL_ADJUST_START`) and
one for the up (`VOL_ADJUST_END`), so a pair is one press:

| `enabled_accessibility_services` | Volume Down held | `dumpsys audio` |
| --- | --- | --- |
| `HomeKeyService` alone (`capabilities=8`) | 3.0 s | the start and the end, nothing between |
| `null`, with `accessibility_enabled 0` | 1.07 s | the start, 14 repeats 50 ms apart from about 250 ms on, the end |

The remote sent one `DOWN` and one `UP` per press in both runs, never a repeat of its own, so every step in
the second row came from the dispatcher; 20 steps a second is also why a held key runs away on this set.
Single presses step once either way. `settings put secure long_press_timeout` is no way to slow the ramp
here: the dispatcher marks its first repeat `FLAG_LONG_PRESS`, which is what Home's long-press action waits
for, and with 800 set that action still fired 400 ms after the down, before and after a reboot.

## A Xiaomi Mi TV 4A on Android TV 10, with the empty-list control run

A version the sections above do not have: Xiaomi Mi TV 4A (MiTV-MOOQ1, `tarzan`, MediaTek), Android TV 10
(SDK 29), firmware `QTG3.200305.006.6148`, stock Xiaomi Bluetooth remote, launcher built from a `main`
download of 2026-10-28 ([#2446](https://github.com/Gathin23/cleanyourtv/issues/2446)).
`com.google.android.tvlauncher` was disabled by `launcher/build-and-install.sh`, and the two buttons this
owner wanted reach the stock launcher by name rather than through a vendor keycode:

- **Input** (`KEYCODE_TV_INPUT`, 178): the firmware's `GlobalKey` handler does not pass the key on, it starts
  `com.android.tv.action.VIEW_INPUTS` with an explicit component,
  `com.google.android.tvlauncher/.inputs.InputsPanelActivity`, so with tvlauncher disabled `logcat` ends
  `GlobalKey: ActivityNotFound` and the press does nothing at all. `INPUT_KEY_SERVICE=1` with
  `handle_view_inputs` true brings the button back, but not by answering that intent:
  `InputKeyService.onKeyEvent` consumes the Input key and starts a **new** `VIEW_INPUTS` intent with no
  component, and `handle_view_inputs` lets the launcher open its Inputs menu for that new intent. It cannot
  redirect the firmware's explicit intent, which is addressed to tvlauncher.
- **PatchWall** (`KEY_WWW`; `getevent -lt` prints `MSC_SCAN 000c0196` and then `KEY_WWW DOWN`): nothing once
  `com.mitv.tvhome.atv` is disabled. `scan:150:com.spotify.tv.android` in `app_keys` opens Spotify instead -
  the remap the [redmi-mitv-mooq3 list](../../examples/redmi-mitv-mooq3/debloat.txt) records as not yet tried
  on a real remote, tried here.

`InputKeyService` was the only helper bound, `HomeKeyService` was never enabled on this set, and
`dumpsys accessibility` listed it as one `Service[label=Inicio, ..., capabilities=8]`:

| `enabled_accessibility_services` | holding Volume Up |
| --- | --- |
| `com.gathin.tvhome/.InputKeyService` | one step per press |
| `''`, with `accessibility_enabled 0` | ramps continuously |
| `InputKeyService` written back | one step per press again |

The third row is the measurement [#944](https://github.com/Gathin23/cleanyourtv/issues/944) could not take.
Clearing the list kills both buttons here too, so it is a test and not a configuration to stay in, but it was
run to see the ramp come back - which is why the sentence above no longer rests on the G09 alone.
`consume_volume_keys` was not tried, and `HomeKeyService` alone was not tried either, so this set gives one
helper's row rather than the all-or-nothing bisect the G09 has.

Android TV 10 is older than the `android12-release` source read above, which was not re-read for SDK 29: the
mechanism claimed here is the `capabilities=8` in the dump and the three rows, not a line of
`KeyboardInterceptor`. The owner kept both buttons and lives with the stepped volume - the second Choice
below - so nothing in that section changes.

## Choices

- **Keep the repeat and skip the helpers.** Do not pass `INPUT_KEY_SERVICE=1` or `HOME_KEY_SERVICE=1`
  when installing. The price is the gear button and the remote's app buttons: see the
  [G09 example](../../examples/tcl-g09-google-tv/README.md), whose install line passes
  `INPUT_KEY_SERVICE=1` for exactly those buttons, so a set that behaves like this one pays for them
  with the volume ramp. The Input button keeps working there without any helper.
- **Keep the buttons and accept stepwise volume.** The volume keys still change the volume, one step
  per press; the launcher cannot make the platform ramp it again. Setting volume from a phone app or
  through an AVR/soundbar remote was not compared on this set.
- **Keep launcherx enabled and bind no helper.** The stock home answers those buttons, so the repeat
  survives. This is the only configuration that has both, and the price is the stock home screen the
  cleanup was replacing.

- **Keep both with a listener you start by hand.** A script run by the `shell` user can answer the app
  buttons without any service bound, so the ramp stays; see "Both at once" below. It does not come back
  after a reboot, so it is started again each time.

It is a toggle, not a fix: two one-line `settings` writes switch between the two behaviours whenever
you want, but never both at once.

## Both at once: a listener you start by hand

A unit with the same product, `G09_4K_GB`, and the same build, `STT2.230203.001.AS23` (Android 12, security
patch 2025-09-05), and a Bluetooth remote that names itself `BT_RC813A_B4`, had the three app buttons and the
volume ramp together, with no accessibility service bound. That does not contradict the bisect above: it is
about services that filter keys, and this uses none. One owner, one unit, and the only checks were pressing
the real Netflix, Prime Video and YouTube buttons, holding volume up and down, and a day of ordinary use.

**What reads the buttons.** With `enabled_accessibility_services` empty and `accessibility_enabled 0` (the
control in the bisect), a script run by the `shell` user reads the remote's consumer-control input node with
`getevent` and starts an app when one of three keys goes down. The keys are the scancodes in the table above,
472, 473 and 569, which `getevent` prints as `0001 01d8`, `0001 01d9` and `0001 0239`. The node is found by
name, not by `/dev/input/event<N>`, as the [TROUBLESHOOTING](../TROUBLESHOOTING.md) section on remapping the
buttons without a key helper explains, along with the limit that a reader cannot consume a press. Here that
costs nothing visible only because these vendor keycodes reach nothing else once launcherx is disabled
(`KeyDownBehaviorReceiver` above); that is read from this note, not re-measured with the script.

**Keep it reading the right node.** The remote sleeps and reconnects, and each time the system recreates its
input node. A `getevent` still open on the old one (`ls -l /proc/<pid>/fd` shows `event9 (deleted)`) never
gets another event and never exits, so the buttons silently stop working until the script is restarted. This
showed up after about a day. The script below checks for it every three seconds and reopens the new node. It
is the script that ran, with its log lines dropped and its paths shortened:

```sh
#!/system/bin/sh
NAME='BT_RC813A_B4 Consumer Control'   # the node's name in `getevent -p`
echo $$ > /data/local/tmp/keys.pid
find_dev() { getevent -p 2>/dev/null | grep -B1 "name: .*\"$NAME\"" | head -1 | sed 's/.*: //'; }
open_app() { am start -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER -n "$1" >/dev/null 2>&1; }
while true; do
  DEV=$(find_dev)
  if [ -z "$DEV" ]; then sleep 5; continue; fi
  (
    getevent "$DEV" 2>/dev/null | while read -r line; do
      case "$line" in
        *"0001 01d8 00000001"*) open_app com.netflix.ninja/.MainActivity ;;
        *"0001 01d9 00000001"*) open_app com.amazon.amazonvideo.livingroom/com.amazon.ignition.IgnitionActivity ;;
        *"0001 0239 00000001"*) open_app com.google.android.youtube.tv/com.google.android.apps.youtube.tv.activity.ShellActivity ;;
      esac
    done
  ) &
  sleep 1
  while true; do
    GP=$(pgrep -f "getevent $DEV" | head -1)
    [ -z "$GP" ] && break
    if ls -l /proc/$GP/fd 2>/dev/null | grep -q '(deleted)'; then kill $GP 2>/dev/null; break; fi
    NOW=$(find_dev)
    if [ -n "$NOW" ] && [ "$NOW" != "$DEV" ]; then kill $GP 2>/dev/null; break; fi
    sleep 3
  done
  wait
  sleep 2
done
```

**Start it by hand**, detached from the debugging session, or it dies with it, as the Streamer section does:
`setsid nohup sh /data/local/tmp/keys.sh > /dev/null 2>&1 < /dev/null &`. It does not come back after a
reboot; start it again the same way. The loop waits for the remote when it is not connected and reads again
when it comes back.

Not covered: the Gear button (that stays with a helper, and so with the lost repeat), the buttons the system
answers itself, starting it again after a reboot or power loss, standby, a second remote, and whether any
firmware other than this build lets the `shell` user read the remote's node.

## Limits

One unit, one firmware build (`STT2.230203.001.AS23`), one session. Only volume up/down was held:
channel up/down, a held D-pad (seek) and the media keys were not tried, so "auto-repeat" here means the
key that was measured. Whether a filter service that is not the launcher's (TalkBack, a remote app)
does the same is untested; the capability in the dump is the only common factor seen. No cold boot, no
second remote. The two other G09 units in this repo (UK `G09_4K_GB` AS24 in Brazil, and
`G09_4K_GB_32BIT` AS10) never looked at this, so the claim belongs to the build, not to "G09". No
launcher-side fix is offered, and none should be expected: an app can decline the key - these two
already do - but cannot hand a repeat that the platform dropped back to the input stack, and putting
`VOLUME_UP` in `app_keys` would open an app rather than ramp the volume.
