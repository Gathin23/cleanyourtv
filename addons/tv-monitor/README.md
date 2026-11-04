# TV Monitor

A small Android TV add-on that keeps an eye on how healthy the TV is, and an optional agent on your PC that can ask
an AI for help and fix things on the TV, inside hard limits and with an undo.

It is built for the [Clean Your TV](../../README.md) launcher (module card, ring in the top bar), but the add-on
runs on its own on any Android TV from Android 8 (API 26).

## What it does

| Part of the screen | What it shows or does | Needs the PC |
|---|---|---|
| Status | One big icon and word: green is fine, amber means watch, red means act. The launcher ring shows the same colour. | No |
| Memory and storage | Free RAM and storage, as bars that turn amber, then red. | No |
| Flagged apps | Apps from the Clean Your TV list that came back on, for example after an update. | No |
| Could be stopped / journal | Apps that may slow the TV, each with its risk; the ones you stopped, with the date, so you can turn them back on. | No |
| Ask the assistant | Sends a small snapshot of the TV to your AI through the PC and shows the answer on the TV. | **Yes** |
| Apply and undo | Applies what the AI proposes after your OK on the TV, with a countdown and an automatic undo. | **Yes** |
| History | Every question and answer, newest first, kept on the TV (last 30). | No |
| Details | All running processes with uptime, processor and memory (and its refresh button). | **Yes** |
| Clear cache | Opens the storage screen of the TV. | No |
| Restart | Asks first, then the PC restarts the TV. Without the PC, restart from Settings, About. | **Yes** |
| USB debugging | Turns debugging on for 30 minutes, then off by itself. | No |
| ? | The same explanation as this page, on the TV, with every AI setting. | No |

On the TV, every button or block that needs the PC carries a small monitor icon (on the corner of the button, and
after the title in the guide), the focused button's name says "Needs the PC", and a line under the bar explains the
icon. Anything without the monitor works on the TV alone.

The interface is icons first, with little text.

## Languages

The add-on follows the language of the TV: English (the default), Italian, French, German and Spanish. Any other
language shows English. The AI is told the TV language and answers in it. The French, German and Spanish texts are
machine-written and have not been reviewed by a native speaker; corrections are welcome.

On the PC, the setup page of `tv_ai_agent.py` is in English and Italian, and the messages it prints in the terminal
are English only.

## How the pieces fit

```
 TV (add-on)                                      PC (tv_ai_agent.py)
 ------------------------------------------      ------------------------------------------
 reads memory, storage, package state     <-->   reads the rest over ADB (process table,
 shows answers, asks, keeps the history          package list), calls the AI, applies actions
 writes   REQUEST / DETAILS / REBOOT /    ---->  polls logcat (tag TvMonitor) every 3 s
          DECISION lines to logcat
 receives reports by broadcast            <----  am broadcast to ReportReceiver (JSON)
```

Android does not let an app read other apps' processes, disable packages or restart the TV. So those things are done
by the PC over ADB, and the add-on and the PC talk through logcat lines and `am broadcast`. The TV never holds the
AI key.

When the owner asks and USB debugging is off, the add-on offers to turn it on (it needs the one-time permission below),
sends the request, and turns debugging off again when the final result arrives. If debugging was already on, it is
left on. A 30-minute alarm turns it off in any case.

## The PC must be on and the agent running

The TV cannot reach the AI by itself: the add-on has no internet access (no `INTERNET` permission) and never holds your
key. So everything that needs the AI, and everything that needs ADB (process table, disabling packages, restart), goes
through `tv_ai_agent.py` running **on the PC**, in a terminal that stays open:

```
python tv_ai_agent.py <tv address> --apply --watch
```

If that program is not running, Ask the assistant, Details and Restart get no answer: the TV shows a hint after about
25 seconds. Status, flagged apps, suggestions and History work without the PC. The PC and the TV must be on the same
network, and USB debugging must be on while a request is served (the add-on offers to turn it on and turns it off
again after the answer).

Today the agent has to be started by hand, or by a Windows startup entry or scheduled task you create. Another way
is to run it only when needed, without `--watch`: `python tv_ai_agent.py <tv address> --apply` does one round and ends.

## Connect your AI

1. On the PC, install Python 3 and the Android platform-tools (`adb`), and copy this folder.
2. On the PC, run `python tv_ai_agent.py --setup`. A page opens on this PC, with:
   - **API address**: where the request goes. Default `https://api.anthropic.com/v1/messages`.
   - **Model**: default `claude-sonnet-5-5`.
   - **API key**: from your AI account. Saved encrypted for your user on this PC (DPAPI on Windows) in
     `%APPDATA%\tv-monitor\config.json`; never sent to the TV. You can set `ANTHROPIC_API_KEY` instead.
   - **TV address**: the IP address of the TV.
   - **Seconds to confirm**: how long you have to press All good before a change is undone; 20 to 600, default 60.
3. On the PC, run `python tv_ai_agent.py 192.168.1.50 --apply --watch` and leave it open: without it the TV cannot reach the AI.
4. On the TV, choose Ask the assistant.

Without the web page, from PowerShell: `powershell -ExecutionPolicy Bypass -File .\set-ai.ps1 -TvAddress 192.168.1.50
-Test`. It asks for the key hidden, saves the same encrypted file, keeps what you do not pass, and `-Test` sends one
8-token request to prove the key and address work. Other options: `-Url`, `-Model`, `-RevertSeconds`, `-KeepKey`.

`--setup --lan` makes the page reachable from other devices on your network; it is plain HTTP, so the key crosses the
network unencrypted. Use it only on a network you trust and close it as soon as you save.

| Option | Meaning |
|---|---|
| `--apply` | Lets you apply what the AI proposes. Without it you only get a preview. |
| `--auto` | Skips the question for low-risk changes; the countdown still runs. |
| `--watch` | Stays running and answers the add-on. Can be started while USB debugging is off. |
| `--max N` | Most packages disabled in one run, default 3. |
| `--revert-seconds N` | Time to confirm before everything is undone, default 60. |
| `--wait N` | Seconds to wait for your answer to a proposal, default 300. |
| `--show-payload` | Print exactly what is sent to the AI, then stop. |
| `--details` | Send the add-on the process table once. |
| `--reboot` | Restart the TV now. |
| `--mock reply.json` | Use a saved AI reply instead of calling the API (tests). |

Environment variables `TV_AI_MODEL` and `TV_AI_URL` override the model and the address.

### The request

```
POST https://api.anthropic.com/v1/messages
x-api-key: <your key>
anthropic-version: 2023-06-01
content-type: application/json

{"model": "claude-sonnet-5-5", "max_tokens": 1500,
 "system": "You help keep an Android TV healthy...",
 "messages": [{"role": "user", "content": "<snapshot of the TV, as JSON>"}]}
```

The reply is read from `content[0].text`, which must hold one JSON object:

```
{"summary": "one or two sentences",
 "findings": ["short observation"],
 "actions": [{"type": "force_stop", "package": "com.example.app", "risk": "low", "reason": "why"}]}
```

The agent speaks only the Anthropic Messages format. Another AI works only through an address that accepts the same
format and headers.

What is sent: the Android version, the TV language, memory numbers, process names and sizes, and package names with
their on/off state. Nothing about the network, accounts, Wi-Fi names, IP addresses or what is watched.

## Safety

- **The AI never types a command.** It can only choose three actions, and the code checks each one:
  `disable_package` (only a package from `res/raw/suggested.txt` that is on, never a protected one, at most `--max`
  per run), `force_stop` (a running app that is not protected and not the launcher) and `trim_caches`.
- **Disable, never uninstall.** Every change is `pm disable-user --user 0` and can be undone.
- **Protected packages are never touched**: Android's core packages, Google Play and Play Services, input and tuner
  services, settings, the launcher and this add-on (the list follows the repo's `AGENTS.md`).
- **Confirm or undo.** After Apply, a countdown shows in a box over the screen. If it runs out, or the TV fails its
  health check, everything is put back, by a watchdog script that runs on the TV itself and survives the PC or the
  ADB link going away.
- **Each run is written to `journal/`** with the exact undo command, and to the History on the TV.
- **The question is a box over everything**, so a pending decision cannot be missed.
- **No app on the TV can speak for the PC or for you.** The report receiver is guarded by `android.permission.DUMP`,
  which the ADB shell holds and an installed app cannot be granted, so a fake answer or question cannot be injected.
  The PC reads the owner's answers with `logcat --uid=<add-on>`, so another app logging the same tag is ignored
  (on a TV whose logcat cannot filter by app the agent prints a warning and falls back to the plain read). Every
  exchange id carries a random part, so an answer cannot be guessed from the clock.
- **No countdown without its safety net.** If the TV-side watchdog cannot be started, the packages are put back at
  once and the run ends as `reverted_no_watchdog`.
- **Debugging is closed even after a restart.** The 30-minute alarm does not survive a restart; the add-on also
  remembers when its window ends and turns debugging off the next time it is on screen.
- **A bad AI reply cannot break the agent.** The reply must be one JSON object with lists of objects; anything else
  is reported as an error on the TV, and `--watch` carries on after a failed round.

## Permissions

| Permission | Why |
|---|---|
| `ACCESS_NETWORK_STATE` | Shows Ethernet or Wi-Fi. |
| `QUERY_ALL_PACKAGES` | Tells which flagged and suggested apps are installed and on. |
| `WRITE_SECURE_SETTINGS` | Lets the USB debugging button turn debugging on and off. It cannot be granted from the TV; it is granted once from the PC: `adb shell pm grant com.mauro.tv.monitor android.permission.WRITE_SECURE_SETTINGS`. It lets the add-on change protected system settings, so install the add-on only from a source you trust. |

| `RECEIVE_BOOT_COMPLETED` | Start at boot: after a restart it closes a USB debugging window that has run out, or sets the timer again for the time left. It does nothing else. |

There is no `INTERNET` permission: the add-on never talks to the network. The report receiver only stores text that
parses as JSON; nothing in it is executed, and only the ADB shell can reach it (see Safety).

What is still true after the review: the AI sees process and package names; the PC that runs the agent is trusted
(it holds the key and the ADB authorisation); and `--setup --lan` sends the key over plain HTTP on your network.

## Build

JDK 17 and Android SDK platform 34.

```
gradlew.bat assembleRelease      (Command Prompt; ./gradlew assembleRelease on macOS and Linux)
```

The APK is `app/build/outputs/apk/release/app-release.apk`. Install it with `adb install -r`.

The release build is signed with your machine's Android debug key (the repo's launcher does the same, so people
build from source). Whoever holds that key can install an update over this add-on and inherit its
`WRITE_SECURE_SETTINGS` grant, so keep `~/.android/debug.keystore` to yourself, and use a keystore of your own if you
hand the APK to anyone.

On Linux and macOS the saved API key is not encrypted: it is base64 in a file only your user can read
(`chmod 600`). Prefer `ANTHROPIC_API_KEY` there.

## Tested on

Philips 58PUS8555/12 (Android TV 12, firmware STT2.230526.001). Not tried on other TVs.

## Compatibility

`minSdk 26`, `targetSdk 34`, plain Java, no native code.

| Android | Status |
|---|---|
| 12 (Philips 58PUS8555/12) | Tried on the TV: all of it. |
| 16 | Tried in an emulator: start, main screen, History, the guide, the USB debugging button; no crash in the log. |
| 8 to 11, 13 to 15 | Not tried. By reading the code nothing should break: no removed APIs, `exported` is declared, `PendingIntent` is immutable, the alarm is inexact (no `SCHEDULE_EXACT_ALARM`), no foreground service, no notification. |

Network ADB on port 5555 depends on the maker's "Network debugging" option. On other TVs with Android 11 or later you
may need Wireless debugging with pairing; the PC agent would then need adapting.

## Not verified yet

- A real call to the AI with a real key.
- The countdown box after Apply when the AI proposes to disable a package (the test reply did not).
- The Details button served by `--watch`.
- A real `disable_package` through the AI flow.
- The 25-second "no PC answered" hint and the Developer options fallback.
- Android 8 to 11 and 13 to 15.
- On the TV, after the security review: that `am broadcast` from the ADB shell still reaches the receiver guarded by
  `DUMP`, that `logcat --uid` filters on this TV, and the watchdog start check. Reasoned from the Android sources.
- USB debugging window after a restart: tried on the TV, the boot receiver sets the timer again for the time left.
  Not tried: a restart after the window has already run out (the receiver then turns debugging off at boot).

## Files

| Path | What it is |
|---|---|
| `app/` | The Android add-on. Strings are in `res/values*/strings.xml`; the lists are `res/raw/flagged.txt` and `suggested.txt`. |
| `tv_ai_agent.py` | The PC agent: setup page, snapshot, AI call, guard, apply, watchdog, restart. |
| `tv_monitor_pc.py` | A read-only per-process monitor drawn on the PC (`top` over ADB). |
| `mock-reply.json`, `mock-safe.json` | Saved AI replies for tests with `--mock`. |
| `journal/` | One JSON file per AI run, with the undo command. |
