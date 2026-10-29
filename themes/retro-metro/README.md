# Retro Metro Launcher

Microsoft's Metro look from the Windows 8 Start screen and the Xbox 360 dashboard of 2011 and 2012. It
has a big thin "Start" title, lowercase group names over the rows, and square tiles in one flat colour each,
taken from the app's icon. The name sits in each tile's bottom corner, and there are no rounded corners,
gloss or glow.

![Retro Metro Launcher theme](screenshot.jpg)

## What it changes

| Key | Value | Effect |
|---|---|---|
| `home_title` | `Start` | The big title at the top left; the time moves to the top right |
| `row_titles` | `essential`, `internet`, `entertainment` | Lowercase group names over the rows |
| `uniform_tiles`, `flat_tiles` | `true` | Each app's icon on a square of one bright colour taken from it |
| `tile_label_inside` | `true` | The name in the tile's bottom left corner, always shown |
| `tile_shape`, `tile_corner_radius` | `square`, `0` | Square tiles with square corners |
| `background_color` | `#1F2223` | The Windows 8 Start screen's charcoal; no wallpaper and no time-of-day sky |
| `dock_row` | `false` | One plain grid from the top, as Start had, instead of a dock |
| `columns`, `rows_below_dock` | `8`, `2` | Eight tiles to a row, three rows on screen |
| `glass_style`, `focus_overshoot` | `rim`, `false` | A thin white rim on the focused tile, no bounce |
| `ambient_glow`, `app_glow`, `show_greeting` | `false` | No glow and no greeting |
| `icon_only_buttons`, `icon_only_inputs` | `true` | Inputs, Wi-Fi and Settings as round icon buttons |

Metro's live tiles also came in wide and large sizes. Here every tile is one size, and the group names
label rows, not columns of tiles. Change `row_titles` to name your own groups.

Tested on an ASUS/MOKA M1G, Android TV 11.

## Not affiliated

An unofficial tribute, made only from the launcher's own keys: no Microsoft logos, artwork, fonts or code are
copied. The tiles show each app's own icon and the colours are plain values. Windows, Xbox and Metro are
trademarks of Microsoft Corporation, named here only to say which look this recalls.
