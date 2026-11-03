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

## On an Apple Silicon Mac or another ARM computer

The Linux AAPT2 that the Android Gradle plugin downloads is built for x86-64 only, so both scripts build and run
the image as `linux/amd64` whatever the computer is; Docker Desktop runs it under Rosetta (turn on "Use Rosetta for
x86_64/amd64 emulation" in its settings) or QEMU, and Colima needs
`colima start --vm-type vz --vz-rosetta`. On an M-series Mac the first build took about 7 minutes and
later ones well under a minute ([#2941](https://github.com/Gathin23/cleanyourtv/issues/2941)). Before this, an
arm64 image stopped at `:app:mergeReleaseResources` with `AAPT2 aapt2-8.5.2-11315950-linux Daemon #0: Daemon
startup failed` or `rosetta error: failed to open elf at /lib64/ld-linux-x86-64.so.2`. If an older copy of the
script left that arm64 image behind, remove it once so the next run builds the right one:

```bash
docker image rm cleanyourtv-android-build
```

A native build works on these computers too: Homebrew's `openjdk@17` and an Android SDK with platform 34 and
build-tools 34.0.0, which the install script below finds on its own when run without `SKIP_BUILD`.

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

That folder has to be one Docker can mount, so both twins refuse it before anything is built when it is not
there or not written in full: Docker makes a missing folder itself (empty, and owned by root on Linux) and reads
a relative path as the name of a volume, so either mistake would sign the build with a new key - the failure
above, reached without a word. Write the path in full, `~/.android` inside quotes is not expanded.

To see which key an installed app carries, compare
`apksigner verify --print-certs` on the APK with `keytool -list -v -keystore <dir>/debug.keystore -storepass android`.
