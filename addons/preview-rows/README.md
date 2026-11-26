# Preview rows

Shows the rows other apps publish as Android TV preview channels (`TvContract.PreviewPrograms`, such as YouTube, Prime Video, Netflix, DAZN or Jellyfin) as rows of still cards. Pressing **OK** on a card opens that item using its `intent_uri` (falling back to the channel's `app_link_intent_uri` or the app itself). It never autoplays video, runs no background service and never polls: it reads `TvProvider` only when the screen is shown or refreshed.

For the [Clean Your TV](../../README.md) launcher it declares a [module card](../../docs/modules.md): once you answer **Show card**, its tile reads "Preview rows · Channel picks" with a blue glow. On any other Android TV home it works as a standalone Leanback app.

Not yet tested on a real TV: a screenshot and a report from one are welcome.

![Preview rows on a TV](screenshot.jpg)

## Permissions

| Permission | Why |
|---|---|
| `android.permission.READ_TV_LISTINGS` (`READ_TV_LISTINGS`) | Runtime permission to query `TvContract.Channels` and `TvContract.PreviewPrograms` for rows marked `searchable`. Grant at the prompt or with `pm grant`. |

No `INTERNET` permission in v1: nothing leaves the TV. If a card's poster URI is `https://` or `http://`, the card shows the publishing app's banner or icon plus the title text instead. Local `content://` and `android.resource://` posters are decoded offline. No background service, no polling, no accounts, telemetry or ads.

## Build and install

JDK 17 and Android SDK platform 34 with build-tools 34.0.0, as for the launcher (or build in Docker with `tools/docker-build.sh` / `tools\docker-build.ps1`; see [`tools/docker-build.md`](../../tools/docker-build.md)).

**macOS / Linux:**

```bash
cd addons/preview-rows && ./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease && cd ../..
```

**Windows (Docker, no local JDK required):**

```powershell
tools\docker-build.ps1 -Project addons\preview-rows -Tasks testReleaseUnitTest,lintRelease,assembleRelease
```

**Install and grant `READ_TV_LISTINGS` over ADB** (or using [`tools/tv.py`](../../tools/tv.py)):

```bash
adb install -r addons/preview-rows/app/build/outputs/apk/release/app-release.apk
adb shell pm grant io.github.jain05vaibhav.previewrows android.permission.READ_TV_LISTINGS
```

## Limits

From [`docs/watch-next.md`](../../docs/watch-next.md):

- **`ACCESS_ALL_EPG_DATA` is `signature|privileged`.** A sideloaded app cannot hold it, so with `READ_TV_LISTINGS` alone the query returns only this app's own rows plus rows other apps mark `searchable`. Apps that publish nothing or only non-searchable rows do not appear.
- **No `INTERNET` permission in v1.** Most streaming apps (YouTube, Prime Video, Netflix, DAZN) publish `https://` poster URIs; those cards show the app's banner or icon plus the title text instead of fetching remote images. Only `content://` and `android.resource://` posters render directly.
- **`browsable` may be `0`.** When the stock launcher is disabled, nothing marks channels as browsable for Home, so this add-on does not require `browsable = 1` and drops channels that have zero preview programs (such as empty live channels).
- **Vendor and app quirks.** Some apps publish promotional or stale rows, or place a tagline in `title` instead of the show name; some TV firmwares omit optional `TvProvider` columns. Queries always pass an explicit named projection (never `null`) and no selection (`null`), falling back cleanly on missing columns, `SecurityException` or empty tables.


