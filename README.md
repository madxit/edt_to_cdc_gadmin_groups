# Sviluppo di un'applicazione desktop standalone per sincronizzare le mailing list dei Consigli di Classe tramite EDT e Google Workspace

## 1. Obiettivo del progetto
Realizzare un'applicazione desktop che automatizzi la creazione e l'aggiornamento delle mailing list istituzionali dei Consigli di Classe dell'istituto.
L'applicazione deve leggere l'elenco delle classi e i relativi docenti da EDT tramite un file XML accessibile via URL, confrontare queste informazioni con i gruppi presenti in Google Workspace e aggiornare automaticamente le mailing list tramite le API ufficiali di Google.
Il progetto deve essere sviluppato con tecnologie adatte alla creazione di un'applicazione desktop multipiattaforma, distribuibile per macOS e Windows.
**Requisito fondamentale:** l'utente finale deve poter scaricare l'applicazione e avviarla con un semplice doppio clic, senza dover installare preventivamente Python, Node.js, runtime, librerie o altre dipendenze di sviluppo.
Non si tratta di un semplice script da eseguire manualmente, ma di un'applicazione completa, utilizzabile anche da chi non ha competenze informatiche.

## 2. Distribuzione e packaging
Prendere come riferimento per l'organizzazione del processo di build e distribuzione il seguente repository:
- **Repository di esempio:** [madxit/moka](https://github.com/madxit/moka)
- **Workflow di packaging:** [package.yml](https://github.com/madxit/moka/blob/main/.github/workflows/package.yml)

Analizzare il workflow di riferimento e riutilizzarne, ove appropriato, la struttura e le buone pratiche.
Creare nel repository del progetto il file:
`.github/workflows/package.yml`

Il workflow GitHub Actions dovrà:
- Eseguire automaticamente il processo di build e packaging.
- Generare versioni utilizzabili su Windows e macOS.
- Includere negli artefatti tutte le dipendenze runtime necessarie.
- Produrre pacchetti scaricabili e facilmente distribuibili.
- Gestire correttamente gli eventuali requisiti specifici delle due piattaforme.
- Rendere disponibili gli artefatti tramite GitHub Actions e, se opportuno, tramite GitHub Releases quando viene pubblicata una versione.
- Non richiedere all'utente finale di compilare il progetto o installare strumenti aggiuntivi.

Scegliere il formato di distribuzione più appropriato per ciascun sistema operativo, ad esempio un'app bundle `.app` e un archivio `.zip` o un'immagine `.dmg` per macOS, e un eseguibile standalone o un installer `.exe` per Windows.
Documentare eventuali limitazioni relative a firma digitale, notarizzazione macOS, Gatekeeper o SmartScreen di Windows. Non dichiarare l'applicazione firmata o notarizzata se tali operazioni non sono state effettivamente configurate.

## 3. Interfaccia grafica
Realizzare un'interfaccia grafica semplice, chiara e professionale, con tutte le funzionalità necessarie accessibili dalla schermata principale.

### 3.1 Configurazione EDT
Inserire un campo di testo nel quale l'utente possa inserire l'URL da cui scaricare il file XML generato da EDT.
**Requisiti:**
- Accettare un URL HTTP o HTTPS, privilegiando HTTPS.
- Conservare l'URL nelle impostazioni locali dell'applicazione.
- Ripristinare automaticamente l'URL al successivo avvio.
- Prevedere un pulsante per verificare la connessione e la validità del file XML.
- Mostrare messaggi comprensibili in caso di URL non valido, problemi di connessione, errori HTTP o XML malformato.
- Includere un pulsante **Aiuto EDT** che spieghi come ottenere l'URL corretto e come verificare che il file XML contenga le informazioni necessarie.
Le istruzioni dovranno essere concrete e basate sul formato effettivamente supportato da EDT. Non inventare percorsi di menu, funzionalità o formati XML: se occorre, documentare le informazioni mancanti e renderle configurabili.

### 3.2 Configurazione Google Workspace
Inserire un'area dedicata all'autenticazione e all'accesso alle API di Google Workspace.
L'utente dovrà poter selezionare dal computer un file JSON di credenziali autorizzato per l'accesso amministrativo necessario all'applicazione.
**Requisiti:**
- Accettare il file JSON appropriato per il metodo di autenticazione effettivamente implementato.
- Mostrare il nome del file selezionato e lo stato della configurazione, senza esporre chiavi private o altri segreti.
- Memorizzare localmente il riferimento al file per gli avvii successivi.
- Verificare la validità della configurazione e la possibilità di autenticarsi.
- Includere un pulsante **Aiuto Google Workspace** che spieghi passo per passo come configurare Google Cloud, abilitare le API necessarie, creare le credenziali e concedere esclusivamente le autorizzazioni richieste.
Il programma dovrà utilizzare le API ufficiali di Google Workspace, in particolare Google Admin SDK Directory API, con i permessi minimi indispensabili.

**Vincolo di sicurezza obbligatorio**
Le credenziali utilizzate dall'applicazione devono avere accesso esclusivamente alle operazioni sui gruppi e sui membri dei gruppi autorizzati.
L'applicazione deve poter gestire esclusivamente mailing list con indirizzi che corrispondono al formato:
`cdc_{nome_classe}@dominioscuola.xyz`
Non deve poter creare, modificare, eliminare o gestire gruppi diversi da quelli appartenenti a questo insieme.
Per rispettare questo requisito:
- Non utilizzare autorizzazioni amministrative globali non necessarie.
- Non richiedere permessi per gestire utenti, unità organizzative, impostazioni di dominio o altre risorse non pertinenti.
- Utilizzare, ove compatibile con l'architettura scelta, una delega a livello di dominio limitata agli scope strettamente necessari e un account amministrativo dedicato con privilegi minimi.
- Verificare che il metodo di autenticazione scelto sia realmente compatibile con il modello di autorizzazione richiesto da Google.
- Applicare nell'applicazione un controllo rigoroso degli indirizzi dei gruppi prima di ogni operazione di scrittura.
- Non eseguire mai operazioni di eliminazione dei gruppi: è consentita soltanto la creazione dei gruppi mancanti e la gestione dei relativi membri.

**Importante:** i controlli implementati nell'interfaccia non sono sufficienti a garantire una limitazione reale dei privilegi. Documentare chiaramente quali restrizioni sono applicate da Google e quali sono invece controlli interni al programma. Se le credenziali configurate consentono privilegi più ampi di quelli desiderati, segnalare il rischio e non presentare la configurazione come realmente limitata.

### 3.3 Esecuzione della sincronizzazione
Inserire un pulsante principale ben visibile:
**Avvia sincronizzazione**
Al clic, il programma dovrà avviare l'intero processo descritto nella sezione 4.
Durante l'esecuzione:
- Mostrare lo stato corrente dell'operazione.
- Evitare l'avvio simultaneo di più sincronizzazioni.
- Consentire all'utente di capire se il programma sta ancora lavorando.
- Gestire correttamente gli errori senza bloccare l'interfaccia.
- Registrare nel log tutte le operazioni significative, gli esiti e gli eventuali problemi.
Prevedere inoltre un pulsante per verificare le configurazioni e, se possibile, una modalità di simulazione (dry run) che mostri le modifiche previste senza apportare modifiche a Google Workspace.

### 3.4 Area dei log
Inserire un'area di testo non modificabile dall'utente, con scorrimento automatico verso le ultime righe.
Il log deve descrivere in modo cronologico e dettagliato ogni fase dell'esecuzione.
Esempio indicativo:
```text
[10:15:01] Avvio della sincronizzazione.
[10:15:02] Download del file XML da EDT.
[10:15:03] XML scaricato e analizzato correttamente.
[10:15:03] Individuate 28 classi.
[10:15:04] Individuati 146 indirizzi email distinti.
[10:15:05] Autenticazione Google Workspace riuscita.
[10:15:06] Verifica del gruppo cdc_1A@dominioscuola.xyz.
[10:15:06] Gruppo esistente.
[10:15:07] Membri attuali: 5.
[10:15:07] Membri previsti: 6.
[10:15:08] Aggiunto docente: docente@example.com.
[10:15:09] Sincronizzazione della classe 1A completata.
[10:15:10] Sincronizzazione terminata: 28 classi elaborate.
```
I valori sono puramente esemplificativi.
I log dovranno distinguere chiaramente tra operazioni completate, elementi già aggiornati, avvisi, errori e operazioni saltate.
Non registrare mai password, token di accesso, chiavi private, contenuti sensibili delle credenziali o altri segreti.
Prevedere la possibilità di copiare il log e, se ragionevole, di salvarlo in un file di testo.

## 4. Logica di sincronizzazione
Implementare il processo seguendo esattamente le fasi descritte di seguito.

**Fase 1 — Download e analisi dell'XML EDT**
- Recuperare il file XML dall'URL configurato.
- Verificare che il download sia riuscito e che il contenuto sia valido.
- Analizzare la struttura XML e individuare le informazioni necessarie per ricostruire le associazioni tra classi e docenti.
- Per ogni classe, ricavare l'insieme degli indirizzi email istituzionali dei docenti associati.
- Normalizzare gli indirizzi email, eliminare gli spazi superflui e rimuovere i duplicati all'interno di ciascuna classe.
- Validare le email prima di utilizzarle.
- Normalizzare il nome della classe per generare l'indirizzo del gruppo nel formato richiesto.

Il risultato dovrà essere una struttura logica equivalente a:
- Classe 1A → insieme degli indirizzi email dei docenti assegnati alla classe 1A.
- Classe 1B → insieme degli indirizzi email dei docenti assegnati alla classe 1B.
- Classe 2A → insieme degli indirizzi email dei docenti assegnati alla classe 2A.

Un docente presente in più classi dovrà comparire in ciascuna mailing list corrispondente, ma mai più di una volta all'interno della stessa lista.
Non presumere la struttura dell'XML senza averla verificata. Se il formato EDT non è documentato o non è disponibile durante lo sviluppo, creare un parser configurabile, documentare le ipotesi adottate e predisporre un XML di esempio anonimizzato per i test.
Il parser dovrà gestire correttamente eventuali namespace XML e rifiutare entità esterne o altre funzionalità XML non necessarie che possano comportare rischi di sicurezza.

**Fase 2 — Connessione a Google Workspace**
- Autenticarsi tramite il metodo di autenticazione configurato.
- Verificare che l'account possa eseguire le operazioni richieste.
- Accertarsi di avere le autorizzazioni necessarie per leggere i gruppi interessati e gestirne i membri.
- Interrompere il processo con un messaggio esplicativo se l'autenticazione o le autorizzazioni non sono valide.
- Non proseguire con operazioni parziali se non è possibile determinare in modo affidabile lo stato iniziale delle risorse necessarie.

**Fase 3 — Creazione dei gruppi mancanti**
Per ogni classe ricavata dall'XML, generare l'indirizzo:
`cdc_{nome_classe}@dominioscuola.xyz`
- Verificare se il gruppo esiste già in Google Workspace.
- Se esiste, non ricrearlo e passare alla gestione dei membri.
- Se non esiste, crearlo tramite le API ufficiali.
- Se la creazione fallisce, registrare l'errore e applicare una strategia sicura per proseguire o interrompere il processo.
- Gestire eventuali conflitti dovuti a operazioni concorrenti o a gruppi già esistenti.
- Non creare gruppi al di fuori di quelli derivati dalle classi effettivamente individuate e validate nel file XML.

**Fase 4 — Sincronizzazione dei membri**
Per ogni gruppo associato a una classe:
- Recuperare l'elenco completo dei membri attuali tramite le API di Google.
- Recuperare l'elenco desiderato degli indirizzi email ricavato da EDT.
- Confrontare i due insiemi ignorando differenze irrilevanti di maiuscole e minuscole negli indirizzi.
- Identificare i membri da aggiungere e quelli da rimuovere.
- Rimuovere tutti i membri attuali che non risultano più associati alla classe nell'XML.
- Aggiungere tutti i docenti previsti che non sono già membri.
- Verificare, quando possibile, che l'elenco finale corrisponda a quello desiderato.
La sincronizzazione deve essere idempotente: eseguire il programma più volte con lo stesso XML e lo stesso stato di Google Workspace non deve generare modifiche inutili.
*Precisazione importante:* per ottenere la corrispondenza esatta, il programma dovrà rimuovere soltanto i membri che non appartengono all'insieme desiderato e aggiungere soltanto quelli mancanti. Non è necessario rimuovere e reinserire indiscriminatamente tutti i membri a ogni esecuzione.
Le operazioni dovranno rispettare rigorosamente il perimetro di sicurezza definito nella sezione 3.2.
Se un gruppo contiene membri che non possono essere gestiti dal metodo di autenticazione utilizzato, oppure l'API restituisce uno stato inatteso, il programma dovrà segnalarlo esplicitamente senza dichiarare la sincronizzazione completata con successo.

**Fase 5 — Riepilogo finale**
Al termine dell'esecuzione, mostrare un riepilogo che includa:
- Numero di classi individuate.
- Numero di gruppi già esistenti.
- Numero di gruppi creati.
- Numero di docenti aggiunti.
- Numero di membri rimossi.
- Numero di gruppi sincronizzati correttamente.
- Numero di gruppi con errori o sincronizzazione incompleta.
- Eventuali anomalie rilevate nell'XML.
Se si sono verificati errori, il risultato complessivo non dovrà essere indicato come pienamente riuscito.

## 5. Memorizzazione delle impostazioni
L'applicazione deve conservare localmente le impostazioni necessarie per evitare di richiederle a ogni avvio.
In particolare:
- URL XML di EDT.
- Percorso del file JSON delle credenziali Google.
- Preferenze non sensibili dell'interfaccia, se presenti.
Al successivo avvio, i campi dovranno essere precompilati con i valori precedenti.
La memorizzazione dovrà essere compatibile sia con Windows sia con macOS.
Non incorporare il file delle credenziali Google nel pacchetto dell'applicazione e non copiarlo automaticamente nel repository o negli artefatti di distribuzione.
Se il file viene spostato o eliminato, mostrare un messaggio chiaro e permettere di selezionarlo nuovamente.
Proteggere le impostazioni e gli eventuali dati locali sensibili con le misure appropriate per ciascun sistema operativo.

## 6. Robustezza e sicurezza
L'applicazione dovrà essere progettata per un utilizzo reale in ambito scolastico.
**Requisiti:**
- Timeout appropriati per richieste HTTP e API.
- Gestione degli errori di rete, dei limiti di frequenza delle API e delle risposte temporaneamente indisponibili.
- Retry controllati con backoff esponenziale per gli errori temporanei, evitando di ripetere operazioni non idempotenti senza averne verificato l'esito.
- Paginazione per recuperare tutti i gruppi e i membri, quando richiesta dalle API.
- Validazione degli indirizzi email e dei nomi delle classi.
- Protezione da URL non validi e risposte HTTP inattese.
- Nessuna esposizione di segreti nei log.
- Nessuna modifica a gruppi estranei al formato autorizzato.
- Nessuna eliminazione di gruppi.
- Gestione esplicita delle sincronizzazioni parziali.
- Separazione tra interfaccia grafica, logica di parsing XML, client Google e logica di sincronizzazione.
Prima di qualsiasi modifica, il programma deve aver determinato in modo affidabile gli insiemi di classi e docenti da sincronizzare.
Se il file XML è vuoto, malformato, incompleto oppure non contiene le informazioni attese, non effettuare modifiche distruttive.
Prevedere una protezione specifica contro la rimozione massiva accidentale di membri dovuta a un XML valido sintatticamente ma incompleto o anomalo. In tali circostanze, richiedere una conferma esplicita oppure interrompere la sincronizzazione in sicurezza.

## 7. Aiuto e documentazione
L'applicazione deve includere due sezioni di aiuto accessibili direttamente dall'interfaccia.
- **Aiuto EDT:** Spiegare come ottenere l'URL dell'XML, come verificare che sia raggiungibile e quali informazioni deve contenere.
- **Aiuto Google Workspace:** Spiegare come:
  - Accedere a Google Cloud.
  - Creare o selezionare un progetto.
  - Abilitare le API necessarie.
  - Configurare il metodo di autenticazione.
  - Configurare l'eventuale delega a livello di dominio.
  - Concedere i privilegi minimi indispensabili.
  - Generare o scaricare il file JSON corretto.
  - Selezionarlo nell'applicazione.
  - Verificare che l'autenticazione funzioni.
Le istruzioni dovranno essere specifiche per la soluzione implementata e aggiornate alla documentazione ufficiale Google.
Se una limitazione tecnica di Google impedisce di garantire le restrizioni richieste, spiegare il problema e proporre l'alternativa più sicura, senza nascondere la limitazione.

## 8. Test automatici
Implementare test automatici per le funzionalità principali.
I test dovranno verificare almeno:
- Parsing di XML validi e gestione di XML malformati.
- Estrazione corretta delle classi e dei docenti.
- Rimozione dei duplicati.
- Normalizzazione e validazione delle email.
- Generazione corretta degli indirizzi `cdc_{classe}@dominioscuola.xyz`.
- Riconoscimento dei gruppi già esistenti.
- Creazione dei gruppi mancanti.
- Calcolo corretto delle aggiunte e delle rimozioni.
- Idempotenza delle sincronizzazioni.
- Impossibilità di modificare gruppi non autorizzati.
- Comportamento sicuro quando l'XML è incompleto o vuoto.
- Gestione degli errori API e delle interruzioni di rete.
- Salvataggio e ripristino delle impostazioni.
- Build e packaging per Windows e macOS.
Utilizzare test con dati simulati e credenziali fittizie. Non eseguire test distruttivi su un dominio Google Workspace reale.

## 9. Deliverable richiesti
Al termine del lavoro, il repository dovrà contenere:
- Codice sorgente completo dell'applicazione.
- Interfaccia grafica funzionante.
- Modulo di download e parsing XML.
- Modulo di autenticazione e integrazione Google Workspace.
- Logica di creazione dei gruppi e sincronizzazione dei membri.
- Gestione delle impostazioni persistenti.
- Test automatici.
- Documentazione per l'installazione in ambiente di sviluppo e per l'utilizzo finale.
- Istruzioni per configurare EDT e Google Workspace.
- File `.github/workflows/package.yml` funzionante.
- Configurazione per generare i pacchetti Windows e macOS.
- File `.gitignore` appropriato, che escluda credenziali, segreti, configurazioni locali e artefatti di build non necessari.
- Un esempio anonimizzato di XML EDT per i test, se necessario.

## 10. Modalità di lavoro
Procedere in modo autonomo e realizzare concretamente il progetto.
Prima di scegliere le tecnologie:
- Analizzare il workflow di packaging indicato.
- Verificare la documentazione ufficiale delle API Google necessarie.
- Identificare il formato effettivo dell'XML EDT oppure individuare esattamente quali informazioni mancano.
- Scegliere un'architettura semplice, affidabile e adatta alla distribuzione standalone su entrambi i sistemi operativi.
- Verificare che il metodo di autenticazione possa rispettare i vincoli di sicurezza richiesti.
Successivamente, implementare l'applicazione, i test e il processo di packaging.
Non limitarsi a proporre un'architettura o a scrivere pseudocodice: creare i file reali del progetto, eseguire i test disponibili, correggere gli errori e verificare il workflow di build per quanto consentito dall'ambiente.
Non dichiarare completate verifiche che non sono state effettivamente eseguite. Se un test, una build o una verifica richiede accessi o credenziali non disponibili, documentare esattamente cosa manca e come completarla.
Al termine, fornire un riepilogo delle funzionalità implementate, delle scelte tecnologiche, dei test eseguiti, dei limiti residui e delle istruzioni per generare e scaricare le versioni per macOS e Windows.

## 11. Architettura del codice, funzioni e leggibilità
Organizzare il codice in modo modulare, leggibile e facile da mantenere. Evitare funzioni monolitiche che svolgano troppe operazioni diverse: suddividere la logica in funzioni fondamentali ben nominate e in sottofunzioni specializzate, ciascuna responsabile di un compito chiaro.
Preferire nomi espliciti e coerenti. Aggiungere commenti quando aiutano a capire il motivo di una scelta, un vincolo di sicurezza, una trasformazione non ovvia o un comportamento particolare delle API. Evitare commenti superflui che si limitino a ripetere letteralmente il codice.

### 11.1 Suddivisione logica suggerita
I nomi seguenti sono esempi: adattarli al linguaggio e alle convenzioni adottate, mantenendo però la separazione delle responsabilità.

**A. Configurazione e impostazioni**
- `load_settings()` — carica le impostazioni locali salvate.
- `save_settings()` — salva l'URL EDT e il percorso del file delle credenziali.
- `validate_settings()` — verifica che le impostazioni obbligatorie siano presenti e valide.
- `get_credentials_file()` — recupera e verifica il percorso del file delle credenziali, senza stamparne il contenuto.
- `show_configuration_help()` — apre o mostra le istruzioni di configurazione.

**B. Download dell'XML EDT**
- `download_edt_xml(url)` — scarica l'XML dall'URL configurato e restituisce il contenuto oppure un errore esplicito.
- `validate_xml_response(response)` — verifica lo stato HTTP, il contenuto ricevuto e i limiti di dimensione previsti.
- `test_edt_connection(url)` — verifica la raggiungibilità dell'URL e la validità del documento senza apportare modifiche a Google Workspace.
Queste funzioni devono gestire timeout, errori HTTP e problemi di rete. Non devono proseguire con un documento vuoto o non valido.

**C. Parsing dell'XML e costruzione della mappa classe → email**
- `parse_edt_xml(xml_content)` — analizza l'XML e restituisce una rappresentazione strutturata delle informazioni rilevanti.
- `extract_classes_and_teachers(parsed_xml)` — individua le classi e le associazioni con i docenti.
- `normalize_class_name(class_name)` — normalizza il nome della classe secondo regole documentate.
- `normalize_email(email)` — rimuove spazi superflui e normalizza l'indirizzo per i confronti.
- `validate_email(email)` — verifica che l'indirizzo sia utilizzabile.
- `build_class_email_map(class_teacher_data)` — costruisce la mappa classe → insieme degli indirizzi email dei docenti, eliminando i duplicati.
- `build_group_email_map(class_email_map)` — converte la mappa in una mappa gruppo CDC → insieme delle email desiderate, generando gli indirizzi nel formato `cdc_{nome_classe}@dominioscuola.xyz`.
- `validate_group_email_map(group_email_map)` — controlla che ogni gruppo generato rispetti il formato autorizzato e che non vi siano classi o indirizzi anomali.

La struttura dati risultante dovrà essere equivalente a:
```json
{
    "cdc_1A@dominioscuola.xyz": {
        "docente1@dominioscuola.xyz",
        "docente2@dominioscuola.xyz"
    },
    "cdc_1B@dominioscuola.xyz": {
        "docente2@dominioscuola.xyz",
        "docente3@dominioscuola.xyz"
    }
}
```
Si tratta di un esempio illustrativo: la struttura effettiva può variare in base al linguaggio scelto, purché rappresenti chiaramente una mappa tra indirizzo del gruppo e insieme degli indirizzi email desiderati.

**D. Integrazione con Google Workspace / Admin SDK**
Raggruppare le chiamate alle API in un modulo o componente dedicato, separandole dalla logica di sincronizzazione.
- `create_google_workspace_client(credentials_path)` — inizializza il client API utilizzando il metodo di autenticazione configurato.
- `test_google_workspace_connection(client)` — verifica autenticazione e autorizzazioni necessarie.
- `get_group(client, group_email)` — verifica se il gruppo esiste e recupera i dati necessari.
- `create_group(client, group_email, description)` — crea un gruppo mancante, solo dopo aver validato l'indirizzo.
- `list_group_members(client, group_email)` — recupera tutti i membri del gruppo, gestendo la paginazione.
- `add_group_member(client, group_email, member_email)` — aggiunge un membro al gruppo.
- `remove_group_member(client, group_email, member_email)` — rimuove un membro dal gruppo.
- `validate_managed_group(group_email)` — verifica che il gruppo rientri nel perimetro autorizzato prima di qualsiasi operazione di scrittura.
Ogni funzione che effettua modifiche deve applicare i controlli di sicurezza anche se viene invocata al di fuori del normale flusso dell'interfaccia. Non affidarsi soltanto ai controlli del chiamante.

**E. Confronto e sincronizzazione dei membri**
- `calculate_membership_changes(current_members, desired_members)` — calcola gli insiemi dei membri da aggiungere e da rimuovere.
- `sync_group_members(client, group_email, desired_members, dry_run=False)` — porta il gruppo allo stato desiderato applicando soltanto le differenze necessarie.
- `sync_single_group(client, group_email, desired_members, dry_run=False)` — coordina la verifica o la creazione del gruppo e la sincronizzazione dei relativi membri.
- `sync_all_groups(client, group_email_map, dry_run=False)` — esegue la sincronizzazione di tutte le classi e raccoglie i risultati.
- `verify_group_members(client, group_email, desired_members)` — verifica, quando possibile, che i membri finali corrispondano all'insieme desiderato.
*Nota sulla sostituzione dei membri:* la funzione di sincronizzazione deve ottenere lo stesso risultato di una sostituzione completa dell'elenco, ma è preferibile rimuovere solo i membri non più desiderati e aggiungere solo quelli mancanti. In questo modo si evitano modifiche inutili e si riduce il rischio operativo. Non eliminare mai il gruppo stesso.

**F. Orchestrazione del processo**
Creare una funzione principale che coordini le fasi, lasciando i dettagli implementativi alle funzioni specializzate.
- `run_sync()` — coordina l'intero processo: validazione della configurazione, download XML, parsing, validazione dei dati, autenticazione Google, eventuale simulazione, sincronizzazione e riepilogo.
- `run_dry_run()` — esegue i controlli e calcola le modifiche previste senza scrivere su Google Workspace.
- `build_sync_summary(results)` — prepara il riepilogo finale con conteggi, errori e avvisi.
- `handle_sync_error(error, context)` — converte gli errori tecnici in messaggi comprensibili e li registra in modo sicuro.

L'orchestrazione dovrebbe essere leggibile a colpo d'occhio, indicativamente secondo questa sequenza:
```text
run_sync()
    ├── validate_settings()
    ├── download_edt_xml()
    ├── parse_edt_xml()
    ├── build_group_email_map()
    ├── validate_group_email_map()
    ├── create_google_workspace_client()
    ├── test_google_workspace_connection()
    ├── sync_all_groups()
    └── build_sync_summary()
```
Lo schema è indicativo e può essere adattato, purché il flusso rimanga semplice da seguire e ogni fase sia testabile separatamente.

**G. Log e interfaccia grafica**
- `log_info(message)` — registra un'operazione informativa.
- `log_warning(message)` — registra un avviso.
- `log_error(message, error=None)` — registra un errore senza includere segreti.
- `update_progress(stage, current, total)` — aggiorna lo stato visibile dell'esecuzione.
- `display_sync_summary(summary)` — mostra il riepilogo conclusivo.
La logica di business non dovrebbe dipendere direttamente dai widget grafici. Il modulo di sincronizzazione dovrebbe restituire risultati strutturati; l'interfaccia si occuperà di visualizzarli.

### 11.2 Principi di implementazione
- **Una responsabilità per funzione:** ogni funzione deve svolgere un compito ben definito.
- **Funzioni piccole e leggibili:** suddividere le operazioni complesse in sottofunzioni con nomi descrittivi.
- **Commenti utili:** documentare scopo, parametri, valore restituito, possibili errori e motivazioni delle decisioni non ovvie. Usare docstring o il sistema di documentazione idiomatico del linguaggio scelto.
- **Tipi e strutture dati espliciti:** quando il linguaggio lo consente, dichiarare i tipi dei parametri e dei valori restituiti.
- **Errori espliciti:** evitare di nascondere gli errori o restituire valori ambigui. Usare eccezioni o risultati strutturati in modo coerente.
- **Dipendenze isolate:** separare download HTTP, parsing XML, API Google, sincronizzazione, configurazione e interfaccia.
- **Testabilità:** le funzioni fondamentali devono poter essere testate indipendentemente dall'interfaccia grafica e dalle API reali.
- **Nessun effetto collaterale nascosto:** una funzione che calcola una mappa o confronta due insiemi non deve effettuare chiamate di rete o modifiche a Google.
- **Sicurezza in profondità:** validare il gruppo autorizzato dentro ogni funzione che può modificarlo, non soltanto nel flusso principale.
- **Niente duplicazione evitabile:** riutilizzare le funzioni comuni invece di copiare la stessa logica in più punti.
Aggiungere commenti in italiano, oppure in inglese se è più coerente con il progetto, mantenendo una sola lingua in modo uniforme. La priorità è che un altro sviluppatore possa comprendere rapidamente il flusso senza dover ricostruire la logica da molte funzioni generiche o eccessivamente complesse.

## 12. Aggiornamento dei deliverable
Oltre ai deliverable elencati nella sezione 9, includere:
- Una struttura dei moduli coerente con le responsabilità descritte nella sezione 11.
- Una breve documentazione tecnica che descriva i moduli principali e il flusso delle chiamate.
- Docstring o commenti per le funzioni fondamentali e per le parti non ovvie.
- Test unitari per le funzioni di parsing, normalizzazione, costruzione della mappa gruppo-email e calcolo delle differenze tra membri.

## 13. Isolamento del codice Google Workspace e revisione delle operazioni sensibili
Un requisito architetturale prioritario è concentrare tutte le interazioni con Google Workspace / Admin SDK Directory API in una classe dedicata, separata dal parsing XML, dall'interfaccia grafica e dall'orchestrazione della sincronizzazione.
L'obiettivo è consentire una revisione manuale rapida e mirata: voglio poter aprire un unico file e individuare immediatamente le poche funzioni che possono modificare lo stato di Google Workspace, senza dover cercare chiamate di scrittura sparse nel progetto.

### 13.1 Classe dedicata
Creare una classe con un nome esplicito, ad esempio `GoogleWorkspaceClient` oppure `GoogleAdminService`, collocata in un modulo autonomo, ad esempio `google_workspace_client.py`.
Questa classe deve contenere tutte le chiamate alle API Google, incluse autenticazione, lettura dei gruppi, lettura dei membri, creazione dei gruppi e gestione dei membri.
Nessun altro modulo deve invocare direttamente le API Google o contenere chiamate HTTP/API equivalenti per modificare Google Workspace. Gli altri componenti devono passare esclusivamente attraverso i metodi pubblici di questa classe.
Separare chiaramente:
- operazioni di sola lettura;
- operazioni che creano o modificano risorse;
- eventuali operazioni di cancellazione.

### 13.2 Ridurre al minimo le funzioni che possono modificare Google Workspace
Raggruppare le operazioni di scrittura nel minor numero ragionevole di metodi, senza sacrificare leggibilità, testabilità o sicurezza.
Preferire una struttura simile alla seguente:
- `GoogleWorkspaceClient.__init__(...)` — inizializza il client e la configurazione di autenticazione.
- `test_connection()` — verifica autenticazione e permessi.
- `get_group(group_email)` — sola lettura: recupera un gruppo, se esiste.
- `list_group_members(group_email)` — sola lettura: restituisce tutti i membri, gestendo la paginazione.
- `ensure_group_exists(group_email, description=...)` — operazione di scrittura: crea il gruppo soltanto se manca; non modifica né elimina gruppi esistenti.
- `replace_group_members(group_email, desired_members, dry_run=False)` — operazione di scrittura: confronta i membri attuali con quelli desiderati, rimuove quelli non più previsti e aggiunge quelli mancanti.
- `sync_group(group_email, desired_members, dry_run=False)` — metodo di alto livello che coordina la verifica/creazione del gruppo e l'aggiornamento dei membri, riutilizzando i metodi precedenti.
Adattare la suddivisione se necessario per rispettare le API effettive, ma mantenere pochi punti d'ingresso di scrittura, facili da individuare e revisionare. I metodi ausiliari che eseguono chiamate di scrittura devono rimanere privati alla classe, chiaramente nominati e commentati.
Non introdurre funzioni di cancellazione dei gruppi: il progetto non deve eliminare gruppi Google Workspace. La rimozione di un membro da una mailing list è invece prevista quando quel membro non compare più nell'insieme desiderato.

### 13.3 Marcatura visiva obbligatoria del codice sensibile
Nel file che contiene la classe Google, inserire commenti molto visibili immediatamente prima di ogni funzione o blocco che può modificare Google Workspace. Utilizzare marcatori uniformi e facilmente ricercabili, ad esempio:
```python
# ============================================================================
# !!! REVISIONE SICUREZZA: OPERAZIONE DI SCRITTURA SU GOOGLE WORKSPACE !!!
# Questo metodo può creare un gruppo o modificarne i membri.
# Verificare con particolare attenzione:
# - che il gruppo sia nel formato cdc_{classe}@dominioscuola.xyz;
# - che non possa operare su gruppi estranei;
# - che le email siano validate;
# - che dry_run impedisca qualsiasi scrittura reale.
# ============================================================================
```
Usare un marcatore analogo per i metodi che rimuovono membri, specificando chiaramente che si tratta di una rimozione di iscritti dal gruppo e non della cancellazione del gruppo stesso.
Aggiungere anche un'intestazione ben visibile all'inizio del file, per esempio:
```python
# SECURITY REVIEW MAP
# Metodi di sola lettura: test_connection, get_group, list_group_members
# Punti di ingresso che possono scrivere su Google: ensure_group_exists,
# replace_group_members, sync_group
# Cancellazione di gruppi: NON IMPLEMENTATA
#
# Cercare "REVISIONE SICUREZZA" per trovare i blocchi sensibili.
```
Aggiornare questa mappa se cambiano i nomi dei metodi.

### 13.4 Regole obbligatorie per tutte le scritture
Ogni metodo che può effettuare una scrittura deve:
- Verificare internamente che l'indirizzo del gruppo sia autorizzato e rispetti rigorosamente il formato `cdc_{nome_classe}@dominioscuola.xyz`.
- Validare il nome della classe e l'indirizzo email del gruppo prima di effettuare chiamate API.
- Validare gli indirizzi email dei membri prima di aggiungerli.
- Non permettere che un parametro arbitrario consenta di aggirare i controlli sul gruppo.
- Rispettare `dry_run=True`, che deve impedire ogni operazione di scrittura reale.
- Restituire un risultato strutturato che indichi le azioni previste o eseguite, gli errori e l'esito della verifica finale.
- Registrare le operazioni senza esporre token, credenziali o chiavi private.
La verifica del gruppo autorizzato deve essere applicata all'interno della classe e nel percorso di scrittura, non soltanto nell'interfaccia grafica o nella funzione di orchestrazione.
Le chiamate API di scrittura devono essere facilmente individuabili nel codice. Evitare di nasconderle in helper generici condivisi con il codice di sola lettura.

### 13.5 Nessuna cancellazione di gruppi e nessuna scrittura indiretta
Il progetto non deve implementare operazioni API per eliminare gruppi. Non deve neppure effettuare modifiche a gruppi da altri moduli, attraverso chiamate HTTP dirette o tramite helper generici.
La rimozione dei membri non più desiderati deve avvenire esclusivamente attraverso il metodo dedicato alla sincronizzazione dei membri nella classe Google, con un commento di revisione sicurezza immediatamente visibile.
Prima di completare il progetto, cercare nel repository tutte le chiamate API e HTTP che potrebbero modificare Google Workspace e verificare che siano contenute nella classe dedicata e marcate correttamente.

### 13.6 Documentazione della revisione
Nel README o in un documento tecnico breve, aggiungere una sezione **Revisione del codice sensibile** che indichi:
- il percorso del file che contiene la classe Google;
- i nomi esatti dei metodi che possono scrivere;
- dove si trova la logica di rimozione dei membri;
- la conferma che non esistono funzioni per eliminare gruppi;
- il comando o la ricerca testuale consigliata per individuare tutti i marcatori `REVISIONE SICUREZZA`.
Aggiungere test automatici che dimostrino che i metodi di scrittura rifiutano gruppi fuori dal formato consentito e che la modalità `dry_run` non esegue scritture reali.
*Criterio di accettazione:* deve essere possibile revisionare le operazioni sensibili aprendo un singolo modulo e leggendo pochi metodi chiaramente marcati. Non distribuire la logica di scrittura tra molti file e non dichiarare che la revisione è completa finché tutte le chiamate di modifica non sono state individuate.