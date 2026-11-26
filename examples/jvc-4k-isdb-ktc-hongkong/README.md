# JVC 4K ISDB TV (KTC `hongkong`, Android TV 11): Google TV kept, the launcher on the Home button

KTC-built JVC, product `KTC_JVC_4K_ISDB_22GV1`, device `hongkong`, MediaTek m7632, Android TV 11 (SDK 30) with the
Google TV launcher, build `RTM7.230903.140`, ISDB-T tuner. 32-bit userspace (`armeabi-v7a`), about 1.8 GB of RAM.
Done over network ADB on 2026-11-13 on one set, with one reboot afterwards, then used every day until
2026-11-24 (11 days) with no problems found. Same board as
[the Philips 65PQT8169 list](../philips-65pqt8169/debloat.txt), which disables launcherx and setupwraith; this set
keeps both.

## Connect

There is no Wireless debugging here: Developer options > USB debugging alone opens port 5555 on the network,
and it comes back after a reboot while the toggle stays on. The TV asks once to allow the computer's key.
When you are done: revoke the USB debugging authorisations, switch USB debugging and Developer options off,
then check with `tools/adb-closed.py <tv ip>`.

## The list

[`debloat.txt`](debloat.txt) disables 3 packages: Play Games, Play Movies and the Google ambient-mode
screensaver. Before disabling ambient mode, pick another screensaver, or the TV is left without the one that
is selected: the basic `Colors` one works, and so does the launcher's clock (below).

The Assistant (`com.google.android.katniss`) and TalkBack were disabled too, and this firmware switched both back
on at the next boot. The cause was not found, so they are commented out in the list.

## Launcher: Home redirect, Google TV kept

The owner wanted the antenna, the inputs, casting and the phone remote untouched, so Google TV (launcherx) and
setupwraith stay enabled and Google TV stays the Home that Android resolves. The new launcher takes the Home
button through `HomeKeyService` with `stock_home_redirect`, and `key_filter` off ([`config.json`](config.json)):

1. Build the launcher and install it. This set was done by hand, before #4161 taught
   `launcher/build-and-install.sh` to keep the stock Home with `STOCK_LAUNCHER=none HOME_KEY_SERVICE=1`: the
   APK was built with `./gradlew --no-daemon assembleRelease` in `launcher/` and installed with
   `adb install --user 0`, with no `-g` and no extra permission granted. The script route should work too, but
   it was not run here; it also installs for every user (this set has a dormant second profile), grants
   location and still sends `set-home-activity`, which launcherx outranks on this set.
2. Push the settings: `TV_HOST=<tv ip> tools/theme.sh examples/jvc-4k-isdb-ktc-hongkong`.
3. Turn on the helper: Settings > Accessibility > Home button. That is the only accessibility service enabled;
   `InputKeyService`, the notification listener and `restore_key_services` stay off.

Checked on this set:

- `dumpsys input` reads `InputFilterEnabled: false` with the helper bound, before and after the reboot, so the
  helper does not filter keys, and a held volume key still ramps.
- Opening Google TV's home sends the TV to the new launcher; Home from apps and from Settings opens it too.
- The owner then checked held volume, Settings and Input, the antenna, HDMI and CEC, casting from a phone, the
  phone remote, Bluetooth and playback in the streaming apps, all working, and rebooted once with the same
  result. The set was then used every day for 11 days with nothing breaking, and unplugged from the mains
  several times in that period: after each cold boot it came back with the redirect and everything working.

Not checked on purpose: the case `themes/README.md` describes for `stock_home_redirect` with `key_filter` off,
where Home is pressed while Google TV's home is already in front (it did not come up in daily use).

Screensaver: the owner chose the launcher's `ClockDream`
(`settings put secure screensaver_components com.gathin.tvhome/.ClockDream`). It belongs to the launcher's
package, so choose another screensaver before uninstalling the launcher.

## Undo

Switch the Home button helper off in Settings > Accessibility, put a screensaver from another package back, and
uninstall the launcher (`pm uninstall --user 0 com.gathin.tvhome`). Google TV was never disabled, so Home goes
straight back to it. Then:

```bash
TV_HOST=<tv ip> tools/restore.sh examples/jvc-4k-isdb-ktc-hongkong/debloat.txt com.google.android.apps.tv.launcherx
```

To get ambient mode back as the screensaver, enable it first and then select
`com.google.android.apps.tv.dreamx/.service.Backdrop`.
