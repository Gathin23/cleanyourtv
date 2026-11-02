#!/usr/bin/env bash
# Build the launcher or an add-on inside Docker, so this computer needs no JDK or Android SDK.
# Usage: tools/docker-build.sh [launcher | addons/<name>] [gradle task ...]
#   tools/docker-build.sh                              the launcher, assembleRelease
#   tools/docker-build.sh addons/weather         an add-on
#   tools/docker-build.sh launcher testReleaseUnitTest lintRelease assembleRelease
# The APK lands where a native build puts it (<project>/app/build/outputs/apk/release/).
# Install the launcher with: SKIP_BUILD=1 TV_HOST=<tv ip> launcher/build-and-install.sh
# Guide, signing key included: tools/docker-build.md
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PROJECT="${1:-launcher}"
[ $# -gt 0 ] && shift
TASKS=("$@")
[ ${#TASKS[@]} -gt 0 ] || TASKS=(assembleRelease)
IMAGE="${IMAGE:-cleanyourtv-android-build}"
GRADLE_VOLUME="${GRADLE_VOLUME:-cleanyourtv-gradle}"
# Where the debug signing key lives inside the container. The default is the image's own, kept in the Gradle volume;
# KEY_DIR picks another place in it, HOST_KEY_DIR mounts a folder from this computer (your ~/.android, say).
KEY_DIR="${KEY_DIR:-/gradle/.android}"

PROJECT="${PROJECT%/}"
if [ ! -f "$ROOT/$PROJECT/settings.gradle" ] && [ ! -f "$ROOT/$PROJECT/settings.gradle.kts" ]; then
  echo "ERROR: $PROJECT is not a Gradle project in this repository (try launcher or addons/<name>)." >&2
  exit 1
fi
command -v docker >/dev/null || { echo "ERROR: docker is not installed or not on PATH." >&2; exit 1; }

KEY_ARGS=()
if [ -n "${HOST_KEY_DIR:-}" ]; then
  # Docker bind-mounts an absolute path that is there, and nothing else: given a folder that is not there it
  # creates it (empty, and owned by root on Linux), and given a relative path it reads the name as a volume. So
  # /key would be an empty folder or volume, the build would sign with a new debug key, and the next install over
  # the app already on the TV would fail with INSTALL_FAILED_UPDATE_INCOMPATIBLE - the failure "The signing key"
  # in tools/docker-build.md explains, reached without a word. A quoted '~/.android' lands here too: no shell
  # expands a tilde inside a variable. A drive-letter path is absolute as well, which is the form a Windows
  # reader's HOST_KEY_DIR takes under Git Bash - tools/theme.sh reads C:/Users/... the same way (#1796).
  case "$HOST_KEY_DIR" in
    /*|[A-Za-z]:[\\/]*) ;;
    *)
      echo "ERROR: HOST_KEY_DIR=$HOST_KEY_DIR is not an absolute path." >&2
      echo "Docker reads a relative one as the name of a volume, so the build would make a new debug key." >&2
      echo "Pass the folder your native build uses: \"\$HOME/.android\" ('~/.android' in quotes is not expanded)." >&2
      exit 1
      ;;
  esac
  if [ ! -d "$HOST_KEY_DIR" ]; then
    echo "ERROR: HOST_KEY_DIR=$HOST_KEY_DIR is not a folder on this computer." >&2
    echo "Docker would not mount your native key there, the build would make a new debug key, and an install" >&2
    echo "over the app already on the TV would fail with INSTALL_FAILED_UPDATE_INCOMPATIBLE (tools/docker-build.md)." >&2
    exit 1
  fi
  KEY_ARGS=(-v "$HOST_KEY_DIR:/key")
  KEY_DIR=/key
fi

# Always x86-64, on an Apple Silicon Mac or an ARM board too: the Linux AAPT2 the Android Gradle plugin downloads
# is published for x86-64 only, so an arm64 image stops at :app:mergeReleaseResources minutes in (#2941). Docker
# Desktop runs it under Rosetta or QEMU; on an x86-64 computer the flag changes nothing.
PLATFORM=linux/amd64
if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
  echo "Building the $IMAGE image once (JDK 17, Android SDK 34, build-tools 34.0.0; a few minutes)..."
  docker build --platform "$PLATFORM" -t "$IMAGE" "$ROOT/tools/docker"
fi

# Git Bash on Windows rewrites /paths in arguments and gives POSIX paths Docker can't mount.
SRC="$ROOT"
USER_ARGS=()
case "$(uname -s)" in
  MINGW* | MSYS* | CYGWIN*)
    export MSYS_NO_PATHCONV=1
    SRC="$(cd "$ROOT" && pwd -W)"
    ;;
  *)
    # The caller's uid, so the build output is not owned by root.
    USER_ARGS=(--user "$(id -u):$(id -g)")
    ;;
esac
# The whole repository is mounted: an add-on's gradlew may call ../../launcher/gradlew.
docker run --rm --platform "$PLATFORM" ${USER_ARGS[@]+"${USER_ARGS[@]}"} ${KEY_ARGS[@]+"${KEY_ARGS[@]}"} \
  -v "$SRC:/repo" -v "$GRADLE_VOLUME:/gradle" -w "/repo/$PROJECT" \
  -e ANDROID_USER_HOME="$KEY_DIR" \
  "$IMAGE" sh ./gradlew --no-daemon --console=plain "${TASKS[@]}"

for apk in "$ROOT/$PROJECT"/app/build/outputs/apk/*/*.apk; do
  [ -f "$apk" ] && echo "APK: ${apk#"$ROOT"/} ($(wc -c < "$apk" | tr -d ' ') B)"
done
exit 0
