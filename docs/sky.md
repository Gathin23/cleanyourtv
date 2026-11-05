# Sky

An optional backdrop that follows the clock: night blues until dawn, a soft blue day, then a warm
horizon that peaks at seven in the evening. It is off in the built-in configuration, so a
launcher built from this repository behaves exactly like before until a theme turns it on.

```json
{ "sky_intensity": 60 }
```

![The backdrop at dusk](sky-sunset.jpg)

Push that as a theme's `config.json` and restart Home:

```bash
TV_HOST=192.168.1.50 tools/theme.sh themes/your-theme
```

## What it draws

A full-screen gradient in three bands — top, middle, bottom — with a soft warm glow near the horizon
whose horizontal position follows the sun: left at sunrise, right at sunset. `sky_intensity` is its
opacity, 0 to 100; the colours themselves are interpolated between a handful of anchors through the
day, which is what makes it drift instead of switching at fixed hours.

It sits behind everything, over the wallpaper when there is one, and the palette was chosen dark on
purpose: the white clock, the greeting, the hero name and the dock band all keep their contrast at
the default 60.

## Where the sun is

With nothing set, the day the sky is drawn on is a fixed temperate one: sunrise 07:30, sunset 19:00,
counted on the TV's own clock. The warm glow therefore peaks at seven in the evening wherever the TV
is, and its position travels left to right through that day — which for most of Europe lands a couple of
hours before the real sunset in summer, and after it in winter.

To pin the sky to the real sun, set `latitude` and `longitude` in decimal degrees (south and west
negative) in `launcher/app/src/main/res/values/config.xml`, before building the launcher:

```xml
<string name="latitude" translatable="false">54.69</string>
<string name="longitude" translatable="false">25.28</string>
```

The sky then stretches the same anchors onto that place's real sunrise and sunset: NOAA's solar
position equations in `Sun.java`, run on the TV from today's date and the system time zone. No
network, no location permission, nothing to query, and a tenth of a degree only moves sunset by
about half a minute, so a city's coordinates are plenty.

Two days keep that fixed day instead: one with no sunrise or sunset at all (polar night, or the
midnight sun), and one whose sunset the arithmetic puts past midnight, as in Reykjavik in June.
Nothing is guessed from the time zone: it only decides which clock the minutes are counted on.

**A theme cannot set this.** `sky_intensity` comes from a theme's `config.json`, but the place is a
launcher build setting — `SkyView` reads those two string resources and `Config` never looks for them
in a theme. A theme that wants the right sun says so in its own README, with the two lines above.

## Cost

`Sky` is pure maths — no Android types, no clock of its own; the caller passes the minutes since
midnight, which is why the palette is unit-tested on the JVM. `SkyView` renders one 480×270 bitmap
and redraws it once a minute, from the same minute tick that updates the clock. Nothing is animated
and nothing is requested: the backdrop is the only change, and a TV with 1 GB of RAM has nothing to
keep up with.

Turning the key off restores the plain background (or the theme's `wallpaper`/`background_color`)
on the next Home start.
