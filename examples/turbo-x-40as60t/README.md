# Turbo-X 40AS60T (Vestel menlo, Android TV 14)

Sold by Plaisio in Greece, built by Vestel on the MediaTek mt5867 `menlo` platform with UK-market firmware
(`UKNV.260914.002`). The debloat list is in [debloat.txt](debloat.txt); this page covers the remote.

## Remote buttons after the stock launcher is gone

`com.google.android.tvlauncher` handled four of the remote's buttons. Once `launcher/` owns Home, Netflix and
YouTube still open their apps, but Prime Video, Google Play, Menu and the star "1" button do nothing. Their
scancodes, read with `tools/tv.py 'timeout 120 getevent -lt > /data/local/tmp/k.txt'` while pressing them:

| Button | `getevent` key | Scancode |
|---|---|---|
| Prime Video | `KEY_BRL_DOT8` | 504 |
| Google Play | `KEY_BRL_DOT9` | 505 |
| Menu | `KEY_MENU` | 139 |
| Star "1" (Vestel favourite app) | `KEY_CAMERA` | 212 |

Run the capture as one foreground command that writes to a file on the TV. With `getevent -lt` in the background
the capture stops when the shell closes, and streamed over `tools/tv.py` it came back empty.

In `launcher/app/src/main/res/values/config.xml` (scancodes are only read from `config.xml`, not from a theme):

```xml
<integer-array name="settings_key_scancodes">
    <item>139</item>
</integer-array>

<string-array name="app_keys" translatable="false">
    <item>scan:504:com.amazon.amazonvideo.livingroom</item>
    <item>scan:505:com.android.vending</item>
    <item>scan:212:com.cltv.fast</item>
</string-array>
```

Then install with `HOME_KEY_SERVICE=1 launcher/build-and-install.sh`. Use `HomeKeyService`, not `InputKeyService`:
the Input button already works through `fusion.android.tv.demo`, and `InputKeyService` would take it over. The
star button can open any app; LiveTV+ (`com.cltv.fast`) was the owner's choice. With the helper on, holding
Volume+ still ramps the volume on this set.
