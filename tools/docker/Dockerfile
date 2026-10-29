# The whole Android toolchain, so the host needs nothing but Docker.
# Temurin 17 is what Android Gradle Plugin 8 is tested against; the host JDK is never read.
FROM eclipse-temurin:17-jdk

ARG CMDLINE_TOOLS_VERSION=11076708
ARG COMPILE_SDK=34
ARG BUILD_TOOLS=34.0.0

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_USER_HOME=/gradle \
    DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip curl make git \
    && rm -rf /var/lib/apt/lists/*

# sdkmanager insists on living at cmdline-tools/latest, and the zip unpacks to
# cmdline-tools/, so the move is not cosmetic.
RUN mkdir -p "$ANDROID_HOME/cmdline-tools" \
    && curl -fsSL -o /tmp/cmdline-tools.zip \
    "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" \
    && unzip -q /tmp/cmdline-tools.zip -d "$ANDROID_HOME/cmdline-tools" \
    && mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest" \
    && rm /tmp/cmdline-tools.zip

ENV PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# Licences are accepted here so no build ever stops on a prompt.
RUN yes | sdkmanager --licenses > /dev/null \
    && sdkmanager --install \
    "platform-tools" \
    "platforms;android-${COMPILE_SDK}" \
    "build-tools;${BUILD_TOOLS}" \
    && sdkmanager --list_installed

# The Gradle home is a named volume at run time. Making it world-writable here
# lets the container run as the caller's uid without root-owned files landing
# in the mounted source tree.
RUN mkdir -p /gradle && chmod 777 /gradle
RUN chmod -R a+rX "$ANDROID_HOME"

# Running as the caller's uid means there is no passwd entry and HOME falls back
# to "/", which is root-owned: the Android Gradle Plugin then dies trying to
# create /.android. Point both at the writable Gradle volume instead.
ENV HOME=/gradle \
    ANDROID_USER_HOME=/gradle/.android

WORKDIR /workspace
