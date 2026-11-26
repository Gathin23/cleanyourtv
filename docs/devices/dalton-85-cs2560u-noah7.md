# DALTON 85" CS2560U / Noah7 (HiSilicon Hi3751V553), Android 14

Device note from an owner report; this is not a debloat list. The TV reports its Android product as
`Smart`, so the hardware product type below is more useful for matching firmware.

| Field | Observed value |
|---|---|
| Retail model | DALTON 85" |
| Product type | `CS2560U_CJ_ARG_DALTON_Noah7` |
| Board / SoC | `CS2560P839` / HiSilicon Hi3751V553 (`bigfish`) |
| Android | 14 (SDK 34) |
| Build | `UP1A.231105.003.A1`, incremental `eng.liuhq.20260511.065158` |

## Source button and the vendor input panel

`dumpsys tv_input` reported no input IDs or hardware input entries. The firmware's own Hikeen/Zeasn
panel lists TV, AV and HDMI 1–3. The owner verified picture and sound on TV, AV and HDMI 1–3. The
physical remote's Source key reports Linux `KEY_SOURCE`; in
`InputKeyService` it arrives as Android keycode `2001`, scancode `250`.

On this firmware the panel can be started with the vendor service. The launcher theme settings for
this model are:

```json
{
  "input_key_keycodes": [2001],
  "handle_view_inputs": true,
  "source_panel_service": "com.hikeen.tvsettings/.menu.HotkeyService",
  "source_panel_action": "com.hikeen.action.hotkeyservice",
  "source_panel_extra": "keyevent_keycode=2001"
}
```

The key helper must be included in the build (`-InputKeyService` on Windows, or
`INPUT_KEY_SERVICE=1` on the shell script). With the helper enabled and this configuration applied,
the owner's physical Source button opens the vendor panel from the CleanYourTV home screen. The
remote's Back button closes it. The owner confirmed the configured panel launch works on the TV.
Picture and sound were confirmed working from TV, AV and HDMI 1–3 in the panel.

The launcher fix is generic: device keycodes and OEM component/action/extra stay in this TV's theme
configuration. The panel component and Source-key handling were confirmed on the physical TV remote;
all five listed sources were checked for picture and sound.
