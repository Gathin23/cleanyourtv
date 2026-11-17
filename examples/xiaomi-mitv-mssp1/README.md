# Xiaomi MiTV-MSSP1 (nino) notes

## The MI button after PatchWall is disabled

The MI button on the remote sends keycode 191. Xiaomi's `com.xiaomi.android.tvsetup.partnercustomizer`
catches it and opens PatchWall while the global setting `patchwall_enable` is `true`. With
`com.mitv.tvhome.atv` disabled the button then does nothing. The list keeps partnercustomizer enabled, and
this needs it.

Tested on the second unit (Mi TV 4S) with the real remote:

1. `tools/tv.py 'settings put global patchwall_enable false'` makes the button open Live TV instead.
2. To open the launcher's Inputs menu instead, turn on the launcher's `InputKeyService` with
   `tools/tv.py 'settings put secure enabled_accessibility_services com.gathin.tvhome/.InputKeyService'`
   (see "Input button" in `docs/TROUBLESHOOTING.md`; that replaces the whole list, so keep any service
   already in `settings get secure enabled_accessibility_services`, separated by `:`) and add this to the theme's `config.json` before pushing
   it with `tools/theme.sh`:

   ```json
   "app_keys": ["191:inputs"]
   ```

   `"191:input:com.mediatek.tvinput/.hdmi.HDMIInputService/HW3"` jumps straight to HDMI 2 instead
   (HW2 is HDMI 1, HW4 is HDMI 3).

Pushing another theme replaces `config.json`, so merge `app_keys` into each theme you push. A force-stop of
the launcher switches the accessibility service off; turn it back on afterwards.

To undo, set `patchwall_enable` back to `true`, turn the service off and push a theme without `app_keys`.
