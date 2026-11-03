# app-settings-sync

Tooling and evidence to move SmartTube/MeeTube TV settings and accounts between TVs over ADB, plus an indexed UI map so an agent can navigate the app without screenshots.

## Specs

- S1 "esportare le impostazioni predefinite del salotto di SmartTube Beta e poterle iniettare in entrambe le Chromecast, in entrambe le app MiTube": a tool exports the settings backup of one app on one TV and imports it into another app on another TV.
- S2 "collegare anche gli stessi account Google": the export/import path carries `files/global_prefs/media_service_account_data`, so a restored backup keeps the signed-in accounts. Accounts are never printed by the tool.
- S3 "abilitare il backup automatico": the documented procedure enables the app's own automatic backup, so the configuration keeps a recoverable copy on the TV.
- S4 "integra in questo repo un nuovo commit che permette di analizzare ed automatizzare l'importazione e l'esportazione delle impostazioni": one script, standard library only, following the existing `tools/` conventions.
- S5 "sia poi data l'istruzione anche a un browser tipo Lightpanda, BrowserOS ... per interagire con le interfacce grafiche": the guide is a browsable page that states what a browser agent can and cannot do here, and gives the copy-paste commands.
- S6 "salvarle come una specie di glossario indicizzato ... senza ogni volta fare screenshot": an indexed map of the app UI, backed by `adb shell uiautomator dump`.

## Tasks

| ID | Spec | Route | Commit |
|---|---|---|---|
| T1 | S1, S2, S4 | `tools/settings_sync.py` + tests, `tools/profiles/sabrina-meetube.json` | 1 |
| T2 | S5, S6 | `docs/workflows/app-settings.md` + README Tools row | 2 |
| T3 | S3 | documented in the guide, verified on both TVs | 2 |
| T4 | all | commit and push to origin | 3 |

## Log

- L1: "Da quanto so, c'è anche SmartTube Beta e vorrei permettere di, se possibile, esportare le impostazioni predefinite del salotto di SmartTube Beta e poterle iniettare in entrambe le Chromecast, in entrambe le app MiTube, se possibile. In modo tale da avere la stessa funzione, soprattutto gli stessi account collegati, e se possibile, collegare anche gli stessi account Google, altrimenti questo ultimo task lascerò invariato."
- L2: "Puoi anche abilitare il backup automatico."
- L3: "Puoi ricercare online, e secondo anche le impostazioni di MiTube che simili a SmartTube, quali sono le migliori impostazioni per performance, qualità, stabilità ... attraverso una ricerca online puoi analizzare quali sono le migliori impostazioni per performance e stabilità ... salvarle come una specie di glossario indicizzato ... senza ogni volta a fare screenshot dell'interfaccia ... anche la comprensione della decompilazione dell'applicazione."
- L4 evidence: SmartTube 32.68 on 192.168.87.245:33171 (Salotto) exported `data/org.smarttube.beta/Backup` containing `files/global_prefs/media_service_account_data` (301 B, one account); restore into `io.yash.meetube.tv` on both sabrina units logged `BackupAndRestoreManager: App just updated. Restoring data...` and the account avatar appeared.
- L5 evidence: both units are `sabrina` (Chromecast with Google TV 4K), 1982996 kB RAM, `armeabi-v7a,armeabi`. Source prefs: `video_player_data[9] = 0,0,null,vp9.2,1920,1080,60.0,null,true,` (1080p60 VP9 preset), `[16]=true` AFR on, `[12]=1` buffer medium, `video_player_tweaks_data[26]=2` Cronet, `[4]=false` hardware decoder, `[18]=false` prefer AVC off.
- L6 evidence: `uiautomator dump` returns the settings panels as text nodes with bounds on these TVs, so navigation and verification need no screenshots.
