# Pixpaq Gold Ray-Mastered Edition projector

Single-unit setup notes from October 2026, not a tested debloat list. No packages were
disabled. The reference launcher was not tested: the owner chose Projectivy instead.

## Device and scope

- Model name supplied by the owner: **Pixpaq Gold Ray-Mastered Edition**.
- Android reports model `ADT-3`, Android 13 / SDK 33, and a 32-bit ARM userspace
  (`armeabi-v7a,armeabi`). Do not identify the retail product from `ADT-3` alone.
- Firmware build: `mt5873_cd-userdebug 13 TP1A.220624.021.A1 eng.swpubl.20260811.110446 dev-keys`.
- Connected over Wi-Fi, with no USB peripherals or USB Ethernet adapter during the session.

These observations apply to this unit and firmware; they do not establish that other
Pixpaq models behave the same way. Follow [PROMPT.md](../../PROMPT.md) for backups,
protected packages, Home checks and closing debugging.

## Reach Android Settings and Developer options before ADB is available

The projector's ordinary Settings entry opens Joyar's interface:
`com.joyar.general.settings/.ui.activity.SettingsActivity`. Android TV Settings is
also installed, but its main screen needs an explicit activity launch.

1. Install **Activity Launcher** (`de.szalkowski.activitylauncher`). The owner downloaded
   its APK, copied it to a USB drive and installed it on the projector. If signed in,
   Google Play is another way to obtain Activity Launcher; that installation route
   was not tested in this session.
2. Open Activity Launcher, select **All**, and search for **`DevelopmentActivity`**
   in the package/action text field.
3. Expand **Settings** belonging to **`com.android.tv.settings`**, then select
   **`.system.development.DevelopmentActivity`** and choose **Launch activity**.
   The full activity name is
   `com.android.tv.settings.system.development.DevelopmentActivity`.
   This component was resolved and launched on the real projector and displayed
   **Developer options**; it is not a factory/test menu.
4. In Developer options, scroll to **Debugging**. This firmware exposes both
   **USB debugging** and **Wireless debugging**. The owner reports enabling
   Wireless debugging here for the initial setup. If Developer options itself is
   off, enable its master switch first; first-time unlocking was not independently
   repeated on this already-configured unit.
5. Follow the connection procedure in [PROMPT.md](../../PROMPT.md), accepting the
   on-screen debugging authorization when requested. If Wireless debugging shows
   pairing details, use those ports and code rather than assuming port 5555.

For the normal Android Settings menu instead, search **`MainSettings`**, expand the
same Settings package and launch **`com.android.tv.settings.MainSettings`**.
Searching only `settings` produces many unrelated matches.

Once ADB is available, the equivalent direct commands are:

```sh
tools/tv.py 'am start -n com.android.tv.settings/.system.development.DevelopmentActivity'
tools/tv.py 'am start -n com.android.tv.settings/.MainSettings'
```

Set `TV_HOST` and use the Python environment as described in the main README.
The standard `android.settings.APPLICATION_DEVELOPMENT_SETTINGS` action also resolved
to the Developer options activity on this unit.

**Connection distinction:** this session used ADB over TCP port 5555, including after
a reboot. On later inspection, the actual **Wireless debugging** row said **Disabled**.
The initial toggle sequence was owner-reported, not observed by the agent; a TLS
wireless-pairing session was not tested. Do not equate an available port 5555 with
the Wireless debugging switch being on. Closing debugging and verifying closed
ports still need to be done at handover.

## A separate Android Settings shortcut in Projectivy

Projectivy's built-in **Android settings** entry opened the manufacturer's menu on
this firmware. A separate pinned shortcut worked with Projectivy 4.72 and Activity
Launcher 2.5.0-dev:

1. In Activity Launcher search **`MainSettings`** and select the activity above.
2. In **Edit shortcut**, change the name to **Android Settings** and choose
   **Create shortcut**.
3. On Projectivy Home, find **Android Settings** in the **Shortcuts** row. The tested
   shortcut had a gear icon and opened `com.android.tv.settings/.MainSettings`.

Keep Activity Launcher installed: the pinned shortcut uses its `ShortcutActivity`
to launch the target. This does not replace or disable the manufacturer's Settings.

## Home and the manufacturer's wallpaper screen

The original Home component on this unit was:

```text
com.zeasn.whale.saas/com.zeasn.whale.open.launcher.technical.ui.WelComeActivity
```

Projectivy 4.72 installed successfully and was selected with
`cmd package set-home-activity com.spocky.projengmenu/.ui.home.MainActivity`.
The original launcher stayed enabled throughout.

Initially the physical Home button opened
`com.joyar.commonx/com.joyar.commx.daydream.DaydreamActivity`; Back revealed Projectivy.
At that moment `dumpsys dreams` showed **no active dream**: a wallpaper-looking
activity was not proof that Android's normal screensaver had started.

Projectivy's accessibility service was enabled through Android Settings as a trial.
The owner then confirmed Home and held-volume controls worked. After one reboot,
Projectivy remained the default Home, but its accessibility service was no longer
enabled or bound. Nevertheless, the owner confirmed that Home from YouTube now
opened Projectivy directly. The cause of this change was not isolated; do not
prescribe the helper or claim it persists across reboot. No Button Remapper was
needed, and the existing Siviton accessibility service was left alone. The projector
initially focused its TV/input app after reboot, so default Home was not proof that
Projectivy appeared immediately at boot.

## Screensaver selection

At the owner's request, Aerial Views 1.8.5 was installed from its
[official release](https://github.com/theothernt/AerialViews/releases/tag/1.8.5).
The generic `android.settings.DREAM_SETTINGS` action had no resolver, but
**Aerial Views → Open system screensaver options** opened a working system chooser
(despite an "Unable to open" toast).

Select **Screen saver → Aerial Views** to replace **CommonX**. The existing delay was
**After 5 minutes of inactivity** and was preserved. **Start now** launched the
Aerial Views dream service and displayed scenery successfully. Automatic idle
activation and persistence across another reboot were not tested. To undo, return
to this chooser and select **CommonX**. No CommonX package was disabled.

## Other observed limitations

- `settings list global` returned **Must be root**. A read-only `dumpsys settings`
  successfully supplied the settings backup instead; no root was used. This does
  not establish that shell `settings put` commands work. Keep that dump private.
- Google Play opened a sign-in screen. Installing Projectivy and Aerial Views from
  their developers' GitHub releases succeeded, but this does not establish that
  every APK is compatible or accepted.
- Google Android TV Remote Service (`com.google.android.tv.remote.service`) was
  absent, including from `pm list packages -u`, and ports 6466/6467 were not listening.
  Remote-protocol clients cannot be assumed to work. Installing the service was
  not tested; absence alone is not proof that installation is impossible.
- An idle-process snapshot found no WPS Office process, about 46 MiB proportional
  memory (PSS) for `zeasn.open.technical.tv.store`, and about 9 MiB for
  `com.zeasn.whale.saas`. These are transient observations, not guaranteed savings
  from disabling anything. The owner chose to keep all three enabled.
