> Traduzione italiana di [docs/app-library.md](../app-library.md). In caso di differenze fa fede la pagina in inglese.

# Libreria app

## Modalità di selezione (facoltativa)

Imposta `"app_library_selection": true` insieme a `"app_library": true` in un tema, oppure attiva entrambi i valori
booleani in `config.xml`, per usare la libreria come selettore di ciò che compare nella Home. È spenta di serie e il
comportamento standard descritto sotto non cambia.

In modalità di selezione, OK attiva e disattiva il segno di spunta verde. Le app spuntate vengono prima; i due gruppi
sono in ordine alfabetico per nome mostrato, secondo la lingua della TV. Dopo aver nascosto un'app, il focus resta
vicino, sull'app successiva (sulla precedente se era l'ultima), invece di seguire l'app nascosta nel secondo gruppo.
Quando aggiungi un'app, il focus va su di lei nel gruppo delle spuntate. In questa modalità le azioni a pressione
lunga di OK non ci sono: controlla solo le scorciatoie della Home e non disinstalla mai le app.

## Facoltativo: OK apre ogni app

Imposta `"library_ok_opens": true` insieme a `"app_library": true` quando la libreria è il posto da cui apri le app che
un tema tiene fuori dalla Home con `hidden_apps`. OK apre allora ogni app, non solo quelle della Home. Tieni premuto OK
per Aggiungi a Home o Rimuovi da Home, come prima. Le schede che non sono nella Home perdono la riga «Aggiungi a Home»
(un'app nuova tiene il suo puntino verde) e il suggerimento dice «Premi per aprire» per ogni app. È spenta di serie e,
con anche `app_library_selection` attivo, vince la modalità di selezione.

## Comportamento standard

Imposta `"app_library": true` nel `config.json` di un tema, oppure attiva il valore booleano `app_library` in
`config.xml`. È spenta di serie. Una piccola pillola di vetro compare dopo l'ultima riga di app della Home.

La libreria orizzontale elenca le app installate che hanno un'attività di avvio per TV o una normale attività di avvio
Android, preferendo quella per TV quando ci sono entrambe. Esclude i servizi e le app senza attività di avvio. Le
normali app Android possono comunque richiedere il tocco o un mouse.

Le app che mancano dalla Home vengono prima. Quelle installate per la prima volta negli ultimi sette giorni guidano quel
gruppo, dalla più recente, con un puntino verde; le altre app mancanti sono in ordine alfabetico. Le app già presenti
nella Home seguono in ordine alfabetico, con un segno di spunta e una leggera sfumatura. Aggiornare un'app esistente
non la segna come nuova.

- Premi OK su un'app mancante per aggiungerla alla Home. Il focus passa alla successiva mancante, senza seguire la
  nuova scorciatoia attraverso il catalogo.
- Premi OK su un'app spuntata per aprirla.
- Tieni premuto OK per Apri, Aggiungi a Home o Rimuovi da Home, e Info app. Il menu usa le stesse risorse del menu a
  tendina degli Ingressi e resta sopra la scheda selezionata.
- Indietro chiude prima il menu, poi la libreria. Giù dall'ultima riga della Home o Destra dall'ultima app arrivano alla
  pillola della libreria; Su torna all'ultima riga. In ebraico la direzione orizzontale è specchiata, quindi è Sinistra
  dall'ultima app a raggiungerla.

Con `"library_button": true` la libreria ha anche un pulsante rotondo nella barra in alto. Aggiungi
`"show_library_pill": false` per togliere la pillola dopo l'ultima riga; il pulsante è allora l'unico accesso, e Giù
dall'ultima riga e Destra dall'ultima app (Sinistra in ebraico) restano dove sono.

Con `tile_menu` attivo, tenendo premuto OK su un riquadro della Home si ha anche Rimuovi da Home, accanto a Sposta e a
Info app. Il tasto Menu del telecomando apre lo stesso menu sul riquadro con il focus. Con `tile_menu` spento, Menu
mantiene il suo comportamento normale. Le assegnazioni del tasto Impostazioni hanno la precedenza; Menu viene lasciato
stare sui pulsanti dell'intestazione, mentre è aperto un altro menu e durante il riordino.
Ogni app spuntata si può togliere dalla Home, comprese le app predefinite del tema. Resta installata e resta nella
libreria, dove una pressione la riporta indietro. Non c'è una sezione delle app nascoste né un'azione di
disinstallazione.

Le scelte sono salvate nei file privati del launcher `library_apps.txt` e `library_home_removed.txt`, con al massimo 128
voci in ciascuno. Ogni file viene scritto in modo atomico. Un file illeggibile resta com'è se una modifica non riesce.
Le scelte sopravvivono ai riavvii e agli aggiornamenti delle app; i pacchetti mancanti o disattivati vengono omessi
finché non tornano. Le rimozioni esplicite dalla Home hanno la precedenza sulle app predefinite del tema finché non le
riaggiungi. La configurazione del tema, l'ordine del dock, le decisioni sulle schede modulo e l'altro ordine salvato dei
riquadri non vengono modificati. Disattivare il pulsante della libreria mantiene le scelte già fatte per la Home.

La libreria usa il ricevitore di pacchetti già esistente per aggiornarsi dopo le modifiche alle app. Le etichette e
l'appartenenza alla Home sono messe in cache a ogni aggiornamento; le icone si caricano solo per le schede collegate.
L'evidenziazione segue l'impostazione delle animazioni del launcher, compreso il movimento istantaneo quando le
animazioni sono spente. Non c'è nessun polling, rete, cattura dello sfondo, nuovo servizio, permesso o dipendenza.
L'interrogazione aggiuntiva nel manifest copre le normali attività LAUNCHER su Android 11 e successivi.
