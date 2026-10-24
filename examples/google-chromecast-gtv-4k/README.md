# Chromecast with Google TV 4K — `sabrina`

| | |
|---|---|
| OS | Google TV 14 / SDK 34 |
| Build | `UTTC.250917.004` / `14193937` |
| Packages | 127 installed; 2 (`tvrecommendations`, `education.cast2class`) disabled at the factory |
| Stock launcher | `com.google.android.apps.tv.launcherx` |

`config.json` is the dock this unit runs — Plex, YouTube, Hami Video, Netflix, with `live_tv_package`
left empty because a dongle has no inputs. Push it without rebuilding the launcher:

```bash
TV_HOST=<tv ip> tools/theme.sh examples/google-chromecast-gtv-4k
```

## Getting ADB in: pairing, then the platform-tools key

There is **no plain 5555** on this device until you switch it over by hand:

1. On the Chromecast: Developer options → **Wireless debugging** → *Pair using pairing code*. The
   **pairing port** in that dialog is not the port on the main Wireless debugging screen — that one is the
   *connection* port.
2. Pair with the **stock** adb (`adb_shell`, which `tools/tv.py` uses, cannot speak the TLS pairing
   protocol — it fails with `Unknown command: 'STLS'`):

   ```
   "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" pair <tv ip>:<pairing-port> <code>
   adb connect <tv ip>:<connection-port>
   adb -s <tv ip>:<connection-port> tcpip 5555
   adb connect <tv ip>:5555
   ```

3. Tell `tv.py` to use the **platform-tools key** — that is the key the pairing authorised. `tv.py`
   normally uses its own `tools/adbkey`, which the Chromecast rejects with an unanswered "Allow USB
   debugging?" prompt: a 120-second timeout and no error message.

   ```
   $env:ADB_KEY_DIR = "$env:USERPROFILE\.android"
   ```

**Both the port and `tcpip 5555` are lost on every reboot.** After one, re-read the connection port from
the Wireless debugging screen (the pairing itself survives) and run `adb connect` + `adb tcpip 5555` again.

## Home key: disable both, in this order

```bash
pm disable-user --user 0 com.google.android.apps.tv.launcherx
pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

`setupwraith`'s `RecoveryActivity` claims HOME at priority 1 **and re-enables launcherx**, so disabling
only the stock launcher is not enough. `set-home-activity` still returns Success and still changes nothing.

## Apply the debloat in small batches, and wait before walking away

The first attempt ran all 21 packages in one batch, reported `new state` for every one, and then the device
rebooted. **19 of the 21 were back after the reboot.**

PackageManager writes `package-restrictions.xml` asynchronously. `launcherx` and `setupwraith`, disabled
minutes earlier, survived; everything disabled seconds before the reboot did not. Applying in four batches
of 4/4/4/7 with a 20-second pause after each — checking the device is still up between batches — worked
cleanly and nothing rebooted.

If you do see a reboot, **check the uptime before assuming a crash loop** (`eureka_info` on `:8008` reports
it). One reboot with a steadily climbing uptime afterwards is not a loop.

## What the Chromecast loses

- **The remote's YouTube button stops working.** It sends `KEYCODE_BUTTON_3` (190) and launcherx was what
  handled it. The Netflix button is unaffected (Netflix handles it itself). `app_keys` in `config.json`
  can map it back — `190:com.google.android.youtube.tv`.
- **No ambient screensaver change.** `com.google.android.apps.tv.dreamx` and `com.google.android.backdrop`
  are deliberately **kept**: disabling them leaves `screensaver_components` pointing at a missing
  component. Newer launchers have a `ClockDream` to redirect it to; the 2.0 build this list was written
  against did not.
- **No Live TV / HDMI inputs** — it is a dongle. `live_tv_package` must be left empty in `config.json`.

## Restore

```bash
TV_HOST=<tv ip> tools/restore.sh examples/google-chromecast-gtv-4k/debloat.txt com.google.android.apps.tv.launcherx
```

Then re-enable the two factory-disabled packages by hand if you want them back — restore skips them
because `pm disable-user` cannot touch them (`Shell cannot change component state`).
