# Tatami

A screensaver for Android TV, and a card on cleanyourtv's Home. Small rooms rearrange themselves in slow, quiet
motion, drawn live on the TV. Inspired by the ending of *The Tatami Galaxy* (2010). It has two looks, and by
default they take turns:

- **Nagaya**: rows of small, warm rooms, like a row house. A few walls at a time glide to new places, each on its
  own time, and then rest. The doors swing open and shut in a slow ripple, and the camera slowly rolls, zooms and
  drifts.
- **Yojōhan**: olive rooms cut like tatami mats around a half mat. One room grows while the room next to it
  shrinks.

![Tatami full screen: Nagaya's walls glide and its doors swing, a dip to dark swaps it for Yojōhan, and one room grows as the next shrinks](https://raw.githubusercontent.com/PBandDev/tatami-home/bf3b3d9eaa09572d4a962dc57241dd48c000991c/docs/tatami.webp)

- Package: `io.github.pbanddev.tatami`
- Author: [PBandDev](https://github.com/PBandDev) ([issues](https://github.com/PBandDev/tatami-home/issues))
- Tested on: Sony Android TV 10 (model private), 1080p, a steady 60 fps

## Permissions

None. No network access (no `INTERNET` permission): nothing leaves the TV. No background service: Tatami runs
only while the screensaver or its own screen is showing. It is drawn live with OpenGL ES 2.0 and uses no footage
from the show.

## Install

From your cleanyourtv checkout, with its `tools/venv` set up as its README says:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/manage.py install tatami
```

Or tell your coding agent: *Install the Tatami add-on from addons/tatami on my TV at \<tv ip\>.*

Or run its script yourself:

```bash
TV_HOST=<tv ip> addons/tatami/install.sh                        # macOS, Linux, Git Bash
powershell -ExecutionPolicy Bypass -File addons\tatami\install.ps1 -TvHost <tv ip>   # Windows
```

No JDK or Android SDK on this computer? Build it with Docker, then install what it built:

```bash
tools/docker-build.sh addons/tatami
SKIP_BUILD=1 TV_HOST=<tv ip> addons/tatami/install.sh
```

The script builds Tatami, installs it, makes it the TV's screensaver (it keeps the old one, for the undo below),
says when the screensaver starts, and opens Tatami once so you see it. Then press Home: the launcher asks once
whether to show the Tatami card.

## Use it

- **The card:** press OK on it to see Tatami full screen. Press OK again for the effects, Back to leave.
- **The effects:** the first row picks the **Look**: Both (the default), Nagaya or Yojōhan. The next sets how
  often Both swaps looks: every 5, 10, 30 or 60 minutes, with a short dip to dark. Below them, turn each effect on
  or off and see it change behind the list: film grain, vignette, tilt-shift edges, doors, fixtures, and camera
  drift and zoom. In Nagaya, the vignette switch turns its colour wash on and off, the camera switch holds the
  view still (the walls and doors still move), and fixtures do nothing.
- **The screensaver** uses the same look and effects. It starts when the TV's own wait runs out: Settings >
  Device Preferences > Screen saver > When to start. To see it now:
  `TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'am start -n com.android.systemui/.Somnambulator'`
- **Any launcher:** Tatami is also a normal app tile, and the screensaver works with every launcher.

## Remove

```bash
TV_HOST=<tv ip> addons/tatami/install.sh --uninstall    # the old screensaver back, Tatami removed
```

## Tatami behind your apps

Want Tatami moving behind the apps on Home, not only as a screensaver? [Tatami Home](https://github.com/PBandDev/tatami-home)
is cleanyourtv's launcher with Tatami built in. You need only one of the two.

## License

MIT: [LICENSE](LICENSE). How Tatami is drawn and tested: [how it works](https://github.com/PBandDev/tatami-home/blob/main/docs/how-it-works.md).
