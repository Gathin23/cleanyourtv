# Building in Docker

`tools/docker-build.sh` (and its twin `tools/docker-build.ps1`) builds the launcher or an add-on inside a container
that has JDK 17, Android SDK 34 and build-tools 34.0.0. This computer needs Docker and nothing else: no JDK, no
Android Studio, no `ANDROID_HOME`. The APK is built from this checkout, on this computer, and lands where a native
build puts it.

## Build

```bash
tools/docker-build.sh                                          # the launcher (assembleRelease)
tools/docker-build.sh addons/weather                     # an add-on
tools/docker-build.sh launcher testReleaseUnitTest lintRelease assembleRelease   # what CI runs
```

```powershell
tools\docker-build.ps1
tools\docker-build.ps1 -Project addons\weather
tools\docker-build.ps1 -Project launcher -Tasks testReleaseUnitTest,lintRelease,assembleRelease
```

The first run builds the image from `tools/docker/Dockerfile` (a few minutes, about 1.5 GB) and Gradle downloads its
distribution into the `cleanyourtv-gradle` volume; later builds reuse both. On Linux and macOS the container runs as
your user, so the build output is not owned by root.

| Variable | Default | What it changes |
|---|---|---|
| `IMAGE` | `cleanyourtv-android-build` | The image name |
| `GRADLE_VOLUME` | `cleanyourtv-gradle` | The Docker volume that keeps Gradle's caches and the signing key |
| `KEY_DIR` | `/gradle/.android` | Where the debug signing key is kept inside the container |
| `HOST_KEY_DIR` | (none) | A folder on this computer to take the key from instead, such as `~/.android` |

## Install

The launcher's install script builds with the JDK on this computer unless told not to. With the APK built in
Docker:

```bash
SKIP_BUILD=1 TV_HOST=<tv ip> launcher/build-and-install.sh
```

```powershell
$env:SKIP_BUILD = '1'; launcher\build-and-install.ps1 -TvHost <tv ip>
```

Everything else the script does (the SDK check, the Home guard, the stock launcher) is unchanged. An add-on's APK
is pushed and installed as [`addons/README.md`](../addons/README.md) shows, without its build step.

## The signing key

The release APK is signed with a debug key, and Android only installs an update signed with the same key as the
app already on the TV. Docker keeps its key in the Gradle volume, which is a different key from the one a native
build uses (`~/.android/debug.keystore`). Switching between a Docker build and a native build therefore fails
with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

- Build one app the same way every time, or
- sign Docker builds with your native key: `HOST_KEY_DIR=~/.android tools/docker-build.sh`, or
- uninstall the app on the TV once and install the new build (its settings on the TV are lost).

To see which key an installed app carries, compare
`apksigner verify --print-certs` on the APK with `keytool -list -v -keystore <dir>/debug.keystore -storepass android`.
