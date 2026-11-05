# Hisense U75 / 75U75N / 65U7ESG (hengshan), Google TV 12

Notes for the `hengshan` board, firmware `STT2.230310.002`. The first list came from a Latin America U75; the
75U75N notes are from a US unit (one owner, from 2026-10-22); a 55U7N report of 2026-10-24 (#1326) confirms the
board, the build and the volume trade below. A 65U7ESG from Colombia (2026-10-25) has its own list,
`debloat-65u7esg.txt`, and [a way to keep both the app buttons and the volume ramp](#65u7esg-the-app-buttons-and-the-volume-ramp-together).

## Live TV and inputs

The Live TV app is `com.vt.tv.tvcenter`, and it also plays the HDMI inputs: the launcher's Inputs menu lists
HDMI 1 to 4, AV and Live TV, and an HDMI row opens `com.vt.tv.tvcenter/.LiveTVMainActivity` on that port. HDMI and
Live TV give empty screenshots, as on most sets. `config.json` here sets it as `live_tv_package` and hides its tile.

## Display

`wm size` reports a 3840x2160 panel with a 1920x1080 override, and density 320: that is the firmware's own UI
scale, not a leftover experiment. Leave it.

## The remote

The 75U75N remote ("SmartRC") pairs over Bluetooth and falls back to infrared: with Bluetooth off its key presses
come from the TV's IR receiver and the D-pad keeps working. Its buttons, read with `getevent -lt` and `logcat`:

| Button | Keycode | Without a key helper, once launcherx is disabled |
|---|---|---|
| Netflix | not captured | works |
| Input | not captured | works |
| Gear | 83 (`KEY_NOTIFICATION`, scancode 598) | nothing: a global key for launcherx |
| YouTube | 190 (`KEY_FN_F4`) | nothing: a global key for launcherx |
| Prime Video | 295 (`KEY_FN_F3`) | nothing |
| Disney+ | 607 (`KEY_ZOOMIN`) | nothing |
| Tubi | 606 (`KEY_ZOOMOUT`) | nothing |

For Prime Video, Disney+ and Tubi the window policy consumes the key and `com.vt.tv.smartsense` asks launcherx
to open the app with `com.google.global_button.ACTION_LAUNCH_APP`, addressed to launcherx, so no other app can
answer it. `config.json` maps all five for `HomeKeyService` (`HOME_KEY_SERVICE=1`): the gear opens the quick
settings panel and the app buttons their apps.

That costs the volume ramp. With the helper bound, holding volume steps once per press. With a CEC soundbar
the 75U75N's log shows why: the accessibility filter hands volume keys to the window policy, which sends the CEC
step and consumes them, so nothing repeats. The soundbar is what that unit's log explains, not what the loss
needs: a 55U7N on the same board and build steps once per press with `HomeKeyService` alone and no soundbar in the
report (#1326), and its log repeats the app-button rows above (`GlobalKeyReceiver` receives 295, 607 and 606 with
launcherx disabled and nothing opens). Its held-key capture was not taken, so the CEC step is unproven there. The
log and the choices are in
[the key-repeat note](../../docs/devices/tcl-55p71b-g09-key-repeat.md). Re-enabling launcherx to answer the
buttons takes Home back on this firmware. The 75U75N owner kept the ramp and runs without the helper; the 65U7ESG
keeps both, with launcherx on behind the launcher ([below](#65u7esg-the-app-buttons-and-the-volume-ramp-together)).

## Sound through a soundbar

With a soundbar on HDMI ARC/eARC and HDMI-CEC volume control on, the TV forwards volume presses to the
soundbar, and its volume overlay shows only + and −. The soundbar reports its level back over CEC
(`<Report Audio Status>` in `dumpsys hdmi_control`), but this firmware does not show a number for it.

## 65U7ESG: the app buttons and the volume ramp together

A fourth unit: a Hisense 65U7ESG from Colombia, an ex-display set, on the same device and build (incremental
`00.00.00.135`, security patch 2025-05-01). It applied `debloat-65u7esg.txt` on 2026-10-25. Its SmartRC remote sends
the codes in the table above and fills its two gaps: Netflix is 265 (`KEY_FN_F5`, scancode 470), answered by
`com.mstar.netflixobserver` whether launcherx is on or off, and Input opens Hisense's own input menu with no helper.

The volume trade measured the same on TV speakers, with no soundbar: holding Volume Up for about 1.5 s gave 9 steps
without a helper and 1 with `HomeKeyService` bound, read from `dumpsys audio | grep adjustSuggestedStreamVolume`,
where every step comes `from android/WindowManager`. `consume_volume_keys` cannot help here: the window policy takes
the volume key inside the accessibility filter, so the helper never receives it.

What keeps both is leaving launcherx enabled and letting the helper send its home screen on, without filtering keys:

1. Keep setupwraith disabled and enable launcherx again: `pm enable --user 0 com.google.android.apps.tv.launcherx`.
2. Set these two keys in the theme (or `config.xml`):
   ```json
   { "key_filter": false, "stock_home_redirect": "com.google.android.apps.tv.launcherx" }
   ```
3. Install the launcher with `STOCK_LAUNCHER=none HOME_KEY_SERVICE=1 launcher/build-and-install.sh`, so the script
   leaves launcherx on and binds the helper.

launcherx then answers YouTube, Prime Video, Disney+ and the gear (its own dashboard) again, Netflix keeps its own
receiver, and a held volume key ramps (10 steps in about 1.5 s with the helper bound). A Home press shows the Google
TV home for about 0.4 s before this launcher; at boot it is about 5 s, until the helper's boot probe. After a
standby (Hisense's `STR_SUSPEND`) the launcher and the helper were still up. It costs what launcherx costs: about
300 MB of RAM and its background services. Run `tools/check.py` and `tools/restore.sh` with launcherx as the second
argument, since the list leaves it out on purpose.

Other notes from that unit:

- Colombia's digital TV is DVB-T2, so `tv.mopa.ginga` (ISDB-Tb) and `com.vt.tv.interactive`
  (`.atsc.InteractMainService`) do nothing there; an antenna channel and HDMI 1 played after both were disabled.
- `net.vidaatv.hisense.android.tv` and `com.vidaa.oemtabcontentprovider` are not installed on it.
- setupwraith owned the Wi-Fi network saved during setup. The set is on its built-in Ethernet (`eth0`,
  `mt5896-gmac`), so the list went on with `FORCE_SETUPWRAITH=1` and the saved network was dropped. setupwraith
  also holds a `CastInfoProvider` here; casting is not used on this unit, so what disabling it does to casting was
  not tested.
- AirPlay lives in `com.mediatek.AirplayAPK`, `com.mediatek.airplaydaemon` and `com.mediatek.partner.airplay_sys`.
  Switched off in Settings > System > AirPlay and HomeKit (`persist.sys.vt.tv.airplay.enable=0`), the set stops
  advertising `_airplay`, but the two AirPlay processes still hold about 167 MB and it still advertises `_hap`
  (HomeKit).
- Private DNS works on this set: with `dns.adguard-dns.com`, HBO Max, YouTube, Netflix, Disney+ and Prime Video
  played.
- Store mode was already off on this ex-display unit (`persist.sys.vt.storemode=false`); disabling
  `com.vt.storemode` changed nothing visible.
