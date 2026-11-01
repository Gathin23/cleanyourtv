# Sony BRAVIA (UK): BBC iPlayer, ITVX, Channel 4 and 5 are YouView web apps

Seen on a Sony KD-65XH9296 (Android 12, `BRAVIA_VH1_EU-user 12 STT2.230505.001.S137 626241`), the UK version of the
board in [`examples/sony-bravia-4k-vh1/`](../../examples/sony-bravia-4k-vh1/debloat.txt), on 2026-10-28.
The launches below were tested over ADB; the tiles built from them and the Play-button remap were checked on the
real remote. Other UK Sony sets with the YouView app (`com.youview.poa`) may behave the same; this one was not tried on them.

## Do not disable `com.youview.poa` on a UK set

`com.youview.poa` is Freeview Play: the "Live TV & Catch Up" tile. It was in the VH1 list as "unused on this EU unit".
On the UK unit, disabling it took BBC iPlayer, ITVX, Channel 4 and 5 off the TV. `pm enable com.youview.poa` brought
them back. The list now keeps it enabled.

## They are not packages

`pm list packages -u` on the same unit finds nothing with `bbc`, `iplayer`, `itv` or `channel4` in its name, and the
Play Store answers "isn't available on Google Play on this device" for iPlayer, ITVX and Channel 4. (Channel 5 does
have a Play Store app; the YouView route below needs no install.) These sets have no per-channel packages either, unlike
the `com.sony.dtv.iplayerlauncher` and `com.sony.dtv.itvlauncher` style launchers some other Sony lists mention.

The YouView app opens each channel's own web app in Sony's HbbTV player. Capturing `logcat` while opening each one
from "Live TV & Catch Up" shows an ordinary intent:

```text
act=com.sony.dtv.intent.action.LAUNCH_HBBTV  cmp=com.sony.dtv.hbbtvplayer/.HbbTvPlayerActivity  dat=<address>
```

| Channel | Address the YouView app used |
|---|---|
| BBC iPlayer | `https://www.live.bbctvapps.co.uk/tap/iplayer/?origin=portal` |
| ITVX | `http://app.10ft.itv.com/youviewsony/` (the app added a `dttRegion=` query from the TV's location; not needed) |
| Channel 4 | `https://yvweb.channel4.com/yvwebapp//` |
| Channel 5 | `https://c5apps.channel5.com/my5youviewsonytv/index.html` |

From a shell, `am start -a com.sony.dtv.intent.action.LAUNCH_HBBTV -d <address> -n com.sony.dtv.hbbtvplayer/.HbbTvPlayerActivity`
opened each one: iPlayer on its profile picker, ITVX and Channel 4 on their home pages, and Channel 5 loaded its address. The owner then confirmed all four tiles on the real remote.
These are the broadcasters' web apps, so the addresses can change; to find the current ones, run
`adb shell logcat -c`, open the channel from the YouView tile, then `adb shell logcat -d | grep -E 'LAUNCH_HBBTV|loadApplication'`.

## Giving each one a Home tile

The launcher lists installed apps with a launcher entry, and has no tile that opens "a screen inside another app", so
a channel needs a launcher entry of its own. The smallest way is a tiny app of your own with one activity per channel;
this is not launcher code and does not belong in `launcher/`.

```xml
<queries><package android:name="com.sony.dtv.hbbtvplayer" /></queries>
<activity android:name=".IPlayer" android:exported="true" android:label="BBC iPlayer"
    android:icon="@drawable/ic_iplayer" android:banner="@drawable/banner_iplayer"
    android:theme="@android:style/Theme.NoDisplay" android:excludeFromRecents="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent-filter>
</activity>
```

```java
public class IPlayer extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        startActivity(new Intent("com.sony.dtv.intent.action.LAUNCH_HBBTV",
                Uri.parse("https://www.live.bbctvapps.co.uk/tap/iplayer/?origin=portal"))
                .setComponent(new ComponentName("com.sony.dtv.hbbtvplayer",
                        "com.sony.dtv.hbbtvplayer.HbbTvPlayerActivity")));
        finish();
    }
}
```

Sideload it like any APK, with `minSdk` 26 and a debug or any other signature. Each activity then shows as its own tile and
opens its channel in one press. Bring your own banner image (320x180 or 640x360) for each; the channels' logos are theirs,
so none ship here. To lead the grid with them, add the app's package to `dock_apps` after your dock apps. The grid is
otherwise alphabetical.

## A related remote button

The Google Play button on this remote sends scancode 587 (`0x24b` in `getevent -lt`, no key name), which the firmware
answers by opening the Play Store. To open YouTube instead, add `scan:587:com.google.android.youtube.tv` to `app_keys` and
build with `INPUT_KEY_SERVICE=1`, as the Sony scancode example in `config.xml` describes.

