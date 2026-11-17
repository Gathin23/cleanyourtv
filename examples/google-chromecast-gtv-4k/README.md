# Google Chromecast with Google TV 4K (Google TV 14): notes after the debloat

Seen on `sabrina`, Google TV 14, `UTTC.250917.004`, with an LG TV over HDMI. The commands for both lists are in
the headers of [`debloat.txt`](debloat.txt) and [`debloat-keep-cast.txt`](debloat-keep-cast.txt).

- **Holding Home does nothing once `com.google.android.apps.tv.launcherx` is disabled.** Android's long press on
  Home opens the panel behind `android.app.action.TOGGLE_NOTIFICATION_HANDLER_PANEL`, and on this box only
  launcherx's dashboard answers it. With [`debloat.txt`](debloat.txt) the press is simply ignored; the TV's
  settings stay one press away on the launcher's Settings button. [`debloat-keep-cast.txt`](debloat-keep-cast.txt)
  keeps launcherx, and the dashboard with it.
- **The TV switches off with the box only with HDMI-CEC on in the TV.** Standby on the Chromecast (its power key,
  or a sleep timer) turned the LG TV off only after SIMPLINK was enabled in the TV's own settings; other brands
  call it Anynet+, BRAVIA Sync and the like.
- **The remote's Input button is infrared, aimed at the TV.** It never reaches the box, so nothing on the
  Chromecast, a key helper included, can remap it.
