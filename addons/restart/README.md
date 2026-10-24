# Restart

Restarts the TV from the remote: open **Restart**, answer **Restart** on "Restart the TV?" (Cancel is focused
first, Back cancels), and the TV restarts. On cleanyourtv's Home it is also a module card, **Restart**.

Package `io.github.jemychao.restart`. English, Traditional Chinese and Simplified Chinese.

## How it restarts the TV

An ordinary app cannot reboot Android: `PowerManager.reboot` needs the signature permission `REBOOT`, and
`DevicePolicyManager.reboot` needs a device owner, which a TV signed in to a Google account cannot have. What
works is an accessibility service: it may open Android's power menu (`GLOBAL_ACTION_POWER_DIALOG`) and press the
menu's **Restart** item.

The owner has already said yes in the app's own prompt, so the service presses Restart for them. On some firmware
the power menu is a phone layout the D-pad cannot reach (a Philips on Android 9 is one), so without this the menu
would open and the remote could not choose anything in it.

For up to 6 seconds after Restart the service listens to window changes (without them its window cache never sees
the menu open), looks outside app windows for the item labelled with Android's own `global_action_restart`
string, in the TV's language, and clicks it. App windows are skipped because Home may show this app's own tile
with the same word. If it finds nothing the menu stays open for the remote. It does nothing at any other time.

On a Philips with Android 11 the request makes the phone SystemUI's power menu crash
(`NotificationShadeWindowController`), and Android falls back to its own `LegacyGlobalActions` menu; the service
presses Restart in that one. The TV can take 25 to 40 seconds to go down after that, from Live TV.

## Install

JDK 17 and Android SDK 34, as for the launcher, and the `tools/venv` from the main README. The `gradlew`
scripts reuse the launcher's pinned Gradle wrapper with this folder as the project. Android 8.0 or later.

```sh
TV_HOST=192.168.1.50 addons/restart/build-and-install.sh
TV_HOST=192.168.1.50 addons/restart/build-and-install.sh --uninstall
```

```powershell
powershell -ExecutionPolicy Bypass -File addons\restart\build-and-install.ps1 -TvHost 192.168.1.50 [-Uninstall]
```

Then press Home and answer **Show card** if you want the card; Keep hidden leaves an ordinary app tile.

The install script takes the service off the enabled list and puts it back: after an update Android 9 can keep
the service bound to the replaced APK, where it opens the menu but reads no windows. For a few seconds after an
install or a boot the service may not be connected yet; the app then says so instead of opening the menu.

## Permissions

- **Accessibility service** (`RestartService`), switched on by the install script. It filters no keys and asks
  for no events except while it looks for the Restart item (window changes, for up to 6 seconds after you chose
  Restart). It can retrieve window content (`canRetrieveWindowContent`, `flagRetrieveInteractiveWindows`) and
  uses it only then, only outside app windows, to find and press the power menu's Restart item. The uninstall
  takes it off the enabled list and leaves any other services alone.
- No network, no storage permission, no background service. `RestartActivity` (exported) is the tile and the
  card.

## Tested on

- Philips 48OLED707/96 (PH1M_EA_9970A, Android TV 11): the card, the prompt and the restart (started over
  Live TV).
- Philips 75PUH8265/96 (TPM191E, PH9M_EA_5599, Android TV 9): the card, the prompt and the restart.

Both with the reference launcher. Banner made by the author with Google Gemini.
