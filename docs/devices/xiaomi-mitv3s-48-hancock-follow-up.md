# Xiaomi Mi TV 3S 48: native inputs, package-state recovery and casting

Device observations from 2026-10-21 on Xiaomi `MiTV3S-48` / `hancock`, Android
4.4.4 (API 19), firmware `7.7.21.1653`. These findings apply to this firmware,
not every Mi TV 3S variant. See the [device lists and notes](../../examples/README.md)
for other models. The reference launcher requires API 26; this note does not
establish compatibility with API 19 or provide an alternative launcher.

## Open the native source popup

The native source picker still worked with `com.mitv.tvhome` hidden. Its entry
is an explicit broadcast to Xiaomi's settings receiver:

```sh
adb -s <tv-ip>:5555 shell am broadcast \
  -a com.xiaomi.mitv.settings.INPUTSOURCE_POPUP \
  -n com.xiaomi.mitv.settings/.SettingsBroadcastReceiver
```

The owner confirmed direction-key navigation and HDMI selection with the
physical remote. Keep `com.xiaomi.mitv.settings`, `com.mstar.tv.service` and
`com.xiaomi.mitv.tvplayer`. An app using this route should check that the
receiver exists, is enabled/exported and does not require a permission it
lacks. On unknown firmware, fall back to native settings.

## Open HDMI 1 through the vendor player

The tested HDMI 1 route uses Xiaomi's external-source activity:

```sh
adb -s <tv-ip>:5555 shell am start \
  -a com.xiaomi.mitv.tvplayer.EXTSRC_PLAY \
  -n com.xiaomi.mitv.tvplayer/.ExternalSourceActivity --ei input 23
```

The `input` extra is an **integer**, not a string. Value `23` identified HDMI 1
on this firmware, and the native HDMI 1 picture was verified. Keep the native
player and TV services; source IDs or standard Android input routes from
another model are not established by this check.

## Reversible package hiding and its undo

The ordinary package-disable operation was refused on this firmware. Its
package manager accepts `pm block --user 0` and the matching `pm unblock
--user 0` without deleting the APK or clearing data. Record the original state
before making a change, and inspect it afterwards:

```sh
adb -s <tv-ip>:5555 shell dumpsys package com.miui.systemAdSolution
```

For that package, the tested hide operation and its undo are alternatives:

```sh
# Hide, only after reviewing the package and saving its original state:
adb -s <tv-ip>:5555 shell pm block --user 0 com.miui.systemAdSolution
# Undo that change if it was originally unblocked:
adb -s <tv-ip>:5555 shell pm unblock --user 0 com.miui.systemAdSolution
```

Check both `blocked` and `enabled`: the observed result was `blocked=true`
while `enabled` could stay unchanged. Restore the original state rather than
blanket-unblocking packages. In particular, `com.xiaomi.mitv.advertise` was
already `enabled=2` before the session and was left untouched.

If the original desktop was hidden after a replacement had been installed,
opened and confirmed as Home, restore it **before removing the replacement**:

```sh
adb -s <tv-ip>:5555 shell pm unblock --user 0 com.mitv.tvhome
adb -s <tv-ip>:5555 shell am start -n com.android.settings/.Settings \
  --es :android:show_fragment com.android.settings.HomeSettings
```

Select the original desktop and verify the physical Home button. Preserve
Xiaomi/AOSP settings, the TV player, MStar TV services, the keyboard, Bluetooth,
networking, media/providers and the installer. This note is not a batch cleanup
list or an instruction to hide the stock desktop on an untested configuration.

## Keep the casting stack

These components were present in the package inventory and preserved:

- `com.xiaomi.mitv.smartshare`
- `com.duokan.airkan.tvbox`
- `com.xiaomi.dlnatvservice`
- `com.xiaomi.upnp`

On 2026-10-23 the owner reported AirPlay and Android casting working again
after several days of household use. Keep the receiver stack when cleaning
the content desktop. This is owner feedback, not proof that any one disabled
package fixed casting, or a test of every sending device, casting protocol
and DRM app.
