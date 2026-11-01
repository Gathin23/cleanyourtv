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
