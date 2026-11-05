# Philips Google TV TA1 (65PUS8729/60)

Philips 65PUS8729/60 (TPV, PH1M_WW_9972, MediaTek), Google TV 14, build UKN2.241117.001, about 2.8 GB RAM.
Debloated on 2026-10-25 and given a custom launcher.

| File | What it is |
|---|---|
| `debloat.txt` | The packages that were disabled, in the two batches they were applied, with what each one is |

All commands run from the repository root and need the Python client once:
`python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt`.
They also need network ADB: Settings > System > About > press Build 7 times, then Developer options >
USB debugging on, and accept the "Allow USB debugging?" prompt the first time a command connects.

## What changed

- **Disabled** (never uninstalled): TP Vision analytics (`candeebug`, `gel`), partner attribution and
  auto-installs, the retail demo, Samba TV, Control4, AirPlay, HbbTV, Google text-to-speech, Play Games,
  calendar sync, Netflix, Prime Video, the Google TV films app, YouTube Music, ivi, the built-in browser,
  the Philips PlayFi and NFT gallery apps, the setup wizard and the stock Google TV launcher.
- **Kept**: everything that plays HDMI and the tuner, the input menu, remote pairing, keyboards, Play Store
  and Play Services, Assistant, Chromecast (phone casting may stop after a reboot with setupwraith off), firmware updates.
- **Wi-Fi first**: the saved network was owned by `setupwraith`, and disabling it while it did would take the
  TV off Wi-Fi and ADB. The network was forgotten and added again before the list ran; check
  `dumpsys wifi | grep -o 'cname=[^ ]*'` on yours before applying the last two lines.
- **Memory**: about 100 MB more free after batch 2 (used PSS 1.62 GB to 1.52 GB), launcher frames 0.28 % janky.

## Check

A cold reboot after the list brings the TV back to Home with ADB and Wi-Fi, no crashes in the crash log.
HDMI and Live TV were not exercised on this unit.

## Re-enable an app

On the TV: Settings > Apps > See all apps > Show system apps (or "Disabled apps"), pick the app, Enable.
Over ADB:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm enable --user 0 com.netflix.ninja'
```

Then delete or comment out its line in `debloat.txt`, so a later run does not disable it again. To undo the
whole list: `TV_HOST=<tv ip> tools/restore.sh examples/philips-google-tv-ta1/debloat.txt com.google.android.apps.tv.launcherx`.
