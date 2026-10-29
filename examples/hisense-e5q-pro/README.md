# Hisense E5Q Pro: ADB setup and firmware notes

The existing [debloat.txt](debloat.txt) keeps stock Home, Live TV and OTA enabled.

## Model

| Item | Value |
|---|---|
| Retail model | Hisense 55E5Q-PRO, 55-inch, China |
| Android model / platform | `VIDAA_TV` / MediaTek `MT9653` |
| Android | 11 / API 30 |
| Incremental firmware | `02.02.70.01` |
| System UI / updater version | `V0000.00.01A.Q0702` |
| Security patch | `2022-09-05` |

Device report: [#1436](https://github.com/Gathin23/cleanyourtv/issues/1436).
Original setup sources:
[Toutiao instructions](https://www.toutiao.com/zixun/7522310789718607891/) and
[Bilibili video](https://www.bilibili.com/video/BV1WRSMBHEEP/).

## Before starting

- Have the original remote and a **USB mouse connected to the TV**. The mouse is mandatory:
  the remote cannot navigate Android system settings on this firmware.
- Connect the computer and TV to a trusted local network. Prepare an ADB client using the
  [manual workflow](../../README.md#doing-it-by-hand). Keep network ADB off the Internet.
- Before changing packages or Home, save package lists, disabled-package lists, properties and
  settings privately, and prepare the [restore tools](../../README.md#doing-it-by-hand).
  Keep native settings, network, audio, input, remote, keyboard and update services enabled.

In the factory menu, change **only ToFac**. Leave calibration, initialization, reset and other
options alone. Stop if the menu differs; do not launch factory/test packages through ADB.

## Enable ADB

### 1. Enter M mode temporarily

1. Open **OEM Settings > Sound settings > Sound Balance**. If Dolby effects make Sound Balance
   unavailable, turn those effects off first.
2. Alternate the remote's **OK** and **Settings/Menu** buttons until the factory screen with a
   large **M** appears. The initial alternating-key count varies by remote and firmware.
3. Press **Menu** to open the factory menu. Select **Options > ToFac** and change **U to M**.
4. **Reboot** the TV.

### 2. Open Android settings

1. Return to **OEM Settings** and open the **version/device information page**.
2. Press the remote's **Menu button FIVE times** to open Android system settings.
3. Use the **USB mouse** to open **About / device status > Build number**. Click Build number
   repeatedly until Developer options are enabled.
4. Open **Developer options**, enable **USB debugging**, and accept the warning.

### 3. Authorize the computer

1. Connect the ADB client to the TV's current LAN address; the repository client defaults to
   TCP port **5555**.
2. Accept the debugging authorization prompt on the TV for your trusted computer. Check its RSA
   fingerprint when shown; use persistent authorization only for a computer you intend to keep trusted.
3. If access remains unauthorized, check the TV prompt, USB debugging switch, address and local
   network connectivity. See [Connecting troubleshooting](../../docs/TROUBLESHOOTING.md#connecting).

### 4. Restore U mode

Reopen the factory menu, change **Options > ToFac from M to U**, and **reboot**.
The authorized ADB client can reconnect on this firmware.
Restore any sound settings changed temporarily while enabling Sound Balance.

**M mode blocks the OTA check:** the updater stays at “Checking for updates”. In U mode after
reboot, it completes with **“Current version is up to date”**, version `V0000.00.01A.Q0702`.
Keep `com.jamdeo.updateservice`, `com.hisense.usbupgrade` and `com.hisense.playerupdate` enabled.

After maintenance, turn off USB and any network/wireless debugging switches, revoke debugging
authorizations and reboot. Follow the [ADB closing guidance](../../docs/TROUBLESHOOTING.md#connecting)
to check that the port is closed. Port 5555 is plaintext and remains accessible in U mode while
debugging is enabled.

## Native Inputs configuration

`TvInputManager` returns no inputs on this firmware. Use the native Hisense panel through the
launcher's Inputs entry. [config.json](config.json) contains these routing settings:

```json
{
  "live_tv_package": "",
  "live_tv_action": "",
  "source_panel_service": "com.hisense.tv.inputs/com.hisense.tv.InputsUIService",
  "source_panel_action": "com.hisense.tv.start_inputs"
}
```

Merge these keys into your [launcher configuration](../../themes/README.md).
The panel lists HDMI 1 (Apple TV), DLNA and HDMI 2–4. Keep `com.hisense.tv.inputs` and
`com.jamdeo.tv.livetv` enabled. Leave both `live_tv_*` fields empty: the available route uses the
Inputs service. Put its action only in `source_panel_action`.

## Cold boot and Home recovery

With stock Home disabled, a replacement without Direct Boot support can leave the TV stuck
during cold boot: user 0 stays `BOOTING`, `sys.boot_completed` remains empty, and no Home appears.

Before replacing stock Home, require a **Direct Boot-aware HOME activity** that uses storage
available before unlock, defers external theme/configuration reads until unlock, and refreshes
apps and configuration on `ACTION_USER_UNLOCKED`. Check a full reboot as well as standby/wake.

Keep the stock package installed and authorized ADB access available during the handover.
To recover this boot failure, re-enable `com.jamdeo.tv.vod` and launch
`com.jamdeo.tv.vod/com.ju.main.launcher.LauncherActivity`; boot and user unlock can then complete.
Handle stock Home separately when restoring a launcher handover: this folder's conservative
list leaves it enabled.

## Apps restored by the firmware

The firmware automatically reinstalls these apps after removal for user 0:

- `com.audiocn.karaoke.micmanager`
- `com.ju.miguvideoTV.cmcc`
- `com.hisense.tv.videochat.social`

Keeping them installed and disabled for user 0 prevented respawn through a later reboot.
For unwanted apps, use the repository's reversible disable/restore approach. Keep
`com.hisense.vidaaassistant` enabled: it manages installation, permissions and app startup.

## Validation

Single validation session on **2026-10-24**, on the model and firmware above.

| Feature | Result |
|---|---|
| Physical remote Home button | Working |
| Apple TV over HDMI | Picture and sound working |
| Native Inputs, media center, casting UI and settings | Open from the replacement launcher |
| Cold boot with the Direct Boot fix | Home appears without a keypress or manual launch |
| Stock Home recovery | Boot and user unlock complete |
| OTA check after restoring U and rebooting | Current version is up to date |
| Phone casting playback | Not tested |
