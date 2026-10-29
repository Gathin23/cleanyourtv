
<p align="center">
  <a href="https://cleanyour.tv"><img src="docs/banner-halloween.png" alt="CleanYourTV: restituisci alla tua TV una schermata Home pulita" width="800"></a>
</p>

<p align="center">
  <a href="README.md">English</a> · <a href="README.zh-CN.md">简体中文</a> · <a href="README.zh-TW.md">正體中文</a> · <a href="README.vi.md">Tiếng Việt</a> · <a href="README.ja.md">日本語</a> · Italiano · <a href="README.nl.md">Nederlands</a> · <a href="README.de.md">Deutsch</a>
</p>

<p align="center">
  <a href="https://cleanyour.tv">Sito del progetto</a> ·
  <a href="PROMPT.md">Prompt per l'assistente</a> ·
  <a href="examples/README.md">Elenco delle TV</a> ·
  <a href="SHOWCASE.md">Progetti della community</a>
</p>

# CleanYourTV: guida in italiano

Collegati dalla rete Wi-Fi di casa a una Android TV o Google TV, disattiva le app preinstallate, la pubblicità e i
servizi che raccolgono dati e che non ti servono, poi installa una schermata Home (launcher) pulita sulle TV che la
supportano. Non serve il root, non serve reinstallare il firmware, non serve un cavo USB. Le app di sistema vengono
solo **disattivate**, mai rimosse, e c'è sempre uno script per ripristinarle.

Questa pagina presenta le funzioni principali e l'uso. Ogni modello, area geografica e versione del firmware ha un
elenco di app e dei limiti propri, quindi controlla prima quello della tua TV. Quando c'è una differenza, fa fede il
[README in inglese](README.md); le note sui singoli modelli stanno nell'[elenco delle TV](examples/README.md).

**Samsung, LG, Roku o un'altra TV non Android?** CleanYourTV lì non funziona, ma puoi comunque disattivare il
tracciamento, passo per passo con il telecomando: [cleanyour.tv/not-android](https://cleanyour.tv/not-android)

![La schermata Google TV originale a confronto con il launcher CleanYourTV](docs/stock-vs-new-side.jpg)

## Da dove iniziare

| Hai | Vai a |
|---|---|
| **Una Android TV o una Google TV** | [Avvio rapido](#avvio-rapido): un assistente di programmazione fa il lavoro in una quindicina di minuti. |
| **Una TV Samsung, LG, Roku, Vizio o Hisense VIDAA** | [cleanyour.tv/not-android](https://cleanyour.tv/not-android): CleanYourTV lì non funziona, ma puoi disattivare il tracciamento con il telecomando. |
| **Solo un telefono Android** | L'[app Clean Your TV](https://cleanyour.tv/app) creata dalla community: niente portatile, niente assistente di programmazione. |
| **Vuoi prima vedere com'è** | [Cosa offre il launcher](#cosa-offre-il-launcher): schermate della nuova Home. |

**Link ufficiali:** [cleanyour.tv](https://cleanyour.tv) e questo repository. Altri domini che usano il nome, come
cleanyourtv.com, non sono gestiti da questo progetto ([#1004](https://github.com/Gathin23/cleanyourtv/issues/1004)).

## Come funziona

Attiva il debug USB nelle opzioni sviluppatore della TV, consenti una volta al computer di collegarsi in ADB via rete,
poi procedi in tre passi:

1. **Salva** l'elenco delle app installate e le impostazioni interessate. Con il [prompt per l'assistente](PROMPT.md)
   l'assistente fa prima un backup completo; se lanci tu `tools/disable.sh`, lo strumento registra solo ciò che serve
   a ripristinare quell'elenco.
2. **Disattiva** con `pm disable-user` i servizi che raccolgono dati, la pubblicità, le righe di contenuti suggeriti e
   le app preinstallate che non usi.
3. **Installa il launcher pulito** e impostalo come schermata Home (HOME) predefinita.

Ogni produttore ha componenti di sistema diversi da tenere. Il [prompt per l'assistente](PROMPT.md) guida l'assistente
di programmazione a controllare prima la TV reale, poi a chiederti quali app e funzioni usi.

## Avvio rapido

Senza computer c'è l'[app Clean Your TV](https://cleanyour.tv/app) per Android, creata dalla community: collega la
TV, mostra prima un piano, salva un punto di ripristino e si limita a disattivare le app. Senza assistente di
programmazione, ma con un computer, puoi fare gli stessi passi a mano: vedi [Fai da te](#fai-da-te).

Con l'assistente ti servono un computer sulla stessa rete della TV e un assistente di programmazione che sappia eseguire comandi da
terminale, per esempio [Claude Code](https://claude.com/product/claude-code), [Codex](https://github.com/openai/codex)
o [Gemini CLI](https://github.com/google-gemini/gemini-cli).

| Passo | Cosa fare |
|---|---|
| 1. Sulla TV | Vai in **Impostazioni → Sistema (o Preferenze dispositivo) → Informazioni**, premi OK sette volte su **Build**, torna indietro, apri **Opzioni sviluppatore** e attiva il **debug USB**. Se c'è anche un interruttore per il debug di rete / ADB, attivalo. I nomi dei menu cambiano da TV a TV. |
| 2. Sul computer | Clona il repository e apri quella cartella nell'assistente (`git clone https://github.com/Gathin23/cleanyourtv`), poi incolla il contenuto di [`PROMPT.md`](PROMPT.md) e indica il modello della tua TV. |
| 3. Rispondi all'assistente | Premi **Consenti** quando la TV chiede di autorizzare il debug, poi di' all'assistente quali app usi, quali vuoi nel dock e se tenere assistente vocale, Chromecast e aggiornamenti di sistema. |
| 4. Verifica | Segui la procedura di backup, pulizia e installazione, poi prova ad aprire le app, i tasti del telecomando, il cambio di ingresso HDMI e il ripristino. |

Perché clonare: l'assistente usa `tools/disable.sh` e `launcher/build-and-install.sh` di questo repository invece di scriverne di propri, ed è lì che si trovano il backup, la protezione della Home e il registro di ripristino.

## Cosa offre il launcher

- **Home essenziale**: sfondo scuro e, di serie, quattro app preferite nel dock in basso.
- **Nome dell'app selezionata**: mostra il nome in grande, con un alone di colore preso dal banner dell'app (o dall'icona,
  se non ha un banner).
- **Griglia delle app**: premi giù per vedere le altre app, con 4, 5, 6 o 7 riquadri per riga.
- **Riordino dal telecomando**: tieni premuto il tasto Impostazioni per aprire il menu ed entrare nel riordino.
- **Libreria app (facoltativa)**: elenca le app installate e permette di aggiungerle o toglierle dalla Home con il
  telecomando.
- **Animazioni**: attivabili o disattivabili, con un livello di effetti avanzati a parte.
- **Scorciatoie utili**: Wi-Fi / Ethernet e Impostazioni; se la TV ha HDMI, AV, TV in diretta o un selettore di ingressi
  riconoscibile, compare anche il pulsante Ingressi. I nomi delle porte HDMI restano quelli che hai dato nelle
  impostazioni della TV.
- **Orologio e salvaschermo**: saluto, ora, data e un salvaschermo con l'orologio incluso.
- **Temi**: cambiano colori, sfondo, dock e numero di colonne con un file di configurazione, senza ricompilare.
- **Schede modulo**: altre app possono aggiungere schede alla Home, che compaiono solo se sei d'accordo.

![App selezionata e alone di colore sullo sfondo](docs/focus-2-up-netflix.png)

### Regolare le impostazioni direttamente sulla TV

Premi il pulsante **Impostazioni** nella Home per aprire le impostazioni della TV. **Tieni premuto OK sul telecomando
mentre quel pulsante è selezionato** e scegli la voce delle impostazioni del launcher:

| Voce | Cosa fa |
|---|---|
| App per riga | Sceglie 4, 5, 6 o 7; più colonne, più piccoli i riquadri. |
| Animazioni | Attiva o disattiva le animazioni del launcher. |
| Effetti avanzati | Riquadro sollevato, zoom dell'icona e transizioni di colore più fluide. Può usare più memoria, a seconda della TV. |
| Segui il tema (azzera le scelte fatte sulla TV) | Cancella le scelte di layout e animazioni fatte sulla TV e torna al tema corrente o alla configurazione di base. Anche applicare o reimpostare un tema le cancella. |
| Scegli la home predefinita | Apre la pagina di Android per scegliere la schermata Home, per passare dal launcher originale a CleanYourTV e viceversa. |

La voce per la home predefinita non riattiva il launcher originale se lo script di installazione lo ha disattivato:
in quel caso ripristinalo seguendo le istruzioni più sotto. Alcune TV non hanno la pagina di scelta della Home. Puoi
anche mostrare questo accesso come riquadro a parte con `show_home_settings_shortcut`.

### Riordino e libreria app

Tieni premuto OK sul pulsante Impostazioni e scegli il riordino nel menu. Premi OK per sollevare l'app da spostare,
usa le frecce per cambiarne la posizione, poi OK o Indietro per posarla; quando non hai nessuna app in mano, premi
Indietro per uscire e salvare. Il pulsante di riordino nella barra in alto è nascosto di serie: si attiva con
`reorder_button` nella configurazione o in un tema.

La libreria app è disattivata di serie. Con `app_library` attivo nella configurazione o in un tema, l'accesso compare
dopo l'ultima riga di app della Home. La libreria elenca sia le app per TV sia le normali app Android; queste ultime
possono richiedere comunque un mouse o uno schermo touch.

- Premi OK su un'app che non è ancora nella Home per aggiungere la scorciatoia.
- Premi OK su un'app già nella Home per aprirla; tieni premuto OK per il menu con cui aggiungere o togliere la
  scorciatoia o vedere le informazioni dell'app.
- Toglierla dalla Home non disinstalla l'app. La scelta resta dopo il riavvio, dopo gli aggiornamenti delle app e
  quando applichi o reimposti un tema.

Vedi anche la [guida alla libreria app](docs/app-library.md). La pagina delle impostazioni del launcher, riga per riga,
la home predefinita, le lingue, i tasti del telecomando, il salvaschermo e la compilazione da soli sono in
[docs/launcher.md](docs/launcher.md) (in inglese).

## Quali TV sono supportate

Gli elenchi di pulizia sono per **Android TV / Google TV**; il launcher richiede **Android 8.0 (API 26) o successivo**.
La community ha contribuito con configurazioni per molte TV e box; alcuni apparecchi hanno una versione di Android
inferiore al minimo del launcher, quindi un elenco di pulizia può esistere anche dove il launcher non si installa.
Confronta modello, area e firmware nell'[elenco delle TV](examples/README.md) e leggi le note della cartella
corrispondente prima di usare un elenco.

- Samsung Tizen e LG webOS non eseguono il launcher Android del progetto;
  [docs/devices/samsung-tizen.md](docs/devices/samsung-tizen.md) descrive cosa espone sulla rete locale una TV Tizen
  del 2021 (niente ADB, quindi niente app da disattivare). I port della community su altre piattaforme sono nei
  [Progetti della community](SHOWCASE.md). Per disattivare il tracciamento su Samsung, LG, Roku e altre TV non Android:
  [cleanyour.tv/not-android](https://cleanyour.tv/not-android).
- Per Fire TV ci sono note di pulizia da seguire a mano; il [prompt per l'assistente](PROMPT.md) non si usa su Fire TV
  e Fire OS non permette di cambiare la Home predefinita.
- Alcune TV vendute nella Cina continentale rifiutano APK esterni o launcher di terzi. Non dedurre la compatibilità
  dalla sola marca.
- Se non c'è ancora un elenco per la tua TV, fai controllare la TV reale all'assistente e poi condividi l'elenco
  verificato seguendo la [guida ai contributi](CONTRIBUTING.md).
- Su molte Google TV il Wi-Fi salvato durante la prima configurazione appartiene al programma di installazione
  `setupwraith`. **Dimentica la rete Wi-Fi e ricollegati** dalle Impostazioni prima di disattivarlo, altrimenti la TV
  perde la rete (vedi [Risoluzione dei problemi](#risoluzione-dei-problemi)).

## Fai da te

I comandi qui sotto si lanciano dalla cartella principale del repository. `192.168.1.50` è un indirizzo di esempio:
sostituiscilo con l'IP della tua TV (lo trovi in **Impostazioni → Rete e Internet**, toccando la rete connessa).
`tools/tv.py` si collega in ADB via TCP sulla porta 5555; se la TV ha solo il **debug wireless** con abbinamento tramite
codice, abbina prima il dispositivo e apri la porta 5555 seguendo i
[passaggi di connessione nel prompt per l'assistente](PROMPT.md).

### 1. Preparare lo strumento di connessione

Lo strumento di connessione richiede Python 3:

```bash
python3 -m venv tools/venv
tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50

# Modello, versione di Android e firmware
tools/venv/bin/python -I tools/tv.py --probe

# Pacchetti installati
tools/venv/bin/python -I tools/tv.py 'pm list packages'
```

### 2. Applicare l'elenco delle app da disattivare

**Il percorso Xiaomi qui sotto è solo un esempio: sostituiscilo con l'elenco giusto per la tua TV, controllato con
cura.** Non usare l'elenco di un altro modello sulla tua TV.

```bash
tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt
```

Il percorso dell'elenco è relativo alla cartella in cui lanci il comando, oppure può essere assoluto. Conserva
l'elenco e il record di ripristino creati dallo strumento, così puoi annullare tutto. Se vuoi un backup completo di
app e impostazioni, fallo tu prima di disattivare: `tools/disable.sh` non lo crea.

### 3. Compilare e installare il launcher

Il launcher è scritto in Java con AndroidX RecyclerView, nome del pacchetto `com.gathin.tvhome`. Servono JDK 17,
Android SDK Platform 34 e Build Tools 34.0.0. Su macOS:

```bash
brew install openjdk@17 android-commandlinetools
sdkmanager "platforms;android-34" "build-tools;34.0.0"
```

Su Linux lo script di installazione cerca l'SDK in `~/Android/Sdk` e Java nel `PATH`. Quando è tutto pronto:

```bash
TV_HOST=192.168.1.50 launcher/build-and-install.sh
```

**Lo script non si limita a installare l'APK: cambia anche la Home predefinita e disattiva il launcher originale, se
lo riconosce.** Prima prova ad avviare il nuovo launcher e cambia solo se funziona; se il cambio non riesce, tenta di
tornare com'era. Riconosce da solo soltanto `com.google.android.tvlauncher`, `com.google.android.apps.tv.launcherx`,
`com.google.android.leanbacklauncher` e `com.mitv.tvhome`; se non lo riconosce, controlla la TV e indica tu
`STOCK_LAUNCHER=<nome pacchetto>`, oppure usa `none`.

Per lasciare attivo il launcher originale:

```bash
TV_HOST=192.168.1.50 STOCK_LAUNCHER=none launcher/build-and-install.sh
```

`none` significa solo non disattivare il launcher originale: **CleanYourTV viene comunque impostato come Home
predefinita**.

### Windows

Il repository ha gli script PowerShell corrispondenti. Installa Python e Android Studio, verifica di avere il JDK e i
componenti dell'SDK indicati sopra, poi:

```powershell
python -m venv tools\venv
tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost 192.168.1.50
```

Lo script PowerShell usa `-StockLauncher <nome pacchetto>` per indicare il launcher originale, oppure
`-StockLauncher none` per lasciarlo attivo. Lo script PowerShell imposta la nuova Home prima di verificare che il nuovo
launcher parta, in ordine inverso rispetto allo script shell qui sopra: assicurati di poter ancora collegarti in ADB
per il ripristino prima di lanciarlo. Per pulizia e ripristino usa [`tools/disable.ps1`](tools/disable.ps1) e
[`tools/restore.ps1`](tools/restore.ps1).

### Strumenti

| Strumento | A cosa serve |
|---|---|
| [`tools/tv.py`](tools/tv.py) | Client ADB in Python: esegue comandi, anche in serie, e copia file in invio e in ricezione. |
| [`tools/b64push.py`](tools/b64push.py) | Invia file grandi, come gli APK, a blocchi e li ricontrolla. |
| [`tools/disable.sh`](tools/disable.sh) / [`tools/restore.sh`](tools/restore.sh) | Applica l'elenco delle app da disattivare, o ripristina dal record fatto prima. |
| [`tools/recovery.py`](tools/recovery.py) | Salva il record di ripristino e genera i comandi per ripristinare. |
| [`tools/check.py`](tools/check.py) | Confronta la TV con l'elenco applicato, per vedere cosa ha annullato un aggiornamento del firmware; con `--fix` rimette a posto. |
| [`tools/adb-closed.py`](tools/adb-closed.py) | Verifica che la porta ADB di rete della TV sia chiusa dopo aver spento il debug. |
| [`tools/privacy.py`](tools/privacy.py) | Controlla la TV alla ricerca di voce, ACR e telemetria e mostra le prove; con `--write` ne ricava un elenco. |
| [`tools/relay.py`](tools/relay.py) | Inoltra una porta locale alla porta ADB della TV, per gli assistenti il cui ambiente isolato non raggiunge la rete. |
| [`tools/theme.sh`](tools/theme.sh) | Invia o reimposta un tema e riavvia la Home. |
| [`tools/manage.py`](tools/manage.py) | Elenca add-on, temi e opzioni con la schermata, mostra cosa c'è sulla TV e permette di installare, aggiornare o rimuovere. |
| [`launcher/build-and-install.sh`](launcher/build-and-install.sh) | Compila e installa il launcher e cambia la Home. |

## Configurazione aggiuntiva

### Layout e temi

La configurazione di base è in [`config.xml`](launcher/app/src/main/res/values/config.xml): ordine del dock, app
nascoste, pacchetto della TV in diretta, numero di colonne, righe visibili sotto il dock, app VPN collegata al pulsante
di rete, `reduced_motion` e altro.

`show_stats` mostra CPU, memoria libera e spazio libero sotto l'orologio; è disattivato di serie e si aggiorna solo
quando sei nella Home. Se la TV blocca la lettura della CPU, il dato CPU viene omesso. `greeting_name` aggiunge un nome
al saluto, per esempio "BUONGIORNO, MARCO".

Un tema usa un file `config.json` che sovrascrive la configurazione di base e può includere colori di sfondo e
immagini. Per esempio:

```bash
TV_HOST=192.168.1.50 tools/theme.sh themes/aurora
```

Tornare alla configurazione di base:

```bash
TV_HOST=192.168.1.50 tools/theme.sh --reset
```

Il formato dei temi e le chiavi di configurazione sono in [`themes/README.md`](themes/README.md).

### Lingua, salvaschermo e telecomando

I comandi ADB qui sotto usano il percorso del venv di macOS/Linux; su Windows sostituisci il prefisso
`tools/venv/bin/python -I tools/tv.py` con `tools\venv\Scripts\python.exe -I tools\tv.py`.

- **Lingua**: segue la lingua di sistema della TV, e l'italiano è incluso. Da Android 13 si può impostare una lingua
  solo per il launcher:
  `tools/venv/bin/python -I tools/tv.py 'cmd locale set-app-locales com.gathin.tvhome --locales it-IT'`.
- **Salvaschermo con l'orologio**: scegli Orologio nelle impostazioni del salvaschermo della TV, oppure lancia
  `tools/venv/bin/python -I tools/tv.py 'settings put secure screensaver_components com.gathin.tvhome/.ClockDream; settings put secure screensaver_enabled 1'`.
- **Tasto Ingressi del telecomando**: installando su macOS/Linux aggiungi `INPUT_KEY_SERVICE=1` per attivare il
  servizio di accessibilità che fa funzionare il tasto degli ingressi sui telecomandi Google TV; su Windows usa
  `-InputKeyService`.
- **Tasti Home / Impostazioni**: installando su macOS/Linux aggiungi `HOME_KEY_SERVICE=1`, insieme ai codici tasto in
  `config.xml` per alcune TV; su Windows usa `-HomeKeyService`. `settings_key_panel` attiva il pannello rapido
  (impostazioni della TV, schermo, rete, app, impostazioni del launcher). A ogni reinstallazione devi ripassare queste
  opzioni di servizio.

### Schede modulo

Un'app può dichiarare il nome della scheda, la descrizione e il colore di sfondo. Il launcher ti chiede se vuoi
mostrarla, di serie è nascosta, e memorizza la scelta in base alla firma dell'app e al nome della scheda. Tieni premuto
OK sul pulsante Impostazioni e scegli il menu delle schede modulo per gestirle.

Per gli sviluppatori vedi [`docs/modules.md`](docs/modules.md); per il contratto di comunicazione vedi
[`docs/specs/card.md`](docs/specs/card.md).

## Il microfono, e cosa si può dire

"Quale di questi pacchetti ascolta?" non ha una risposta onesta in una riga: una scansione dei permessi trova decine di
pacchetti di sistema con `RECORD_AUDIO` che non registrano nulla, e i client ACR vedono lo schermo senza alcun
permesso audio. [`tools/privacy.py`](tools/privacy.py) riporta ciò che la TV dichiara davvero, con le prove per ogni
candidato di voce, ACR e telemetria: `TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/privacy.py`; con `--write`
li trasforma in un normale elenco di app da disattivare. Non può però provare le intenzioni:
[docs/privacy.md](docs/privacy.md) (in inglese) spiega perché.

## Ripristinare il launcher e le app originali

Prima di disattivare, `tools/disable.sh` registra lo stato di ogni app dell'elenco (attiva, disattivata o non
installata), la Home predefinita di quel momento, il pacchetto che la deteneva e le tre velocità di animazione del
sistema. Non salva l'intero elenco delle app né tutte le impostazioni. Il record è in `~/.cleanyourtv/` sul computer,
separato per TV e per elenco. Rilanciare lo stesso elenco non sovrascrive il primo record; le voci nuove aggiunte
all'elenco vengono registrate in aggiunta.

Sostituisci il percorso con **l'elenco esatto che hai usato** e ripristina:

```bash
TV_HOST=192.168.1.50 tools/restore.sh examples/xiaomi-mi-tv-4a/debloat.txt
```

Per indicare il pacchetto del launcher originale di riserva, aggiungi un ultimo argomento. Su Google TV di solito è
`com.google.android.apps.tv.launcherx`, ma controlla sulla tua TV:

```bash
TV_HOST=192.168.1.50 tools/restore.sh "examples/il-tuo-modello/debloat.txt" com.google.android.apps.tv.launcherx
```

- Se c'è un record, lo script ridà la Home al componente che la deteneva prima; se allora non era determinabile, o la
  Home è già di CleanYourTV, usa il pacchetto di riserva che indichi (senza indicazioni prova
  `com.google.android.tvlauncher`).
- Le app già disattivate prima della pulizia restano disattivate. Lo script prova anche ad attivare il pacchetto del
  launcher di riserva; se non è nel record, non conosce il suo stato iniziale, quindi controlla prima il nome.
- Senza record lo script lo segnala e riattiva l'intero elenco, riportando le velocità di animazione a `1.0`; non è
  esattamente lo stato di prima.
- Lo script di ripristino non rimuove CleanYourTV e non annulla le modifiche fatte a mano fuori dall'elenco. Per
  rimuovere il launcher vai in **Impostazioni → App** sulla TV.
- Se l'ADB non si collega, puoi comunque riattivare le app dalle impostazioni della TV.

Per i dettagli (cosa contiene il record, quando conta l'argomento del launcher originale, cosa succede senza record)
vedi [docs/restore.md](docs/restore.md), in inglese.

## Dopo un aggiornamento del firmware

Un aggiornamento si installa sopra il sistema ripulito: le app dell'elenco tornano, la Home passa al launcher originale
e compaiono nuove app preinstallate che nessun elenco cita. Riapri l'ADB come al passo 1 dell'[Avvio rapido](#avvio-rapido),
poi confronta la TV con l'elenco applicato; con `--fix` rimette tutto a posto:

```bash
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/check.py examples/il-tuo-modello/debloat.txt
```

I codici di uscita, un controllo settimanale e i casi particolari sono in
[docs/firmware-updates.md](docs/firmware-updates.md) (in inglese).

## Risoluzione dei problemi

La guida completa (in inglese) è in [`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md).

- **macOS dice `No route to host`**: controlla il permesso Rete locale (Local Network) di macOS; puoi usare il client
  ADB in Python del repository con `tools/venv/bin/python -I tools/tv.py`.
- **Home rotta dopo la sostituzione**: assicurati che il nuovo launcher sia installato e funzioni prima di disattivare
  quello originale. Lo script shell rispetta questo ordine; per l'ordine dello script Windows vedi sopra.
- **Il programma di configurazione di Google TV prende la Home**: `com.google.android.tungsten.setupwraith` può
  occupare la Home dopo che il launcher originale è stato disattivato. Controlla prima se detiene il Wi-Fi attuale,
  come spiega la guida alla risoluzione dei problemi; disattivarlo subito può cancellare quella rete e far perdere la
  connessione alla TV. Gli strumenti di pulizia lo saltano da soli in questo caso: devi prima dimenticare la rete
  Wi-Fi e ricollegarti dalle Impostazioni. Se è già successo, prova a riattivare il launcher originale di Google TV:
  `tools/venv/bin/python -I tools/tv.py 'pm enable --user 0 com.google.android.apps.tv.launcherx'`.

## Domande frequenti

**Non programmo e non ho un portatile: posso farlo lo stesso?**

Sì. L'[app per telefono Android](https://cleanyour.tv/app), gratuita, open source e creata dalla community, fa la stessa
pulizia via Wi-Fi: si collega alla TV, mostra prima un piano, salva un punto di ripristino e si limita a disattivare
le app, quindi tutto si può rimettere a posto.

**Reinstalla il firmware o rimuove le app di sistema?**

No. La pulizia disattiva soltanto le app, ma disattivare per errore un componente di sistema può comunque causare
problemi, quindi confronta con il tuo modello, procedi a piccoli lotti e tieni sempre pronto il ripristino.

**Gli aggiornamenti di sistema e delle app ne risentono?**

Alcuni elenchi disattivano il servizio di aggiornamento del sistema, che va riattivato per ricevere nuovo firmware. Le
app di streaming si aggiornano comunque dallo store se le tieni; dipende dalla TV e dall'elenco che usi.

**Si può fare da remoto per un'altra persona?**

Sì, tramite una rete Tailscale privata: vedi la [guida al lavoro da remoto](docs/REMOTE.md). Non esporre mai la porta
ADB a Internet dal router.

**Dopo bisogna spegnere il debug?**

Sì. La connessione ADB via TCP (di serie porta 5555) usata dagli strumenti non è cifrata, e alcuni firmware non
chiedono nemmeno l'autorizzazione. Finito il lavoro, spegni il debug USB, il debug di rete / ADB e il debug wireless
(quelli che ha la tua TV), revoca le autorizzazioni date, riavvia la TV e verifica dal computer che la porta sia
chiusa: `python3 tools/adb-closed.py <ip della TV>` deve dire che la porta è chiusa. Il launcher funziona anche senza
debug. Altri dettagli nelle [domande frequenti in inglese](docs/faq.md#isnt-adb-access-a-security-hole).

**Supporta DNS privato o AirPlay?**

Il DNS privato (Private DNS) è una funzione di sistema di Android in alcune versioni, non un blocco pubblicità del
launcher, e non ferma tutta la pubblicità nei video. AirPlay non è incluso nel launcher e va installato e configurato a
parte. Altre opzioni sono nelle [domande frequenti in inglese](README.md#questions-people-ask).

**C'è un APK già pronto?**

Oggi il repository compila e installa il launcher con lo script di installazione, così si può adattare la
configurazione a ogni TV.

## Contribuire

Sono benvenuti elenchi di TV verificati su un apparecchio reale, miglioramenti al launcher, correzioni alla
documentazione e nuovi temi. Leggi [`CONTRIBUTING.md`](CONTRIBUTING.md) prima di inviare: una PR per argomento e
indica su quale TV hai provato. I progetti derivati e i launcher di altro stile possono registrarsi in
[`SHOWCASE.md`](SHOWCASE.md).

Quando il README in inglese cambia, aggiorna volentieri anche questa pagina. Per il numero di modelli, l'elenco delle TV
supportate e quello dei temi guarda direttamente le cartelle corrispondenti, così non devi correggere più punti.

## Contatti e segnalazioni di sicurezza

Le domande sull'uso delle TV si possono fare su
[GitHub Discussions](https://github.com/Gathin23/cleanyourtv/discussions). Per stampa e collaborazioni vedi la
[sezione contatti in inglese](README.md#contact). Le vulnerabilità vanno segnalate in privato dalla
[pagina Security](https://github.com/Gathin23/cleanyourtv/security) del repository, vedi [`SECURITY.md`](SECURITY.md).

## Licenza

Il progetto usa la [licenza MIT](LICENSE). Il font Outfit usa la [SIL Open Font License](launcher/FONT-LICENSE-OFL.txt).
