# Formovie / Fengmi MiProjM05 (Xiaomi Mi Smart Projector 2): the remote and autofocus

Two units, Android TV 9 (`eva`, Amlogic), builds `PQ3B.190705.003.2102` and `PQ3B.190705.003.1912`. The debloat
list and what to keep are in [`debloat.txt`](debloat.txt). This page is about the remote: which button sends what,
and how to run autofocus from it. Checked on the second unit (`.1912`) on 25 October 2026.

## The remote

"Xiaomi RC" (`/vendor/usr/keylayout/Vendor_2717_Product_32b9.kl`), the same remote as the
[Mi Box S](../xiaomi-mi-box-s/debloat.txt). Earlier notes for this model called the apps button the gear button;
the remote has no gear button.

| Button | Scancode | Android key | After the stock launcher is disabled |
|---|---|---|---|
| Home (a circle) | 172 | `KEYCODE_HOME` | Opens the new Home |
| Apps (a grid of squares) | 580 | `KEYCODE_ALL_APPS` (284) | Nothing: only the stock launcher answered it |

## Autofocus on a remote button

`com.fengmi.projector.imaging` (keep it enabled) exports activity aliases for its screens, all pointing at
`.access.ImagingFeatureActivity`:

| Alias | Action | Opens |
|---|---|---|
| `.alias.FocusActivity` | `com.fengmi.projector.imaging.action.START_FOCUS` | Autofocus (自動對焦中…), then Left/Right fine-tune and Back closes it |
| `.alias.AutoKeystoneActivity` | `...action.START_AUTO_KEYSTONE` | Auto keystone (not tried) |
| `.alias.KeystoneActivity` | `...action.START_KEYSTONE` | Manual keystone (not tried) |

The aliases are exported with no permission, and the activity picks the screen from the alias, so the
launcher's `app_keys` can open one by component alone. In `launcher/app/src/main/res/values/config.xml`, to make
the apps button run autofocus:

```xml
<string-array name="app_keys" translatable="false">
    <item>KEYCODE_ALL_APPS:com.fengmi.projector.imaging/.alias.FocusActivity</item>
</string-array>
```

then `HOME_KEY_SERVICE=1 launcher/build-and-install.sh` (`-HomeKeyService` on Windows). Pressed on the real
remote, the apps button ran autofocus from Home and from inside an app.

The second unit's owner swapped the buttons instead: Home runs autofocus, and the apps button goes Home, which
HomeKeyService does by itself for `KEYCODE_ALL_APPS` when no `app_keys` entry claims it:

```xml
<item>KEYCODE_HOME:com.fengmi.projector.imaging/.alias.FocusActivity</item>
```

On the real remote, Home ran autofocus from Home and from inside an app, and the apps button went Home from an
app; after a reboot the helper was bound again. Back, Netflix and the voice button kept working. If the helper
is switched off (a force stop of the launcher does that), Home is plain Home again.

## What it costs

HomeKeyService filters keys, and on this projector a bound key filter stops a held Volume key from ramping: one
step per press (the first unit's notes in [`debloat.txt`](debloat.txt)). On the second unit, with the helper bound
and `consume_volume_keys` true, a held Volume Up still moved one step, so that switch does not bring the ramp back
here. A reinstall without `HOME_KEY_SERVICE=1` switches the helper off: the ramp returns and the apps button does
nothing again. It is the same trade as on the Mi Box S with this remote and in
[the key-repeat note](../../docs/devices/tcl-55p71b-g09-key-repeat.md).
