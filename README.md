# EDT to Google Workspace CdC Sync

Applicazione desktop standalone multiplatform (macOS e Windows) sviluppata in **Kotlin** con **Compose Multiplatform** per automatizzare la creazione e l'aggiornamento delle mailing list istituzionali dei **Consigli di Classe (CdC)** tramite file XML esportato da **EDT (Index Éducation)** e le API ufficiali di **Google Workspace (Admin SDK Directory API)**.

---

## Indice
1. [Obiettivo e Caratteristiche](#obiettivo-e-caratteristiche)
2. [Architettura del Progetto](#architettura-del-progetto)
3. [Revisione del Codice Sensibile (Sicurezza)](#revisione-del-codice-sensibile-sicurezza)
4. [Guida alla Configurazione](#guida-alla-configurazione)
   - [Configurazione EDT](#configurazione-edt)
   - [Configurazione Google Workspace](#configurazione-google-workspace)
5. [Guida all'Uso dell'Applicazione](#guida-alluso-dellapplicazione)
6. [Packaging e Distribuzione Multiplatform](#packaging-e-distribuzione-multiplatform)
7. [Firma Digitale, Gatekeeper e SmartScreen](#firma-digitale-gatekeeper-e-smartscreen)
8. [Test Automatici](#test-automatici)
9. [Struttura dei File nel Repository](#struttura-dei-file-nel-repository)

---

## 1. Obiettivo e Caratteristiche

L'applicazione risolve il problema della gestione manuale delle mailing list dei Consigli di Classe nelle scuole:
- **Zero dipendenze runtime per l'utente finale**: distribuibile come pacchetto nativo `.dmg` / `.app` per macOS, installer `.msi` / `.exe` e archivio standalone `.zip` per Windows. Non richiede l'installazione preventiva di Java, Python o Node.js.
- **Formato autorizzato esclusivo**: gestisce unicamente gruppi nel formato:
  $$\text{cdc\_}\{\text{nome\_classe}\}@\text{dominioscuola.xyz}$$
- **Nessuna eliminazione di gruppi**: l'applicazione non contiene alcuna funzione per cancellare gruppi Google Workspace. Rimuove esclusivamente i docenti non più presenti dal singolo gruppo CdC e aggiunge i nuovi docenti previsti.
- **Idempotenza e sicurezza**: sincronizzazioni ripetute con gli stessi dati non comportano alcuna modifica superflua.
- **Protezione XXE**: parser XML configurato secondo gli standard OWASP per disabilitare DTD ed entità esterne.
- **Modalità Simulazione (Dry Run)**: calcola e visualizza nei log tutte le modifiche previste senza scrivere su Google Workspace.
- **Salvataggio automatico impostazioni**: memorizza URL EDT, dominio e percorso credenziali nella cartella utente standard (`~/.edttocdc/settings.json`).

---

## 2. Architettura del Progetto

Il codice sorgente è organizzato secondo il principio di singola responsabilità e rigoroso isolamento delle dipendenze di rete e delle API:

```text
it.madx.edttocdc
├── config
│   ├── AppSettings.kt           // Modello dei parametri di configurazione
│   └── SettingsManager.kt       // Salvataggio/caricamento JSON e validazione credenziali
├── model
│   └── EdtModels.kt             // Modelli di dominio (ClassTeacherData, MembershipDiff, SyncSummary, LogEntry)
├── edt
│   ├── EdtXmlDownloader.kt      // Download HTTP/HTTPS sicuro, timeout e verifica integrità
│   ├── EdtXmlParser.kt          // Parser XML con protezione XXE (schema relazionale standard EDT)
│   └── ClassGroupMapper.kt      // Normalizzazione, validazione sintassi email e verifica formato cdc_
├── google
│   └── GoogleWorkspaceClient.kt // ISOLAMENTO RIGOROSO di tutte le API Google Workspace Admin SDK
├── sync
│   └── SyncEngine.kt            // Orchestrazione della sincronizzazione e monitoraggio avanzamento
└── ui
    ├── theme/                   // Tema Material 3, colori (Dark/Light mode)
    ├── components/
    │   ├── HelpDialogs.kt       // Guide modali interattive per EDT e Google Cloud
    │   └── SummaryDialog.kt     // Finestra di riepilogo con statistiche conclusive
    ├── MainViewModel.kt         // State holder, gestione coroutine asincrone e clipboard
    ├── MainScreen.kt            // Schermata principale Compose Multiplatform
    └── Main.kt                  // Entry point desktop dell'applicazione
```

### Flusso delle Chiamate

```text
runSync(settings, dryRun)
  │
  ├── 1. SettingsManager.validateSettings()
  │      └─ Verifica URL EDT, percorso JSON credenziali, dominio scuola
  │
  ├── 2. EdtXmlDownloader.downloadEdtXml()
  │      └─ Scarica XML via HTTP/HTTPS con timeout (15s) e controllo dimensioni (max 25MB)
  │
  ├── 3. EdtXmlParser.parseEdtXml()
  │      └─ Analisi protetta da XXE basata sullo schema ufficiale relazionale EDT
  │
  ├── 4. ClassGroupMapper.buildClassEmailMap() & buildGroupEmailMap()
  │      └─ Normalizzazione nomi classi, pulizia email, deduplicazione per classe
  │      └─ Generazione indirizzi cdc_{classe}@dominio e validazione anomalie
  │
  ├── 5. GoogleWorkspaceClient.testConnection()
  │      └─ Autenticazione Service Account / Delega di dominio su Directory API
  │
  ├── 6. Iterazione su ciascun gruppo autorizzato:
  │      ├── GoogleWorkspaceClient.ensureGroupExists()  [SCRITTURA: solo se non esiste]
  │      └── GoogleWorkspaceClient.replaceGroupMembers() [SCRITTURA: diff membri]
  │
  └── 7. Generazione e visualizzazione SyncSummary
```

---

## 3. Revisione del Codice Sensibile (Sicurezza)

Come prescritto dalle direttive di sicurezza, tutte le interazioni con Google Workspace / Admin SDK Directory API sono concentrate **in un unico file dedicato**, facilitando la verifica manuale e l'audit del codice.

### File del client Google
- **Percorso del file:**  
  [`composeApp/src/desktopMain/kotlin/it/madx/edttocdc/google/GoogleWorkspaceClient.kt`](file:///Users/madx/Projects/Madx/edt_to_cdc_gadmin_groups/composeApp/src/desktopMain/kotlin/it/madx/edttocdc/google/GoogleWorkspaceClient.kt)

### Punti di ingresso e operazioni consentite

| Metodo | Natura | Descrizione e Vincoli |
| :--- | :--- | :--- |
| `testConnection()` | **Sola lettura** | Verifica l'autenticazione leggendo un gruppo con limite 1. |
| `getGroup(groupEmail)` | **Sola lettura** | Recupera il gruppo se esiste, oppure restituisce `null`. |
| `listGroupMembers(groupEmail)` | **Sola lettura** | Elenca tutti i membri attuali gestendo la paginazione (`pageToken`). |
| `ensureGroupExists(...)` | **Scrittura** | Crea il gruppo solo se mancante. Rispetta `dry_run`. |
| `replaceGroupMembers(...)` | **Scrittura** | Rimuove gli iscritti non previsti e aggiunge i docenti mancanti. Rispetta `dry_run`. |
| `syncGroup(...)` | **Scrittura** | Coordina la verifica/creazione del gruppo e l'allineamento dei membri. |

### Conferma di non-eliminazione dei gruppi
- Nel progetto **NON esiste alcuna funzione o chiamata API per eliminare gruppi Google Workspace**.  
- La rimozione dei docenti riguarda unicamente la cancellazione dell'iscrizione dal gruppo (`directoryService.members().delete(groupEmail, memberEmail)`), **mai la cancellazione del gruppo stesso**.

### Ricerca rapida nel codice dei blocchi sensibili
Per individuare immediatamente tutti i blocchi sensibili di scrittura:
```bash
grep -rn "REVISIONE SICUREZZA" composeApp/src/desktopMain/
```

Ogni metodo di scrittura richiama internamente:
```kotlin
validateManagedGroup(groupEmail)
```
Tale funzione valida che l'indirizzo corrisponda tassativamente a `^cdc_[a-zA-Z0-9_-]+@<dominio>$`. Qualsiasi tentativo di operare su indirizzi non conformi (es. `tuttascuola@...`, `dirigenza@...`) genera immediatamente una `SecurityException` bloccando l'operazione prima di effettuare qualsiasi chiamata di rete.

---

## 4. Guida alla Configurazione

### Configurazione EDT
1. **Esportazione da EDT (Index Éducation):**
   - Dal client amministrativo EDT, esportare la pianificazione oraria in formato XML contenente Docenti (con email istituzionale), Classi e Corsi/Attività.
   - Pubblicare il file su un server HTTP o preferibilmente **HTTPS** raggiungibile dall'applicazione.
2. **Campi richiesti:**
   - Ciascun docente deve avere valorizzato l'attributo o nodo `Email`, `Courriel` o `Mail`.
   - Ciascuna classe deve avere un nome o codice (es. `1A`, `2B`, `3BS`).
   - Le lezioni/corsi devono collegare i docenti alle rispettive classi.
3. Nella schermata principale dell'applicazione, inserire l'URL e premere **Verifica XML**.

### Configurazione Google Workspace
1. **Google Cloud Console:**
   - Accedere a [console.cloud.google.com](https://console.cloud.google.com) con l'account super-amministratore della scuola.
   - Creare un nuovo progetto (es. `MailingList-CdC`).
   - In *API e servizi > Libreria*, cercare **Admin SDK API** e fare clic su **Abilita**.
2. **Creazione Service Account:**
   - In *IAM e amministrazione > Account di servizio*, creare un account di servizio (es. `cdc-sync-sa`).
   - Non assegnare ruoli IAM cloud non necessari.
   - Aprire l'account di servizio creato, andare nella scheda **Chiavi**, fare clic su **Aggiungi chiave > Crea nuova chiave** e selezionare il formato **JSON**.
   - Salvare il file JSON in modo sicuro sul proprio computer.
3. **Delega a livello di dominio (Domain-Wide Delegation):**
   - Nella Console di amministrazione Google Workspace ([admin.google.com](https://admin.google.com)), andare in:  
     **Sicurezza > Controllo degli accessi e dei dati > Controlli API > Gestisci delega a livello di dominio**.
   - Fare clic su **Aggiungi nuovo**.
   - Nel campo **ID client**: incollare l'ID client numerico del Service Account (reperibile nella scheda del Service Account su Google Cloud oppure aprendo il file JSON scaricato alla voce `"client_id"`).
   - Nel campo **Ambiti OAuth (separati da virgola / comma-delimited)**, inserire esattamente:
     ```text
     https://www.googleapis.com/auth/admin.directory.group, https://www.googleapis.com/auth/admin.directory.group.member, https://www.googleapis.com/auth/apps.groups.settings
     ```
   - Fare clic su **Autorizza**.
4. **Configurazione nell'applicazione:**
   - Selezionare il file JSON di credenziali con il pulsante **Sfoglia...**.
   - Inserire l'email dell'amministratore per la delega DWD (es. `admin@scuola.edu.it`).
   - Il dominio istituzionale della scuola viene ricavato automaticamente dal file EDT.
   - Fare clic su **Verifica Connessione**.

---

## 5. Guida all'Uso dell'Applicazione

1. **Avvio:** aprire l'applicazione tramite doppio clic sul pacchetto installato o eseguibile standalone.
2. **Compilazione iniziale:** compilare i campi EDT e Google Workspace. I valori vengono salvati automaticamente per i successivi utilizzi.
3. **Simulazione preventiva (Dry Run):**
   - Lasciare abilitata l'opzione **Simulazione (Dry Run)**.
   - Fare clic su **Avvia Simulazione (Dry Run)**.
   - Nella console dei log di destra apparirà l'analisi dettagliata con l'anteprima dei gruppi da creare e dei docenti da aggiungere o rimuovere, senza effettuare scritture reali.
4. **Sincronizzazione effettiva:**
   - Disattivare l'opzione **Simulazione (Dry Run)**.
   - Fare clic su **Avvia Sincronizzazione**.
   - Seguire l'avanzamento in tempo reale sulla barra di progresso e nella console.
   - Al termine, una finestra di dialogo mostrerà il riepilogo finale (classi elaborate, gruppi creati, docenti aggiunti, docenti rimossi, eventuali anomalie).
5. **Esportazione dei log:** è possibile copiare l'intero log negli appunti (**Copia log**) o salvarlo in un file di testo locale (**Salva su file**).

---

## 6. Packaging e Distribuzione Multiplatform

Il repository include il workflow di packaging automatizzato tramite **GitHub Actions**:
`.github/workflows/package.yml`

Il workflow si attiva al rilascio di una release o tag (`Release-*`, `v*`) o manualmente tramite `workflow_dispatch`, producendo gli artefatti distribuiti:
- **macOS:** Immagine disco `.dmg` con app bundle `.app` autonomo (`EdtToCdcSync-1.0.0.dmg`).
- **Windows:** Installer `.msi`, eseguibile `.exe` e archivio standalone `.zip` contenente l'applicazione portabile con runtime JVM Temurin 21 incorporato.
- **Linux:** Pacchetto Debian `.deb`.
- **Standalone JAR:** `EdtToCdcSync-1.0.0-standalone.jar` (eseguibile multipiattaforma).

### Compilazione ed esecuzione in ambiente di sviluppo locale
Requisiti di sviluppo: JDK 21 installato.

```bash
# Esecuzione dell'applicazione in sviluppo
./gradlew :composeApp:run

# Esecuzione della suite completa di test unitari
./gradlew :composeApp:desktopTest

# Creazione del pacchetto DMG per macOS (se eseguito su macOS)
./gradlew :composeApp:packageDmg

# Creazione dell'app bundle portabile
./gradlew :composeApp:createDistributable

# Creazione del JAR standalone con runtime nativi inclusi
./gradlew :composeApp:fatJar
```

---

## 7. Firma Digitale, Gatekeeper e SmartScreen

- **macOS Gatekeeper:** i pacchetti compilati localmente o tramite workflow GitHub Actions non sono firmati con un certificato Apple Developer a pagamento né sottoposti a notarizzazione Apple. Al primo avvio su macOS, se Gatekeeper blocca l'applicazione ("sviluppatore non verificato"), è sufficiente fare clic destro sull'app > **Apri**, oppure andare in *Impostazioni di Sistema > Privacy e Sicurezza* e fare clic su **Apri comunque**.
- **Windows SmartScreen:** in assenza di un certificato EV (Extended Validation) di firma del codice Windows, SmartScreen potrebbe mostrare un avviso di "PC protetto da Windows". È sufficiente fare clic su *Ulteriori informazioni* > *Esegui comunque*.

---

## 8. Test Automatici

La suite di test verifica automaticamente:
- Parsing di XML relazionali e gerarchici EDT.
- Rifiuto ed eccezione per XML vuoto, incompleto o malformato.
- Blocco di tentativi di iniezione XXE (disabilitazione DTD ed entità esterne).
- Normalizzazione dei nomi delle classi (rimozione spazi, sostituzione barre e caratteri speciali).
- Normalizzazione e validazione RFC di indirizzi email.
- Deduplicazione delle email dei docenti all'interno della stessa classe.
- Generazione e convalida degli indirizzi `cdc_{classe}@dominio`.
- Rifiuto immediato (`SecurityException`) di qualsiasi gruppo esterno al formato autorizzato.
- Calcolo esatto delle differenze (`MembershipDiff`) tra membri correnti e desiderati.
- Confronto email case-insensitive.
- Idempotenza completa delle sincronizzazioni.
- Assenza riflessiva di metodi di cancellazione gruppi in `GoogleWorkspaceClient`.
- Presenza nel codice sorgente dei marcatori di sicurezza obbligatori (`SECURITY REVIEW MAP` e `REVISIONE SICUREZZA`).
- Salvataggio, caricamento e validazione delle impostazioni locali.

Per eseguire i test:
```bash
./gradlew :composeApp:desktopTest
```

---

## 9. Struttura dei File nel Repository

```text
.
├── .github/workflows/package.yml  # Workflow CI/CD GitHub Actions multiplatform
├── composeApp/
│   ├── build.gradle.kts           # Configurazione Compose Multiplatform e native packaging
│   └── src/
│       ├── desktopMain/
│       │   ├── kotlin/it/madx/edttocdc/
│       │   │   ├── config/        # Gestione impostazioni locali
│       │   │   ├── edt/           # Download e parser XML EDT
│       │   │   ├── google/        # Client isolato Google Workspace Admin SDK
│       │   │   ├── model/         # Modelli dati di dominio
│       │   │   ├── sync/          # Motore di orchestrazione
│       │   │   └── ui/            # Interfaccia grafica Compose Desktop
│       │   └── resources/
│       │       ├── icons/         # Icone native (.png, .ico, .icns)
│       │       └── samples/       # File XML EDT di esempio anonimizzati
│       └── desktopTest/           # Suite di test unitari automatizzati
├── gradle/
│   ├── libs.versions.toml         # Version catalog dipendenze
│   └── wrapper/                   # Gradle wrapper 8.10
├── build.gradle.kts               # Root build script
├── settings.gradle.kts            # Configurazione moduli Gradle
├── gradle.properties              # Parametri JVM per il build
├── .gitignore                     # Esclusione build, artefatti e credenziali Google JSON
└── README.md                      # Documentazione completa del progetto
```