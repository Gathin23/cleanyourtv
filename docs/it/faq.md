> Traduzione italiana di [docs/faq.md](../faq.md). In caso di differenze fa fede la pagina in inglese.

# Domande frequenti: le risposte lunghe

[Torna al README](../../README.it.md#domande-frequenti)

Il README tiene una risposta breve a ogni domanda. Queste sono quelle che hanno bisogno di più spazio.

## È sicuro? Può rendere inutilizzabile la TV?

Non viene flashato, rootato o eliminato nulla; i pacchetti vengono disattivati nel profilo utente. Il rischio è
disattivare qualcosa su cui la TV si appoggia, ed è per questo che gli elenchi si applicano a piccoli lotti, con uno
script di ripristino pronto. La pagina Impostazioni → App della TV può riattivare i pacchetti anche senza gli strumenti.

Se la TV non parte più abbastanza da permetterlo, né da permettere l'ADB, di solito la via del ritorno è un ripristino
di fabbrica dal menu di recovery ([#1578](https://github.com/Gathin23/cleanyourtv/issues/1578)):

- Tutto ciò che questi strumenti cambiano sta nei dati utente che il ripristino cancella, quindi ogni app disattivata
  torna e il nuovo launcher sparisce, insieme alle tue app e ai tuoi account.
- Una TV che dopo il ripristino continua a riavviarsi in ciclo fa pensare all'hardware più che alla pulizia
  ([#1592](https://github.com/Gathin23/cleanyourtv/issues/1592)).

La [Risoluzione dei problemi](../TROUBLESHOOTING.md#working-on-the-tv) (in inglese) ha i casi visti finora.

## L'accesso ADB non è un buco di sicurezza?

È un solo tocco esplicito di «consenti questo computer» sulla TV, come quando si sviluppa un'app Android. L'ADB di
rete passa in chiaro sulla porta 5555, e sui firmware con `ro.adb.secure=0` (o senza quel valore, su una build di debug
o su Android 4.4) qualunque dispositivo della rete locale ottiene una shell, quindi chiudilo quando hai finito:

1. Sulla TV: Opzioni sviluppatore → spegni **Debug USB**, **Debug di rete / ADB** e **Debug wireless**, quelli che la
   TV ha, e revoca le autorizzazioni del debug USB.
   - Il debug wireless ascolta su una porta tutta sua e resta aperto quando il debug USB si spegne (visto su un
     Chromecast con Google TV 4K).
   - Annota quella porta prima di spegnerlo (`adb mdns services` la elenca come `_adb-tls-connect._tcp`) e controllala
     subito dopo, prima del riavvio: `python3 tools/adb-closed.py <ip della tv> <porta>` deve dire che quella porta è
     chiusa.
   - Dopo il riavvio il vecchio numero risulta comunque chiuso: il debug wireless prende una porta nuova a ogni avvio.
2. Riavvia la TV, poi conferma dal computer che la porta non c'è più:
   `python3 tools/adb-closed.py <ip della tv>` deve dire che la porta è chiusa.
   - Il debug wireless deve risultare ancora spento nelle Opzioni sviluppatore, e `adb mdns services` non deve
     elencare nessuna voce `_adb-tls-connect._tcp` per la TV; se è tornata, spegnilo e controlla la sua nuova porta
     come al punto 1.
   - Con `nc -vz <ip della tv> 5555` conta solo «Connection refused»: su macOS la privacy della rete locale può
     bloccare `nc` e farlo fallire con «No route to host» anche quando la porta è aperta.

Il launcher continua a funzionare senza il debug.

## Può bloccare la pubblicità anche dentro le app?

In parte, con il DNS privato di Android (Android 9 e successivi). Puntalo a un resolver che blocca la pubblicità, come
[AdGuard DNS](https://adguard-dns.io), e i domini di pubblicità e tracciamento di ogni app smettono di risolversi, in
tutto il sistema, anche dopo i riavvii. Il compromesso: quel resolver vede poi ogni richiesta DNS che fa la TV.

```bash
tools/tv.py 'settings put global private_dns_specifier dns.adguard-dns.com'
tools/tv.py 'settings put global private_dns_mode hostname'
```

Non toglie la pubblicità di YouTube, che arriva dagli stessi server dei video.

Quando la TV segnala «Nessuna connessione a Internet», il resolver è spento o irraggiungibile oppure, su un box senza
orologio con batteria dopo un'interruzione di corrente (o con `auto_time` spento), il suo orologio è tornato alla data
della build del firmware, così il DNS su TLS rifiuta il certificato del resolver perché non ancora valido, mentre l'NTP
non riesce a risolvere il suo server per mettere l'ora; tornare indietro ripristina tutto:
`tools/tv.py 'settings put global private_dns_mode opportunistic; settings put global auto_time 1'`.

- Quella riga richiede di riattivare il debug, perché l'app Impostazioni di Android TV non ha una pagina per il DNS
  privato (non c'è in AOSP, e non c'è in Google TV 14 su un Chromecast con Google TV 4K).
- L'ADB va all'indirizzo della TV, quindi si connette anche mentre il resolver è spento.

Un resolver che risponde può comunque rompere una sola app, quindi se una singola app non funziona mentre il resto
va, guarda [un'app non vuole riprodurre](../TROUBLESHOOTING.md#one-app-refuses-to-play-while-the-rest-of-the-tv-is-online)
(in inglese) prima di fare il debug dell'app.

## Ci sono interruttori nelle Impostazioni della TV che vale la pena girare?

Alcuni. Non servono l'ADB, il telecomando li rimette a posto e la pulizia li lascia dove sono (a parte Utilizzo e
diagnostica e la modalità Solo app): le loro pagine appartengono a Impostazioni e ai servizi Google Play, che ogni
elenco tiene. I percorsi sono quelli di Google TV; altre versioni di Android TV ne spostano alcuni.

- **Impostazioni → Privacy → Annunci** (Ads): elimina l'ID pubblicitario, oppure attiva la disattivazione della
  personalizzazione degli annunci (Opt out of Ads Personalization). Su Android TV si trova in Informazioni → Note legali
  → Annunci (in Preferenze dispositivo su alcuni apparecchi).
- **Impostazioni → Privacy → Posizione** (Location) spenta. La pillola di rete del launcher potrebbe allora dire
  «Wi-Fi» invece del nome della rete.
- **Impostazioni → Privacy → Utilizzo e diagnostica** (Usage & diagnostics) spento, prima della pulizia. Su un
  Chromecast con Google TV 4K la pagina viene da setupwraith, che la maggior parte degli elenchi Google TV disattiva
  per il cambio di launcher, e su un
  [Hisense 50A6H4](../../examples/hisense-smarttv-4k-ffm-android11/debloat.txt) dopo la pulizia non apriva nulla.
- **Impostazioni → Rete e Internet → Scansione sempre disponibile** (Scanning always available) spenta. Permette al
  servizio di posizione di Google e ad altre app di cercare reti Wi-Fi anche a Wi-Fi spento, quindi conta soprattutto
  per una TV collegata via Ethernet.
- **Impostazioni → Account e accesso → il tuo profilo → modalità Solo app** (Apps only mode), dove launcherx resta
  attivo (`stock_home_redirect`, oppure il `debloat-keep-cast.txt` del Chromecast). La Home di Google TV smette allora di
  mostrare i suggerimenti personalizzati e il casting funziona ancora, ma Ricerca e Assistente si spengono. L'interruttore
  sta in launcherx, quindi un elenco che disattiva launcherx lo toglie.

Su Fire OS: Impostazioni → Preferenze → Impostazioni sulla privacy, con gli annunci basati sugli interessi, i dati di
utilizzo del dispositivo e la raccolta dei dati di utilizzo delle app spenti. Si sincronizzano con l'account Amazon,
quindi cambiali dal menu, non con `settings put`.
