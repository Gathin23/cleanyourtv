# Quiet Machine (cleandavystv fork)

This native interface belongs to `davygit67/cleandavystv`, on
`feature/quiet-machine-rams-ui`. It is not an upstream CleanYourTV change.

## Using it

- Home: time-aware greeting, real 24-hour device clock/date, monochrome panorama,
  WATCH (the existing live-TV/HDMI picker), and existing configured tiles in their
  saved order. Missing apps are handled by the existing adapter.
- Library: a three-column installed-app library, with actual app icons and launch
  intents. Search matches names and package identifiers. Music filters music apps;
  Movies, Series and Documentaries explicitly state that no catalog is connected.
  No movie titles, watch history or availability are invented. A cross-service
  catalog would need separately approved integrations and service credentials.
- Control: real media volume where Android permits it; system settings fallbacks;
  ambient light explicitly not connected; persistent Off/Day/Evening/Night modes.
  Focus dims this launcher surface and requests window brightness. Firmware may
  ignore the brightness request; it never changes the TV's global picture settings.
- D-pad moves focus; the selected control has a thicker orange border, a neutral
  inset fill and slight elevation. OK activates it; arrows adjust a focused volume
  slider. Up/Down leaves the slider. Back returns to Home; Home retains the original
  activity and remote-helper routes. System tabs open real Android settings.
- Settings → Classic launcher returns to the complete original interface, including
  tile ordering, module cards, and existing menus. The bottom-right Quiet Machine
  button returns to the redesign. No existing preferences are erased.

This is an English personalized fork. Existing translations remain available in the
classic interface and settings. The redesign deliberately does not synthesize
weather, location, light levels or a healthy/connected indicator without a source.

## Photographs

[LA, USC and Florence photo pack](../launcher/quiet-machine-wallpapers/CREDITS.md)
contains attributed, licensed Wikimedia photographs. They stay outside the APK to
keep the launcher within its size budget. The launcher decodes only the selected
photo, downsampled to at most 1024 pixels wide, and renders it grayscale.
Select the panorama and press OK to cycle LA → USC → Florence. The selection persists.
If the pack is absent, an existing `wallpaper.jpg` theme asset is used. No app
configuration is replaced by the photo pack.

## Build

Use JDK 17, Android SDK platform 34 and build-tools 34.0.0:

```sh
cd launcher
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
```

The package remains `com.gathin.tvhome`; minSdk 26, targetSdk 34, permissions,
services, dependencies and install/recovery scripts are unchanged. The release
build retains the upstream debug-signing convention: keep your original signing
keystore. APKs are not committed.

## Safe installation and rollback (not performed on a physical TV)

1. Keep the currently working APK, its signing key, and the existing theme/settings
   backups before updating. Keep stock Home enabled until the new launcher has
   been checked. Do not disable packages or enable new key helpers for this UI.
2. Build with the same signing key as the installed launcher. Verify package and
   signing certificate before an `adb install -r` update. If signatures differ,
   stop and recover the original key; do not uninstall to work around it.
3. After an explicitly authorized connection, update the APK and launch the existing
   `com.gathin.tvhome/.MainActivity` without changing the default Home app. Test the
   physical remote, app launches, live TV/HDMI, screensaver and system settings.
4. To add the photo pack without overwriting config.json or existing wallpapers,
   push each of `la.jpg`, `usc.jpg`, `florence.jpg` to a temporary TV path, then write
   it through the existing theme provider, for example:

   ```sh
   adb -s <tv-serial> push launcher/quiet-machine-wallpapers/la.jpg /data/local/tmp/quiet-la.jpg
   adb -s <tv-serial> shell 'content write --uri content://com.gathin.tvhome.theme/wallpapers/la.jpg < /data/local/tmp/quiet-la.jpg'
   adb -s <tv-serial> shell rm /data/local/tmp/quiet-la.jpg
   ```

   Repeat for USC and Florence. Reopen Home. On Android 8/9, use the existing
   documented theme-tool fallback after backing up and merging the current theme;
   do not replace the current configuration with an empty theme.
5. For immediate UI rollback use Settings → Classic launcher. For code rollback,
   rebuild the previous fork commit with the same signing key and update in place.
   The version code is unchanged. Existing `tools/restore.sh` remains the path for
   restoring a previous debloat operation; no debloat is part of this change.
6. Close wireless/USB debugging after physical-TV validation using the existing
   project procedure. Accessibility-helper keys need actual physical presses;
   injected emulator keys cannot prove those hardware paths.

## Validation

See the delivery report for the actual build, test and emulator results. Physical TV
validation remains necessary for vendor HDMI behavior, hardware volume, settings
handlers, accessibility-helper keys, screensaver timing and firmware brightness.
