# Smart Home

Your [Home Assistant](https://www.home-assistant.io) on the TV: scenes, lights and switches, climate, temperature
and humidity sensors, doors and windows, and locks, in sections you work with the remote. OK runs a scene or
switches a light; ◀ ▶ dims a light by 10 % or moves a thermostat by half a degree. A community add-on, not made by
or affiliated with the Home Assistant project. For the [Clean Your TV](../../README.md) launcher it declares a
[module card](../../docs/modules.md): once you answer **Show card** its tile reads "Smart Home" with a blue glow.
On any other Android TV home it is an ordinary app.

![Smart Home on a TV, with demo data](screenshot.jpg)

| Section | Shows | With the remote |
|---|---|---|
| Scenes | `scene.*` | OK runs it |
| Lights and switches | `light.*`, `switch.*`, `fan.*`, `input_boolean.*`, on or off with brightness | OK toggles, ◀ ▶ dims a light |
| Climate | `climate.*`: room temperature → set temperature | ◀ ▶ moves the set temperature |
| Sensors | `sensor.*` with device class temperature or humidity | Shown only |
| Doors and windows | `binary_sensor.*` with device class door, window, garage door or opening | Shown only |
| Locks | `lock.*`, locked or unlocked | **Shown only: a TV remote never unlocks a door** |

At most 80 entities, sorted by name within each section; unavailable ones are dimmed. It reads `GET /api/states`
every 10 seconds while the screen is open, and nothing when it is closed.

## Setup

You need the server's address (for example `http://192.168.1.10:8123`) and a long-lived access token: in Home
Assistant, open your profile, then Security, then Long-lived access tokens, Create token. Then either:

- open the app and fill in **Home Assistant address** and **Access token** with the TV's keyboard (or the phone
  remote's), or
- put both in a file and push it from a computer, so the token is never typed on the TV or put on a command line:

  ```bash
  echo '{"url": "http://192.168.1.10:8123", "token": "eyJ..."}' > backup/smart-home.json
  TV_HOST=<tv ip> addons/smart-home/install.sh backup/smart-home.json
  ```

  The app takes the file into its own storage and deletes it. Keep the file outside the repo (`backup/` is
  git-ignored).

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Your Home Assistant server, and nothing else: no telemetry, no update checks, no ads. |

Plain `http://` is allowed for the home network (`res/xml/network.xml`); use `https://` with a certificate from a
public authority (Nabu Casa, Let's Encrypt). The token stays in the app's own storage (backups are off) and is
sent only to the address you entered, as `Authorization: Bearer`. `ConfigProvider` takes a pushed file and only
the ADB shell can use it (it requires `WRITE_SECURE_SETTINGS`, as Frigate Glance's does). No background service,
accounts or libraries.

## Build and install

JDK 17 and Android SDK platform 34 with build-tools 34.0.0, as for the launcher:

```bash
TV_HOST=<tv ip> addons/smart-home/install.sh [config.json]
```

To build only: `cd addons/smart-home && ./gradlew assembleRelease`; the JVM tests run with
`./gradlew testReleaseUnitTest`.

## Limits

- **Self-signed HTTPS** certificates are refused. Use `http://` on the home network or a public certificate.
- **`homeassistant.local`** names only resolve where the TV's network does mDNS name lookups; use the IP address
  if it doesn't.
- **Which entities** is fixed by domain and device class; there is no picker yet.

## Tested on

MOKA R8G (Android 14), on a home app that implements the module-card spec, against a stand-in server on the home network answering with Home Assistant's documented response shapes: the config pushed through `ConfigProvider`, reading the states, running a scene and dimming a light (the requests arrived as the REST API documents them). **Not yet tried against a real Home Assistant:** the author's server was down; a report from someone with one is welcome. The screenshot shows that demo data, with the address blurred. The JVM tests cover parsing, the service calls, the request headers against a local stand-in, and errors.

## Maintainer

[@maxhayim](https://github.com/maxhayim)
