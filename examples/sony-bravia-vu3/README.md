# Sony BRAVIA VU3 (Google TV 14, Realtek rtd2885m)

Android product `BRAVIA_VU3` / `BRAVIA_VU3_4K`, build `UKR1.240726.001`. The list in `debloat.txt` came from a
KD-55X75K. A second unit on the same product and build, on Wi-Fi in India, was set up on 25 October 2026 and is
what the notes below are about: its remote's app buttons.

| File | What it is |
|---|---|
| `debloat.txt` | The tested list; it disables launcherx last, after the new launcher owns Home |

**How much testing is behind it:** one session on the second unit. The list's packages were disabled in three
batches plus setupwraith, and the new launcher was installed. After one reboot with the final setup, the owner
checked Home, the four app buttons and the volume ramp on the real remote.

## The remote's app buttons need launcherx

With launcherx disabled as the list does, the Netflix, YouTube, Prime Video and YouTube Music buttons on the
Bluetooth remote (`SONY TV VRC 001`) do nothing. They are global keys routed to Realtek's settings app:

| Button | Scancode | Keycode | Global key receiver |
|---|---|---|---|
| Netflix | 758 | `BUTTON_4` | `com.realtek.advancesetting/.GlobalKeyReceiver` |
| YouTube | 569 | `BUTTON_14` | same |
| Prime Video | 652 | `BUTTON_2` | same |
| YouTube Music | 653 | `BUTTON_15` | same |

`dumpsys window policy | grep -A30 GlobalKey` shows the table. The receiver logs `handlerGlobalkey` and
`Starting packageName = ...` for each press, then starts nothing. Its `Utils.startApp` checks the
`com.google.android.feature.AMATI_EXPERIENCE` system feature, which this Google TV has, and then hands the launch
to launcherx with a `com.google.global_button.ACTION_LAUNCH_APP` broadcast to
`launcherx/.globalbutton.GlobalButtonLaunch_Receiver`. It sends that broadcast only when the receiver can be
resolved, so with launcherx disabled the press is dropped and nothing is logged as failing.

Answering the buttons in the new launcher instead needs a key-filtering helper. `app_keys` with scancodes 758, 569,
652 and 653 and `INPUT_KEY_SERVICE=1` opened all four apps, but held Volume then moved one step per press. On this
set the volume key never reaches the helper (a log line in `VolumeKeys.handle` saw the Netflix button and not
Volume), so `consume_volume_keys` cannot repeat it either.

## Install with launcherx kept

Keep launcherx enabled and let `HomeKeyService` send its home screen on to the new launcher, with key filtering
off. The buttons then go through launcherx as they did before the cleanup, and the volume ramp stays.

1. Apply the list without its last line (launcherx), or re-enable launcherx afterwards:
   `TV_HOST=<tv-ip> tools/tv.py 'pm enable --user 0 com.google.android.apps.tv.launcherx'`.
2. Install the launcher without disabling the stock one:
   `TV_HOST=<tv-ip> STOCK_LAUNCHER=none RESTORE_KEY_SERVICES=1 launcher/build-and-install.sh`.
   Its Home check then reports launcherx and exits 1, which is expected with launcherx kept.
3. Before step 2, set these in `launcher/app/src/main/res/values/config.xml`: `stock_home_redirect` to
   `com.google.android.apps.tv.launcherx`, `key_filter` to false and `restore_key_services` to true.
4. Enable the helper:
   `tools/tv.py 'settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService'` and
   `tools/tv.py 'settings put secure accessibility_enabled 1'`.

Home from inside an app lands on `com.gathin.tvhome/.MainActivity` (`mFocusedApp`); Google TV's home flashes for a
moment first. After a reboot the TV started into the new launcher. Do not force-stop the launcher from Settings:
that unbinds the helper until Home next starts, and `restore_key_services` puts it back then.

## Other notes from the second unit

- Live TV is `com.android.tv`; the inputs are `com.realtek.tv.passthrough`, and the Input button goes to
  `com.sony.dtv.smartui`, which stays enabled.
- The display had an override size of 1920x1080 on a 3840x2160 panel before anything changed; leave it.
- Network debugging was only offered as Wireless debugging. It switched itself off at every reboot, and port
  5555 opened only with USB debugging on.
- setupwraith did not own the Wi-Fi network (`cname=android.uid.system`), so disabling it kept the TV online.
- Disabling setupwraith costs phone casting after the next reboot, as the list's comment says.
