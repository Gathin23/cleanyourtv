> Traduzione italiana di [docs/restore.md](../restore.md). In caso di differenze fa fede la pagina in inglese.

# Ripristinare la TV

[Torna al README](../../README.it.md#ripristinare-il-launcher-e-le-app-originali)

Conserva l'elenco che hai applicato. Una riga rimette la TV com'era:

```bash
TV_HOST=192.168.1.50 tools/restore.sh examples/<la tua tv>/debloat.txt                                    # Android TV
TV_HOST=192.168.1.50 tools/restore.sh examples/<la tua tv>/debloat.txt com.google.android.apps.tv.launcherx  # Google TV
```

Su Windows lo stesso ripristino è:

```powershell
powershell -ExecutionPolicy Bypass -File tools\restore.ps1 -TvHost 192.168.1.50 examples\<la tua tv>\debloat.txt
```

e su Google TV la chiamata prende dopo l'elenco anche il launcher originale, `-Stock
com.google.android.apps.tv.launcherx`. `TV_HOST=... tools/restore.sh` non funziona in PowerShell: imposta una
variabile e poi chiama un programma che Windows non ha.

## Cosa contiene il record

Prima di cambiare qualcosa, `tools/disable.sh` annota com'era la TV:

- per ogni pacchetto dell'elenco, se era attivo, già disattivato o non installato;
- quale componente deteneva la Home;
- le tre velocità di animazione.

Quel record sta in `~/.cleanyourtv/`, un file per ogni TV e per ogni elenco.

- Il file prende il nome dall'indirizzo con cui è stata raggiunta la TV, quindi è `TV_HOST` a scegliere il record:
  un'esecuzione senza `TV_HOST` si ferma con il messaggio di tv.py e non manda nulla alla TV, invece di ripiegare
  sull'intero elenco per un record che non sa nominare.
- Una seconda esecuzione dello stesso elenco conserva il primo record, senza salvargli sopra lo stato già ripulito.
- Se nel frattempo hai aggiunto pacchetti all'elenco, quell'esecuzione li aggiunge al record prima di disattivarli, e
  un pacchetto dell'elenco che allora non era installato ma ora è installato e attivo viene registrato come attivo.
- `check.py --fix` fa lo stesso prima di disattivare qualsiasi cosa.

Conserva l'elenco che hai applicato. Lo script di ripristino riesegue il record: un pacchetto che avevi disattivato
tu resta disattivato e la Home torna al componente che la deteneva.

## L'argomento del launcher originale

L'argomento del launcher originale è solo un ripiego: con un record, a riavere la Home è il launcher che la deteneva
davvero, e l'argomento si usa solo quando allora nessuno rispondeva a HOME oppure la deteneva il launcher di questo
progetto.

Il record annota anche se i launcher originali noti erano attivi, indicati nell'elenco o no, e il launcher originale
viene riattivato soltanto se lo era:

- Uno che era già spento (di fabbrica, o perché lo script di installazione lo aveva disattivato prima che applicassi
  questo elenco) resta spento, e se allora la Home era del launcher di questo progetto, la Home torna a lui.
- Un record precedente a questa nota non lo dice, quindi lo script lascia quel launcher com'è e stampa il comando
  `tools/tv.py 'pm enable <pacchetto>'` che lo riattiva.

## Senza un record

Senza un record (un'esecuzione più vecchia, oppure un record che hai cancellato) lo script lo dice e ripiega
sulla riattivazione dell'intero elenco e sul ripristino delle velocità a `1.0`, che è ciò che faceva prima.

## Cosa il ripristino lascia stare

Lascia installato il nuovo launcher (toglilo da Impostazioni → App della TV se vuoi eliminarlo) e non conosce i
pacchetti che hai disattivato fuori dall'elenco. Per tornare indietro più avanti, rilancia lo script di installazione:
capisce da solo quale launcher disattivare anche quando la Home è ricaduta sul selettore di Android. Se la TV non è
raggiungibile, anche la pagina Impostazioni → App della TV può riattivare i pacchetti.
