# Home+ STB-6252C

The cable and OTT box China Network Systems (Home+, 中嘉寬頻, Taiwan) rents out, made by Inspur:
`STB-6252C`, MediaTek MT5221, 1.85 GB RAM, 32-bit userspace, Android TV 11 (API 30, Operator Tier),
build `Homeplustv/STB6252C/STB6252C:11/MAIN/2.22.12`. Debloated and running this repo's launcher in place
of the operator launcher. Everything is disabled, nothing is uninstalled; `debloat.txt` says why each
package went and lists what was kept on purpose.

All commands run from the repo root on Windows. ADB has to be on for any of them: Developer options >
USB debugging, then accept the prompt on the box. Port 5555 opens with that alone.

## Install

```powershell
tools\disable.ps1 -TvHost <box ip> examples\homeplus-stb-6252c\debloat.txt
launcher\build-and-install.ps1 -TvHost <box ip> -StockLauncher com.inspur.isoc.launcher
```

`-StockLauncher` is required: the operator launcher is not one the script recognises.

Run `build-and-install.ps1` in a console window. With command-line tools 15859902 the SDK writes XML
version 4, Gradle prints a harmless warning about it on stderr, and Windows PowerShell 5.1 turns that line
into a terminating error when stderr is redirected. The script stops before it touches the box.

## Undo everything

```powershell
tools\restore.ps1 -TvHost <box ip> examples\homeplus-stb-6252c\debloat.txt -Stock com.inspur.isoc.launcher
```

## Live TV

The Live TV app is `com.android.tv` (the operator's LiveTv_CNS build) on the DVB-C tuner
`com.inspur.tang.mwtvinput/.MWTvInputService/HW28`. Put it in the dock and set it as `live_tv_package`.

The launcher's Inputs menu sends Live TV a passthrough channel URI. LiveTv_CNS cannot parse it on a cold
start, and its window closes again, which looks like a crash. In a theme's `config.json`:

```json
"live_tv_package": "com.android.tv",
"live_tv_input_id": "none",
"input_sources": ["HW28=-"]
```

`live_tv_input_id` names an input that does not exist, so the Live TV row falls back to starting the app
normally, which works. `input_sources` drops the tuner's own row, which sends the same URI.

## Notes

- `screencap` returns an empty file. Check Home with `dumpsys window | grep mCurrentFocus`.
- There is no Google launcher. The only other HOME claimant is `com.android.tv.settings/.system.FallbackHome`.
- `com.inspur.android.daemon` stays enabled. It runs the front LED, a low-memory process killer, the
  remote's global keys (the YouTube button) and some boot-time settings. It does not re-enable packages
  or reset Home, and it did not kill the launcher in the background.
- The box is usually on Wi-Fi. Disabling a `com.inspur.*` package with the system uid logs
  "Remove all networks for app", but the saved networks belong to the system and survived. Check
  `cmd wifi list-networks` before and after.
- The box is not Netflix certified, and Play Store reports Netflix as incompatible.
