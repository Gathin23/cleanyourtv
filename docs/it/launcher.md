> Traduzione italiana di [docs/launcher.md](../launcher.md). In caso di differenze fa fede la pagina in inglese.

# Il launcher

[Torna al README](../../README.it.md#cosa-offre-il-launcher)

Una piccola schermata Home nativa per Android TV: **circa 572 KB**, Java semplice, pensata per TV con 1 GB di RAM.
Niente sfocature, niente video, nessun permesso di rete, nessun servizio in background. Questa pagina ha i dettagli che il
README omette: impostazioni, lingue, tasti del telecomando e come compilarlo da soli. Ogni chiave che un tema può
impostare è in [`themes/README.md`](../../themes/README.md#keys) (in inglese), e gli interruttori facoltativi con le loro
schermate sono in [`docs/switches.md`](../switches.md) (in inglese).

## Quali TV

La **TV** richiede Android 8.0 (API 26) o successivo: `minSdk` è un limite fissato in compilazione in
[`launcher/app/build.gradle`](../../launcher/app/build.gradle), e lo script di installazione chiede alla TV
`ro.build.version.sdk` prima di compilare, così a un apparecchio più vecchio vengono dette entrambe le cifre invece di
fallire a `pm install` (vedi la [Risoluzione dei problemi](../TROUBLESHOOTING.md), in inglese, per ciò che resta su un
apparecchio simile).

## Compilarlo da soli

Servono JDK 17 e Android SDK platform 34. Su macOS: `brew install openjdk@17 android-commandlinetools` e
`sdkmanager "platforms;android-34" "build-tools;34.0.0"`. Su Linux lo script di installazione trova
`~/Android/Sdk` e il `java` nel tuo PATH.

## Configurazione

[`config.xml`](../../launcher/app/src/main/res/values/config.xml) imposta:

- l'ordine del dock, le app nascoste e il pacchetto della TV in diretta;
- le colonne (i riquadri si rimpiccioliscono da 5 in su) e le righe sotto il dock;
- un'app VPN facoltativa per la pillola di rete;
- `reduced_motion`, per le TV la cui GPU non regge il ritmo.

I temi impostano le stesse chiavi, più un colore di sfondo e uno sfondo immagine.

## Impostazioni del launcher e home predefinita

Fai clic su **Impostazioni** per le impostazioni della TV. Tieni premuto OK per il suo menu:

- **Impostazioni del launcher**. Con `launcher_settings_button` un clic apre quelle, e il menu lascia fuori la voce.
- **Impostazioni del produttore**, dove la TV ha un'app di impostazioni sua accanto a quella di Android (Xiaomi Cina,
  TCL/FFALCON e Fire OS sono in `settings_fallback_components`), perché quell'app ha pagine che quella di Android non ha,
  come Account e sicurezza di Xiaomi con l'interruttore dell'ADB.
- **Schede modulo**, con `module_cards`.
- **Modifica dock**, con `dock_row`.
- **Riordina le app**: OK prende un'app, le frecce la spostano, Indietro la posa e un secondo Indietro finisce. Con
  `tile_menu` (attivo di serie dalla 3.3), tenendo premuto OK su un riquadro si ha **Sposta**, che prende subito quell'app.
- **Riavvia…** mentre `HomeKeyService` è in esecuzione, che apre il menu di accensione del sistema (un launcher non può
  riavviare la TV da solo).

La pagina del launcher ha queste righe, dall'alto in basso:

- **Riordina le app**, solo con `launcher_settings_button`, la stessa del menu.
- **App per riga** 4, 5, 6 o 7; **Righe sotto il dock** da 0 a 3, quante righe di riquadri restano visibili sotto la
  barra del dock; **Opacità della barra del dock** 100%, 70%, 50% o 30%. Le ultime due fanno qualcosa solo mentre il
  dock è una riga a sé (`dock_row`, attivo di serie).
- **Orologio**: **Sistema** segue l'impostazione a 12 o 24 ore della TV; **12 ore** o **24 ore** imposta così gli
  orologi del launcher (Home e salvaschermo dell'orologio), qualunque cosa usi la TV (`clock_format` in un tema).
- **Sfondo**: un'immagine dalla raccolta del tema (o il suo `wallpaper.jpg`), **Nessuno** o **Predefinito del tema**.
  Una scelta tiene quell'immagine e ferma la rotazione finché non scegli Predefinito del tema, premi Segui il tema o
  invii di nuovo il tema.
- **Animazioni** attive o spente, ed **Effetti avanzati** (riquadro sollevato, zoom dell'icona e transizioni di colore
  fluide; possono usare più memoria) una volta attive le animazioni.
- **Apri all'avvio**, solo con `open_on_start_choice`: Spento, oppure un'app che si apre al posto della Home la prima
  volta che la Home compare dopo l'avvio della TV. Una volta per avvio, così Home e Indietro raggiungono comunque il
  launcher.
- **Segui il tema (azzera le scelte fatte sulla TV)**, che cancella ogni scelta qui sopra. Anche inviare o reimpostare
  un tema le cancella.
- **Scegli la home predefinita** apre il selettore della home di Android, e **Impostazioni di sistema della TV** la
  pagina delle impostazioni della TV.

**Cambiare la home predefinita:** le impostazioni del launcher e il riquadro facoltativo della home predefinita
(`show_home_settings_shortcut`) aprono il selettore della home di Android. Scegli il launcher originale per smettere di
usare CleanYourTV come Home, o CleanYourTV per tornarci. Il launcher originale deve essere attivo; se lo script di
installazione lo ha disattivato, esegui prima lo [script di ripristino](restore.md). Alcune TV non hanno il selettore
della home.

## Lingue

Inglese, tedesco, danese, spagnolo, spagnolo latinoamericano, catalano, francese, russo, serbo (latino), polacco, ceco,
italiano, portoghese, turco, arabo, ebraico, cinese semplificato, cinese tradizionale (Taiwan), thailandese, finlandese,
vietnamita, bengalese, indonesiano, norvegese bokmål, svedese, olandese, uzbeco (latino), ucraino, coreano e asturiano.
Il launcher segue la lingua della TV. Da Android 13 si può impostare una lingua solo per il launcher:
`tools/tv.py 'cmd locale set-app-locales com.gathin.tvhome --locales ca'`.

L'ebraico specchia il layout:

- i pulsanti del menu a sinistra, data e orologio a destra con il saluto sopra;
- il dock e le righe di app che partono da destra, con la navigazione del telecomando corrispondente;
- le icone e le etichette del selettore degli ingressi a destra;
- le date con il prefisso del mese ב (per esempio, 19 באוקטובר).

| ![Home in ebraico: i pulsanti a sinistra, il saluto, l'orologio e la data a destra](../hebrew-home.png) | ![Dock e griglia: entrambe le righe partono da destra](../hebrew-apps.png) |
|---|---|
| ![Il menu Ingressi: nomi e icone delle sorgenti a destra, ciò che non è collegato a sinistra](../hebrew-inputs.png) | ![Il salvaschermo dell'orologio, la data sotto l'ora, a destra del centro](../hebrew-screensaver.png) |

Il salvaschermo dell'orologio allinea a destra la data e l'ora. Il russo copre ogni schermata, compresi i fine
settimana di gara, i saluti delle festività e la data Shamsi. Le altre lingue mantengono il loro layout originale.

## Schede modulo

Qualunque app della TV può dichiarare un proprio riquadro (etichetta, didascalia, alone). Il launcher te lo chiede una
volta per app, lo tiene nascosto di serie e ricorda la risposta per la chiave di firma di quell'app e per il nome della
scheda. Tieni premuto OK su Impostazioni e scegli Schede modulo per cambiare idea. Come farne una:
[`docs/modules.md`](../modules.md); il contratto: [`docs/specs/card.md`](../specs/card.md) (entrambi in inglese). Quelle
già pronte sono in [`addons/`](../../addons/), e [cleanyour.tv/addons](https://cleanyour.tv/addons) mostra insieme temi,
interruttori e add-on.

## Salvaschermo dell'orologio

Scegli «Orologio» nelle impostazioni del salvaschermo della TV, oppure via ADB:

```bash
tools/tv.py 'settings put secure screensaver_components com.gathin.tvhome/.ClockDream; settings put secure screensaver_enabled 1'
```

## Tasti del telecomando

- Un servizio di accessibilità facoltativo mantiene funzionante il tasto **Ingressi** del telecomando su Google TV
  (`INPUT_KEY_SERVICE=1` all'installazione).
- Può riportare altri tasti a cui rispondeva solo il launcher originale, come il tasto **YouTube** (`app_keys` e
  `input_key_keycodes`, in config.xml o in un tema; `HOME_KEY_SERVICE=1` legge anche `app_keys`).
- Un prefisso `hold:` su una voce di `app_keys` dà a quel tasto una seconda app: una pressione normale apre l'app senza
  prefisso, tenendolo premuto mezzo secondo si apre quella con `hold:` (per codice del tasto o `hold:scan:...`). Una voce
  può nominare un'attività (`pacchetto/.Attivita`), come la barra rapida di un add-on, invece della voce di avvio
  dell'app. Un prefisso `front:` stabilisce cosa apre un tocco mentre l'app di quel tasto è già in primo piano
  (solo `HomeKeyService`).
- Al posto di un'app, una voce di `app_keys` può nominare un'azione: `back`, `home`, `recents`, `mute`, `sleep`
  (standby, Android 9 e successivi), `screensaver`, `settings`, `wifi`, `library` (Home con la libreria app aperta, per
  il tasto App di un telecomando; richiede `app_library`) oppure `ignore` (il tasto non fa nulla), così un tasto colorato
  libero può mettere la TV in muto o, tenuto premuto, in standby.
- `HOME_KEY_SERVICE=1` sistema anche le chiavette il cui tasto Home invia ALL_APPS, e apre le Impostazioni dal tasto
  **ingranaggio** del telecomando, anche se Android non lo assegna (codici di scansione o dei tasti in config.xml).
- Con `settings_key_panel` l'ingranaggio apre un [piccolo pannello di impostazioni rapide](../quick-settings-panel.jpg)
  sopra la Home o qualunque app: Impostazioni TV, Schermo, Rete, App e Impostazioni del launcher.

Il formato completo di `app_keys` è nella [tabella delle chiavi di `themes/README.md`](../../themes/README.md#keys).

## Navigazione e immagini

Su e Giù spostano di una riga. I pulsanti in alto non cadono mai sui riquadri, e Giù da loro riporta al riquadro del
dock da cui venivi. L'ultima app con il focus viene ricordata, e installare o rimuovere un'app aggiorna i riquadri
mentre la Home è aperta. I banner delle app si caricano a xhdpi o meglio, così le interfacce TV a bassa densità non li
riducono prima dello zoom del focus.
