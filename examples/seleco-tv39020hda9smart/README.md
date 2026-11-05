# Seleco TV39020HDA9SMART 39" (Android TV 9)

Toptech + Zeasn board on a Realtek RTK2842P (`rtd285o`). Android reports the model as `AT_SmartScreen`, panel
`13AT204V10_PAS`, firmware `AT_SmartScreen-userdebug 9 RTK2842P eng.arge.20220324.112716`. Tested in Italy in
October 2026: the debloat list and the Clean Your TV launcher as Home.

| File | What it is |
|---|---|
| `debloat.txt` | The 14 packages that were disabled, grouped and commented |
| `config.json` | Dock, Inputs panel, Live TV tile label and artwork, App library; push it with `tools/theme.sh` |

## Notes from this TV

- **Inputs.** The Inputs pill opens the TV's own source bar (`com.toptech.tvmenu/.input.InputActivity`), which
  lists HDMI 1-3, AV and the tuner. The Live TV tile (`com.toptech.tvmenu`) opens the last source used.
- **The remote's Input/Source key does nothing on the new Home.** The firmware sends it to the Zeasn launcher.
  The launcher's InputKeyService helper can take it over, but on this set it stopped held Volume Up from
  ramping, so it was left off.
- **Live TV and Settings are not leanback apps here**, so they only show on Home once added from the App
  library (`app_library` is on in `config.json`).
- **Prime Video:** the set came with the phone build of Prime Video (`com.amazon.avod.thirdpartyclient`, installed
  in `/data/app`, not part of the firmware) instead of the TV app. The phone build is hard to use with a remote,
  and Play offers no install button for the TV build (`com.amazon.amazonvideo.livingroom`) on this firmware. The
  TV build was installed by hand in its place and signs in with a code at primevideo.com/mytv.
- **The TV's IP changed after a reboot**, so check it before reconnecting ADB.
