# 2014 Leanback Launcher

A tribute to the first Android TV home screen: the Leanback Launcher that came with Android 5.0 Lollipop
and the Nexus Player in 2014. Its parts are here: the green search orb at the top left, the time on its
own at the top right, rows of app banners with a heading over each, the focused app's artwork washing the
top of a dark slate screen, and a plain white focus.

![2014 Leanback Launcher theme](screenshot.jpg)

The orb's green (`#86C739`) and the slate (`#263238`) are the defaults in Google's own Leanback library
(`lb_default_search_color` and `lb_default_brand_color_dark`).

## What it changes

| Key | Value | Effect |
|---|---|---|
| `search_orb` | `#86C739` | Leanback's round green search button at the top left; OK opens the TV's search |
| `clock_right` | `true` | The time alone at the top right, beside the buttons |
| `row_titles` | `Featured`, `Apps`, `More apps` | A heading over each row, like Leanback's row headers |
| `tile_corner_radius` | `2` | Nearly square banner corners, as on the original |
| `background_color` | `#263238` | Leanback's dark slate; no wallpaper and no time-of-day sky |
| `cinematic_home`, `cinematic_art` | `true`, `70` | The focused app's artwork fills the top of the screen, as Leanback's backgrounds did |
| `cinematic_dots`, `cinematic_white_focus` | `false`, `true` | No position dots; a plain white focus |
| `dock_title`, `show_greeting`, `dock_band` | `false` | No large app name, no greeting, no band behind the first row |
| `columns`, `rows_below_dock` | `5`, `2` | Rows of five banners, three rows on screen |
| `glass_style`, `focus_overshoot` | `rim`, `false` | A quiet rim on the focused banner, no bounce |
| `ambient_glow`, `app_glow` | `false` | No colour glow behind the rows |
| `icon_only_buttons`, `icon_only_inputs` | `true` | Inputs, Wi-Fi and Settings as round icon buttons |

The headings are names, not sorting: the rows hold your own apps in your order, as everywhere else in the
launcher. Change `row_titles` to suit (Leanback's own rows were "Apps" and "Games"). On a TV with no
search app, the orb says it can't open search.

Tested on an ASUS/MOKA M1G, Android TV 11.
