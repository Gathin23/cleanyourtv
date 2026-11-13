# Xiaomi Mi Box S 2nd gen (MiTV-AFKR0) — validated variants

Stock list: [`debloat.txt`](debloat.txt) (disable `launcherx` and `setupwraith` only after CleanYourTV owns Home).

## RTT0.211222.001.683 (EU remote map)

Validated on **MiTV-AFKR0** / **jaws**, **Google TV 11**, build **`RTT0.211222.001.683`**, 2 GB RAM, Xiaomi RC
(`Vendor_2717_Product_32b9.kl`). The **737/772** entries in the header of `debloat.txt` document keycode **190**
for YouTube; on **683** the same remote uses **scancodes** instead — do not copy the 737 `app_keys` lines blindly.

### Extra packages (typical for SmartTube + custom remote map)

| Package | Why |
| --- | --- |
| `com.xiaomi.android.tvsetup.partnercustomizer` | Stock shortcut handler; disable when using CleanYourTV `app_keys` with **InputKeyService** / **HomeKeyService** |
| `com.google.android.youtube.tv` | Optional: disable when using SmartTube (`org.smarttube.stable`) |
| `com.netflix.ninja` | Optional: disable if unused |

### Launcher install

```powershell
$env:TV_HOST = '<box-ip>'
cd launcher
powershell -ExecutionPolicy Bypass -File .\build-and-install.ps1 -TvHost $env:TV_HOST -InputKeyService -HomeKeyService
```

Remote mapping for this build is in [`launcher-config.example.json`](launcher-config.example.json). Push the same
`app_keys` in **your own** theme folder (copy a theme from `themes/` first — `manage.py` refuses writing into
`examples/`), or bake the keys into `launcher/app/src/main/res/values/config.xml` before `build-and-install`.

### Remote (`app_keys` — build 683, `Vendor_2717_Product_32b9.kl`)

| Button | Scancode(s) | Example target |
| --- | --- | --- |
| Netflix | 240, 416 | Crunchyroll (`com.crunchyroll.crunchyroid`) |
| YouTube | 425, 426 | SmartTube stable (`org.smarttube.stable`) |
| Prime | 207 | Max (`com.wbd.stream`) |

Confirm scancodes on your unit: `tools/tv.py 'getevent -lt'` (press OK on the remote first if it was sleeping).

Install order: **launcher first**, then disable `com.google.android.apps.tv.launcherx` and
`com.google.android.tungsten.setupwraith` (see `debloat.txt`). If `disable.sh` skips setupwraith because Wi‑Fi was saved
during setup, re-join the network in Settings or use `FORCE_SETUPWRAITH=1` knowing the password may be asked again.

Apply the list with `TV_HOST=<box-ip> tools/disable.sh examples/xiaomi-mi-box-s-2nd-gen/debloat.txt` (same ordering
rules as in the file header).

### Still working after soak (683)

Wi‑Fi, Chromecast, Google Assistant, SmartTube, scancode shortcuts with partnercustomizer disabled.

## RTT0.211222.001.767 (additional unit)

Tested on Xiaomi TV Box S (2nd Gen), **MiTV-AFKR0 / jaws**, **Android 11 (SDK 30)**,
build **`RTT0.211222.001.767 release-keys`**, from Windows 11. Initial setup was tested on
2026-10-24, with physical remote shortcut checks on 2026-11-11. See [compatibility report #1459](https://github.com/Gathin23/cleanyourtv/discussions/1459).
This report does not validate every optional package in the shared debloat list.

### Home and saved Wi-Fi

The new launcher owned Home only after both `com.google.android.apps.tv.launcherx` and
`com.google.android.tungsten.setupwraith` were disabled. Before disabling the wizard, an older saved
network owned by setupwraith was forgotten; the active network was owned by Settings/system and
remained available. Check **all** saved networks, not just the active one, and keep the setup-wizard
guard. Home, Wi-Fi and the applied disabled-package state survived the initial reboot test.

### Dedicated remote buttons

After a later reboot, Home still worked but the four dedicated app shortcuts did not. Xiaomi's
GlobalKey log received Netflix and Prime presses, then reported `startAppByLauncherX()` could not
handle the target. The stock launcher was disabled.

| Button | Observed Android keycode or Linux scancode | Tested handling |
| --- | --- | --- |
| Netflix | Keycode 193 (`BUTTON_6`) | Direct app launch with `app_keys` |
| Prime Video | Keycode 194 (`BUTTON_7`) | Direct app launch with `app_keys` |
| YouTube | Scancode 425 (`KEY_PRESENTATION`) | Direct app launch with `app_keys` |
| All Apps | Scancode 216 (`KEY_CHAT`, mapped to `ALL_APPS` in the device key layout) | Not mapped in upstream CleanYourTV (tested only with a local launcher build) |

Only **InputKeyService** was enabled for this test; HomeKeyService was not needed. The Xiaomi
partnercustomizer package stayed enabled. All four buttons were confirmed with the physical remote.
For the first three buttons, use the existing `app_keys` package mappings, with `scan:425:<target-package>`
for YouTube. Confirm the codes on the actual unit before copying them.

The All Apps destination was implemented in a **local customization**. This report does not claim
that upstream accepts that custom action or that a package-only mapping opens upstream's app library.
Keep the key helper enabled in Accessibility, check that it is bound, and re-test the real buttons
after reboot or launcher updates. A force-stop can disable the launcher's accessibility helper.
Check held-key repeat too when enabling a filtering service on another unit.

### USB Ethernet

Enabling USB debugging made the USB Ethernet adapter unavailable on this unit, so setup used Wi-Fi
with the adapter unplugged. After disabling debugging, rebooting and reconnecting the adapter,
Ethernet playback worked with Wi-Fi disabled. Verify Ethernet again after maintenance and close
network ADB as described in the main prompt.

Streaming playback and voice search were confirmed. Chromecast was kept enabled during initial
setup but was **not tested** on this unit.
