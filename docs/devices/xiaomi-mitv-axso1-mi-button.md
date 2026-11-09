# Xiaomi MiTV-AXSO1: Mi TV button to Home without a key-filtering helper

This is an owner report from 2026-11-02, with a read-back after a restart on 2026-11-04 and a check on the physical
remote on 2026-11-09. It is not an upstream launcher option, and nothing is shipped here: the owner builds the small
provider app below. It records a keyboard-layout overlay that gives the remote's Mi TV (PatchWall) button a job again
once `com.mitv.tvhome.atv` is disabled, on a set where `HomeKeyService` breaks held-volume repeat. It is a workaround,
using the same idea as the [Sony VH22 Settings-key note](sony-bravia-4k-vh22-settings-key.md), selected with
`service call` instead of a dex run through `app_process`.

## Observed firmware

| Field | Observed value |
|---|---|
| Android model | `MiTV-AXSO1` |
| Device | `dangalFHD` |
| Android / API | `9` / `28` |
| Firmware build | `PI.6157 release-keys` |
| Remote | `Xiaomi RC`, vendor `0x2717`, product `0x32b9` (Bluetooth) |

The launcher is CleanYourTV 3.3 as Home, with no accessibility service enabled. The debloat list for this model is
[`examples/xiaomi-mitv-axso1/debloat.txt`](../../examples/xiaomi-mitv-axso1/debloat.txt).

## The problem it works around

The button sends scancode 400, which `Vendor_2717_Product_32b9.kl` maps to `BUTTON_5` (192). The framework's
`global_keys` resource hands that key to `com.xiaomi.android.tvsetup.partnercustomizer/.GlobalKeyReceiver`, which
starts `com.mitv.tvhome.atv` by name, with no setting and no fallback. With PatchWall disabled the press does nothing.

`app_keys` `"192:home"` with `HomeKeyService` brings the button back, but a held volume key then moves one step, as
in the [key-repeat section](../TROUBLESHOOTING.md#a-key-helper-is-bound-and-a-held-remote-button-stopped-repeating) of
the troubleshooting guide.

## The overlay

A layout of type `OVERLAY` changes only the keys it names. This one turns scancode 400 into `HOME` before the window
policy sees the key, so Xiaomi's receiver never fires and Android opens the default launcher. No key passes through
an app, so the input filter stays off.

The layout file (`res/raw/mi_button_home.kcm`):

```text
type OVERLAY

map key 400 HOME
```

The provider app needs no permissions and one receiver, which nothing ever starts: the system only reads its
metadata.

```xml
<!-- AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="local.mitv.mibutton"
    android:versionCode="1"
    android:versionName="1.0">
    <uses-sdk android:minSdkVersion="21" android:targetSdkVersion="28" />
    <application android:label="Mi button to Home" android:allowBackup="false">
        <receiver android:name=".Layouts" android:label="Mi button to Home" android:exported="true">
            <intent-filter>
                <action android:name="android.hardware.input.action.QUERY_KEYBOARD_LAYOUTS" />
            </intent-filter>
            <meta-data
                android:name="android.hardware.input.metadata.KEYBOARD_LAYOUTS"
                android:resource="@xml/keyboard_layouts" />
        </receiver>
    </application>
</manifest>
```

```xml
<!-- res/xml/keyboard_layouts.xml -->
<keyboard-layouts xmlns:android="http://schemas.android.com/apk/res/android">
    <keyboard-layout
        android:name="mi_button_home"
        android:label="Mi button opens Home"
        android:keyboardLayout="@raw/mi_button_home" />
</keyboard-layouts>
```

```java
// src/local/mitv/mibutton/Layouts.java
package local.mitv.mibutton;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class Layouts extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
    }
}
```

This unit's APK was built with `aapt2` against android-28, `javac` and `d8` for the receiver, then `zipalign` and
`apksigner` with a debug key.

## Selecting it

The layout must be selected for the remote. The shell holds `SET_KEYBOARD_LAYOUT`, so no helper dex is needed:

```sh
adb install -r mi-button-home.apk
tools/tv.py 'service call input 15 i32 1 s16 <descriptor> i32 10007 i32 12985 s16 local.mitv.mibutton/local.mitv.mibutton.Layouts/mi_button_home'
```

- 15 is `IInputManager.setCurrentKeyboardLayoutForInputDevice` and 14 its getter on this firmware. Transaction
  numbers differ between builds, so do not reuse them on another one without reading its `IInputManager`.
- 10007 and 12985 are the remote's vendor and product in decimal. The setting is stored for that remote model, so it
  survives re-pairing, and `<descriptor>` only matters when both are 0. Take it from `dumpsys input` and keep it out
  of reports.

Read it back:

```sh
tools/tv.py "dumpsys input | grep -A13 'Xiaomi RC' | grep Overlay"
tools/tv.py 'service call input 14 i32 1 s16 <descriptor> i32 10007 i32 12985'
```

The first should print `HaveKeyboardLayoutOverlay: true` while the remote is connected. The second returns a Parcel
that spells out the layout name.

**Gotcha:** after a restart the Bluetooth remote is not an input device until a key is pressed. `dumpsys input` listed
no `Xiaomi RC`, and so no overlay, nearly six minutes after this restart. That does not mean the overlay is lost: the
getter still returned the layout.

Undo: `adb uninstall local.mitv.mibutton`. Android drops the layouts of a removed package, and the button goes back to
doing nothing.

## What the owner checked

On the physical remote, with the overlay on:

- The Mi TV button opened Home from inside an app.
- Held Volume Up and Volume Down ramped until released, instead of moving one step.

Over adb:

- `sendevent` of scancode 400 on the remote's input device produced `KEYCODE_HOME` and started `com.gathin.tvhome`.
- `dumpsys input` reported `HaveKeyboardLayoutOverlay: true` for `Xiaomi RC`.
- After the restart on 2026-11-04, the getter still named the layout and the accessibility service list was empty.
  Once the remote had connected, `dumpsys input` reported `HaveKeyboardLayoutOverlay: true` again.

## What it does not fix

It remaps this one button on this remote. It changes no Xiaomi setting, and it gives the button nothing but Home.
