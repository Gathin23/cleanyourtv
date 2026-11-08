# VU SmartTV 4K FFM (MediaTek songshan, Android 11): remote shortcut buttons without launcherx and surviving standby

An owner report from 2026-11-07, with physical remote key captures on a VU SmartTV 4K FFM (retail model
sold in India, official VU Bluetooth/IR remote). It belongs to the same MediaTek `songshan` (m7632) family
as [`examples/hisense-smarttv-4k-ffm-android11`](../../examples/hisense-smarttv-4k-ffm-android11/debloat.txt),
where the fifth unit reported that YouTube, Prime Video, and Disney+ buttons went dead after disabling
`launcherx`.

## Observed firmware

| Field | Observed value |
|---|---|
| Brand | `VU` |
| Android model | `SmartTV 4K FFM` |
| Device / Board | `songshan` / MediaTek `m7632` (`songshan_emH_FFM`) |
| Android / API | `11` / `30` |
| Firmware build | `RTT2.220118.001` |
| Remote | VU Smart Remote (`SmartRC Keypad` / `SmartRC Consumer Control`, `MStar Smart TV IR Receiver`) |

The launcher is CleanYourTV as default Home. `launcherx` is disabled, and stock streaming apps (Netflix,
YouTube, Prime Video, Disney+ Hotstar) are kept enabled.

## The problem

With `launcherx` disabled:

- **Netflix** keeps working: the firmware handles the Netflix key (`com.mstar.netflixobserver.NetflixKeyReceiver`),
  so it launches independently of the stock launcher.
- **YouTube, Prime Video, and Disney+ Hotstar** do nothing: on Google TV, stock `launcherx` intercepted
  these buttons via `com.google.android.apps.tv.launcherx.globalbutton.GlobalButtonLaunch_Receiver`. Once
  `launcherx` is disabled, the broadcast has no recipient and the button presses fall through silently.

## Button codes on this remote

Captured via `logcat` (`WindowManager: interceptKeyTq keycode=... scancode=...`) and `getevent -lt`
while pressing each button on the physical remote:

| Button | KeyCode | ScanCode | Linux Key | HID Usage | Target package |
|---|---|---|---|---|---|
| Netflix | `KEYCODE_STEM_1` (265) | 242 | `KEY_STEM_1` | | `com.netflix.ninja` (firmware-handled) |
| YouTube | 293 | 469 | `KEY_FN_F4` | `0x000c00ab` | `com.google.android.youtube.tv` |
| Prime Video | 295 | 468 | `KEY_FN_F3` | `0x000c00d6` | `com.amazon.amazonvideo.livingroom` |
| Disney+ Hotstar | 337 | 484 | `KEY_FN_B` | `0x000c00dc` | `in.startv.hotstar` |
| Cricket | 662 | 489 | `KEY_CRICKET` | `0x000c01d0` | `com.fancode.tv` / sports app |
| Cinema | 664 | 490 | `KEY_CINEMA` | `0x000c01d1` | `com.sonyliv` / entertainment app |

## Bringing the buttons back

CleanYourTV's `InputKeyService` intercepts these keycodes and scancodes and launches the corresponding apps.

### 1. Map the keys in `config.xml` or theme `config.json`

In `config.xml`:

```xml
<string-array name="app_keys" translatable="false">
    <item>293:com.google.android.youtube.tv</item>
    <item>scan:469:com.google.android.youtube.tv</item>
    <item>295:com.amazon.amazonvideo.livingroom</item>
    <item>scan:468:com.amazon.amazonvideo.livingroom</item>
    <item>337:in.startv.hotstar</item>
    <item>scan:484:in.startv.hotstar</item>
    <item>662:com.fancode.tv</item>
    <item>scan:489:com.fancode.tv</item>
    <item>664:com.sonyliv</item>
    <item>scan:490:com.sonyliv</item>
</string-array>
```

Or in a pushed theme `config.json`:

```json
{
  "app_keys": [
    "293:com.google.android.youtube.tv",
    "scan:469:com.google.android.youtube.tv",
    "295:com.amazon.amazonvideo.livingroom",
    "scan:468:com.amazon.amazonvideo.livingroom",
    "337:in.startv.hotstar",
    "scan:484:in.startv.hotstar",
    "662:com.fancode.tv",
    "scan:489:com.fancode.tv",
    "664:com.sonyliv",
    "scan:490:com.sonyliv"
  ]
}
```

### 2. Enable `InputKeyService`

Check `settings get secure enabled_accessibility_services` first; if it lists other services, append
`:com.gathin.tvhome/.InputKeyService` instead of replacing them.

```bash
cmd appops set com.gathin.tvhome AUTO_START allow 2>/dev/null || true
cmd appops set com.gathin.tvhome APP_AUTO_START allow 2>/dev/null || true
appops write-settings 2>/dev/null || true
settings put secure enabled_accessibility_services com.gathin.tvhome/.InputKeyService
settings put secure accessibility_enabled 1
```

### 3. Surviving standby (`com.hisense.STR_SUSPEND`)

As noted in [`examples/hisense-smarttv-4k-ffm-android11`](../../examples/hisense-smarttv-4k-ffm-android11/debloat.txt),
this board's standby kills third-party background services (`PowerVtImp: kill 3rd app`), which drops
accessibility services from `enabled_accessibility_services`.

To make the service persistent:
1. Grant `WRITE_SECURE_SETTINGS`:
   ```bash
   pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS
   ```
   (Or run `build-and-install.sh` with `RESTORE_KEY_SERVICES=1`).
2. Set `restore_key_services` to `true` in `config.xml` or `"restore_key_services": true` in `config.json`.

With both in place, CleanYourTV automatically restores `InputKeyService` every time Home returns to the
foreground after standby.
