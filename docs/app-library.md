# App library

## Optional selection mode

Set `"app_library_selection": true` along with `"app_library": true` in a theme,
or enable both booleans in `config.xml`, to use the library as a Home visibility
picker. It is off by default and the standard behavior below is unchanged.

In selection mode, OK toggles the green checkmark. Checked apps come first; both
groups are alphabetized by displayed name using the TV's locale. After hiding an
app, focus stays nearby on the next app (the previous app at the end), rather
than following the hidden app into the second group. Adding an app focuses it
in the checked group. Hold-OK actions are omitted in this mode; it only controls
Home shortcuts and never uninstalls apps.

## Standard behavior

Set `"app_library": true` in a theme’s `config.json`, or enable the `app_library`
bool in `config.xml`. It is off by default. A small glass pill appears after the
last row of Home apps.

The horizontal library lists installed apps with a TV or ordinary Android launch
activity, preferring the TV entry when both exist. It excludes services and apps
without a launch activity. Ordinary Android apps may still need touch or a mouse.

Apps missing from Home come first. Apps first installed within the last seven
days lead that group, newest first, with a green dot; older missing apps are
alphabetical. Apps already on Home follow alphabetically, with a checkmark and a
subtle tint. Updating an existing app does not mark it as new.

- Press OK on a missing app to add it to Home. Focus advances to the next missing
  app without following the new shortcut across the catalogue.
- Press OK on a checked app to open it.
- Hold OK for Open, Add to Home or Remove from Home, and App info. The menu uses
  the existing Inputs dropdown resources and stays over the selected card.
- Back closes the menu first, then the library. Down from the last Home row or
  Right from the final app reaches the library pill; Up returns to the last row.
  Hebrew mirrors the horizontal direction, so Left from the final app reaches it.

With `tile_menu` on, holding OK on a Home tile offers Remove from Home as well, next to Move and App info.
The remote's Menu key opens that same menu on the focused tile. With `tile_menu` off,
Menu keeps its normal behavior. Settings-key mappings take priority; Menu is left alone
on header buttons, while another popup is open, and during reordering.
Every checked app can be removed from Home, including the theme’s default apps.
It stays installed and remains in the library, where one press adds it back.
There is no hidden-app section and no uninstall action.

Choices are stored in the launcher’s private `library_apps.txt` and
`library_home_removed.txt`, with at most 128 entries in each. Each file is written
atomically. An unreadable file is left unchanged if an edit fails. Choices
survive restarts and app updates; missing or disabled packages are omitted until
they return. Explicit Home removals override theme defaults until re-added.
Theme config, dock ordering, module-card decisions and other saved tile order
are not edited. Disabling the library button keeps existing Home choices.

The library uses the existing package receiver to refresh after app changes.
Labels and Home membership are cached for each refresh; icons load only for
bound cards. The highlight follows the launcher's animation setting, including
instant movement when animations are off. There is no polling, network, backdrop capture, new service,
permission or dependency. The additional manifest query covers ordinary
LAUNCHER activities on Android 11 and later.
