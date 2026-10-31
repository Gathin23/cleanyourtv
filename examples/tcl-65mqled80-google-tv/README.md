# TCL 65MQLED80 Google TV

Cleaned up on 30 October 2026. Board G08 (MediaTek tcl9618), Android 14, firmware UTT2.250416.001 AU02, the
same build as the [65C7L](../tcl-65c7l-google-tv/README.md) and [C755](../tcl-c755-google-tv/debloat.txt)
lists. Bought in Norway, on built-in Ethernet. Replace 192.168.1.50 below with your TV's IP address.

What changed:

- **32 packages disabled**, none uninstalled. They are listed with reasons in [debloat.txt](debloat.txt):
  TCL tracking (Samba TV, TCL analytics, logs), TCL promos and FAST channels, the retail demo, TCL's cleaner,
  the partner-app auto-installer and Alexa, plus what this owner does not use: Google Assistant, Chromecast,
  Miracast and T-Cast, the USB media player and gallery, YouTube Music, the Google TV store app and Rakuten TV.
- **Google TV home replaced** by the launcher from `launcher/`: a frosted-glass dock of YouTube, Netflix,
  HBO Max, Prime Video and Disney+, five columns, and the launcher's clock as the screensaver.

Checked on the TV afterwards: all four HDMI inputs, AV and the tuner stay registered, Live TV opens from the
Inputs menu, YouTube loads with AdGuard Private DNS, power-on lands on the new Home, and all 32 packages stayed
disabled across two reboots. One session so far, not yet lived with for days.

## Differences from the 65C7L

- **Four remote buttons need the key helper.** With launcherx disabled, the gear (keycode 83), YouTube (190),
  Netflix (4062) and Prime Video (4099) buttons did nothing on this unit, while on the 65C7L every button kept
  working. Input and volume were unaffected. [config.json](config.json) maps all four again, read by the
  launcher's HomeKeyService.
- **The helper costs the volume ramp.** With HomeKeyService bound, a held Volume Up moved about two steps
  instead of ramping (here it goes over CEC to a soundbar). This owner kept the buttons. The background is in
  `docs/devices/tcl-55p71b-g09-key-repeat.md`.
- **setupwraith owned the saved Wi-Fi network even though the TV runs on Ethernet.** Disabling it deletes that
  network. On Ethernet that is harmless (`FORCE_SETUPWRAITH=1`); on Wi-Fi, forget the network and join it again
  in Settings before applying the list.
- `com.tcl.hotelmenu`, in the 65C7L list, is not installed on this model.

## Apply the list and install the launcher

1. Apply the list. It skips the stock home (launcherx), which still owns Home, and the setup wizard while it
   owns the Wi-Fi network:

   ```sh
   TV_HOST=192.168.1.50 tools/disable.sh examples/tcl-65mqled80-google-tv/debloat.txt
   ```

2. Build the launcher and make it Home with the key helper; this also disables launcherx. It needs JDK 17 and
   Android SDK 34 (see the main README):

   ```sh
   TV_HOST=192.168.1.50 HOME_KEY_SERVICE=1 launcher/build-and-install.sh
   ```

3. Push this folder's [config.json](config.json) (dock, columns, glass dock, Live TV app, remote buttons):

   ```sh
   TV_HOST=192.168.1.50 tools/theme.sh examples/tcl-65mqled80-google-tv
   ```

4. Switch the screensaver to the launcher's clock before Google's promo screensaver (dreamx) goes, then run
   step 1 again to disable dreamx and launcherx:

   ```sh
   TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/tv.py 'settings put secure screensaver_components com.gathin.tvhome/.ClockDream'
   ```

Then press the gear, YouTube, Netflix and Prime Video buttons on the real remote: an injected key does not
prove the helper works.

## Undo everything

```sh
TV_HOST=192.168.1.50 tools/restore.sh examples/tcl-65mqled80-google-tv/debloat.txt com.google.android.apps.tv.launcherx
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/tv.py 'settings delete secure screensaver_components'
```

The first line turns every disabled app back on and makes the Google TV home Home again. The second puts
Google's screensaver back.
