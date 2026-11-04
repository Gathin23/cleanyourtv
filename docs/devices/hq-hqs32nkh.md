# HQ HQS32NKH 32": Android 14, Toptech board, HiSilicon Hi3751V553

One unit, sold in Brazil under the HQ brand ("HQ screen" on the stock home), tested during one session on
2026-11-04 with two owner-initiated restarts. This records observations, not a several-day debloat list.
The rear label reads `Modelo: HQS32NKH`, "TELA smart 32"", made by Bel Micro Tecnologia S/A. Android does not
carry the model code: it reports `ro.product.model=SmartTV`, Android 14 / SDK 34,
firmware `HISI352U`, fingerprint `HiDPT/Hi3751V553_32_DMO_2layer_1024M/Hi3751V553:14/HISI352U/:user/`,
platform `bigfish`, about 1 GB RAM. Connected over Wi-Fi, with USB storage attached.

The board is Toptech with a Zeasn TitanOS home, the same software family as the
[RCA RC50A21S-4KSM](../../examples/rca-rc50a21s-4ksm/) and the
[Seleco TV39020HDA9SMART](../../examples/seleco-tv39020hda9smart/), but on a HiSilicon SoC and Android 14 rather
than Realtek and Android 9, and several component names differ (see Inputs below). Do not apply either list
because the vendor names match.

The session used source revision `04aec4b7`, with no launcher source changes: the launcher was built with
`tools/docker-build.sh` and configured with a pushed `config.json`.

## ADB

With debugging on in Developer options, network ADB answered on port 5555. `ro.adb.secure` is empty, so the TV accepted
the connection with no "Allow USB debugging?" prompt: while debugging is on, any device on the network gets a
shell. `tools/disable.sh` prints its warning for this. After the first restart, network debugging
was off again and had to be switched back on in Developer options.

At the end, `settings put global adb_wifi_enabled 0` dropped the ADB connection, and
`tools/adb-closed.py <tv ip>` answered that 5555 refused the connection. After a further restart it still
refused.

The TV took a new IPv4 address after each restart, and its Wi-Fi MAC changed too, so find it again before
reconnecting.

## Packages disabled during this session

Four packages, all still disabled after the first restart. Nothing was uninstalled. This is a record of the owner's
choices, not a ready-to-apply list.

| Package | Purpose / owner choice |
|---|---|
| `com.zeasn.deviceportal.asdprovider.toptech` | Zeasn ad/portal provider: boot receiver, `INSTALL_PACKAGES`. It also answers `com.zeasn.deviceportal.action.OTA_PULL_CHECK`, so with it off the TV no longer announces firmware updates by itself; the owner kept it off and checks for updates by hand |
| `com.zeasn.services.general` | Zeasn search and "weekly" service: boot receiver, `READ_LOGS`, `INSTALL_PACKAGES` |
| `com.hisilicon.dlna.dmr` | DLNA renderer (advertises itself as `SmartTV_DMR`), unused by the owner |
| `com.zeasn.ru_toptech` | Stock Home (`M043_BRAZIL_TitanOS1_Launcher`), disabled by `launcher/build-and-install.sh` with `STOCK_LAUNCHER=com.zeasn.ru_toptech` |

Kept by the owner's choice: Miracast (`com.hisilicon.miracast`), the Google voice assistant
(`com.google.android.katniss`), the Zeasn store (`cn.zeasn.overseas.tv.toptech.rtk2851.ru`) and the firmware
updater (`com.toptech.systemupdate`). Left alone as the TV's own: every other `com.toptech.*` package (TV
service, `tvmenu`, `quickmenu`, `mkeyeventservice`, keyboard, setup wizard, and the factory tools, which were
not launched) and every other `com.hisilicon.*` package.

## Home handover

The HOME claimants were the Zeasn launcher and Settings' `FallbackHome`; there is no `setupwraith`. The saved
Wi-Fi network belongs to `android.uid.system`, so nothing in the handover touched it. `launcher/build-and-install.sh`
started the reference launcher, disabled the Zeasn home and set Home; HOME resolved to
`com.gathin.tvhome/.MainActivity` before and after the first restart, and the owner confirmed the physical Home key.
The `minSdk` 26 launcher installed with `pm install` without a verification failure.

## Inputs

`dumpsys tv_input` lists no hardware or HDMI inputs, so the launcher's own input rows have nothing to show.
The Toptech source panel works instead, under a different name from the RCA and Seleco builds:
`com.toptech.tvmenu/.ui.InputsActivity`, action `com.toptech.tv.action.TV_INPUTS`. Opened from the Inputs pill it
listed AV, HDMI1 (ARC), HDMI2 and HDMI3. No HDMI picture, CEC or tuner reception was tested.

## The Menu key

The remote's Menu button arrives as `KEYCODE_MENU` (82), scancode 139. With the Zeasn home disabled, Menu on
Home did nothing for the owner: on the reference launcher it opens the focused tile's menu, and with focus
off a tile it does nothing. `settings_key_keycodes: [82]` in `config.json` makes the launcher open Settings for
Menu while Home is in front, with no accessibility helper (`HomeKeyService` was not enabled), so Menu inside
other apps is not intercepted. The owner confirmed it with the real remote on Home; Menu inside the Live TV
player was not checked. The tile menu stays on a held OK.

The `config.json` pushed with `tools/theme.sh`:

```json
{
  "dock_apps": [
    "com.google.android.youtube.tv",
    "org.videolan.vlc"
  ],
  "live_tv_package": "",
  "source_panel_service": "com.toptech.tvmenu/.ui.InputsActivity",
  "source_panel_action": "com.toptech.tv.action.TV_INPUTS",
  "app_labels": [
    "com.toptech.tvmenu=TV"
  ],
  "settings_key_keycodes": [82],
  "columns": 4
}
```

## Settings → Network → IP settings crashes

Opening the IP settings of the connected Wi-Fi network closes Settings and returns to Home.
`com.android.tv.settings/.connectivity.EditIpSettingsActivity` dies with:

```text
java.lang.NullPointerException: Attempt to invoke virtual method
'android.net.IpConfiguration$IpAssignment android.net.IpConfiguration.getIpAssignment()' on a null object reference
    at com.android.tv.settings.connectivity.setup.IpSettingsState$IpSettingsFragment.onViewCreated(IpSettingsState.java:127)
```

`cmd wifi list-networks` shows the saved network twice under the same network ID, as `wpa2-psk` and
`wpa3-sae` (the router runs WPA2/WPA3 transition mode), and `dumpsys wifi` reports `IP assignment: DHCP`, so
the configuration itself is there. None of the disabled packages appears in the trace, but the screen was not
tried with them re-enabled, so this session does not prove the crash is independent of them. The rest of the
Network screen opens normally. A fixed address is better reserved on the router.

## Private DNS

`private_dns_mode hostname` with `dns.adguard-dns.com` validated on this set, a known ad domain resolved to a
blocked address, and YouTube loaded its home screen (its own ads remain, as the README says).

## Not tested

Several days of use, a cold power cut, `tools/restore.sh`, HDMI and tuner picture, CEC, Miracast after the
changes (it worked from a Linux laptop before them), and the remote's other buttons beyond Home, Menu and the
Inputs pill.
