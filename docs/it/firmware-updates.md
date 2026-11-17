> Traduzione italiana di [docs/firmware-updates.md](../firmware-updates.md). In caso di differenze fa fede la pagina in inglese.

# Dopo un aggiornamento del firmware

[Torna al README](../../README.it.md#dopo-un-aggiornamento-del-firmware)

Un aggiornamento si installa sopra il sistema ripulito: i pacchetti dell'elenco tornano, la Home passa al launcher
originale e compaiono nuove app preinstallate che nessun elenco cita. Nient'altro se ne accorge, quindi confronta la
TV con l'elenco che hai applicato.

## Riapri l'ADB, poi controlla

Il controllo e `--fix` raggiungono la TV con l'ADB di rete. Se hai chiuso l'ADB come descrive
[L'accesso ADB non è un buco di sicurezza?](../faq.md#isnt-adb-access-a-security-hole) (in inglese), riaprilo prima: debug USB, e
debug di rete / ADB se la TV lo ha, di nuovo attivi come al passo 1 dell'[Avvio rapido](../../README.it.md#avvio-rapido).
Su una TV la cui porta 5555 si apre solo con il debug wireless, attiva anche quello e abbinala di nuovo con l'`adb`
originale. Da lì vanno bene entrambe le strade: `adb tcpip 5555`, come in [Fai da te](../../README.it.md#fai-da-te), che di
solito dura solo fino al riavvio della TV, oppure tenere chiusa la 5555 e controllare tramite il canale abbinato, che
descrive il paragrafo dopo il comando qui sotto.
La chiusura ha revocato l'autorizzazione di questo computer, quindi la strada della 5555 chiede «Consentire il debug
USB?» sulla TV quando il controllo si collega, mentre quella abbinata non chiede nulla: il codice lo hai inserito
all'abbinamento. Finché l'ADB è chiuso, il controllo può dire solo «impossibile raggiungere la TV», anche con la TV
accesa e `TV_HOST` giusto. Chiudi l'ADB allo stesso modo quando hai finito. Con l'ADB aperto:

```bash
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/check.py examples/<la tua tv>/debloat.txt
```

Su Windows lo stesso comando è `tools\venv\Scripts\python.exe -I tools\check.py <elenco>`.

Una TV che offre solo il debug wireless non ha bisogno per niente della porta in chiaro: lascia fuori `adb tcpip 5555`,
punta `TV_ADB_BINARY` allo stesso `adb` di platform-tools e `TV_HOST` alla porta di connessione del debug wireless, e il
controllo passa dal canale già abbinato, senza nulla in ascolto sulla 5555 e senza la richiesta «Consentire il debug
USB?» da accettare ([tools/native-adb.md](../../tools/native-adb.md), in inglese). Rileggi quella porta prima di ogni
esecuzione, perché cambia ogni volta che il debug wireless viene spento e riacceso.

## Codici di uscita e un controllo settimanale

Esce con `0` quando la TV corrisponde ancora all'elenco, `1` quando qualcosa si è discostato e `2` quando non è
riuscito a controllare affatto: la TV non ha risposto, nessuno ha accettato in tempo la richiesta «Consentire il debug
USB?», il percorso dell'elenco non esiste, l'elenco disattiva i pacchetti di sistema di Android oppure, con `--fix`,
il record di ripristino di questa TV e di questo elenco non si può leggere o aggiornare (in quel caso sulla TV non
cambia nulla). Solo l'1 è una deriva, quindi un lavoro che manda una mail solo sull'1 resta zitto mentre il controllo
non è mai partito; manda la mail su tutto tranne lo 0, oppure anche sul 2, e un lavoro settimanale su un server di casa
o su un Raspberry Pi basta per saperlo:

```bash
0 9 * * 1 cd ~/cleanyourtv && TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/check.py examples/<la tua tv>/debloat.txt
```

`TV_HOST` si legge prima di tutto il resto: un'esecuzione che lo lascia vuoto si ferma in `tools/tv.py` con il suo
messaggio ed esce con 2, il codice di un controllo mai partito, quindi impostalo nella riga del lavoro, come sopra,
perché cron non lo eredita dalla tua shell. Un lavoro su un computer dove l'ambiente venv degli strumenti non c'è più
risponde 2 allo stesso modo, indicando la riga che lo crea.

Il lavoro ottiene una risposta solo da una TV che tiene l'ADB di rete in ascolto tra un'esecuzione e l'altra e che ha
accettato «Consentire il debug USB?» per il computer che lo esegue con «Consenti sempre da questo computer» spuntato:
senza la spunta la TV lo richiede a ogni esecuzione, e se non c'è nessuno ad accettare, l'esecuzione esce con 2. Una
porta lasciata in ascolto è quella che [L'accesso ADB non è un buco di sicurezza?](../faq.md#isnt-adb-access-a-security-hole)
dice di chiudere, perché qualunque dispositivo in rete può raggiungerla. Con l'ADB chiuso come descritto lì, ogni
esecuzione esce con 2: dopo un aggiornamento fai invece il controllo a mano, con l'ADB riaperto come sopra.

## Rimettere a posto con `--fix`

`--fix` riapplica l'elenco a ciò che si è discostato: riporta la Home al launcher che l'elenco non disattiva (passa un
pacchetto come secondo argomento se usi un fork), poi disattiva i pacchetti dell'elenco che sono tornati e rilegge la
TV per confermare entrambe le cose. Non scrive mai un record di ripristino suo, perché solo il record della prima
esecuzione conserva lo stato di prima della pulizia. Quando ce n'è uno, `--fix` prima gli aggiunge ciò che aggiungerebbe
una seconda esecuzione di `tools/disable.sh` (i nomi che l'elenco ha guadagnato, e un pacchetto che allora non era
installato e ora è installato e attivo), così `tools/restore.sh` riporta comunque la TV allo stato originale. La Home si
sistema per prima; se non resta nessun launcher a prenderla, `--fix` non cambia nulla e lo dice.

Esegue gli stessi controlli di sicurezza di `tools/disable.sh` prima di disattivare qualsiasi cosa, perché un
aggiornamento è il momento in cui tornano a contare: lascia attivo setupwraith finché la rete Wi-Fi salvata gli
appartiene (`FORCE_SETUPWRAITH=1` su Ethernet), lascia attivi i pacchetti sotto gli ingressi HDMI/AV (`TVINPUT_ALLOW=1`
se li vuoi davvero spenti), lascia attive le app con cui la TV apre i suoi ingressi e le Impostazioni
(`HANDLERS_ALLOW=1`), e non porta mai la Home su una `FallbackHome` del produttore né su un'app di collaudo di
fabbrica.
Ogni pacchetto che lascia attivo viene indicato con il motivo e fa uscire con 1, perché la TV non è tornata all'elenco
finché non te ne occupi a mano.

## Quando il launcher originale resta la Home di proposito

Con `stock_home_redirect` il launcher originale può restare la Home di proposito: l'elenco lo lascia fuori, lo script
di installazione è stato lanciato con `STOCK_LAUNCHER=none`, Android lo tiene come Home e `HomeKeyService` manda la sua
schermata Home a questo launcher. Sul Chromecast con Google TV 4K quell'impostazione corrisponde al file
`debloat-keep-cast.txt` della cartella, non al suo `debloat.txt`, che disattiva setupwraith e la coppia di salvaschermo
che l'impostazione con reindirizzamento tiene. Sull'Acer AR55AR2851UDPRO è il `debloat.txt` della cartella. Controlla
quegli elenchi con il launcher originale come secondo argomento. Senza, il controllo si aspetta `com.gathin.tvhome` sulla
Home, quindi ogni esecuzione segnala la Home come deriva, e `--fix` prova a spostarla lì, cosa che quelle TV non
mantengono:

```bash
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/check.py examples/google-chromecast-gtv-4k/debloat-keep-cast.txt com.google.android.apps.tv.launcherx
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/check.py examples/acer-ar55ar2851udpro/debloat.txt com.google.android.tvlauncher
```

Lascia fuori l'argomento dove il launcher originale deve restare spento. Dove lo script di installazione lo disattiva,
come con il `debloat.txt` del Chromecast e l'elenco dell'XGIMI MoGo 3 Pro (lì il reindirizzamento copre soltanto i
firmware che lo riaccendono), il launcher originale sulla Home è una deriva, e nominarlo come secondo argomento la
nasconderebbe. Dove lo disattiva l'elenco stesso, come quello dell'XGIMI MoGo 4, `check.py` rifiuta quell'argomento
con uscita 2 e non controlla nulla: un launcher che l'elenco disattiva non può essere quello su cui la Home resta.

## Elenchi che disattivano il servizio di aggiornamento

Gli elenchi che disattivano anche il servizio di aggiornamento non ricevono nessun aggiornamento. Per installarne uno,
riattiva il servizio sulla TV, lascia che aggiorni, esegui `check.py --fix` e disattiva di nuovo il servizio.
