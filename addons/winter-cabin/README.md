# Winter Cabin

A silent winter room with falling snow, a flickering fireplace, twinkling tree lights, and
an armchair with a draped throw. Drawn on the TV with Android Canvas; no external assets.

Open the **Winter Cabin** app tile or accept its CleanYourTV module card and press **OK** to
view the scene full-screen. **Back** closes the preview. To make it your idle screensaver,
select **Winter Cabin** in Android TV's screensaver settings (usually under **Device
Preferences**). Use your TV's existing idle and sleep settings. This is not a Home wallpaper
and does not change the launcher or set itself as your screensaver.

## Permissions

None. The manifest requests no permissions. The `android.permission.BIND_DREAM_SERVICE`
on the screensaver service limits binding to Android; the app does not request that
permission. There is no boot receiver, background polling, overlay, data collection, or
network access. The preview keeps the screen awake only while open; the screensaver runs
only when started by Android. Animation stops when the view is no longer visible or active.

## Build

From this folder with JDK 17 and Android SDK 34, run
`./gradlew testReleaseUnitTest lintRelease assembleRelease` (`gradlew.bat` on Windows).
See the [add-ons guide](../README.md#how-an-add-on-works) for source-build and install steps.
Android 8.0 (API 26) is the declared minimum SDK, not a claim of testing on every version.

## Tested on

Hisense SmartTV 4K FFM (Android TV 11, firmware 00.00.01.38): the module card was
discovered, its target displayed the animated preview, and the selected Android screensaver
started with the add-on process cold. Snow, fire and tree lights moved in both modes, and
Back closed them. The TV was not rebooted or left to reach its idle timeout during this test.

## Attribution

MIT, under the repository [license](../../LICENSE). Original procedural scene by
[TylerBuza](https://github.com/TylerBuza) with AI coding assistance. Its Activity and
DreamService follow CleanYourTV add-on patterns; the Gradle wrapper keeps its upstream
Apache 2.0 license. Background: [Winter Cabin proposal](https://github.com/Gathin23/cleanyourtv/discussions/4010).
