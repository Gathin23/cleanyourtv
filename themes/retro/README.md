# Retro

A minimal, retro-flavoured home screen: pure black background, a dock that has no band or border at
all (the apps float over the wallpaper), a rim-only focus highlight instead of the bright glass slab, no
ambient colour gradient, and a wallpaper that follows the time of day.

![Retro theme](screenshot.jpg)

**Author:** REGT-URRED (GitHub) · [@yulrred](https://instagram.com/yulrred) (Instagram)

## What it changes

| Key | Value | Effect |
|---|---|---|
| `background_color` | `#000000` | Pure black behind everything |
| `dock_band` | `false` | No dock band and no edge: tiles float on the wallpaper |
| `glass_style` | `rim` | Focus drawn as a crisp rim alone, no slab, no sheen, no glow |
| `dock_title` | `false` | No large app name above the dock while you move |
| `ambient_glow` / `app_glow` | `false` | No full-screen colour gradient behind the focused app |
| `columns` | `5` | Five tiles per row |
| `icon_only_buttons` / `icon_only_inputs` | `true` | Inputs, Wi-Fi and Settings as round icons |
| `wifi_status_color` | `true` | Wi-Fi icon green while online |
| `tile_menu` | `true` | Hold OK on a tile for Move, Remove and App info |
| `wallpaper_rotation_minutes` | `60` | Rotates only when the time-of-day group changes |
| `wallpaper_moods` | morning/evening/night | See below |
| `wallpaper_dim` | `10` | A light shade so the picture keeps its colour |
| `reduced_motion` | `true` | Calmer motion, fewer frames |

`glass_style` and `dock_band` both work on the main launcher. A build older than either still applies
the rest of the theme and ignores that key.

## Apps: you choose

This theme does **not** pin any app. Your installed apps fill the dock (`fill_dock` is on), and you
can set your own order with `dock_apps` in `themes/retro/config.json` or in Launcher settings on
the TV.

**Recommended:** install [SmartTube](https://github.com/yuliskov/smarttube) (the user installs it,
sideload or store) as a YouTube replacement that has no ads and no tracking. It is optional: if you
keep the stock YouTube app, leave it as is.

## Wallpapers: time of day or a single picture

The theme ships three pictures in `wallpapers/`:

| File | Shown |
|---|---|
| `6am_4pm.jpg` | Morning to afternoon |
| `4pm_9pm.jpg` | Afternoon to night |
| `9pm_6am.jpg` | Night to morning |

The launcher picks the group that fits the clock, so the same theme looks different during the day.
The times are the ones the author uses (Peru); edit `wallpaper_moods` to change them.

To use a **single static picture** instead:

1. Copy your picture to `themes/retro/wallpaper.jpg` (1920x1080, under about 300 KB).
2. In `config.json` set `"wallpaper_rotation_minutes": 0` and remove `"wallpaper_moods"`.
3. Push the theme again.

To swap the pictures, any 1920x1080 JPEG under about 300 KB works. Keep the names if you want
the time-of-day moods, or rename them and update `wallpaper_moods`. The shipped pictures are
the author's own; see `CREDITS.md`.

## Install

```bash
TV_HOST=<tv ip> tools/theme.sh themes/retro
# Windows
powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost <tv ip> themes\retro
```

Undo with `tools/theme.sh --reset`.

## Tested on

Philips Google TV TA7 (PH3M_AL_T32, Android 14, Google TV), launcher 2.6.

## Credit and forks

You may copy, change and reuse this theme. Keep the author credit above. If you publish a modified
version, use a different folder name and identifier for your theme so people can tell them apart.
