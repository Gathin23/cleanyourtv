# Indurama Google TV 4K (KTC `hongkong`, ISDB) — notes

KTC-built Indurama Google TV 4K, `KTC_INDURAMA_4K_ISDB_22GV1`, device `hongkong`, MediaTek m7632,
Google TV 14 (SDK 34), build UKNV.260610.001. Same board as `../ktc-4k-smart-tv-hongkong/` (Taiwan
firmware UKNV.260626.001); this is the Ecuadorian ISDB-Tb variant.

## The list

`debloat.txt` disables **40 packages**. Applied over network ADB with the repo's tools, reboot-checked,
nothing re-enabled by the firmware at boot. Netflix and Max were off the owner's keep-list: on the
tested set they are disabled (commented out in this shared list, so each owner opts in) and had
arrived factory-disabled here anyway, as had YouTube Music, which the list does send.

Kept: the owner's streaming apps (YouTube, Prime Video, Disney+, Spotify, DirecTV Go, TuneIn), Google
Assistant (katniss), Chromecast, Miracast (`com.mediatek.androidbox`), Xuper/Magis TV
(`com.android.mgstv`) and Mercado Play (the LatAm preloads), Apple TV app and `flar2.homebutton`
(owner's choice), Live TV and the ISDB tuner firmware.

## Launcher and Home

- `com.google.android.apps.tv.launcherx` is the stock launcher; the CleanYourTV launcher took Home
  from it. `com.google.android.tungsten.setupwraith` answers HOME ahead of an installed launcher on
  Google TV (its `.RecoveryActivity`), so it was disabled in the same pass as launcherx.
- The active Wi-Fi belonged to the user, not the wizard
  (`networkCreator=CREATOR_USER`), so disabling setupwraith did not delete the network and ADB
  survived. Check yours with `dumpsys wifi | grep -o 'cname=[^ ]*'` first.
- Applied with `HOME_KEY_SERVICE=1 INPUT_KEY_SERVICE=1 RESTORE_KEY_SERVICES=1`; volume auto-repeat
  stayed intact with the helpers on.

## Remote keys (measured on this set)

`getevent` is not permitted for the shell user on this firmware, so the keycodes were read with a
temporary `Log.d` in the launcher's key services (`docs/TROUBLESHOOTING.md` covers the fallback):

| Button | Keycode | Scancode | Mapped to |
|---|---|---|---|
| Settings (gear) | 80 | 240 | `settings_key_keycodes` (the stock launcher used to answer it) |
| Netflix | 265 | 423 | `app_keys` `265:com.android.mgstv` → opens Xuper/Magis TV (owner's request) |

The Inputs, Wi-Fi and Settings pills on the new Home work without helpers; the remote's Input and app
buttons (YouTube, Prime Video, Disney+) kept working through the key services.

## Firmware updates

An OTA update will re-enable the listed packages and put Home back on launcherx. Re-run the list with
`tools/disable.sh` after re-opening ADB, and `tools/venv/bin/python -I tools/check.py examples/indurama-google-tv-4k-ktc-hongkong/debloat.txt`
shows what came back.
