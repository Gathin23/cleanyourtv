# WeatherMint

WeatherMint is a reusable setup prompt for the existing CleanYourTV **Weather** add-on. It is not a separate app or APK. The add-on's current screen shows a chosen city's weather; it does not put weather beside the launcher's greeting or replace Android TV's system screensaver.

To use the existing add-on, follow the [build and install instructions](README.md#build-and-install) or, from a CleanYourTV checkout with ADB connected, run:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/manage.py install weather
```

The repository distributes add-ons as source and does not accept prebuilt APKs. The launcher stays unchanged; add-ons run as separate apps through the module-card contract.

## Prompt

Paste this into a coding assistant in a CleanYourTV checkout:

> I want to configure the CleanYourTV Weather add-on for my TV. Before changing anything, ask me:
>
> 1. Which city and country should provide the weather? If the name is ambiguous, show me the matching places and ask me to choose.
> 2. Which language, locale, time zone, date format and temperature units should it use?
> 3. How often should weather refresh? Recommend once per hour to reduce requests; offer every 30 minutes if I prefer fresher readings.
> 4. Do I want a time-of-day greeting (morning, afternoon, evening) in the add-on's own screen?
>
> Reuse `addons/weather/`; do not create a duplicate weather app. Keep all changes within the add-on. Do not modify `launcher/`, add network access to the launcher, or claim that the add-on changes the launcher's Home screen or the TV's system screensaver. If I require weather beside the launcher's greeting or in the system screensaver, explain that this repository's module-card add-on contract does not provide that integration and ask before proposing a separate design.
>
> Use the selected city's local time zone for the greeting. Show the city name, a small condition icon and temperature. Do not use GPS, accounts, telemetry, ads or a background polling service. Fetch only at the selected interval while the weather screen is visible, and keep the last successful reading if a request fails. State the provider, permissions, network behavior and refresh interval in the README.
>
> Build from source, verify the D-pad flow and screen on a real Android TV, and include a real screenshot only after removing private information. Keep a reversible way to restore defaults.

