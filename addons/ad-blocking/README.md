# Ad Blocking

Your network's ad blocker on the TV: today's queries, how many were blocked and the share, from
[Pi-hole](https://pi-hole.net) (version 6, or version 5) or [AdGuard Home](https://adguard.com/adguard-home/overview.html),
and one OK to pause blocking for five minutes when a site or app won't work. A community add-on, not made by or
affiliated with either project. For the [Clean Your TV](../../README.md) launcher it declares a
[module card](../../docs/modules.md): once you answer **Show card** its tile reads "Ad Blocking" with a red glow.
On any other Android TV home it is an ordinary app.

![Ad Blocking on a TV, with demo data](screenshot.jpg)

| Row | What it does |
|---|---|
| **Blocking** | On or Paused. OK pauses blocking for 5 minutes, or turns it back on. |
| **Queries today**, **Blocked today**, **Blocked share** | The server's own counters. |
| **Blocker** | Pi-hole or AdGuard Home; OK switches. |
| **Address**, **Username** (AdGuard Home), **Password** | The connection. |

It reads the server every 30 seconds while the screen is open, and nothing when it is closed.

## How it talks to each server

- **Pi-hole 6:** `POST /api/auth` with the password (or an app password, needed when two-factor login is on), then
  `GET /api/stats/summary` and `GET /api/dns/blocking` with the session id, `POST /api/dns/blocking` to pause,
  and `DELETE /api/auth` at the end of every round, so it never holds one of Pi-hole's limited sessions. A
  Pi-hole without a password needs none.
- **Pi-hole 5** (when `/api/auth` isn't there): `/admin/api.php?summaryRaw&auth=<API token>`, and `disable=300` or
  `enable` to pause. The API token is under Settings, API in Pi-hole's web page.
- **AdGuard Home:** `GET /control/stats` and `GET /control/status` with HTTP basic authentication, and
  `POST /control/protection` to pause.

## Setup

Open the app and fill in the address and login with the TV's keyboard, or push them from a computer as a file so
the password is never typed on the TV or put on a command line:

```bash
echo '{"kind": "pihole", "url": "http://192.168.1.2", "password": "..."}' > backup/ad-blocking.json
# AdGuard Home: {"kind": "adguard", "url": "http://192.168.1.2:3000", "user": "admin", "password": "..."}
TV_HOST=<tv ip> addons/ad-blocking/install.sh backup/ad-blocking.json
```

The app takes the file into its own storage and deletes it. Keep the file outside the repo (`backup/` is
git-ignored).

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Your Pi-hole or AdGuard Home, and nothing else: no telemetry, no update checks, no ads. |

Plain `http://` is allowed for the home network (`res/xml/network.xml`). The password stays in the app's own
storage (backups are off) and is sent only to the address you entered. `ConfigProvider` takes a pushed file and
only the ADB shell can use it (it requires `WRITE_SECURE_SETTINGS`). No background service, accounts or libraries.

## Build and install

JDK 17 and Android SDK platform 34 with build-tools 34.0.0, as for the launcher:

```bash
TV_HOST=<tv ip> addons/ad-blocking/install.sh [config.json]
```

To build only: `cd addons/ad-blocking && ./gradlew assembleRelease`; the JVM tests run with
`./gradlew testReleaseUnitTest`.

## Limits

- **Self-signed HTTPS** certificates are refused. Use `http://` on the home network.
- **Today** is the server's own window: Pi-hole counts the last 24 hours, AdGuard Home its statistics period.

## Tested on

MOKA R8G (Android 14), on a home app that implements the module-card spec, against a stand-in Pi-hole 6 on the home network answering with the documented response shapes: the config pushed through `ConfigProvider`, login, the numbers, pause and resume, and the session closed after each round. **Not yet tried against a real Pi-hole or AdGuard Home:** the author has neither; reports are welcome. Pi-hole 5 and AdGuard Home are covered by JVM tests against their documented responses and local stand-ins. The screenshot shows that demo data, with the address blurred.

## Maintainer

[@maxhayim](https://github.com/maxhayim)
