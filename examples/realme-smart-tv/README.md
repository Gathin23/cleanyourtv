# realme Smart TV (Android TV 11): debloated, with Clean TV Home

Tested on realme Smart TV (India), board Skyworth `SW4` (`SW4H_FF`, product `SW4H_FF_realme`),
MediaTek `m7332` (`armeabi-v7a`, 1.7 GB RAM, 1080p @ 320 dpi), Android TV 11 (API 30),
build `RTM5.220609.106`, security patch 2023-10-06.

This set runs classic Android TV with `com.google.android.tvlauncher`, built on a Skyworth SW4H
board with MediaTek m7332 chipset. Handover to Clean TV Home is clean and standard; no setupwraith
Wi-Fi trap applies (Wi-Fi is owned by `com.google.android.gms`).

| File | What it is |
|---|---|
| `debloat.txt` | The 22 packages that were disabled, grouped and commented |
| `config.json` | 4 columns, dock apps, hidden internal apps, Live TV package |
| `tv.json` | Model, OS version, and notes for the TV tables |

## Connect

```bash
export TV_HOST=<tv ip>
```

Turn on Network debugging / Wireless debugging in Developer Options and confirm the authorization
prompt on the TV.

## Debloat

```bash
TV_HOST=<tv ip> tools/disable.sh examples/realme-smart-tv/debloat.txt
```

(Windows: `tools\disable.ps1 -TvHost <tv ip> examples\realme-smart-tv\debloat.txt`.)

The debloat list disables 22 packages: Skyworth daily play, ACR recommendations, e-POP player,
shop customizer, QJY browser, speedchannels, partner recommendations, and unused Google feedback
and calendar sync adapters. Everything is disabled via `pm disable-user --user 0`, never uninstalled.

### Critical packages kept enabled

- **`com.mediatek.partner.netflix`**: Despite the package name, on this Skyworth/MediaTek board this
  package contains the `GLOBAL_BUTTON` broadcast receiver that handles hardware remote hotkeys
  (`KEYCODE_YOUTUBE`, `KEYCODE_PRIMEVIDEO`, `KEYCODE_APPS`). Disabling it breaks the remote's
  YouTube and Prime Video buttons. Keep it enabled.
- **`com.google.android.sss.authbridge` and `com.google.android.partnersetup`**: Required by Google
  Play Services for authentication token exchange when casting private media (such as Google Photos
  albums). Keep them enabled.
- **Live TV and Inputs**: Live TV is `com.smartdevice.livetv`. HDMI1, HDMI2, HDMI3 and AV inputs are
  managed by `com.mediatek.tvinput` and list directly in Clean TV Home's Inputs menu.

## Install the launcher

Clean TV Home installs cleanly with `build-and-install.sh`:

```bash
launcher/build-and-install.sh
```

(Windows: `launcher\build-and-install.ps1 -TvHost <tv ip>`.)

The script sets `com.gathin.tvhome` as the default Home activity via `cmd package set-home-activity`,
confirms focus, and safely disables `com.google.android.tvlauncher`.

## Push theme and layout

Push the tested 4-column layout and dock:

```bash
tools/theme.sh examples/realme-smart-tv
```

This configures:
- 4 columns layout
- Dock apps: YouTube, Netflix, Prime Video, Disney+ Hotstar
- Live TV package: `com.smartdevice.livetv`
- Hidden internal apps: Live TV shortcut, TVCenter, and Mi Gallery leftovers

## Restore

To undo all changes and restore the stock Android TV launcher:

```bash
TV_HOST=<tv ip> tools/restore.sh examples/realme-smart-tv/debloat.txt com.google.android.tvlauncher
```

(Windows: `tools\restore.ps1 -TvHost <tv ip> examples\realme-smart-tv\debloat.txt -Stock com.google.android.tvlauncher`.)
