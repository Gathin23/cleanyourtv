# World Clock

Optional offline CleanYourTV add-on. It opens from a normal Android TV tile or a
spec-1 module card. The first view contains **Local, London and Sydney**. It is an independent
Android app; no launcher source, configuration, signing identity or Home behavior changes.

Choose up to six clocks. Each shows the time, local date, UTC offset and calendar-day difference
from the TV's local date. Local follows the TV's configured time zone. Named zones use the platform's
rules, including daylight-saving transitions and fractional-hour offsets. Accuracy depends on the
TV's clock and installed time-zone data; this app does not download updates or determine location.

## Remote controls

- Arrows move between the clocks, Add city, Clock format and Screensaver. Horizontal movement is mirrored in RTL.
- OK on a clock opens Change city, Move earlier, Move later and Remove city where applicable.
- Add city opens a searchable list. Press OK in the text field to use the TV keyboard, Down to choose
  a result, or the keyboard's Done action to return to the list. Back cancels without editing clocks.
- Clock format offers the TV setting, 12-hour or 24-hour time.
- At least one clock remains. Back leaves the app. Clock updates change text without rebuilding focus.

City names come from the TV's IANA time-zone catalogue, rather than a complete geographical city
database. Search also accepts region/zone names. The legacy Katmandu spelling is displayed and
searchable as Kathmandu. There is no GPS or network lookup.

![World Clock on an Android TV](screenshot.jpg)

## Screensaver

Screensaver opens a remote-friendly menu with Preview, Background, Motion, Clock brightness and
TV screensaver settings. Choose World Clock in the TV's Android screensaver settings to use it
automatically. Installing the app or opening Preview does not select it or change the TV's timeout.
The system owns activation and input dismissal; Preview returns on a fresh button press or Back.
Some firmware intercepts power/volume keys before an app receives them.

Every configured clock appears together in the same saved order: one row for one to three clocks,
two rows for four to six. It uses the normal app's cities and clock format. There is no paging or
second city list to maintain. The clocks float directly over the background without panels.

World map (the default), Starfield, Sky gradient and Black are bundled offline backgrounds. Motion
slowly drifts the clocks within a reserved inset and animates the starfield/gradient at at most 15
updates per second. Reduced motion keeps everything stationary and schedules no animation frames.
Clock brightness changes text opacity only; it does not write system brightness. Android may also
dim an actual screensaver according to the TV's power policy. No screen-on flag or wake lock is used.

CleanYourTV's Home-only idle clock (`home_idle_clock_minutes`) is separate from Android's selected
screensaver. Selecting World Clock replaces the Android dream, not that explicit Home Activity.
The Home-only timer defaults to 0/off; leave it off when using the Android screensaver. This add-on
never edits the launcher, its theme, Home role or settings. TV firmware without a screensaver chooser
can still use Preview; ordinary apps do not have permission to write the secure selection directly.

The [decorative map silhouette](app/src/main/res/raw/world_map.txt) is made with Natural Earth,
[public-domain data](https://www.naturalearthdata.com/about/terms-of-use/), from
[1:110m land, v5.1.2](https://github.com/nvkelso/natural-earth-vector/blob/v5.1.2/geojson/ne_110m_land.geojson).
Land and lake rings use the [Equal Earth projection](https://proj.org/en/stable/operations/projections/eqearth.html).
They were simplified at 0.10 degrees, small rings below 0.15 square degrees omitted and projected
coordinates rounded to 0.1 viewport units. Regenerate with the [map generator](tools/build_map.py)
and its pinned input. All projection calculations run at development time. It is a background, without
borders or city markers. A retained Canvas Path avoids a full-screen bitmap or map-library cache.
The 96 stars are generated once from a fixed seed. There are no map tiles or downloaded assets.

## Permissions

No requested permissions. No network, telemetry, advertisements, subscription, account, native
library, overlay, accessibility service, boot receiver, alarm or always-running background worker.
The system binds ClockDream only as a screensaver, protected by BIND_DREAM_SERVICE on its service;
this is a restriction on callers, not a permission the app requests. Preview is not exported.
Local SharedPreferences store the selected zone IDs, clock format and screensaver appearance.
Automatic backup/device transfer is disabled. Clock text refreshes at minute boundaries and reacts
to system time, date and time-zone changes while visible. Leaving the Activity, hiding the window
or stopping/detaching the dream stops callbacks. The app does not keep the TV awake.

## Build

JDK 17, Gradle 8.9, Android SDK platform/build-tools 34; Android Gradle plugin 8.5.2.

```sh
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
```

Use `--offline` when the build dependencies are already cached. The release build uses the standard
local Android debug signing configuration. Retain the same private signing
key for later in-place updates; no key or prebuilt APK belongs in a contribution.

The app targets Android 8.0/API 26 and later and has no native ABI restriction. JUnit is test-only;
there are no runtime dependencies. A module card has a static caption and opens this screen; it
cannot display live clocks inside the launcher's Home grid. Screensaver support uses the platform DreamService.

## Validation

Tested on a Samsung C71KW-200 / AT&T Osprey, Android TV 11, ARMv7, at 1920×1080.
The screenshot above is from the physical TV, resized to 1280×720. Input checks use injected
D-pad/OK/Back through Android's input dispatcher, rather than a physical handset.

The 41 JVM tests cover DST gaps/overlaps, different transition dates, local-zone changes,
date-line differences, fractional-hour offsets, formatting, saved settings, list edits, search,
all focus targets/reachability for one to six clocks in LTR/RTL, bounded drift, row counts,
brightness ordering and preview input cancellation/orphan releases. Release assembly and lint
pass with zero errors or warnings.

A local-only Android instrumentation build passed 56 additional cases on that TV: damaged
SharedPreferences fallback; all one-to-six-clock layouts at 100%, 150% and 200% font scale in
LTR and RTL configuration contexts; preview callback/receiver cleanup for all backgrounds and
motion choices; and all 156 arrow transitions on the actual Activity. The layout cases measure
text width and height, and caught a city-label clipping bug at 200% text, fixed by keeping the
autosize minimum in physical dp while allowing the preferred size to follow font scale.
These alternate contexts do not change the TV's global language or font settings. The test-only
instrumentation and manifest changes were removed after testing.

The final signed release was also exercised through the TV UI: all four backgrounds, all three
text-brightness levels, moving and stationary starfield, all six saved clocks in Android's native
dream, input dismissal and city/appearance persistence after a cold process restart. Original
cities and appearance were restored; the selected screensaver and existing five-minute system
timeout were retained. This signed build is 54,175 bytes (about 53 KiB); signing can change APK size.

The current repository suite ran 1,497 tests: 1,496 passed, with one optional real-Perfetto-trace
case skipped. The prior 0.1 device checks covered city entry on the TV keyboard, change/move/remove,
empty-search cancellation, the six-clock limit, 24-hour format, cold restart and module-card launch.

The TV's installed time-zone database supplies the rules. There is no network time sync,
working-hours overlay or live clock inside the launcher's grid. Other Android versions and
screen resolutions still need physical-device coverage; RTL/font-scale layout contexts are
not a substitute for a full system-locale/physical-handset check. No memory, CPU or frame-time
performance claim is made. No APK or private key is included in this source contribution.
