# Remote buttons

Gives a remote button an app of your choice, from any app and without costing the volume ramp. Separate from the
launcher.

A Mi Box S on Android TV 12 is the case it was made for: its Prime Video button only ever sends Prime Video's own
intent, so once Prime Video is disabled the button does nothing, and the launcher's key helpers that could take the
button make a held volume key step once per press ([`tcl-55p71b-g09-key-repeat.md`](../../docs/devices/tcl-55p71b-g09-key-repeat.md)).
With this add-on the button opens what you pick (YouTube, Netflix, a live TV app, anything a launcher shows) and
volume keeps ramping. Which app is your choice; nothing is mapped until you pick.

Package `io.github.knochango.remotebuttons`. You see it as **Remote buttons** (Spanish: **Botones del mando**). On
cleanyourtv's Home it is also a module card, **Remote buttons**, which opens its settings.

## Settings

Open the card (or the app's own tile):

| Row | What OK does |
|---|---|
| One row per button, "Button 194 (BUTTON_7) → YouTube" | opens the app grid for that button; pick another app, or None to take the button off. With the cleanyourtv launcher installed the grid also offers **App library**, for a remote's Apps button |
| Add a button | waits up to 20 s for the next press on the remote, then opens the app grid for it. D-pad, OK, Back, Home, volume and power are not learned. Back cancels |

Under the rows, what the add-on needs and the last button it saw:

- **Helper**: whether the service is on (Settings, Device Preferences, Accessibility, Remote buttons).
- **Reading the key log**: whether `READ_LOGS` was granted.
- **Opening over other apps**: whether the `SYSTEM_ALERT_WINDOW` app op is allowed.
- **Last button seen**: the last key it read, so you can tell whether a press reaches it at all.

## How it works

On some builds the window manager writes every key it handles to logcat at debug level. A Mi Box S on Android TV 12
prints `D/WindowManager: interceptKeyTi keyCode=194 down=true repeatCount=0`; the TCL G10 in
[`tcl-g10-app-keys.md`](../../docs/devices/tcl-g10-app-keys.md) prints `handleKeyBeforeQueueing ... action=ACTION_DOWN,
keyCode=4065, ... repeatCount=0`. The service reads `logcat -s WindowManager:D`, takes the key-down of a press and
opens the app you gave that key.

It is an accessibility service only so that Android binds it again after every boot. It asks for no key filtering
(`dumpsys accessibility` shows it with `capabilities=0`) and reads no windows or events, so nothing is consumed and
held keys keep their auto-repeat. That is also its limit: a button that still does something of its own does both.
On a Mi Box S, Netflix's button opens Netflix through Xiaomi's receiver whatever you map, so give a button an app only
where its own target is gone (Prime Video disabled) or where it opens nothing.

**App library** opens cleanyourtv's Home with its App library up, by the action the launcher's own `library` word in
`app_keys` uses (`com.gathin.tvhome.action.APP_LIBRARY`); the theme or `config.xml` needs `app_library` on, or Home
only comes forward. On a Mi Box S the Apps button (`ALL_APPS`, 284) reaches nothing once the stock launcher is
disabled, so mapping it to the library does only that.

The key code is Android's, which is not always the kernel's: the Mi Box S Prime Video button is `KEY_YELLOW` (398) in
`getevent` and 194 (`BUTTON_7`, scan code 400) to Android. Add a button learns it from the log, so you never need the
number.

## Install

```bash
TV_HOST=<tv ip> addons/remote-buttons/build-and-install.sh
```

On Windows: `addons\remote-buttons\build-and-install.ps1 -TvHost <tv ip>`. The script builds the app, installs it,
grants `READ_LOGS`, allows `SYSTEM_ALERT_WINDOW`, waits a few seconds and switches the service on (it adds itself to
the accessibility list and leaves other services on it). Without a JDK, build in Docker first
(`tools/docker-build.sh addons/remote-buttons`) and run the install with `SKIP_BUILD=1`. Then open the card, choose
Add a button and press the button.

Uninstall: the same script with `--uninstall` (`-Uninstall` on Windows); it takes the service off the list first.

## Permissions

| Permission | Why |
|---|---|
| `READ_LOGS` | to read the window manager's key lines in logcat. Granted over ADB by the install script (`pm grant`); Android 12 asked for no consent on the Mi Box S |
| `SYSTEM_ALERT_WINDOW` | Android 10 and later open another app from the background only for an app allowed to draw over other apps. The app draws nothing over other apps |
| Accessibility service (`BIND_ACCESSIBILITY_SERVICE`, held by the system) | so Android starts the service again after a reboot. No key filtering, no window content, no events |

No network, no telemetry. The choices stay in the app's own storage on the TV.

## Limits

- It needs a build whose window manager logs keys at debug level; most do not. Before installing, press the button
  with `tools/tv.py 'logcat -v time -s WindowManager:D'` running: no line, no add-on. A firmware update can drop
  those lines.
- A press arrives a moment after the key: the log is read, then the app is started.
- Nothing is consumed (see How it works). The same goes for another app that answers the same button, such as a
  second log reader or a launcher key helper: both act on the press, and whichever opens last stays in front. Give
  each button to one of them only.
- Tried on one Mi Box S only. The TCL line format is read as the G10 note gives it, not tried with this add-on.

## Tested on

Xiaomi Mi Box S (MIBOX4, oneday), Android TV 12, firmware STTC.230106.001.4560, with the cleanyourtv launcher as Home.
