# Haier MatrixTV DE (HR9676EUF, omonia): a Google TV 14 same-day note

Single-session note from one set, tested on 31 October 2026 with the real remote. This is **not** a `debloat.txt`: the
CONTRIBUTING bar of a few days lived with is not met yet. The board is the one in
[AQUA MatrixTV EE 50](../../examples/aqua-matrixtv-ee-50/), and that list applied almost unchanged.

| | |
|---|---|
| **TV** | Haier MatrixTV DE, `HR9676EUF`, device `omonia`, MediaTek, 1.8 GB RAM, 32-bit userspace |
| **OS** | Google TV 14, firmware `UKN2.241219.053`, security patch 2025-01-01 |
| **Display** | 3840x2160 panel, UI at 1920x1080 (`wm size` override, factory), 320 dpi |
| **Worked** | Debloat, the launcher as Home, Open on start, reboots |

## Different from the AQUA list

- `com.mediatek.tv.oneworld.tvcenter` is enabled here and is the Live TV app, with `com.cltv.hybrid` and `com.cltv.mal`
  disabled from the factory. On the AQUA set it is the other way round, so set `live_tv_package` to match.
- `com.haierubic.usmartdevice` is disabled from the factory. `tv.anoki.acr.controller` is present and enabled, and
  disabling it caused no trouble on this set.
- `com.netflix.ninja` comes back enabled at every boot and restarts by itself through a scheduled job, so
  `pm disable-user` does not stick. `cmd appops set com.netflix.ninja RUN_ANY_IN_BACKGROUND ignore` is accepted and
  stopped it for the session; that was not checked across a reboot. `am set-standby-bucket` has no effect, as it is
  on the system power allowlist.

## Connecting

Port 5555 was closed out of the box and Wireless debugging was the way in: `adb pair`, `adb connect`, then
`adb tcpip 5555`. Both 5555 and Wireless debugging are gone after every boot, so each full start needs a new pairing
code from the TV. setupwraith owned one saved network that was not in use; it was forgotten with
`cmd wifi forget-network <id>` before setupwraith was disabled.

## Power and sleep

- The remote's power button is a light sleep: no boot, and ADB can still answer for a few seconds. Open on start does
  not fire, as it is once per boot; `open_on_start_on_wake` covers it.
- `input keyevent KEYCODE_SLEEP` or `26` over ADB took the set off the network within seconds, and it then needed the
  real remote to wake. Do not script a wake over ADB on this set.
- Launcher animations cost more here than anywhere else measured: 49% of frames late with a 57 ms median moving along
  the dock, none late at 12 ms with `animations` off. Wallpaper made almost no difference.
