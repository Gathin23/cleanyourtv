# Making a module

A module is a normal Android TV app that declares a card for this launcher. Your repo, your
licence, your signing key, released as an APK wherever you like, for example on your GitHub
Releases page with its SHA-256. The TV owner sideloads it like any other app. The launcher finds the
card and asks the owner once; if they answer Show, your app appears as a card from then on: your
label, an optional caption, and your colour in the ambient glow, placed ahead of the alphabetical
grid.

The contract is [`docs/specs/card.md`](specs/card.md). This page is the short version.

## 1. Declare the card

On the activity that already has the `LEANBACK_LAUNCHER` filter, add a second filter and a
`meta-data` entry:

```xml
<activity android:name=".LibraryActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="com.gathin.tvhome.category.DOCK_CARD" />
    </intent-filter>
    <meta-data android:name="com.gathin.tvhome.card" android:resource="@xml/module_card" />
</activity>
```

Then create `res/xml/module_card.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<card spec="1" label="Retro" caption="NES games" accent="#E8A33D" />
```

- `label`: 1 to 24 characters, letters, digits, space and `. & + ' -`. Keep it short; it is the
  name on the tile and in the top shelf. Words like "verified" or "official", the launcher's own UI
  names and input names such as HDMI are refused, and so is a new card whose label (or whose app
  label) is the name of another app installed on the TV (the full list is in the spec). Changing
  the label in an update makes the owner decide again, so pick it once.
- `caption`: optional, up to 32 characters, shown after the label while the card is focused. The
  endorsement words refused in labels are refused here too. The
  name line under the tile is one line; label and caption together show about 26 characters on a
  4 column grid, so keep the caption to a few words.
- `accent`: optional `#RRGGBB`, the glow colour behind the card while it is focused in the dock.
- Write the values as literal text. `@string` references are not resolved in spec 1.

The card must sit on the same activity as your `LEANBACK_LAUNCHER` filter, and that activity must
be exported and enabled. Otherwise the card is dropped.

## 2. Keep it light

The launcher targets TVs with 1 GB of RAM, and the owners trust what they put on their home screen.

- No background services unless the feature needs one, and say so in your README.
- No telemetry without an opt-in. No ads.
- Do not bundle content you have no right to redistribute.
- Use your own art. Do not use other companies' logos as your banner or icon.
- Describe every permission you request in your README.

## 3. Test on a TV

Install over ADB, press Home, and the prompt appears:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell input keyevent KEYCODE_HOME
```

(This repo's `tools/tv.py` works where the `adb` binary is blocked; see the README.)

Useful checks:

```bash
# Did the card filter and the meta-data land on the right activity?
adb shell pm dump io.github.udhaykumarbala.retro | grep -B2 -A6 DOCK_CARD

# Why was a card rejected? One line per reason.
adb logcat -s TvHomeCards
```

Then: answer Keep hidden and check the app keeps its ordinary tile; hold OK on Settings, open the
Module cards list, set it to Shown, and check the card takes the tile's place; press OK on the card
and check your app opens; uninstall and check the card is gone. Take a screenshot of the card
focused in the grid for your README.

## 4. Release and keep your key

The owner's answer is pinned to your signing certificate. Publish the certificate digest with every
release so people can check what they install:

```bash
apksigner verify -print-certs app-release.apk | grep 'SHA-256'
```

(`apksigner` accepts its options with one dash as well as two.)

The line "Signer #1 certificate SHA-256 digest" is what the launcher computes on the TV. Sign every
update with the same key: a different key makes every TV ask again, and the card stays hidden
until the owner answers. Sign with one certificate; the launcher rejects APKs with several signers.
Keep the keystore out of your repo.

Publish the APK's own SHA-256 next to it as well.

## Reference module

Retro, an NES library for the TV, declares a card exactly as above:
<https://github.com/udhaykumarbala/tv-vibehack-retro>.

A launcher that wants other apps' Continue watching rows can read them, see
[`docs/watch-next.md`](watch-next.md).
