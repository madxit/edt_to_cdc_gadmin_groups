# EDT to Google Workspace CdC Sync

Applicazione desktop standalone multiplatform (macOS e Windows) sviluppata in **Kotlin** con **Compose Multiplatform** per automatizzare la creazione e l'aggiornamento delle mailing list istituzionali dei **Consigli di Classe (CdC)** tramite file XML esportato da **EDT (Index Éducation)** e le API ufficiali di **Google Workspace (Admin SDK Directory API e Groups Settings API)**.

---

## Indice
1. [Obiettivo e Caratteristiche](#1-obiettivo-e-caratteristiche)
2. [Architettura del Progetto](#2-architettura-del-progetto)
3. [Politica di Accesso e Permessi dei Gruppi (Mailing List Interna)](#3-politica-di-accesso-e-permessi-dei-gruppi-mailing-list-interna)
4. [Funzionalità di Esportazione (CSV ed Excel)](#4-funzionalità-di-esportazione-csv-ed-excel)
5. [Revisione del Codice Sensibile (Sicurezza)](#5-revisione-del-codice-sensibile-sicurezza)
6. [Guida alla Configurazione](#6-guida-alla-configurazione)
   - [Configurazione EDT](#configurazione-edt)
   - [Configurazione Google Workspace](#configurazione-google-workspace)
7. [Guida all'Uso dell'Applicazione](#7-guida-alluso-dellapplicazione)
8. [Packaging e Distribuzione Multiplatform](#8-packaging-e-distribuzione-multiplatform)
9. [Firma Digitale, Gatekeeper e SmartScreen](#9-firma-digitale-gatekeeper-e-smartscreen)
10. [Test Automatici](#10-test-automatici)
11. [Struttura dei File nel Repository](#11-struttura-dei-file-nel-repository)
---

## 1. Obiettivo e Caratteristiche

L'applicazione risolve il problema della gestione manuale delle mailing list dei Consigli di Classe nelle scuole:
- **Zero dipendenze runtime per l'utente finale**: distribuibile come pacchetto nativo `.dmg` / `.app` per macOS, installer `.msi` / `.exe` e archivio standalone `.zip` per Windows. Non richiede l'installazione preventiva di Java, Python o Node.js.
- **Rilevamento automatico del dominio**: il dominio istituzionale della scuola (es. `scuola.edu.it`) viene ricavato automaticamente analizzando i suffissi email dei docenti estratti dal file XML di EDT.
- **Formato autorizzato esclusivo**: gestisce unicamente gruppi con indirizzo email nel perimetro protetto:
  $$\text{cdc\_}\{\text{nome\_classe}\}@\text{dominioscuola.xyz}$$
  mentre il nome visualizzato del gruppo viene formattato con la classe in maiuscolo (es. `Consiglio di Classe 1A`).
- **Nessuna eliminazione di gruppi**: l'applicazione non contiene alcuna funzione o chiamata API per cancellare gruppi Google Workspace. Rimuove esclusivamente i docenti non più presenti dal singolo gruppo CdC e aggiunge i docenti previsti.
- **Idempotenza e sicurezza**: sincronizzazioni ripetute con gli stessi dati non comportano alcuna modifica superflua. Se le impostazioni di accesso o i membri sono già conformi, le chiamate di scrittura vengono saltate.
- **Protezione XXE**: parser XML configurato secondo gli standard OWASP per disabilitare DTD ed entità esterne.
- **Flusso sequenziale guidato in 3 fasi**: all'avvio della sincronizzazione vengono eseguite in sequenza la verifica della sorgente dati EDT, la verifica della connessione a Google Workspace e infine l'allineamento effettivo.
- **Salvataggio automatico impostazioni**: memorizza URL EDT, email amministratore e percorso credenziali nella cartella utente standard (`~/.edttocdc/settings.json`).

---

## 2. Architettura del Progetto

Il codice sorgente è organizzato secondo il principio di singola responsabilità e rigoroso isolamento delle dipendenze di rete e delle API:

```text
it.madx.edttocdc
├── config
│   ├── AppSettings.kt              // Modello dei parametri di configurazione
│   └── SettingsManager.kt          // Salvataggio/caricamento JSON e validazione credenziali
├── model
│   └── EdtModels.kt                // Modelli di dominio (ClassTeacherData, MembershipDiff, SyncSummary, LogEntry)
├── edt
│   ├── EdtXmlDownloader.kt         // Download HTTP/HTTPS sicuro, timeout e verifica integrità
│   ├── EdtXmlParser.kt             // Parser XML con protezione XXE (schema relazionale standard EDT)
│   └── ClassGroupMapper.kt         // Normalizzazione, validazione sintassi email, rilevamento dominio e mapping cdc_
├── google
│   ├── GoogleWorkspaceAuthHelper.kt // Autenticazione DWD e factory servizi Directory e Groups Settings
│   ├── GoogleWorkspaceClient.kt    // Sincronizzazione gruppi, gestione membri e allineamento permessi
│   └── GoogleWorkspaceExporter.kt  // Servizio in SOLA LETTURA per esportazioni CSV ed Excel (.xlsx)
├── sync
│   └── SyncEngine.kt               // Orchestrazione della sincronizzazione e monitoraggio avanzamento
└── ui
    ├── theme/                      // Tema Material 3, colori e tipografia
    ├── components/
    │   ├── AboutDialog.kt          // Dialog informativo con icona grande e tabella Key-Value borderless
    │   ├── ExportDropdownButton.kt // Menu a discesa multilivello integrato per export CSV/Excel
    │   ├── HelpDialogs.kt          // Guide modali interattive per EDT e Google Cloud
    │   └── SummaryDialog.kt        // Finestra di riepilogo con statistiche conclusive
    ├── MainViewModel.kt            // State holder, gestione coroutine asincrone e clipboard
    ├── MainScreen.kt               // Schermata principale Compose Multiplatform
    └── Main.kt                     // Entry point desktop dell'applicazione (finestra 1300x840 dp)
```

---

## 3. Politica di Accesso e Permessi dei Gruppi (Mailing List Interna)

I gruppi CdC creati o aggiornati dall'applicazione sono configurati per funzionare come **mailing list interne**, scrivibili da chiunque all'interno dell'organizzazione della scuola ma con conversazioni e dettagli riservati a proprietari e gestori.

I criteri applicati sono:
- **Chi può modificare i dettagli del gruppo:** Gestori del gruppo (`ALL_MANAGERS_CAN_CONTACT`)
- **Chi può visualizzare le conversazioni web:** Gestori del gruppo (`ALL_MANAGERS_CAN_VIEW`)
- **Chi può inviare/pubblicare post:** Intera organizzazione (`ALL_IN_DOMAIN_CAN_POST`)
- **Chi può visualizzare i membri:** Intera organizzazione (`ALL_IN_DOMAIN_CAN_VIEW`)
- **Adesione al gruppo:** Solo su invito (`INVITED_CAN_JOIN`)
- **Membri esterni all'organizzazione:** Disabilitati (`false`)

### Verifica e allineamento automatico dei gruppi esistenti
- **Gruppi esistenti:** viene effettuata una singola lettura (`GET`) delle impostazioni attuali da Groups Settings API. Se i permessi sono già conformi, **non viene eseguita alcuna operazione di scrittura**. Se differiscono, vengono aggiornati via `PATCH`.
- **Gruppi nuovi:** al momento della creazione, i permessi vengono applicati direttamente.

### Personalizzazione manuale dei permessi da codice
All'inizio di [`GoogleWorkspaceClient.kt`](composeApp/src/desktopMain/kotlin/it/madx/edttocdc/google/GoogleWorkspaceClient.kt) è presente l'oggetto `CdcGroupAccessPolicy` in cui è possibile modificare in modo immediato e documentato i singoli parametri:

```kotlin
object CdcGroupAccessPolicy {
    var whoCanContactOwner: String = "ALL_MANAGERS_CAN_CONTACT"
    var whoCanViewGroup: String = "ALL_MANAGERS_CAN_VIEW"
    var whoCanPostMessage: String = "ALL_IN_DOMAIN_CAN_POST"
    var whoCanViewMembership: String = "ALL_IN_DOMAIN_CAN_VIEW"
    var whoCanJoin: String = "INVITED_CAN_JOIN"
    var allowExternalMembers: String = "false"
}
```

---

## 4. Funzionalità di Esportazione (CSV ed Excel)

Nella barra superiore dell'applicazione è presente il pulsante **Esporta**, dotato di un menu a discesa multilivello integrato in un unico pannello:

1. **`classe_email_cdc` (2 colonne):**
   - Colonne: `classe`, `mail_cdc`
   - Formati disponibili:
     - **File CSV (.csv)**: generato con codifica UTF-8 BOM (`\uFEFF`) per l'apertura immediata con caratteri e accenti corretti in Microsoft Excel.
     - **File Excel (.xlsx)**: cartella di lavoro generata nativamente con Apache POI, foglio `"Classi e CdC"` e colonne auto-dimensionate.

2. **`classe_email_cdc_email_partecipanti` (3 colonne):**
   - Colonne: `classe`, `mail_cdc`, `membro`
   - Formati disponibili:
     - **File CSV (.csv)**: formato tabellare UTF-8 BOM con una riga per ciascun docente partecipante.
     - **File Excel (.xlsx)**: cartella di lavoro con foglio `"CdC e Partecipanti"`.

L'esportazione è gestita dal componente dedicato in sola lettura [`GoogleWorkspaceExporter`](composeApp/src/desktopMain/kotlin/it/madx/edttocdc/google/GoogleWorkspaceExporter.kt), senza effetti collaterali né scritture su Google Workspace.

---

## 5. Revisione del Codice Sensibile (Sicurezza)

Come prescritto dalle direttive di sicurezza, tutte le interazioni con Google Workspace / Admin SDK Directory API e Groups Settings API sono concentrate in classi dedicate nel package `it.madx.edttocdc.google`.

### File del client Google
- **Autenticazione e Factory:**  
  [`composeApp/src/desktopMain/kotlin/it/madx/edttocdc/google/GoogleWorkspaceAuthHelper.kt`](composeApp/src/desktopMain/kotlin/it/madx/edttocdc/google/GoogleWorkspaceAuthHelper.kt)
- **Operazioni e sincronizzazione:**  
  [`composeApp/src/desktopMain/kotlin/it/madx/edttocdc/google/GoogleWorkspaceClient.kt`](composeApp/src/desktopMain/kotlin/it/madx/edttocdc/google/GoogleWorkspaceClient.kt)
- **Esportazione in sola lettura:**  
  [`composeApp/src/desktopMain/kotlin/it/madx/edttocdc/google/GoogleWorkspaceExporter.kt`](composeApp/src/desktopMain/kotlin/it/madx/edttocdc/google/GoogleWorkspaceExporter.kt)

### Operazioni consentite

| Metodo | Natura | Descrizione e Vincoli |
| :--- | :--- | :--- |
| `testConnection()` | **Sola lettura** | Verifica l'autenticazione leggendo un gruppo con limite 1. |
| `getGroup(groupEmail)` | **Sola lettura** | Recupera il gruppo se esiste, oppure restituisce `null`. |
| `listGroupMembers(groupEmail)` | **Sola lettura** | Elenca tutti i membri attuali gestendo la paginazione (`pageToken`). |
| `ensureGroupAccessSettings(...)` | **Lettura / Scrittura** | Verifica e allinea i permessi di accesso del gruppo (salta la scrittura se già allineati). |
| `ensureGroupExists(...)` | **Scrittura** | Crea il gruppo solo se mancante, impostando nome e permessi. |
| `replaceGroupMembers(...)` | **Scrittura** | Rimuove gli iscritti non previsti e aggiunge i docenti mancanti. |
| `syncGroup(...)` | **Scrittura** | Coordina la verifica/creazione del gruppo e l'allineamento dei membri. |
| `fetchClassGroups()` | **Sola lettura** | Estrae l'elenco delle classi e indirizzi CdC per l'export. |
| `fetchClassGroupMembers()` | **Sola lettura** | Estrae l'elenco delle classi, indirizzi CdC e membri partecipanti per l'export. |

### Conferma di non-eliminazione dei gruppi
- Nel progetto **NON esiste alcuna funzione o chiamata API per eliminare gruppi Google Workspace**.  
- La rimozione dei docenti riguarda unicamente la cancellazione dell'iscrizione dal gruppo (`directoryService.members().delete(groupEmail, memberEmail)`), **mai la cancellazione del gruppo stesso**.

---

## 6. Guida alla Configurazione

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
   - Accedere a [console.cloud.google.com](https://console.cloud.google.com) con l'account amministratore della scuola.
   - Creare un nuovo progetto (es. `MailingList-CdC`).
   - In *API e servizi > Libreria*, cercare e abilitare entrambe le API:
     1. **Admin SDK API** (per la gestione di gruppi e membri).
     2. **Groups Settings API** (per configurare i permessi delle mailing list).
2. **Creazione Service Account:**
   - In *IAM e amministrazione > Account di servizio*, creare un account di servizio (es. `cdc-sync-sa`).
   - Non assegnare ruoli IAM cloud non necessari.
   - Aprire l'account di servizio creato, andare nella scheda **Chiavi**, fare clic su **Aggiungi chiave > Crea nuova chiave** e selezionare il formato **JSON**.
   - Salvare il file JSON in modo sicuro sul proprio computer.
3. **Delega a livello di dominio (Domain-Wide Delegation):**
   - Nella Console di amministrazione Google Workspace ([admin.google.com](https://admin.google.com)), andare in:  
     **Sicurezza > Controllo degli accessi e dei dati > Controlli API > Gestisci delega a livello di dominio**.
   - Fare clic su **Aggiungi nuovo**.
   - Nel campo **ID client**: incollare l'ID client numerico del Service Account (reperibile aprendo il file JSON scaricato alla voce `"client_id"`).
   - Nel campo **Ambiti OAuth (separati da virgola / comma-delimited)**, inserire esattamente:
     ```text
     https://www.googleapis.com/auth/admin.directory.group, https://www.googleapis.com/auth/admin.directory.group.member, https://www.googleapis.com/auth/apps.groups.settings
     ```
   - Fare clic su **Autorizza**.
4. **Configurazione nell'applicazione:**
   - Selezionare il file JSON di credenziali con il pulsante **Sfoglia...**.
   - Inserire l'email di un amministratore Google Workspace abilitato alla delega DWD (es. `admin@scuola.edu.it`).
   - Il dominio istituzionale della scuola viene ricavato automaticamente dal file EDT.
   - È possibile verificare le credenziali premendo **Verifica Connessione**.

---

## 7. Guida all'Uso dell'Applicazione

La procedura dell'interfaccia utente è rigorosamente **guidata e forzata in sequenza**:

1. **Punto 1 - Sorgente Dati EDT (XML):**
   - Inserire l'URL del file XML EDT esportato dalla scuola.
   - Premere **Verifica XML**: l'applicazione verifica la disponibilità del file, la correttezza del formato ed estrae automaticamente il dominio istituzionale della scuola.
   - Una volta superata la verifica, il Punto 1 mostra il badge verde **Verificato** e viene sbloccato il Punto 2.

2. **Punto 2 - Destinazione Google Workspace:**
   - *(Bloccato finché il Punto 1 non è verificato)*.
   - Selezionare il file JSON di credenziali con **Sfoglia...** e inserire l'email dell'amministratore Google Workspace (DWD).
   - Premere **Verifica Connessione**: l'applicazione convalida le credenziali e l'autorizzazione di delega DWD su Google Workspace.
   - Una volta superata la verifica, compare il badge verde **Verificato**, si sblocca l'esecuzione al Punto 3 e si abilita il pulsante **Esporta** nella barra superiore.

3. **Punto 3 - Esecuzione Sincronizzazione:**
   - *(Bloccato finché entrambi i Punti 1 e 2 non sono verificati con successo)*.
   - Premere il pulsante **Avvia Sincronizzazione**.
   - Viene eseguito l'allineamento dei gruppi e dei docenti iscritti (con creazione dei gruppi mancanti e allineamento dei permessi di accesso).
   - Al termine, una finestra di dialogo mostrerà il riepilogo con le statistiche conclusive (classi elaborate, gruppi creati, docenti aggiunti, docenti rimossi, eventuali anomalie).

4. **Esportazione Dati (Attiva solo dopo la verifica del Punto 2):**
   - Dal pulsante in alto **Esporta**, selezionare la tipologia desiderata (`classe_email_cdc` o `classe_email_cdc_email_partecipanti`) e il formato (**CSV** o **Excel**).

5. **Console e Log:**
   - È possibile attivare lo switch **"Dettagli gruppi nei log"** per visualizzare per esteso l'elenco di tutte le email per ogni gruppo.
   - È possibile copiare i log negli appunti (**Copia log**) o salvarli in un file di testo locale (**Salva su file**).

---

## 8. Packaging e Distribuzione Multiplatform

Il repository include il workflow di packaging automatizzato tramite **GitHub Actions**:
`.github/workflows/package.yml`

Il workflow si attiva al rilascio di una release o tag (`Release-*`, `v*`) o manualmente tramite `workflow_dispatch`, producendo gli artefatti distribuiti:
- **macOS:** Immagine disco `.dmg` con app bundle `.app` autonomo (`EdtToCdcSync-1.0.1.dmg`).
- **Windows:** Installer `.msi`, eseguibile `.exe` e archivio standalone `.zip` contenente l'applicazione portabile con runtime JVM Temurin 21 incorporato.
- **Linux:** Pacchetto Debian `.deb`.
- **Standalone JAR:** `EdtToCdcSync-1.0.1-standalone.jar` (eseguibile multipiattaforma).

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

## 9. Firma Digitale, Gatekeeper e SmartScreen

- **macOS Gatekeeper:** i pacchetti compilati localmente o tramite workflow GitHub Actions non sono firmati con un certificato Apple Developer a pagamento né sottoposti a notarizzazione Apple. Al primo avvio su macOS, se Gatekeeper blocca l'applicazione ("sviluppatore non verificato"), fare clic destro sull'app > **Apri**, oppure andare in *Impostazioni di Sistema > Privacy e Sicurezza* e fare clic su **Apri comunque**.
- **Windows SmartScreen:** in assenza di un certificato EV (Extended Validation) di firma del codice Windows, SmartScreen potrebbe mostrare un avviso di "PC protetto da Windows". È sufficiente fare clic su *Ulteriori informazioni* > *Esegui comunque*.

---

## 10. Test Automatici

La suite di 37 test unitari verifica automaticamente:
- Parsing di XML relazionali e gerarchici EDT.
- Rifiuto ed eccezione per XML vuoto, incompleto o malformato.
- Blocco di tentativi di iniezione XXE (disabilitazione DTD ed entità esterne).
- Normalizzazione dei nomi delle classi e rilevamento del dominio dominante.
- Normalizzazione e validazione RFC di indirizzi email.
- Deduplicazione delle email dei docenti all'interno della stessa classe.
- Generazione e convalida degli indirizzi `cdc_{classe}@dominio`.
- Rifiuto immediato (`SecurityException`) di qualsiasi gruppo esterno al formato autorizzato.
- Calcolo esatto delle differenze (`MembershipDiff`) tra membri correnti e desiderati.
- Confronto email case-insensitive e idempotenza delle sincronizzazioni.
- Valori predefiniti della policy di accesso `CdcGroupAccessPolicy`.
- Verifica che `GoogleWorkspaceExporter` sia rigorosamente in sola lettura (nessun metodo di scrittura/modifica).
- Generazione corretta e validità strutturale dei file CSV (con BOM) ed Excel `.xlsx` (fogli, intestazioni, celle).
- Assenza riflessiva di metodi di cancellazione gruppi in `GoogleWorkspaceClient`.
- Salvataggio, caricamento e validazione delle impostazioni locali.

Per eseguire l'intera suite di test:
```bash
./gradlew :composeApp:desktopTest
```

---

## 11. Struttura dei File nel Repository

```text
.
├── .github/workflows/package.yml  # Workflow CI/CD GitHub Actions multiplatform
├── composeApp/
│   ├── build.gradle.kts           # Configurazione Compose Multiplatform e native packaging
│   └── src/
│       ├── desktopMain/
│       │   ├── kotlin/it/madx/edttocdc/
│       │   │   ├── config/        # Gestione impostazioni locali (AppSettings, SettingsManager)
│       │   │   ├── edt/           # Download, parser XML EDT e mapping classi
│       │   │   ├── google/        # AuthHelper, GoogleWorkspaceClient ed Exporter
│       │   │   ├── model/         # Modelli dati di dominio e log
│       │   │   ├── sync/          # Motore di orchestrazione SyncEngine
│       │   │   └── ui/            # Interfaccia grafica Compose Desktop, temi e dialoghi
│       │   └── resources/
│       │       ├── icons/         # Icone native (.png, .ico, .icns)
│       │       └── samples/       # File XML EDT di esempio anonimizzati
│       └── desktopTest/           # Suite di 37 test unitari automatizzati
├── gradle/
│   ├── libs.versions.toml         # Version catalog dipendenze
│   └── wrapper/                   # Gradle wrapper 8.10
├── build.gradle.kts               # Root build script
├── settings.gradle.kts            # Configurazione moduli Gradle
├── gradle.properties              # Parametri JVM per il build
├── .gitignore                     # Esclusione build, artefatti e credenziali Google JSON
└── README.md                      # Documentazione completa del progetto
```