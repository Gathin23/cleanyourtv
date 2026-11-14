# Match Day

A football scoreboard for Android TV. The **Match Day** module card opens a dark, remote-friendly list of fixtures
for the selected local date, grouped into league cards. It includes the Premier League, Champions League, La Liga,
Serie A, Bundesliga and Ligue 1. Each fixture shows both API-provided team crests, the teams, kick-off time in the
TV's timezone, match status and score when available. Free football-data.org scores are delayed, so the app labels
them accordingly and shows when the schedule was last updated.

## Set up

Open **Settings** to choose SportScore or football-data.org. SportScore is the default and needs no key;
football-data.org remains available and uses your own API key. The key stays saved when you switch providers.
All six competitions appear together, grouped by league. The app reads saved schedules when opened or when the
date changes. It fetches a competition only when that date has no saved schedule, or when you choose **Refresh**. It fetches up to six
competitions concurrently and shows each league as its response arrives. Uncached requests run in batches no more
than once per minute; saved schedules are shown during the cooldown. It has no background refresh or service.
Left and Right date arrows move one day; **Today** returns to the TV's current date. Scroll through the
league cards with Up and Down. Match rows do not open a details screen.

The football-data.org key stays in Match Day's private app storage. The app requests matches from the selected
provider and loads returned crest URLs over HTTPS. SportScore fixtures are requested separately for every UTC
calendar day touched by the TV's local date, then filtered back to that local date. Match responses are cached by
provider, competition and local date, so switching providers never mixes schedules. Existing saved schedules
are replaced only by a successful manual refresh; a date with no saved schedule is fetched once to populate its
cache. Crest files are cached by team ID.
Saved matches remain available when the TV is offline. A missing or unavailable crest is reported for retry; the
app does not substitute team initials.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Read fixtures from SportScore or football-data.org and load API-provided team crests. |

No analytics, ads, location access, account login or background service. The API key is sent only in the
`X-Auth-Token` header to football-data.org. SportScore requires a visible “Powered by SportScore” credit linked
to [sportscore.com](https://sportscore.com/), shown in the scoreboard footer. SportScore documents a free tier of
approximately 10,000 requests per IP per day and edge caching for 60 seconds; Match Day keeps refresh limited to
app open or manual request, with its existing one-minute network cooldown. Crest images use provider-supplied
HTTPS URLs.

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
