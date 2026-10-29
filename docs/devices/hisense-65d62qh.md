# Hisense 65D62QH (China, JuUI 9): debloat, and what a launcher swap costs

Owner report for a China-market Hisense 65D62QH running 聚好看 JuUI 9. The tested package list for
this unit lives in [`examples/hisense-65d62qh/debloat.txt`](../../examples/hisense-65d62qh/debloat.txt)
and its launcher config in
[`examples/hisense-65d62qh/config.json`](../../examples/hisense-65d62qh/config.json). **That list is
debloat only: it keeps the stock Home `com.jamdeo.tv.vod` enabled.** The launcher handover further
down was tried on this same set as a separate experiment and is *not* part of the list — it is here
because the two failures it runs into are firmware behaviour no package list can work around.

## Observed firmware

| Field | Observed value |
|---|---|
| Owner-reported retail model | Hisense 65D62QH (65", 4K, Dolby Atmos / dts:X, 200 W) |
| Manufacturer | `Hisense` |
| Android model | `VIDAA_TV` |
| Device | `MT9653` |
| Android / API | `11` / `30` |
| Build display id | `RP1A.200720.011 release-keys` |
| UI version | JuUI 9.0.10.845 |
| Application ABIs | `arm64-v8a, armeabi-v7a, armeabi` |
| `ro.adb.secure` | `0` |
| `service.adb.tcp.port` | `5555` |
| Installed packages | 131 |
| Disabled at the factory | none (`pm list packages -d` was empty) |

Same platform and build as [`examples/vidda-mt9653/`](../../examples/vidda-mt9653/debloat.txt) and
[`examples/hisense-e5q-pro/`](../../examples/hisense-e5q-pro/debloat.txt) — device `MT9653`, build
`RP1A.200720.011`, package namespace `com.jamdeo.*` / `com.hisense.*`. No GMS and no Play Store; the
account and store layer is JuUI (`com.hisense.hitv.*`, `com.jamdeo.tv.vod`).

Turning USB debugging on opens port 5555 directly. **`ro.adb.secure=0` means the TV never shows the
"Allow USB debugging?" prompt: any device on the LAN gets a shell.** Turn debugging off when the work
is done and confirm the port refuses connections (`tools/adb-closed.py <tv ip>`).

### ADB access on this unit

- The **USB debugging switch also opens TCP port 5555**; there is no separate network-debugging toggle
  to rely on. Turning that switch off closes 5555 and USB ADB at the same time, so it is the one switch
  to leave alone while working on the TV.
- The USB connector itself does **not** enumerate on a PC: with USB debugging on and a known-good data
  cable, `adb devices` stays empty and Windows shows no Android interface. Every session on this unit
  ran over Wi-Fi.
- The modern **Wireless debugging (pairing code)** page is present and `adb pair` succeeds, but its
  connect port changes on every toggle and reboot, and it does not expose 5555 by itself. Use
  `adb mdns services` to read the live port instead of copying it off the screen.
- `adb tcpip 5555` after a pairing-code session is the quickest way back to the plain 5555 port.

### A USB mouse froze input once, with a second accessibility service running

On one setup of this set, plugging in a USB HID device froze **all** input: video kept playing, but
the mouse, the keyboard and the remote stopped responding, and the screen went dark on the screen
timeout a while later. Only a mains power cycle (unplug for ~30 s, not standby) brought input back.
Bluetooth HID did the same, so it ran deeper than the USB port.

It is not a property of this TV: on the factory system a mouse works, USB debugging on included. After
a factory reset the same setup was rebuilt and **three cold boots, a mouse plugged in each time,
froze nothing** — launcher focused, 28 packages disabled, Home up at ~37 s every round.

The one thing that differed on the failing setup is visible in `logcat`: the vendor's accessibility
service was also enabled then —

```text
I HisXiaoJu: initAccessibilityService(), services = com.hisense.vidaaassistant/…AppAccessibilityService : com.gathin.tvhome/.HomeKeyService
```

Two services filtering key events at once is the failure mode this repo records for other firmware.
So on this set, keep `enabled_accessibility_services` to the one service that is needed, and keep
`key_filter` off in the theme. If a freeze appears, check that list first. Until it is understood,
a phone remote app (it goes over the network, not through the input stack) is the safer way to type or
click on this TV, and the USB port is best left empty.

## Stock Home, and the swap that was tried

**Not part of the list.** Everything below is what happened on this same set when Home was replaced
by hand, recorded so the next owner knows what they are taking on before they disable it.

```text
com.jamdeo.tv.vod/com.ju.main.launcher.LauncherActivity
```

`com.jamdeo.tv.vod` (聚好看) is the stock launcher and it also renders the sponsored rows and the VIP
banner on Home — those are fetched by the launcher itself, not by the ad sidecars a debloat list
disables, so only replacing the launcher removes them. That is the whole reason anyone would try.

The install scripts do not recognize `com.jamdeo.tv.vod` automatically, but accept an explicit stock
launcher (`-StockLauncher` on Windows or `STOCK_LAUNCHER` on Unix). The commands below were run
manually because this TV also requires disabling `com.keylab.speech.view.vidaa` separately; the
scripts do not know about that vendor package:

```powershell
tools\venv\Scripts\python.exe -I tools\tv.py 'cmd package set-home-activity com.gathin.tvhome/.MainActivity'
tools\venv\Scripts\python.exe -I tools\tv.py 'pm disable-user --user 0 com.jamdeo.tv.vod'
tools\venv\Scripts\python.exe -I tools\tv.py 'pm disable-user --user 0 com.keylab.speech.view.vidaa'
```

`com.keylab.speech.view.vidaa` has to go with it: it starts `com.ju.secondscreen.HomeActivity` at boot
and that takes the foreground on top of the new launcher. Component-level disable is refused on this
firmware (`SecurityException: Shell cannot change component state ... to 2`), so it can only be
disabled as a whole package, which also removes the voice assistant UI and the remote's mic key.

Enabling `com.jamdeo.tv.vod` again makes the TV reclaim Home even after an explicit
`set-home-activity`: disable it first, then set Home.

### Boot behavior with the stock launcher disabled

With `com.jamdeo.tv.vod` disabled, **nothing starts Home at boot**. Measured on the set, 99 s after
boot:

```text
mWakefulness=Awake
Display Power: state=ON
mCurrentFocus=null
mFocusedApp=null
$ ps -A | grep gathin        (no output — the launcher process does not exist)
```

The power menu still drew, so the display and SystemUI were fine; there was simply no window. The TV
sat there until the remote woke it, which made the system resolve HOME again.

This firmware starts its launcher by component name rather than asking the system for the HOME
activity, and it uses its own boot broadcast:

```text
I hiservice: <BootReceiver> ... onReceive:android.intent.action.HISENSE_BOOT_COMPLETED
I hiservice: <BootReceiver> default vision -->-100, vidaa-->1, googletv-->-100
```

What did **not** work:

- A `BOOT_COMPLETED` receiver that calls `startActivity`. Both `BOOT_COMPLETED` and
  `LOCKED_BOOT_COMPLETED` were sent (visible in `logcat -b events`) and the receiver was registered in
  the manifest with `RECEIVE_BOOT_COMPLETED granted=true`, but the launch was silently dropped: not a
  single line of the receiver's own logging appeared. `BOOT_COMPLETED` is not on Android's
  background-activity-launch exemption list.
- `appops set com.gathin.tvhome SYSTEM_ALERT_WINDOW allow`: refused on this firmware (the op stays
  `default`, and its `rejectTime` records the refused background launch).

A local launcher build with an experimental `boot_start_home` change was tested separately, but that
change is not included in the upstream launcher represented by this TV-list submission. The upstream
build does not act on that setting. With an unmodified build, keep `com.jamdeo.tv.vod` enabled unless
you accept that Home will not start automatically and the screen may remain blank until you press the
remote.

Consequences worth knowing before doing this: the remote's **Home button stops working** (the physical
key is Linux scancode 102, which this keylayout maps to `KEYCODE_F12` for the vendor's SysUIMerge app
to switch to the previous app — the stock launcher, until it was disabled), and **voice is gone** with
`com.keylab.speech.view.vidaa`. Both come back by re-enabling those two packages:

```powershell
tools\venv\Scripts\python.exe -I tools\tv.py 'pm enable com.keylab.speech.view.vidaa'
tools\venv\Scripts\python.exe -I tools\tv.py 'cmd package set-home-activity com.jamdeo.tv.vod/com.ju.main.launcher.LauncherActivity'
tools\venv\Scripts\python.exe -I tools\tv.py 'pm enable com.jamdeo.tv.vod'
```

## Inputs

`dumpsys tv_input` is completely empty on this firmware: no `TvInputManager` inputs at all, so the
launcher's Inputs pill has nothing to list. It uses the vendor source panel instead — see
[`config.json`](../../examples/hisense-65d62qh/config.json):

```json
"source_panel_service": "com.hisense.tv.inputs/com.hisense.tv.InputsUIService",
"source_panel_action": "com.hisense.tv.start_inputs"
```

The panel performs the switch itself. Keep `com.hisense.tv.tiletransition` enabled: its name reads
like a boot-ad layer, but it hosts `TileAnimationService`, which the panel binds before switching —
disabled, the panel still opens and animates but picking HDMI does nothing.

## 魔镜 (`com.hisense.tv.mirror`)

The stock Home hides this one; the reference launcher lists every launchable app, so it shows up as a
tile with a mirror icon (`com.hisense.tv.mirror/.PreviewActivity`). It is the screen-mirroring
receiver, so it is hidden from Home rather than disabled (`hidden_apps` in `config.json`); disabling it
costs casting from a phone.

## Firmware quirks

- `com.android.calendar`, `com.android.deskclock` and `com.android.music` are re-enabled at every boot.
  Disabled, rebooted, and `pm list packages -d` no longer listed them. They are left out of the list
  instead of being disabled again after each boot: they do not sit in the background and the gain is a
  clock, a calendar and a music app nobody launches.
- `com.hisense.tv.baseservice` throws once at boot
  (`RecentService`: `IndexOutOfBoundsException: Index: 0, Size: 0`) because the recents list is empty
  with the stock launcher disabled. One occurrence, not a loop, and it did not hold Home up — the
  blank screen had a different cause, above.
- `install_black_list` in `settings global` blocks `com.dangbeimarket` (当贝市场) and
  `com.dangbei.tvlauncher` (当贝桌面) on this firmware, along with a long list of IPTV apps.
- No app store is preinstalled at all: no `com.android.vending`, no vendor store package. Apps go on
  over ADB or from a USB stick.
