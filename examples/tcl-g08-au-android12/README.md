# TCL Smart TV Pro (G08, Android 12), Australian firmware

TCL `G08` set, model "Smart TV Pro", product `G08_4K_GB`, firmware `STT2.221228.001 AS34`, Android 12 with the
Google TV home (`launcherx`) in Basic TV mode, 32-bit userspace, about 2.4 GB RAM. Same fingerprint as
[`tcl-g08-eu-android12`](../tcl-g08-eu-android12/), but the Australian set ships more on top: Stan, AirPlay,
TCL Channel, TCL Live free channels, a retail sticker, a hotel portal and TCL Guard. It has no Google Cast and
no voice assistant.

| File | What it is |
|---|---|
| `debloat.txt` | The 28 packages that were disabled, grouped and commented |
| `config.json` | Dock, hidden apps, no Live TV row, remote buttons; push it with `tools/theme.sh` |

**How much testing is behind it:** one set, 22 October 2026. The list was applied with `tools/disable.sh` in
five batches with a screenshot after each, the launcher with `launcher/build-and-install.ps1 -HomeKeyService`.
All 28 packages stayed disabled across a reboot. Checked afterwards: the remote's Input key panel, HDMI 1 and
HDMI 2 from the launcher's Inputs menu, SmartTube and Plex playback (with Private DNS on AdGuard), and the
remote buttons below with the real remote. Not checked: Live TV (no aerial), AirPlay and Miracast sessions.

```sh
TV_HOST=<tv ip> tools/disable.sh examples/tcl-g08-au-android12/debloat.txt
TV_HOST=<tv ip> HOME_KEY_SERVICE=1 launcher/build-and-install.sh
TV_HOST=<tv ip> tools/theme.sh examples/tcl-g08-au-android12
```

On Windows the launcher step is `launcher\build-and-install.ps1 -HomeKeyService`, which is what this set was
installed with.

`launcher/build-and-install.sh` stops at the first try if the setup wizard still claims Home
(`setupwraith/.RecoveryActivity`): the list disables it, so apply the list first. Before that, check that the
saved Wi-Fi network does not belong to the wizard (`tools/tv.py "dumpsys wifi" | grep cname=`); `disable.sh`
skips it if it does.

## Remote buttons

With `launcherx` off, only Home and Input still work on their own. `config.json` maps the rest back through
`HomeKeyService` (`app_keys` work with either helper, so one is enough):

| Button | Keycode | Scancode | Mapped to |
|---|---|---|---|
| Input | `KEYCODE_TV_INPUT` | 417 | nothing needed: TCL draws the QuickPanel itself (it takes about a second) |
| Settings (gear) | `KEYCODE_NOTIFICATION` 83 | 233 | `settings_key_keycodes`, Android Settings |
| TV | 4001 (TCL then sends `KEYCODE_GUIDE`) | 365 | HDMI 1, see Live TV below |
| Netflix | 4062 | 242 | Plex |
| YouTube | 4065 | 245 | SmartTube |
| Prime Video | 4099 | 752 | free |
| Stan | 4136 | 654 | free |
| TCL Channel | 4020 | 456 | free (TCL starts `com.tcl.waterfall.overseas`, which the list disables) |
| Disney+ | 4141 | 762 | TCL's shortcut manager opens the Play Store page itself when the app is missing |

Found with `logcat` while pressing each button (`handleKeyBeforeQueueing` from `WindowManager`).

**Volume repeat:** as on the [G09](../../docs/devices/tcl-55p71b-g09-key-repeat.md), a bound key-filtering
helper makes a held volume button step once per press on this G08 build too: ramp before
`HomeKeyService`, one step per press with it alone. The owner kept the buttons. Leave out `HOME_KEY_SERVICE=1`
(or set `"key_filter": false`) to keep the ramp and lose the buttons.

## Live TV without an aerial

The tuner input (`com.tcl.tvinput/.TunerInputService/HW2`, "TV" in TCL's panel) mixes aerial channels with
TCL Live's free internet channels from `com.tcl.channelplus`. With no aerial scanned, all 173 channels on the
tested set were TCL Live ones (`EN_TCL_IP`). `com.tcl.tv` tunes "TV" to the channel it remembers (here 503,
an IP channel). With `channelplus` disabled that tune fails, and the TV falls back to the last HDMI input. So
the TV button, TCL's own "TV" item and a Live TV row in the launcher all land on HDMI 1, which is why
`config.json` drops the Live TV row (`"live_tv_package": ""`) and points the TV button at HDMI 1.

`channelplus` stays off because it is ad-supported and asks for `com.google.android.gms.permission.AD_ID`,
`ACCESS_WATCHED_PROGRAMS`/`ACCESS_ALL_EPG_DATA`, TCL's BI analytics (`com.tcl.bi.permission.access`) and the
TCL device number (`READ_DNUM`), and starts a `BootService` at every boot.

With an aerial: scan channels in Settings, set `"live_tv_package": "com.tcl.tv"` and the TV button to
`"4001:input:com.tcl.tvinput/.TunerInputService/HW2"`, and tune one aerial channel once so it becomes the
remembered channel. Two notes for that case: the launcher's `input:` targets send a passthrough URI, which
`com.tcl.tv` does accept for the tuner (`UriTuneInputIntent`); TCL's own panel sends
`com.tcl.tv/.TVActivity` with the string extra `inputId` instead.

## Notes for this set

- The set was in Google TV Basic TV mode with no Google account (`dumpsys account`: 0), which is why Netflix,
  YouTube and Stan were disabled before the list ran. Sign in under Settings → Accounts to use the Play Store.
- After the first reboot TCL's `com.tcl.useragreement` showed a terms-update screen over the new home;
  cancelled once, it did not come back.
- With `launcherx` off, `com.tcl.partnercustomizer/.tkey.TKeyFree` appears in the app grid as "TCL Channel".
  It is hidden in `config.json`; the package itself was left alone.
- SmartTube needs the `armeabi-v7a` APK (`ro.product.cpu.abilist` is `armeabi-v7a,armeabi`).
