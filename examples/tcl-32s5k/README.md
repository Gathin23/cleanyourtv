# TCL C06 cleanup with casting kept

This session was on a TCL reporting **Smart TV**, device **C06**, product **C06_2K_GB**, Android TV 11
(SDK 30), build **AR2101 / AR21**, and TCL software **V8-T221T05-LF1V361**. The retail model was
unconfirmed. It shares the firmware family of the [32S5K example](debloat.txt). Use the package list and
Wi-Fi ownership from your own TV when deciding what to disable.

## Back up and choose what to keep

1. Follow the [backup and connection steps](../../PROMPT.md) before changing packages. Save the enabled
   and disabled package states, settings and original Home. Keep the applied batch files and
   `tools/disable.sh` recovery records so you can use `tools/restore.sh` with each batch in reverse order.
2. Check Wi-Fi ownership with `tools/tv.py 'dumpsys wifi | grep -o "cname=[^ ]*"'`. On this unit the saved
   network belonged to `com.google.android.tungsten.setupwraith`. Leave that package enabled if it owns
   your network. Disabling it can remove the saved network and disconnect ADB.
3. Ask which streaming apps, voice features and casting modes the owner uses. In this session the
   Chromecast receiver, Wi-Fi Direct support and TCL firmware updater stayed enabled. Keep
   `com.tcl.tv` and `com.tcl.tvinput` for Live TV and the physical inputs. Keep TCL Settings and the
   remote and pairing services available.
4. Disable the chosen packages in small batches using `tools/disable.sh` and check the screen after
   each batch. This session disabled TCL promotions and the partner app installer, then TGuard and
   selected Google extras, then unused streaming apps and voice assistants. The three streaming apps
   the owner kept opened after cleanup.

## Check the Home handover

Set `live_tv_package` to `com.tcl.tv` and choose the dock apps in
[the launcher configuration](../../launcher/app/src/main/res/values/config.xml). Build from source
using the [launcher guide](../../docs/launcher.md) or [the Docker build](../../tools/docker-build.md).
With `TV_HOST` set, run `STOCK_LAUNCHER=none launcher/build-and-install.sh` to leave the stock launcher
enabled during the test. If Docker already built the APK, add `SKIP_BUILD=1`.

On this firmware the stock launcher had HOME intent priority 2. With the stock launcher enabled,
the installer assigned CleanYourTV the Android Home role, but an ordinary press of Home still
opened the stock launcher. The installer could start the replacement and reported the remaining
Home resolution failure. Check the installed replacement with the stock launcher still available:

```sh
tools/tv.py 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
tools/tv.py 'am start -W -a android.settings.SETTINGS'
tools/tv.py 'am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n com.gathin.tvhome/.MainActivity'
tools/tv.py 'dumpsys activity activities | grep -E "mFocusedApp|Task.*type=home.*com.gathin.tvhome"'
```

In this session, launching from Settings let the replacement take focus in an Android Home task.
The Home candidates were the two launchers and Android Settings' fallback. Only after the focus
and Home task checks passed did we use `tools/disable.sh` for a separate batch containing the stock
launcher, `com.google.android.tvlauncher`. We set CleanYourTV as Home again and pressed Home from
Settings to check the handover. Keep the stock launcher enabled if the replacement cannot take
focus as a Home task. If the handover fails after the batch, restore that batch while ADB is still
available.

## Verify and close debugging

All 13 package changes from this session, including the stock launcher, persisted through a reboot.
CleanYourTV opened at boot. The D-pad worked in all four directions, and the Inputs menu showed
HDMI 1, HDMI 2, AV and Live TV. The owner confirmed Home, Input and Settings on the physical remote.
Casting and HDMI playback were outside the test.

The owner revoked ADB authorizations, turned debugging off, restarted, and confirmed it was still
off. The network check timed out. The repo's [ADB closure procedure](../../PROMPT.md#the-prompt)
requires the TV to refuse the connection before reporting the port closed.
