# Move app settings between TVs

**When to use this:** you have SmartTube or MeeTube installed on two Android TVs and want to move the app settings, video preferences and signed-in YouTube accounts from one TV to the other over ADB.

## What it does and does not do

The [`tools/settings_sync.py`](../../tools/settings_sync.py) tool exports the app's existing backup directory from one TV, copies it into the target app's backup directory on another TV, and prints the remote steps needed to restore it. It supports the SmartTube package `org.smarttube.beta` and the MeeTube package `io.yash.meetube.tv`.

It does not install either app, pair ADB, create a backup before exporting, or change settings by itself. Make the backup in the app first, and install the same or a compatible app build on both TVs. The import copies files; the app applies them only after you choose the restore action.

## Do not import into the app's own backup folder

The import writes the backup under a folder named after the package the backup came from: `/sdcard/Android/media/<target>/data/<source package>/Backup`. Keep it that way.

Writing the same files into the target app's own folder, the one named after the target package, looks equivalent and is not. The app rewrites that folder the next time it backs up, before any restore reads it, so the restore copies the old values back and the import silently does nothing. Measured on a Chromecast with Google TV 4K: a patched tree pushed over the target's own folder restored the previous settings, and only a tree under the source package's folder applied.

## Prerequisites

- Turn on **Wireless debugging** on both TVs.
- Authorize the computer on each TV when Android asks whether to allow USB debugging.
- Keep both TVs reachable over ADB. The command accepts a TV IP address or an `IP:port`.
- Install SmartTube or MeeTube on both units before exporting or importing.
- On the source TV, create an app backup at **Backup locale > Backup dati app** before running `export`.
- Create the Python environment used by the repository tools if it does not exist:

```bash
python3 -m venv tools/venv
```

## Export, import and inspect

The command forms in the tool's own help are:

```text
settings_sync.py export <tv> [--app PACKAGE] [--out FILE]
settings_sync.py import <tv> --app TARGET --from SOURCE_PACKAGE --zip FILE [--drive]
settings_sync.py ui <tv> [--tap TEXT]
settings_sync.py glossary
```

Export a SmartTube backup from the source TV. The default app is `org.smarttube.beta`, but it is shown explicitly here:

```bash
tools/venv/bin/python -I tools/settings_sync.py export <source tv ip> --app org.smarttube.beta --out settings-backup.zip
```

Export a MeeTube backup instead:

```bash
tools/venv/bin/python -I tools/settings_sync.py export <source tv ip> --app io.yash.meetube.tv --out settings-backup.zip
```

The tool prints the number of files, a decoded preset when it recognizes one, and only whether account data is present. It never prints account contents. If the app backup is missing, it prints this exact next step:

```text
No backup found on the TV. In the app: Backup locale > Backup dati app
```

Import a SmartTube backup into MeeTube on the target TV:

```bash
tools/venv/bin/python -I tools/settings_sync.py import <target tv ip> --app io.yash.meetube.tv --from org.smarttube.beta --zip settings-backup.zip
```

Import a MeeTube backup into SmartTube:

```bash
tools/venv/bin/python -I tools/settings_sync.py import <target tv ip> --app org.smarttube.beta --from io.yash.meetube.tv --zip settings-backup.zip
```

The optional `--drive` sends the tool's fixed key sequence after copying. Manual restore is safer when you need to see which package is selected, so normally leave it off. The import prints these restore instructions:

```text
restore steps:
1. Home -> Settings gear (Impostazioni).
2. Select Backup e ripristino.
3. Select Backup locale.
4. Select Ripristino dati app.
5. Select the copied backup and confirm the restore.
restore key sequence: DPAD_UP DPAD_RIGHT DPAD_RIGHT DPAD_DOWN DPAD_DOWN DPAD_CENTER
```

To print the indexed UI nodes from a TV, use `ui`. The optional `--tap` matches a text node case-insensitively and taps the centre of its bounds:

```bash
tools/venv/bin/python -I tools/settings_sync.py ui <tv ip>
tools/venv/bin/python -I tools/settings_sync.py ui <tv ip> --tap "Backup locale"
```

To print the tool's glossary:

```bash
tools/venv/bin/python -I tools/settings_sync.py glossary
```

## Restore on the remote

After `import` has copied the backup, use the remote and follow the measured Italian labels:

**Impostazioni (gear) > Backup e ripristino > Backup locale > Ripristino dati app > pick the package > Conferma**

The app kills itself while restoring. Start the app again; the restored preferences are applied on its next start. Do not treat the app closing as a failed copy unless the backup is still absent after reopening the app.

## Accounts are secrets

The backup carries files/global_prefs/media_service_account_data, so the YouTube accounts travel with the settings when that file is present. This file holds a Google refresh token. Treat `settings-backup.zip` as a secret: do not upload it, commit it, paste its contents into a log, or leave it in a shared directory. Delete the zip after verifying the restore and use a secure transfer while it exists.

The tool reports only `accounts: present` or `accounts: absent`; it does not print the file. NewTube-derived builds exclude media_service_account_data on purpose, so their backups do not carry the Google accounts through this path.

## Automatic backup

On each TV, open **Backup locale > Backup automatico > Una volta al giorno**. Automatic backup matters because a later settings experiment, app update or failed restore has a recoverable copy on the TV without requiring another computer export first. It does not replace the secret-handling rule for a zip exported to the computer.

## Indexed UI map

The following tables are the glossary printed by the `glossary` subcommand, split by screen. The labels and storage indexes are measured entries only; an empty storage cell means the glossary does not identify a preference for that label.

### Home

| Label | Panel it opens | Pref file/index |
|---|---|---|
| Impostazioni | Settings gear | |

### Settings

| Label | Panel it opens | Pref file/index |
|---|---|---|
| Account | grid row 1 | |
| Controllo remoto | grid row 1 | |
| Lingua/Paese | grid row 1 | |
| Generali | grid row 1 | |
| Interfaccia utente | grid row 1 | |
| Lettore | grid row 1 | |
| Frequenza fotogrammi automatica | grid row 2 | files/app_prefs/video_player_data, index 16 AFR |
| Sottotitoli | grid row 2 | |
| Cerca | grid row 2 | |
| SponsorBlock | grid row 2 | |
| DeArrow | grid row 2 | |
| Backup e ripristino | grid row 2 | |
| Informazioni su | grid row 3 | |

### Lettore

| Label | Panel it opens | Pref file/index |
|---|---|---|
| Modalità di riproduzione | panel | |
| Preset video | panel | files/app_prefs/video_player_data, index 9 preset |
| Imposta i pulsanti del lettore | panel | |
| Motore di rete | panel | files/app_prefs/video_player_tweaks_data, index 26 network engine 0/1/2 |
| Buffer video | panel | files/app_prefs/video_player_data, index 12 buffer |
| Zoom video | panel | |
| Velocità video | panel | |
| Lingua audio | panel | |
| Ritardo audio | panel | |
| Volume | panel | |

### Backup e ripristino

| Label | Panel it opens | Pref file/index |
|---|---|---|
| Backup locale | panel | |
| Google Drive | panel | |
| Importa (GrayJay/PocketTube/NewPipe) | panel | |

### Backup locale

| Label | Panel it opens | Pref file/index |
|---|---|---|
| Backup dati app | panel | |
| Ripristino dati app | panel | |
| Backup automatico | panel | |

### Backup automatico

| Label | Panel it opens | Pref file/index |
|---|---|---|
| Nessuno | options | |
| Una volta al giorno | options | |
| Una volta alla settimana | options | |
| Una volta al mese | options | |

### Files and preference storage

| Label | Panel it opens | Pref file/index |
|---|---|---|
| app backup file | `/sdcard/Documents/SmartTubeBackup` | |
| backup root | `/sdcard/Android/media/<pkg>` | |
| video_player_data | files/app_prefs/video_player_data | 9 preset; 12 buffer; 16 AFR; 45 AFR pause ms |
| video_player_tweaks_data | files/app_prefs/video_player_tweaks_data | 4 force SW decoder; 18 prefer AVC over VP9; 26 network engine 0/1/2; 12 tunneled |

## Navigate without screenshots

The tool's `ui` command asks the repository ADB client to run this exact remote command and then pulls the XML:

```text
uiautomator dump /sdcard/window-cleanyourtv.xml
```

With the stock `adb` binary, the equivalent copy-paste commands are:

```bash
adb shell uiautomator dump /sdcard/window-cleanyourtv.xml
adb pull /sdcard/window-cleanyourtv.xml ./window-cleanyourtv.xml
```

The dump is XML. Find the node with `focused="true"`; its `text`, `content-desc` and `bounds` attributes identify the current selection. A bounds value has the form `[left,top][right,bottom]`. For example, a node with `bounds="[100,100][300,200]"` is tapped at `150,150`.

```bash
grep -o 'focused="true"[^>]*' window-cleanyourtv.xml
grep -o '<node[^>]*>' window-cleanyourtv.xml | grep -E 'text=|content-desc=|bounds='
```

For a text match, the tool does the coordinate calculation and prints the result as `tap: x,y`; the corresponding remote action is `input tap x y`. Use `ui` for repeatable navigation and verification. A screenshot is still worth taking when the XML omits visual state, when a layout is ambiguous, or when you need to verify an image, icon, animation or video surface rather than a text node.

## Browser agents

Lightpanda and BrowserOS agents can read this page and run the copy-paste shell commands in an authorized terminal. They can use the printed UI map and XML fields as text evidence.

They cannot drive a TV's D-pad UI through a browser. A browser session does not gain control of the TV remote, ADB authorization or the app's focus just by opening this page. The person or an authorized shell/ADB tool must perform the TV interaction; a browser agent should not claim that it selected **Conferma** unless the ADB command or a person verified it.

## Verified sabrina baseline

On a Chromecast with Google TV 4K (`sabrina`), measured with 2 GB RAM and a 32-bit userspace (`armeabi-v7a`), this is the verified playback baseline:

- **Preset:** 1080p60 VP9.
- **Buffer:** medium.
- **AFR:** on.
- **Network engine:** Cronet.
- **Decoder:** hardware decoder, with the `force SW decoder` preference off.

The device has no AV1 hardware decoder. Its preset list caps at 1440p unless **unlock all video formats** is enabled. Do not copy a higher or AV1 preset to `sabrina` and call it a hardware baseline; the measured preset remains 1080p60 VP9.
