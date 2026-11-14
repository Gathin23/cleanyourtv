# Match Day

A football scoreboard for Android TV. The **Match Day** module card opens a dark, remote-friendly list of fixtures
for the selected local date, grouped into league cards. It includes the Premier League, Champions League, La Liga,
Serie A, Bundesliga and Ligue 1. Each fixture shows both API-provided team crests, the teams, kick-off time in the
TV's timezone, match status and score when available. Free football-data.org scores are delayed, so the app labels
them accordingly and shows when the schedule was last updated.

## Set up

Open **Settings**, enter your own football-data.org API key, and choose **Save key**. All six competitions appear
together, grouped by league. The app reads saved schedules when opened or when the date changes. It fetches a
competition only when that date has no saved schedule, or when you choose **Refresh**. It fetches up to six
competitions concurrently and shows each league as its response arrives. Uncached requests run in batches no more
than once per minute; saved schedules are shown during the cooldown. It has no background refresh or service.
Left and Right date arrows move one day; **Today** returns to the TV's current date. Scroll through the
league cards with Up and Down. Match rows do not open a details screen.

The key stays in Match Day's private app storage. The app requests matches from
[football-data.org](https://www.football-data.org/) and loads the returned crest URLs over HTTPS. Match responses
are cached by competition and local date. Existing saved schedules are replaced only by a successful manual
refresh; a date with no saved schedule is fetched once to populate its cache. Crest files are cached by team ID.
Saved matches remain available when the TV is offline. A missing or unavailable crest is reported for retry; the
app does not substitute team initials.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Read fixtures from football-data.org and load the API-provided team crests. |

No analytics, ads, location access, account login or background service. The API key is sent only in the
`X-Auth-Token` header to football-data.org. Crest images are requested from their API-provided HTTPS URLs.

## Build and install

JDK 17 and Android SDK platform 34 with build-tools 34.0.0:

```bash
cd addons/match-day && ./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
TV_HOST=<tv ip> ../../tools/venv/bin/python -I ../../tools/tv.py --push app/build/outputs/apk/release/app-release.apk /data/local/tmp/match-day.apk
TV_HOST=<tv ip> ../../tools/venv/bin/python -I ../../tools/tv.py 'pm install -r /data/local/tmp/match-day.apk; rm -f /data/local/tmp/match-day.apk'
```

On Home, the launcher asks once whether to show the **Match Day** card. The module card follows
[`docs/modules.md`](../../docs/modules.md).

## Tested on

Xiaomi Mi TV MOOQ1, Android TV 10, build QTG3.200305.006.5028: candidate update preserved the saved API key.
Date navigation, November 7, 2026 scores, all returned league cards and team crests were verified on the TV.

## Screenshot

[`screenshot.jpg`](screenshot.jpg) is a 16:9 capture from the verified TV scoreboard.
