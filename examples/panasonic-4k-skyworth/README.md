# Panasonic 4K — Skyworth SW4H board

| Clé | Valeur |
|---|---|
| **Board** | Skyworth SW4H / SW-22AE |
| **CPU** | MediaTek MT5897 |
| **Android** | 10 (SDK 29) |
| **Platform** | Android TV |
| **Firmware testé** | G0017.75WW_01_20240410 |

## Contexte

Panasonic commercialise ces téléviseurs 4K en Europe en utilisant la carte-mère Skyworth **SW4H** (également connue sous la référence `SW-22AE`). Cette même carte se retrouve dans certains modèles Hisense et Skyworth. Le système est un Android TV 10 pur, sans surcouche Google TV.

## Avertissements importants

### ⚠️ setupwraith et le Wi-Fi
Sur ce board, `com.google.android.setupwraith` peut être le propriétaire déclaré du réseau Wi-Fi sauvegardé. Avant de le désactiver (il n'est pas dans la liste), vérifiez toujours avec :

```sh
adb shell dumpsys wifi | grep -o 'cname=[^ ]*'
```

Si la sortie contient `setupwraith`, ne le désactivez pas ou vous perdrez la connexion Wi-Fi et ADB.

### ⚠️ com.community.oneroom (Transsion / Push Background)
Ce package n'est pas présent sur tous les boards SW4H. Il a été détecté écoutant sur `127.0.0.1:33367`. Vérifiez sa présence avant d'appliquer la liste :

```sh
adb shell pm list packages | grep oneroom
```

S'il est présent, décommentez la ligne correspondante dans `debloat.txt`.

## Applications conservées

Les apps suivantes sont présentes et **conservées** intentionnellement (ne figurent pas dans la liste) :

- `com.netflix.ninja` — Netflix
- `com.amazon.amazonvideo.livingroom` — Prime Video
- `com.google.android.youtube.tv` — YouTube
- `com.spotify.tv.android` — Spotify
- `com.qjy.browser` — Navigateur Web
- `com.android.vending` — Google Play Store
- `com.smartdevice.livetv` — Live TV / Entrées HDMI (requis)
- `com.google.android.setupwraith` — À garder si propriétaire du Wi-Fi (voir ci-dessus)

## Packages désactivés (26 au total)

Voir [`debloat.txt`](debloat.txt) pour la liste complète avec commentaires.

## Après débloat

Le launcher de référence `com.gathin.tvhome` a été installé et déclaré comme Home par défaut. Le stock launcher Google (`com.google.android.tvlauncher`) a ensuite été désactivé.

DNS chiffré AdGuard activé au niveau système :
```sh
adb shell settings put global private_dns_specifier dns.adguard-dns.com
adb shell settings put global private_dns_mode hostname
```

