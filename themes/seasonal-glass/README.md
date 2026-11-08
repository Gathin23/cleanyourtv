# Seasonal Glass

A seven-slot glass dock with round header controls and 24 original seasonal illustrations:
one daylight and one moonlit landscape for each month. Winter lights, spring blossom,
summer lakes, the harvest and autumn woodland share a calm, cinematic look.

The collection rotates every 30 minutes in filename order. The existing `wallpaper_moods`
feature selects daylight pictures in the morning and evening, and moonlit pictures at
night. The launcher uses its configured sunrise/sunset times, or its documented defaults.
This theme does not select pictures by calendar month and does not open a boot welcome.
It works with the standard launcher without changes to its code or permissions.

The theme sets seven columns but leaves `dock_apps` unspecified: the launcher's configured
favourites remain, with free slots filled normally. The screenshots show the owner's dock;
those apps are not installed or selected by the theme.

Apply with the standard tools from the repository root:

```sh
TV_HOST=<tv ip> tools/theme.sh themes/seasonal-glass
```

On Windows:

```powershell
tools\theme.ps1 -TvHost <tv ip> themes\seasonal-glass
```

Undo with the same tool's reset option, or apply your previous theme again.
Tested on an IRBIS 43F1YDX000BS2 running Android 9. `screenshot.jpg` is a real TV capture.
See [CREDITS.md](CREDITS.md) for the artwork and theme attribution.
