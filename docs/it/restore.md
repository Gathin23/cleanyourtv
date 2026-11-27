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
- Anche l'elenco fa parte del nome: gli otto caratteri dopo l'indirizzo sono un hash del percorso dell'elenco,
  risolto prima, quindi `examples/x/debloat.txt`, `./examples/x/debloat.txt` da quella cartella e un link simbolico
  al file arrivano tutti allo stesso record. Una copia dell'elenco - una che tieni accanto ai tuoi appunti, un
  secondo checkout - è un file diverso, con un nome suo: il ripristino da lì non trova alcun record, dice
  `no recovery record for this TV and list` mentre il record dell'originale della copia sta in `~/.cleanyourtv/`, e
  prende la via senza record qui sotto, che riattiva ogni pacchetto dell'elenco, compresi quelli che avevi
  disattivato tu. Fai il ripristino con il file che hai applicato, nel checkout da cui hai ripulito la TV.
- Lo stesso file può anche dare due hash. Un hard link, o lo stesso elenco scritto con altre maiuscole su un disco
  che le ignora (macOS, di default), è un solo file raggiunto da un percorso che dà un hash diverso: l'esecuzione
  trova il record preso per quel file e stampa quale ha usato (`using <record>, the record taken for this list
  under another spelling of its path`) invece di ripiegare sull'intero elenco.
- Due record che nominano entrambi questa TV e questo elenco non vengono scelti: l'esecuzione stampa entrambi i
  percorsi e si ferma prima che qualcosa raggiunga la TV (`this TV and list have two recovery records:`, poi
  `Nothing was changed on the TV`). Sono il nome più vecchio per questo indirizzo accanto a quello attuale, oppure
  due grafie dell'elenco che hanno scritto un record a testa prima che esistesse la ricerca qui sopra, e possono
  non concordare su com'era la TV prima della pulizia: quale delle due sia questa TV non è cosa che lo script possa
  dire. Sposta da parte, deliberatamente, quello che non è lo stato di questa TV prima della pulizia, poi eseguilo
  di nuovo.
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

Senza un record (un'esecuzione più vecchia, un record che hai cancellato, oppure una TV raggiunta a un nuovo
indirizzo: un record è archiviato con l'indirizzo a cui è stato preso) lo script lo dice e ripiega sulla
riattivazione dell'intero elenco e sul ripristino delle velocità a `1.0`, che è ciò che faceva prima.

Un record per lo stesso elenco sotto un altro indirizzo viene nominato, con il suo file, prima che quel gruppo
parta, perché è il caso in cui il ripiego riattiva pacchetti che avevi disattivato prima della pulizia: esegui
invece il ripristino a quell'indirizzo, oppure sposta il record al nome che l'esecuzione stampa. Se l'altro
indirizzo è una seconda TV che hai pulito con lo stesso elenco, i due record sono entrambi giusti e non c'è nulla
di sbagliato.

## Cosa il ripristino lascia stare

Lascia installato il nuovo launcher (toglilo da Impostazioni → App della TV se vuoi eliminarlo) e non conosce i
pacchetti che hai disattivato fuori dall'elenco. Per tornare indietro più avanti, rilancia lo script di installazione:
capisce da solo quale launcher disattivare anche quando la Home è ricaduta sul selettore di Android. Se la TV non è
raggiungibile, anche la pagina Impostazioni → App della TV può riattivare i pacchetti.
