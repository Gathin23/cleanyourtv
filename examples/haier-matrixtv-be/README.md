# Haier MatrixTV BE (Google TV 14)

One Haier MatrixTV BE on firmware `UKNV.260313.001` was checked from 31 October to
1 November 2026. This was a short trial with one reboot, not a multi-day soak.
The owner reported that Home, streaming, Live TV, HDMI, Wi-Fi and the physical
remote buttons used below were working after the cleanup. The package list and
the CleanYourTV launcher both worked on this set.

| File | Purpose |
|---|---|
| `debloat.txt` | The 9 TV packages confirmed disabled, with choices and restore notes |
| `config.json` | Four dock apps and the two physical remote-button mappings |

The stock Home is `com.google.android.apps.tv.launcherx`; Live TV is
`com.mediatek.wwtv.tvcenter`. The install script disables stock Home only after
the replacement launcher starts. The list does not disable Live TV, HDMI/input
services, remote pairing, Play Store, voice search, Chromecast or firmware updates.
The owner kept TV Housekeeper. HaiCast/Miracast was disabled by owner choice
and is commented out in the list; uncomment it if you never mirror a screen. Chromecast was left enabled,
but casting itself was not separately tested.

## Apply and undo

Run commands from the repository root. First check whether
`com.google.android.tungsten.setupwraith` owns any saved Wi-Fi network: disabling
it may remove that network and cut off ADB. The connected Wi-Fi remained usable
on the tested set. Keep the TV's own recovery path available before changing Home.

```bash
TV_HOST=YOUR_TV_IP tools/disable.sh examples/haier-matrixtv-be/debloat.txt
INPUT_KEY_SERVICE=1 TV_HOST=YOUR_TV_IP launcher/build-and-install.sh
TV_HOST=YOUR_TV_IP tools/theme.sh examples/haier-matrixtv-be
```

The last line pushes the example dock and key mappings. A pushed theme replaces
the previous theme; the owner used a private copy of `themes/scenes` with these
mappings and `dock_glass: true` for the final look.

To restore the recorded package and Home state, turn off CleanYourTV's
InputKeyService under the TV's Accessibility settings, then run:

```bash
TV_HOST=YOUR_TV_IP tools/restore.sh examples/haier-matrixtv-be/debloat.txt com.google.android.apps.tv.launcherx
```

The original stock Home is passed explicitly because it is disabled by
`launcher/build-and-install.sh`, not by `debloat.txt`. The service can otherwise
keep filtering keys even after stock Home returns. The test set's private
recovery record also covers the few personally chosen apps that are not in
this shareable list.

## Physical remote keys

These results came from the real remote and a temporary Android key listener,
which was removed after measurement. The Linux events alone were not enough:
two buttons have the same Android scancode but different Android keycodes.

| Button | Raw event on this set | Android key | Tested result |
|---|---|---|---|
| TV | `MSC_SCAN 000c006f`, Linux `KEY_BRIGHTNESSUP` / scan 225 | Scan 225 in the launcher mapping | Opens `com.mediatek.wwtv.tvcenter` (Live TV) |
| Settings gear | `MSC_SCAN 000c009f`, Linux `KEY_UNKNOWN` | `KEYCODE_NOTIFICATION` (83), scan 240 | Opens CleanYourTV's quick settings panel |
| Three-line button | `MSC_SCAN 000c0071`, Linux `KEY_UNKNOWN` | `KEYCODE_3D_MODE` (206), scan 240 | Distinct from gear; leave unmapped here |

Do **not** map Android scan 240 to Settings or Live TV: both gear and
three-line buttons use it. The working configuration maps keycode 83 to
`com.gathin.tvhome/.QuickSettingsActivity` and Linux scan 225 to Live TV in
`app_keys`. `settings_key_panel: true` is also set. A mapping to the
`settings` action opened full TV Settings instead of the quick panel while
InputKeyService was the active helper.

With InputKeyService filtering keys, holding Volume Up or Down moved volume
only one step on this set. `consume_volume_keys: true` was tried and did not
restore held repeat. The owner chose to keep the working TV and Settings
shortcuts and press Volume repeatedly. Turning the key helper off restores
held repeat but removes these two shortcuts.

The on-screen Home Settings chip was separately fixed in the owner's local
launcher build to honor `settings_key_panel`. That one-line launcher fix is
not in this TV-list folder; on the unmodified upstream launcher, the chip
still opens full TV Settings. The physical gear mapping above works with
the upstream launcher and the example config.

## Other observations

- The new Home remained selected after one reboot. Haier TV+ /
  `com.haier.launcher.haiermatrix` was re-enabled by the firmware during that
  reboot and was disabled again at the owner's request. It may need rechecking
  after later reboots; it is not Google TV's stock Home.
- The launcher opened YouTube and Netflix from the dock, HDMI 1 and Live TV
  from Inputs, and Settings and Wi-Fi from its top bar.
- `com.release.aryzaptv` was disabled in the initial run but appeared enabled
  in a later check, so its persistence is unknown and it is excluded here.
  A user-installed game was also disabled by personal choice and excluded.
- TalkBack was already disabled before cleanup and is not in this list.