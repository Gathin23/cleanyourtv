> Traduzione italiana di [docs/privacy.md](../privacy.md). In caso di differenze fa fede la pagina in inglese.

# Il microfono, e cosa si può dire

[Torna al README](../../README.it.md#il-microfono-e-cosa-si-può-dire)

«Quale di questi pacchetti ascolta?» non ha una risposta onesta in una riga, e una scansione dei permessi la
complica:

- Su molti apparecchi la maggior parte dei pacchetti gira come `android.uid.system`, quindi una cinquantina mostrano
  `RECORD_AUDIO: granted=true` e nessuno registra nulla.
- Su altri il microfono sta nel telecomando, non nel pannello, e un pacchetto senza alcun permesso audio vede comunque
  tutto ciò che c'è sullo schermo: i client ACR come `tv.inscape.tvclient.inscapeacr` riconoscono i contenuti dalle
  immagini e hanno bisogno solo di `INTERNET`.

`tools/privacy.py` riporta ciò che la TV dice davvero, invece di tirare a indovinare:
`TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/privacy.py` stampa i candidati di voce, ACR e telemetria in
al massimo quattro gruppi (i tre tipi di cui sa indicare la funzione e un quarto intitolato «Matched by name only,
nothing else known», cioè riconosciuti solo dal nome), ciascuno con:

- la sua prova: indicato dal `voice_interaction_service` del firmware stesso, un componente noto di
  [`examples/`](../../examples/), oppure nient'altro che un nome corrispondente;
- se il pacchetto dichiara `RECORD_AUDIO` e se condivide l'UID di sistema;
- se la cronologia AppOps restituita contiene accessi al microfono. Una modalità configurata o un tentativo respinto, da
  soli, non sono una prova di accesso.

`--write privacy.txt` trasforma i candidati identificati in un normale elenco di app da disattivare, così applicarlo e
annullarlo sono i comandi che già usi (`tools/disable.sh`, `tools/restore.sh`). I pacchetti di quel quarto gruppo, quelli
riconosciuti solo dal nome, restano fuori a meno che tu non passi `--include-low`; sono quelli da controllare prima con
`dumpsys package <nome>`.

L'audit legge `dumpsys package` per al massimo 25 pacchetti, quindi su una TV con un elenco più lungo di nomi
corrispondenti il rapporto mostra i primi 25 e dice quanti ne ha lasciati fuori, e `--write` scrive soltanto quelli:
controlla a mano gli altri.

Ciò che l'audit non può fare è provare le intenzioni. `appops` mostra che un'operazione è stata toccata, non cosa è
successo al flusso dopo, e nessuna scansione dei nomi dei pacchetti prova che una TV stia registrando o no.
Disattivare un assistente è uno scambio che fai con una perdita precisa (il tasto del microfono del telecomando, la
ricerca vocale dentro le app), ed è per questo che il rapporto nomina quella perdita per ogni pacchetto, invece di
chiamarla una vittoria.
