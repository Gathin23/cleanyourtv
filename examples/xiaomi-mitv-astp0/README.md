# MiTV-ASTP0 (mulan): HDMI and AV input configuration

The [input config](config.json) records a second unit: Xiaomi A55, `MiTV-ASTP0`
(`mulan`), Android 9 / API 28, China firmware `24.7.4.3982`, tested on
2026-10-28. The existing [debloat list](debloat.txt) is from firmware
`22.12.2.1630`; no package from that list was disabled during this input test.
This is not a new verification of that list or its Home setup.

## Symptom and fix

Selecting HDMI 2 in Home on `v2.5.0` left Home in front and showed
“此电视不支持该功能” (“Not available on this TV”). The standard `ACTION_VIEW`
passthrough URI with MIME type `vnd.android.cursor.item/channel` resolved to
no activity on this unit.

Xiaomi's player instead accepts `com.xiaomi.mitv.tvplayer.EXTSRC_PLAY` with
an **integer** extra named `input`. On this firmware, that action resolves
to `com.xiaomi.mitv.tvplayer/.ExternalSourceActivity`.

| Port | Framework input ID | Integer `input` |
| --- | --- | --- |
| HDMI 1 | `com.droidlogic.tvinput/.services.Hdmi1InputService/HW5` | 23 |
| HDMI 2 | `com.droidlogic.tvinput/.services.Hdmi2InputService/HW6` | 24 |
| HDMI 3 | `com.droidlogic.tvinput/.services.Hdmi3InputService/HW7` | 25 |
| AV 1 | `com.droidlogic.tvinput/.services.AV1InputService/HW1` | 2 |

AV is **HW1 on this unit**, not the HW2 used by some other Xiaomi/Redmi
records. Do not copy a sibling model's input mapping without checking
`dumpsys tv_input`. The config does not hide any inputs or guess tuner mappings.

A PS5 appeared as a CEC child input under HDMI 2, labelled
`Playback_1 · HDMI2`. Its parent is `Hdmi2InputService/HW6`. The vendor source
number belongs to that physical parent; the CEC child number is not the
player's `input` value. Current Home already resolves the parent for
`input_sources` while keeping the child ID for the standard URI path.

## Applying the two keys

Use a launcher revision that supports both `input_action` and `input_sources`,
as current `main` does. Unmodified `v2.5.0` does **not** implement
`input_sources`, so sending this config to that release alone cannot fix it.
Setting only `input_action` sends a URI without the required port number.

Merge the two keys from [config.json](config.json) into the TV's existing
theme config, keeping its other settings. See the [theme key guide](../../themes/README.md)
for the standard package. Restart Home after updating its overlay.

To undo just this input override, restore the previous values of these two
keys, or remove them if they were absent. Keep the rest of the existing theme.

## Validation and limits

- Before the fix, clicking HDMI 2 reproduced the toast, left Home in front,
  and created no TV input session.
- The native action and each integer above opened the correct physical port.
- With a local `v2.5.0` compatibility patch sending those same integer extras,
  clicking HDMI 1, HDMI 2 (including the PS5 CEC row), HDMI 3 and AV 1 from
  Home entered the native player and established sessions on HW5, HW6, HW7
  and HW1 respectively. HOME returned to the replacement launcher.
- The owner confirmed PS5 picture and sound on HDMI 2.
- The exact current-main APK with this JSON overlay was not installed over
  that working setup. This contribution reuses current main's existing
  `input_sources` implementation; it adds no model-specific Java routing.
- Other-port picture/sound, a reboot after the fix, long-term behavior and
  other firmware remain unverified. HDMI screenshot capture returned no
  image, so it is not offered as picture/sound evidence.

The stock Xiaomi launcher and all system packages remained enabled on this
unit. No Home/boot changes or debloat changes are part of this input fix.
