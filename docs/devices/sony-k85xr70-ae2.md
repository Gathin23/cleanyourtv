# Sony K-85XR70 (`BRAVIA 4K AE2`, China): a 1080p UI, and a density that comes back as 640

Two reports from the same owner of the same set: the launcher compatibility report in
[#29](https://github.com/Gathin23/cleanyourtv/issues/29) (closed) and the display-density report in
[#590](https://github.com/Gathin23/cleanyourtv/issues/590). There is no debloat list here and this is
not an `examples/` entry - it is the display half of the picture, which is worth its own note because
the symptom (an enlarged, clipped UI) reads like a broken launcher grid.

## Identify the firmware

| Field | Reported value |
|---|---|
| Retail model | Sony K-85XR70 |
| Android model | `BRAVIA 4K AE2` / `BRAVIA_AE2` |
| Android / SDK | Android 14 / 34, China firmware, no GMS |
| Build id | `114.602.170.1` (China/CNA), from [#29](https://github.com/Gathin23/cleanyourtv/issues/29) |
| Panel | 3840×2160, reported physical density 320 |
| UI resolution | 1920×1080 |
| Stock HOME | `com.dangbei.TVHomeLauncher/com.dangbei.leard.leradlauncher.ui.sony.main.MainActivity` |
| Live TV package | `com.sony.dtv.tvlin` |

## The 1080p UI is the firmware, not a setting you broke

From [#29](https://github.com/Gathin23/cleanyourtv/issues/29): the panel is 4K but the Android UI runs
at 1920×1080, and asking for a 4K UI changed nothing. The firmware says so in its own properties:
`ro.surface_flinger.max_graphics_width=1920`, `ro.surface_flinger.max_graphics_height=1080` and
`ro.vendor.mtk.ui.resolution=2K`. That is the normal state of this model, so a 1080p UI is not
evidence of damage and not something to "fix" on a set you don't own. Video playback and HDMI input
are separate paths and #29 makes no claim about them.

The [#590](https://github.com/Gathin23/cleanyourtv/issues/590) follow-up is a different problem in the
same place: the logical size stayed 1920×1080 and the runtime density went to 640, **twice the
intended size**.

## What #590 measured

Three confirmed boots. The owner had a 1920×1080 size override on the TV before this began (from
earlier resolution experiments, per the report; who wrote it was never established) and a working
density of 320.

| Boot | `wm size` | `wm density` | `settings get secure display_density_forced` |
|---|---|---|---|
| Working session | Override size 1920×1080 | Override density 320 | empty |
| After reboot, twice | Override size 1920×1080 | **Override density 640** | empty |
| After the workaround below | Override size 1920×1080 | Override density 320 | 320 |

So the saved density was empty in all three boots, the good one included. It is not a value the owner
had set and lost: the same empty setting produced 320 in a session and 640 after a reboot, which is
the mismatch #590 reports. Something in the boot path picks 640 and the report could not establish
what.

`wm density reset` brought back a usable 320 session, and the next boot returned to 640. Running
`wm density 320` alone did the same: on this firmware it leaves the saved density empty, so nothing
carries over. This is the part that costs an hour - the first command looks like it fixed the TV
until you reboot.

## The workaround, and how to check it

Both commands, in this order, then a real reboot:

```sh
tools/tv.py 'wm density 320'
tools/tv.py 'settings --user 0 put secure display_density_forced 320'
tools/tv.py --reboot               # returns once ADB accepts it; wait for the TV to come back
tools/tv.py 'wm size; wm density; settings --user 0 get secure display_density_forced'
```

After the reboot the owner read back 1920×1080, density 320 and a saved 320, and HOME, the stock
launcher's enabled state, the disabled-package set, the first-install time and the animation scales
all matched the pre-boot baseline. Nothing was cleared, and no launcher crash appeared in the crash
buffer.

Two cautions that matter more than the fix:

- **Record the three values before you change anything**, because an empty saved density is a valid
  Android default and the original state here *was* empty. Undo with `wm density reset` and
  `settings delete secure display_density_forced`.
- **Do not copy 320 to another TV.** It is the value this panel wants at 1080p. A different panel or
  a different size override needs its own number, and a launcher should never force a global density.

## What is not established

- The exact vendor framework branch that picks 640 when the saved value is empty. Generic AOSP code
  does not explain it, and `DisplayDeviceRepository` still reports native 3840×2160 at 640 dpi
  *after* the successful workaround - so native 640 alone is not proof of a broken driver.
- A factory-default reproduction: the 1080p size override was already there when the report started.
- Stability: two failing boots and one good boot after the workaround, no long standby and no
  repeated cycles.
- Wireless debugging needed a fresh connection port after each reboot; the pairing itself survived.

`ro.sf.lcd_density=320`, `vendor.mtk.sf.lcd.density=320`, `ro.config.density_override` empty and
`config_maxUiWidth=3840` were also read, which is why the working value is 320 rather than 640.

## Launcher compatibility (from #29)

Kept here because it is the same set: the dangbei launcher above is the stock Home, the project's
launcher could be selected as HOME with the stock one still enabled, it ran without Google Play
services at all, and the legacy Python connection path did not establish a session in that setup
while official `adb` over Wireless debugging pairing did. Those are that TV's results, not a claim
about Android 11+ in general.

The diagnostic and the commands also live in
[docs/TROUBLESHOOTING.md](../TROUBLESHOOTING.md), because the symptom turns up after a reboot with no
report to compare against.
