package it.madx.edttocdc.google
// ============================================================================
// SECURITY REVIEW MAP
// Metodi di sola lettura:
//   - testConnection
//   - getGroup
//   - listGroupMembers
//   - verifyGroupMembers
// Punti di ingresso che possono scrivere su Google Workspace:
//   - ensureGroupExists (creazione gruppo mancante)
//   - ensureGroupAccessSettings (verifica/impostazione permessi access type)
//   - configureRestrictedGroupSettings (impostazione permessi retrocompatibile)
//   - replaceGroupMembers (aggiunta/rimozione iscritti al gruppo)
//   - syncGroup (orchestrazione sincronizzazione singolo gruppo)
// Cancellazione di gruppi:
//   - NON IMPLEMENTATA (nessun metodo o API di eliminazione gruppi presente)
//
// Cercare "REVISIONE SICUREZZA" per trovare i blocchi sensibili di scrittura.
// ============================================================================
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.directory.Directory
import com.google.api.services.directory.DirectoryScopes
import com.google.api.services.directory.model.Group
import com.google.api.services.directory.model.Member
import com.google.api.services.groupssettings.Groupssettings
import com.google.api.services.groupssettings.model.Groups
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import it.madx.edttocdc.edt.ClassGroupMapper
import it.madx.edttocdc.model.GoogleConnectionInfo
import it.madx.edttocdc.model.GroupSyncResult
import it.madx.edttocdc.model.MembershipDiff
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.logging.Logger

// ============================================================================
// CONFIGURAZIONE POLICY DI ACCESSO GRUPPI ("Access Type" / Groups Settings)
//
// Questa sezione permette di modificare a mano i livelli di autorizzazione
// applicati alle mailing list dei Consigli di Classe (cdc_{classe}@dominio).
//
// OBIETTIVO PREDEFINITO:
// - Mailing list interna scrivibile da chiunque nell'organizzazione scolastica.
// - Conversazioni web e dettagli riservati esclusivamente a gestori e proprietari.
// - Elenco membri visibile a tutta l'organizzazione per trasparenza interna.
//
// LIVELLI E VALORI ACCETTATI DALLE GOOGLE GROUPS SETTINGS API:
//
// 1. whoCanViewGroup (Chi può visualizzare le conversazioni web del gruppo):
//      "ALL_MANAGERS_CAN_VIEW"  -> Solo Gestori e Proprietari (PREDEFINITO)
//      "ALL_MEMBERS_CAN_VIEW"   -> Solo i docenti membri del CdC
//      "ALL_IN_DOMAIN_CAN_VIEW" -> Chiunque nell'organizzazione
//      "ANYONE_CAN_VIEW"        -> Pubblico su Internet
//
// 2. whoCanViewMembership (Chi può visualizzare i membri del gruppo):
//      "ALL_IN_DOMAIN_CAN_VIEW" -> Intera organizzazione (PREDEFINITO)
//      "ALL_MEMBERS_CAN_VIEW"   -> Solo i membri del CdC
//      "ALL_MANAGERS_CAN_VIEW"  -> Solo Gestori e Proprietari
//
// 3. whoCanPostMessage (Chi può pubblicare post / inviare email alla mailing list):
//      "ALL_IN_DOMAIN_CAN_POST" -> Intera organizzazione (PREDEFINITO)
//      "ALL_MEMBERS_CAN_POST"   -> Solo i docenti membri del CdC
//      "ALL_MANAGERS_CAN_POST"  -> Solo Gestori e Proprietari
//      "ANYONE_CAN_POST"        -> Chiunque su Internet
//
// 4. whoCanContactOwner (Chi può modificare dettagli / contattare i gestori):
//      "ALL_MANAGERS_CAN_CONTACT" -> Gestori del gruppo (PREDEFINITO)
//      "ALL_MEMBERS_CAN_CONTACT"  -> Membri del CdC
//      "ALL_IN_DOMAIN_CAN_CONTACT"-> Intera organizzazione
//
// 5. whoCanJoin (Chi può iscriversi al gruppo):
//      "INVITED_CAN_JOIN"         -> Solo su invito / gestito dall'applicativo (PREDEFINITO)
//      "CAN_REQUEST_TO_JOIN"      -> Richiesta di adesione con approvazione
//      "ALL_IN_DOMAIN_CAN_JOIN"   -> Iscrizione libera per chiunque nell'organizzazione
//
// 6. allowExternalMembers (Consenti membri con email esterne al dominio della scuola):
//      "false" -> Disabilitato / Solo account del dominio della scuola (PREDEFINITO per sicurezza)
//      "true"  -> Consente membri esterni
// ============================================================================
object CdcGroupAccessPolicy {
    /** Chi può visualizzare le conversazioni web: Gestori del gruppo */
    var whoCanViewGroup: String = "ALL_MANAGERS_CAN_VIEW"

    /** Chi può visualizzare i membri: Intera organizzazione */
    var whoCanViewMembership: String = "ALL_IN_DOMAIN_CAN_VIEW"

    /** Chi può pubblicare post / inviare email: Intera organizzazione */
    var whoCanPostMessage: String = "ALL_IN_DOMAIN_CAN_POST"

    /** Chi può modificare dettagli / contattare i gestori: Gestori del gruppo */
    var whoCanContactOwner: String = "ALL_MANAGERS_CAN_CONTACT"

    /** Chi può iscriversi: Solo invitati / gestiti automaticamente dall'applicazione */
    var whoCanJoin: String = "INVITED_CAN_JOIN"

    /** Consenti membri esterni all'organizzazione della scuola ("false" = disabilitato) */
    var allowExternalMembers: String = "false"
}

/**
 * Client dedicato e isolato per tutte le interazioni con Google Workspace / Admin SDK Directory API e Groups Settings API.
 * Tutte le chiamate alle API Google risiedono esclusivamente in questa classe.
 * Le operazioni di scrittura sono ridotte al minimo indispensabile e protette da controlli
 * tassativi sul formato dell'indirizzo del gruppo e sul rispetto della modalità simulazione (dry-run).
 *
 * IMPORTANTE - Limitazione dei privilegi:
 * gli scope OAuth utilizzati (ADMIN_DIRECTORY_GROUP, ADMIN_DIRECTORY_GROUP_MEMBER e APPS_GROUPS_SETTINGS) consentono
 * tecnicamente di operare sui gruppi del dominio. Google non permette di limitare gli scope
 * a un sottoinsieme di gruppi. La restrizione ai soli gruppi `cdc_{classe}@{dominio}` è quindi
 * applicata esclusivamente a livello applicativo tramite [validateManagedGroup].
 *
 * Questa classe non implementa alcuna operazione di eliminazione di gruppi.
 */
class GoogleWorkspaceClient(
    val credentialsFile: File,
    val domain: String,
    val adminEmail: String? = null
) {
    private companion object {
        val logger: Logger = Logger.getLogger(GoogleWorkspaceClient::class.java.name)
        const val CONNECT_TIMEOUT_MS = 30_000
        const val READ_TIMEOUT_MS = 60_000
    }

    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val normalizedDomain = domain.trim().trimStart('@').lowercase()

    private val directoryService: Directory by lazy {
        initDirectoryService()
    }

    private val groupsSettingsService: Groupssettings by lazy {
        initGroupsSettingsService()
    }

    private fun createGoogleCredentials(scopes: List<String>): GoogleCredentials {
        return GoogleWorkspaceAuthHelper.createGoogleCredentials(credentialsFile, adminEmail, scopes)
    }

    private fun initDirectoryService(): Directory {
        return GoogleWorkspaceAuthHelper.createDirectoryService(credentialsFile, adminEmail)
    }

    private fun initGroupsSettingsService(): Groupssettings {
        return GoogleWorkspaceAuthHelper.createGroupsSettingsService(credentialsFile, adminEmail)
    }

    // ========================================================================
    // SEZIONE METODI DI SOLA LETTURA (READ-ONLY)
    // ========================================================================

    /**
     * Verifica la connessione e la validità delle credenziali per l'accesso ad Admin SDK Directory API.
     * Operazione di sola lettura.
     */
    fun testConnection(): Result<GoogleConnectionInfo> {
        return runCatching {
            // Tentativo di lettura gruppi con limite 1 per validare autenticazione e permessi
            val request = directoryService.groups().list()
                .setDomain(normalizedDomain)
                .setMaxResults(1)
            executeWithRetry { request.execute() }

            logger.info("Connessione Google Workspace riuscita per il dominio $normalizedDomain")

            GoogleConnectionInfo(
                authenticatedEmail = adminEmail?.ifBlank { null } ?: "Service Account (${credentialsFile.name})",
                domain = normalizedDomain,
                directoryAccessConferred = true
            )
        }.onFailure { e ->
            logger.severe("Connessione Google Workspace fallita per il dominio $normalizedDomain: ${e.message}")
        }
    }

    /**
     * Recupera un gruppo Google Workspace se esistente.
     * Operazione di sola lettura.
     * @param groupEmail Indirizzo del gruppo da verificare.
     * @return L'oggetto Group se presente, oppure null se non esiste.
     */
    fun getGroup(groupEmail: String): Group? {
        val normalizedEmail = ClassGroupMapper.normalizeEmail(groupEmail)
        validateManagedGroup(normalizedEmail)

        return try {
            executeWithRetry {
                directoryService.groups().get(normalizedEmail).execute()
            }
        } catch (e: GoogleJsonResponseException) {
            if (e.statusCode == 404) {
                null
            } else {
                throw e
            }
        }
    }

    /**
     * Recupera tutti gli indirizzi email dei membri attuali di un gruppo, gestendo automaticamente la paginazione.
     * Operazione di sola lettura.
     * @param groupEmail Indirizzo email del gruppo autorizzato.
     * @return Elenco normalizzato delle email dei membri.
     */
    fun listGroupMembers(groupEmail: String): List<String> {
        val normalizedEmail = ClassGroupMapper.normalizeEmail(groupEmail)
        validateManagedGroup(normalizedEmail)

        val members = mutableListOf<String>()
        var pageToken: String? = null

        do {
            val finalPageToken = pageToken
            val response = executeWithRetry {
                val request = directoryService.members().list(normalizedEmail)
                    .setMaxResults(200)
                if (!finalPageToken.isNullOrBlank()) {
                    request.pageToken = finalPageToken
                }
                request.execute()
            }

            val list = response.members
            if (list != null) {
                for (m in list) {
                    val email = m.email
                    if (!email.isNullOrBlank()) {
                        members.add(ClassGroupMapper.normalizeEmail(email))
                    }
                }
            }
            pageToken = response.nextPageToken
        } while (!pageToken.isNullOrBlank())

        logger.info("Letti ${members.size} membri attuali per il gruppo $normalizedEmail")
        return members
    }

    /**
     * Verifica, quando possibile, che i membri attuali del gruppo corrispondano all'insieme desiderato.
     * Operazione di sola lettura.
     * @param groupEmail Indirizzo email del gruppo autorizzato.
     * @param desiredMembers Insieme degli indirizzi email attesi.
     * @return Result<Boolean>: true se lo stato attuale corrisponde all'elenco desiderato, false altrimenti.
     */
    fun verifyGroupMembers(
        groupEmail: String,
        desiredMembers: Set<String>
    ): Result<Boolean> {
        return runCatching {
            val normalizedEmail = ClassGroupMapper.normalizeEmail(groupEmail)
            validateManagedGroup(normalizedEmail)

            val existing = getGroup(normalizedEmail)
            if (existing == null) {
                false
            } else {
                val currentMembers = listGroupMembers(normalizedEmail).toSet()
                val desiredNormalized = desiredMembers
                    .map { ClassGroupMapper.normalizeEmail(it) }
                    .toSet()
                currentMembers == desiredNormalized
            }
        }.onFailure { e ->
            logger.severe("Verifica finale del gruppo $groupEmail fallita: ${e.message}")
        }
    }

    // ========================================================================
    // SEZIONE METODI DI SCRITTURA (MODIFICA GOOGLE WORKSPACE)
    // ========================================================================

    // Flag per evitare di riprovare Groups Settings API ad ogni singolo gruppo se non abilitata/autorizzata
    @Volatile
    private var groupsSettingsDisabledReason: String? = null

    // ============================================================================
    // !!! REVISIONE SICUREZZA: OPERAZIONE DI SCRITTURA SU GOOGLE WORKSPACE !!!
    // Metodo: ensureGroupAccessSettings
    // Questo metodo configura o verifica i permessi di accesso di un gruppo Google
    // secondo i parametri definiti in [CdcGroupAccessPolicy].
    // Verificare con particolare attenzione:
    // - che l'indirizzo appartenga tassativamente al perimetro cdc_{classe}@dominio;
    // - che validateManagedGroup(groupEmail) blocchi qualsiasi altro indirizzo;
    // - che se dryRun == true NESSUNA chiamata di configurazione/scrittura venga eseguita;
    // - che se il gruppo esiste già, verifichi prima lo stato e applichi modifiche SOLO se non allineato;
    // - che un eventuale errore di autorizzazione su Groups Settings API non blocchi
    //   il processo ma segnali chiaramente l'avviso all'amministratore.
    // ============================================================================
    /**
     * Verifica e applica i criteri di accesso configurati in [CdcGroupAccessPolicy] al gruppo specificato.
     *
     * @param groupEmail Indirizzo email del gruppo autorizzato.
     * @param checkIfAlreadyAligned Se true, legge lo stato attuale e applica modifiche solo se differiscono.
     * @param dryRun Se true, simula l'operazione senza effettuare chiamate API di scrittura reali.
     * @return Result<Boolean>: true se l'operazione ha avuto successo o non erano necessarie modifiche, false altrimenti.
     */
    fun ensureGroupAccessSettings(
        groupEmail: String,
        checkIfAlreadyAligned: Boolean = false,
        dryRun: Boolean = false
    ): Result<Boolean> {
        return runCatching {
            val normalizedEmail = ClassGroupMapper.normalizeEmail(groupEmail)
            validateManagedGroup(normalizedEmail)

            if (dryRun) {
                logger.info("Impostazioni di accesso verrebbero verificate/applicate per il gruppo $normalizedEmail (dry-run)")
                return@runCatching true
            }

            val disabledReason = groupsSettingsDisabledReason
            if (disabledReason != null) {
                logger.fine("Impostazioni di accesso per $normalizedEmail non verificate: $disabledReason")
                return@runCatching false
            }

            val targetSettings = Groups()
                .setWhoCanViewGroup(CdcGroupAccessPolicy.whoCanViewGroup)
                .setWhoCanViewMembership(CdcGroupAccessPolicy.whoCanViewMembership)
                .setWhoCanPostMessage(CdcGroupAccessPolicy.whoCanPostMessage)
                .setWhoCanContactOwner(CdcGroupAccessPolicy.whoCanContactOwner)
                .setWhoCanJoin(CdcGroupAccessPolicy.whoCanJoin)
                .setAllowExternalMembers(CdcGroupAccessPolicy.allowExternalMembers)

            try {
                if (checkIfAlreadyAligned) {
                    val current = groupsSettingsService.groups().get(normalizedEmail).execute()
                    val isAlreadyAligned = current != null &&
                            current.whoCanViewGroup == CdcGroupAccessPolicy.whoCanViewGroup &&
                            current.whoCanViewMembership == CdcGroupAccessPolicy.whoCanViewMembership &&
                            current.whoCanPostMessage == CdcGroupAccessPolicy.whoCanPostMessage &&
                            current.whoCanContactOwner == CdcGroupAccessPolicy.whoCanContactOwner &&
                            current.whoCanJoin == CdcGroupAccessPolicy.whoCanJoin &&
                            current.allowExternalMembers == CdcGroupAccessPolicy.allowExternalMembers

                    if (isAlreadyAligned) {
                        logger.info("Impostazioni di accesso per il gruppo $normalizedEmail già conformi alla policy (nessuna modifica necessaria)")
                        return@runCatching true
                    }
                    logger.info("Impostazioni di accesso per il gruppo esistente $normalizedEmail non conformi: aggiornamento in corso...")
                }

                groupsSettingsService.groups().patch(normalizedEmail, targetSettings).execute()
                logger.info(
                    "Impostazioni di accesso applicate con successo al gruppo $normalizedEmail " +
                            "(conversazioni=${CdcGroupAccessPolicy.whoCanViewGroup}, " +
                            "post=${CdcGroupAccessPolicy.whoCanPostMessage}, " +
                            "membri=${CdcGroupAccessPolicy.whoCanViewMembership})"
                )
                true
            } catch (e: Exception) {
                val msg = e.message ?: ""
                val isAuthError = msg.contains("401", ignoreCase = true) ||
                        msg.contains("Unauthorized", ignoreCase = true) ||
                        msg.contains("invalid_grant", ignoreCase = true) ||
                        msg.contains("unauthorized_client", ignoreCase = true) ||
                        msg.contains("access_denied", ignoreCase = true)

                if (isAuthError) {
                    val reason = "Groups Settings API non autorizzata (401 Unauthorized). Verificare che: " +
                            "1) Nella Google Cloud Console sia abilitata la 'Groups Settings API'; " +
                            "2) Nella Google Workspace Admin Console (DWD) sia autorizzato l'ambito 'https://www.googleapis.com/auth/apps.groups.settings'."
                    groupsSettingsDisabledReason = reason
                    logger.warning("Impossibile verificare/applicare le impostazioni al gruppo $normalizedEmail: $reason")
                } else {
                    logger.warning(
                        "Impossibile verificare/applicare le impostazioni al gruppo $normalizedEmail tramite Groups Settings API: ${e.message}."
                    )
                }
                false
            }
        }.onFailure { e ->
            logger.severe("Errore in ensureGroupAccessSettings per il gruppo $groupEmail: ${e.message}")
        }
    }

    /**
     * Metodo retrocompatibile per configurare le impostazioni del gruppo.
     */
    fun configureRestrictedGroupSettings(
        groupEmail: String,
        dryRun: Boolean = false
    ): Result<Boolean> {
        return ensureGroupAccessSettings(groupEmail, checkIfAlreadyAligned = false, dryRun = dryRun)
    }

    /**
     * Attende che un gruppo appena creato sia visibile nelle repliche di lettura di Google Directory API.
     */
    private fun waitForGroupPropagation(groupEmail: String, maxAttempts: Int = 5, delayMs: Long = 1000) {
        for (attempt in 1..maxAttempts) {
            try {
                val group = directoryService.groups().get(groupEmail).execute()
                if (group != null) {
                    logger.info("Gruppo $groupEmail propagato con successo e visibile nelle API Google")
                    return
                }
            } catch (e: GoogleJsonResponseException) {
                if (e.statusCode != 404) {
                    logger.warning("Verifica propagazione gruppo $groupEmail: HTTP ${e.statusCode}")
                }
            } catch (e: Exception) {
                logger.warning("Verifica propagazione gruppo $groupEmail: ${e.message}")
            }
            try {
                Thread.sleep(delayMs)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
        logger.info("Attesa propagazione per il gruppo $groupEmail terminata: proseguo con la sincronizzazione")
    }

    // ============================================================================
    // !!! REVISIONE SICUREZZA: OPERAZIONE DI SCRITTURA SU GOOGLE WORKSPACE !!!
    // Metodo: ensureGroupExists
    // Questo metodo può creare un gruppo mancante in Google Workspace.
    // Verificare con particolare attenzione:
    // - che l'indirizzo appartenga tassativamente al perimetro cdc_{classe}@dominio;
    // - che validateManagedGroup(groupEmail) blocchi qualsiasi altro indirizzo;
    // - che se dryRun == true NESSUNA chiamata di creazione venga eseguita;
    // - che non esista alcuna operazione di cancellazione o sovrascrittura del gruppo;
    // - che un eventuale 409 Conflict durante l'insert sia gestito come gruppo già esistente.
    // ============================================================================
    /**
     * Verifica l'esistenza del gruppo e, se mancante, lo crea con le impostazioni di policy configurate.
     * Se il gruppo esiste già, verifica e allinea le impostazioni di accesso.
     * Operazione di scrittura.
     * In dry-run non esegue alcuna creazione e restituisce true se il gruppo verrebbe creato.
     *
     * @param groupEmail Indirizzo email del gruppo autorizzato.
     * @param className Nome della classe usato per il nome del gruppo.
     * @param description Descrizione del gruppo.
     * @param dryRun Se true, non esegue scritture reali.
     * @return Result<Boolean>: true se il gruppo è stato creato o verrebbe creato, false se esisteva già.
     */
    fun ensureGroupExists(
        groupEmail: String,
        className: String,
        description: String? = null,
        dryRun: Boolean = false
    ): Result<Boolean> {
        return runCatching {
            val normalizedEmail = ClassGroupMapper.normalizeEmail(groupEmail)
            // CONTROLLO DI SICUREZZA INTERNO OBBLIGATORIO:
            validateManagedGroup(normalizedEmail)

            val formattedClassName = className.trim().uppercase()
            val groupDescription = description ?: "Mailing list Consiglio di Classe $formattedClassName"

            val existing = getGroup(normalizedEmail)
            if (existing != null) {
                // Il gruppo esiste già: nessuna creazione necessaria
                logger.info("Gruppo $normalizedEmail già esistente")
                // Verifica e allinea le impostazioni di accesso dell'Access Type se non conformi
                ensureGroupAccessSettings(normalizedEmail, checkIfAlreadyAligned = true, dryRun = dryRun)
                false
            } else {
                if (dryRun) {
                    // Modalità simulazione: segnala la creazione prevista senza effettuare chiamate API di scrittura
                    logger.info("Gruppo $normalizedEmail verrebbe creato come 'Consiglio di Classe $formattedClassName' con impostazioni mailing list (dry-run)")
                    true
                } else {
                    val newGroup = Group()
                        .setEmail(normalizedEmail)
                        .setName("Consiglio di Classe $formattedClassName")
                        .setDescription(groupDescription)
                    try {
                        executeWithRetry {
                            directoryService.groups().insert(newGroup).execute()
                        }
                        logger.info("Creato gruppo $normalizedEmail (Consiglio di Classe $formattedClassName)")
                    } catch (e: GoogleJsonResponseException) {
                        if (e.statusCode != 409) {
                            throw e
                        }
                        // 409 = il gruppo è stato creato nel frattempo da un altro processo: nessuna azione necessaria
                        logger.info("Gruppo $normalizedEmail già creato da un altro processo (409): nessuna azione necessaria")
                    }

                    // Configura le impostazioni di accesso sul nuovo gruppo
                    ensureGroupAccessSettings(normalizedEmail, checkIfAlreadyAligned = false, dryRun = false)

                    // Attende che il nuovo gruppo sia propagato e visibile a Google Directory API
                    waitForGroupPropagation(normalizedEmail)

                    true
                }
            }
        }.onFailure { e ->
            logger.severe("Errore in ensureGroupExists per il gruppo $groupEmail: ${e.message}")
        }
    }

    // ============================================================================
    // !!! REVISIONE SICUREZZA: OPERAZIONE DI SCRITTURA SU GOOGLE WORKSPACE !!!
    // Metodo: replaceGroupMembers
    // Questo metodo aggiunge docenti mancanti e rimuove docenti non più presenti
    // nella classe. Se il gruppo non esiste ancora, lo crea automaticamente.
    // Verificare con particolare attenzione:
    // - che il gruppo sia rigorosamente nel formato cdc_{classe}@dominio;
    // - che rimuova ESCLUSIVAMENTE iscritti non previsti e MAI il gruppo stesso;
    // - che tutte le email dei nuovi docenti siano validate prima dell'aggiunta;
    // - che se dryRun == true NON venga effettuata alcuna chiamata API di scrittura;
    // - che la sincronizzazione sia idempotente;
    // - che un eventuale 409 su insert e 404 su delete siano gestiti come operazioni già completate;
    // - che sia presente una protezione contro la rimozione massiva quando desiredMembers è vuoto.
    // ============================================================================
    /**
     * Sostituisce i membri del gruppo con quelli desiderati, rimuovendo solo quelli non previsti
     * e aggiungendo solo quelli mancanti. Se il gruppo non esiste, lo crea automaticamente con
     * impostazioni 'Restricted'.
     * Operazione di scrittura.
     *
     * @param groupEmail Indirizzo email del gruppo autorizzato.
     * @param desiredMembers Insieme degli indirizzi email desiderati.
     * @param dryRun Se true, calcola le modifiche previste senza eseguirle.
     * @param allowEmptyDesiredMembers Se true, consente la rimozione di tutti i membri anche quando
     *                                 desiredMembers è vuoto. Usare SOLO dopo conferma esplicita dell'utente.
     * @return Result contenente il diff calcolato/eseguito.
     */
    fun replaceGroupMembers(
        groupEmail: String,
        desiredMembers: Set<String>,
        dryRun: Boolean = false,
        allowEmptyDesiredMembers: Boolean = false
    ): Result<MembershipDiff> {
        return runCatching {
            val normGroupEmail = ClassGroupMapper.normalizeEmail(groupEmail)
            // CONTROLLO DI SICUREZZA INTERNO OBBLIGATORIO:
            validateManagedGroup(normGroupEmail)

            // Valida le email desiderate
            for (m in desiredMembers) {
                if (!ClassGroupMapper.validateEmail(m)) {
                    throw IllegalArgumentException("Email membro non valida nel gruppo $normGroupEmail: $m")
                }
            }

            // Se il gruppo non esiste:
            // - in dry-run assumiamo nessun membro attuale;
            // - in esecuzione reale lo creiamo automaticamente con impostazioni 'Restricted'
            val existingGroup = getGroup(normGroupEmail)
            val currentMembers = if (existingGroup == null) {
                if (dryRun) {
                    logger.info("Gruppo $normGroupEmail non esistente: in dry-run si assume nessun membro attuale")
                    emptySet<String>()
                } else {
                    val className = extractClassNameFromGroupEmail(normGroupEmail)
                    logger.info("Gruppo $normGroupEmail non presente: creazione automatica con impostazioni 'Restricted'...")
                    val ensureResult = ensureGroupExists(normGroupEmail, className, dryRun = false)
                    if (ensureResult.isFailure) {
                        throw ensureResult.exceptionOrNull()
                            ?: IllegalStateException("Impossibile creare automaticamente il gruppo $normGroupEmail")
                    }
                    emptySet<String>()
                }
            } else {
                listGroupMembers(normGroupEmail).toSet()
            }

            // PROTEZIONE CONTRO RIMOZIONE MASSIVA ACCIDENTALE:
            // se l'elenco desiderato è vuoto ma il gruppo contiene membri, blocchiamo l'operazione reale.
            // La dry-run può invece mostrare le modifiche previste per consentire una valutazione esplicita.
            if (!dryRun && !allowEmptyDesiredMembers && desiredMembers.isEmpty() && currentMembers.isNotEmpty()) {
                throw IllegalStateException(
                    "Sincronizzazione bloccata per il gruppo $normGroupEmail: " +
                            "l'elenco dei membri desiderato è vuoto ma il gruppo contiene ${currentMembers.size} membri attuali. " +
                            "Possibile XML EDT vuoto o incompleto. " +
                            "Se l'operazione è intenzionale, ripetere la chiamata con allowEmptyDesiredMembers = true dopo conferma esplicita."
                )
            }

            val diff = calculateMembershipChanges(currentMembers, desiredMembers)

            if (dryRun) {
                logger.info(
                    "Dry-run gruppo $normGroupEmail: previste ${diff.toAdd.size} aggiunte e ${diff.toRemove.size} rimozioni"
                )
            } else {
                // 1. Rimozione membri non più associati
                for (toRemove in diff.toRemove) {
                    try {
                        executeWithRetry {
                            directoryService.members().delete(normGroupEmail, toRemove).execute()
                        }
                        logger.info("Rimosso membro $toRemove dal gruppo $normGroupEmail")
                    } catch (e: GoogleJsonResponseException) {
                        if (e.statusCode != 404) {
                            throw e
                        }
                        // 404 = membro già rimosso, nessuna azione necessaria
                        logger.info("Membro $toRemove già rimosso dal gruppo $normGroupEmail (404): nessuna azione necessaria")
                    }
                }

                // 2. Aggiunta membri mancanti con retry resiliente alla propagazione
                for (toAdd in diff.toAdd) {
                    val newMember = Member()
                        .setEmail(toAdd)
                        .setRole("MEMBER")
                    try {
                        executeWithRetryMemberInsert(normGroupEmail, newMember)
                        logger.info("Aggiunto membro $toAdd al gruppo $normGroupEmail")
                    } catch (e: GoogleJsonResponseException) {
                        if (e.statusCode != 409) {
                            throw e
                        }
                        // 409 = membro già presente, nessuna azione necessaria
                        logger.info("Membro $toAdd già presente nel gruppo $normGroupEmail (409): nessuna azione necessaria")
                    }
                }
            }

            diff
        }.onFailure { e ->
            logger.severe("Errore in replaceGroupMembers per il gruppo $groupEmail: ${e.message}")
        }
    }

    private fun executeWithRetryMemberInsert(
        groupEmail: String,
        newMember: Member,
        maxAttempts: Int = 4,
        initialDelayMs: Long = 1500
    ) {
        var delay = initialDelayMs
        for (attempt in 1..maxAttempts) {
            try {
                directoryService.members().insert(groupEmail, newMember).execute()
                return
            } catch (e: GoogleJsonResponseException) {
                if (e.statusCode == 409) return
                if (e.statusCode == 404 && attempt < maxAttempts) {
                    logger.warning("Gruppo $groupEmail non ancora propagato per inserimento membri (tentativo $attempt/$maxAttempts). Attesa ${delay}ms...")
                    try {
                        Thread.sleep(delay)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw e
                    }
                    delay *= 2
                    continue
                }
                if ((e.statusCode == 429 || e.statusCode == 500 || e.statusCode == 503) && attempt < maxAttempts) {
                    logger.warning("Errore temporaneo API (${e.statusCode}) inserimento membro (tentativo $attempt/$maxAttempts). Attesa ${delay}ms...")
                    try {
                        Thread.sleep(delay)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw e
                    }
                    delay *= 2
                    continue
                }
                throw e
            }
        }
    }

    // ============================================================================
    // !!! REVISIONE SICUREZZA: OPERAZIONE DI SCRITTURA SU GOOGLE WORKSPACE !!!
    // Metodo: syncGroup
    // Metodo coordinatore di alto livello per la sincronizzazione di un singolo gruppo.
    // Coordina ensureGroupExists e replaceGroupMembers riutilizzando le funzioni sopra.
    // Verificare con particolare attenzione:
    // - che rispetti rigorosamente dryRun;
    // - che restituisca un risultato strutturato GroupSyncResult con l'esito verificabile;
    // - che, in esecuzione reale, esegua una verifica finale dello stato dei membri;
    // - che in dry-run il campo created indica che il gruppo verrebbe creato, non che è stato creato.
    // ============================================================================
    /**
     * Sincronizza un singolo gruppo: verifica/crea il gruppo e aggiorna i membri.
     * Operazione di scrittura.
     *
     * @param groupEmail Indirizzo email del gruppo autorizzato.
     * @param desiredMembers Insieme degli indirizzi email desiderati.
     * @param dryRun Se true, simula le operazioni senza scrivere su Google Workspace.
     * @param allowEmptyDesiredMembers Se true, consente la rimozione di tutti i membri anche quando
     *                                 desiredMembers è vuoto. Usare SOLO dopo conferma esplicita dell'utente.
     * @return GroupSyncResult con esito, membri aggiunti/rimossi ed eventuali errori.
     */
    fun syncGroup(
        groupEmail: String,
        desiredMembers: Set<String>,
        dryRun: Boolean = false,
        allowEmptyDesiredMembers: Boolean = false
    ): GroupSyncResult {
        val normalizedEmail = ClassGroupMapper.normalizeEmail(groupEmail)
        val className = extractClassNameFromGroupEmail(normalizedEmail)

        val errors = mutableListOf<String>()
        var created = false
        var addedMembers = listOf<String>()
        var removedMembers = listOf<String>()

        try {
            validateManagedGroup(normalizedEmail)

            val ensureResult = ensureGroupExists(normalizedEmail, className, dryRun = dryRun)
            if (ensureResult.isFailure) {
                val err = ensureResult.exceptionOrNull()?.message ?: "Errore verifica/creazione gruppo"
                errors.add(err)
                return GroupSyncResult(normalizedEmail, className, created = false, errors = errors)
            }

            created = ensureResult.getOrDefault(false)

            val membersResult = replaceGroupMembers(
                normalizedEmail,
                desiredMembers,
                dryRun = dryRun,
                allowEmptyDesiredMembers = allowEmptyDesiredMembers
            )

            if (membersResult.isFailure) {
                val err = membersResult.exceptionOrNull()?.message ?: "Errore aggiornamento membri"
                errors.add(err)
            } else {
                val diff = membersResult.getOrThrow()
                addedMembers = diff.toAdd.toList()
                removedMembers = diff.toRemove.toList()

                // Verifica finale dello stato reale solo dopo una sincronizzazione reale con tolleranza per propagazione
                if (!dryRun) {
                    var verified = false
                    for (vAttempt in 1..3) {
                        val verifyResult = verifyGroupMembers(normalizedEmail, desiredMembers)
                        if (verifyResult.isSuccess && verifyResult.getOrDefault(false)) {
                            verified = true
                            break
                        }
                        if (vAttempt < 3) {
                            try {
                                Thread.sleep(1000L * vAttempt)
                            } catch (ie: InterruptedException) {
                                Thread.currentThread().interrupt()
                                break
                            }
                        }
                    }
                    if (!verified) {
                        val finalVerify = verifyGroupMembers(normalizedEmail, desiredMembers)
                        if (finalVerify.isFailure) {
                            errors.add(
                                "Impossibile verificare lo stato finale del gruppo: " +
                                        (finalVerify.exceptionOrNull()?.message ?: "errore sconosciuto")
                            )
                        } else if (!finalVerify.getOrDefault(false)) {
                            errors.add("Lo stato finale del gruppo non corrisponde all'elenco desiderato (possibile ritardo di propagazione Google Workspace)")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            errors.add(e.message ?: "Errore imprevisto durante la sincronizzazione del gruppo")
        }

        val success = errors.isEmpty()
        if (success) {
            logger.info("Sincronizzazione gruppo $normalizedEmail completata con successo (dryRun=$dryRun)")
        } else {
            logger.severe("Sincronizzazione gruppo $normalizedEmail completata con errori: $errors")
        }

        return GroupSyncResult(
            groupEmail = normalizedEmail,
            className = className,
            created = created,
            addedMembers = addedMembers,
            removedMembers = removedMembers,
            errors = errors,
            success = success
        )
    }

    // ========================================================================
    // FUNZIONI DI SICUREZZA E CALCOLO DELLE DIFFERENZE
    // ========================================================================

    /**
     * CONTROLLO DI SICUREZZA CRITICO:
     * verifica internamente che l'indirizzo del gruppo rientri tassativamente nel perimetro:
     * cdc_{nome_classe}@{dominio}
     * Qualsiasi tentativo di accedere o modificare gruppi esterni a questo perimetro
     * lancia una SecurityException immediata, bloccando l'operazione prima delle chiamate API.
     */
    fun validateManagedGroup(groupEmail: String) {
        val normalized = ClassGroupMapper.normalizeEmail(groupEmail)
        if (!ClassGroupMapper.validateGroupEmail(normalized, normalizedDomain)) {
            val message = "VIOLAZIONE DI SICUREZZA: Il gruppo '$groupEmail' non è autorizzato. " +
                    "L'applicazione può gestire esclusivamente mailing list nel formato cdc_{classe}@$normalizedDomain"
            logger.severe(message)
            throw SecurityException(message)
        }
    }

    /**
     * Calcola la differenza tra i membri attuali e quelli desiderati:
     * toAdd: membri in desired ma non in current (case-insensitive)
     * toRemove: membri in current ma non in desired
     * unchanged: membri presenti in entrambi
     */
    fun calculateMembershipChanges(
        currentMembers: Collection<String>,
        desiredMembers: Collection<String>
    ): MembershipDiff {
        val currentNormalized = currentMembers.map { ClassGroupMapper.normalizeEmail(it) }.toSet()
        val desiredNormalized = desiredMembers.map { ClassGroupMapper.normalizeEmail(it) }.toSet()

        val toAdd = desiredNormalized - currentNormalized
        val toRemove = currentNormalized - desiredNormalized
        val unchanged = currentNormalized.intersect(desiredNormalized)

        return MembershipDiff(
            toAdd = toAdd,
            toRemove = toRemove,
            unchanged = unchanged
        )
    }

    private fun extractClassNameFromGroupEmail(groupEmail: String): String {
        val local = groupEmail.split("@")[0]
        return local.removePrefix("cdc_").uppercase()
    }

    /**
     * Esegue una chiamata API con retry automatico ed esponenziale in caso di errori temporanei
     * (HTTP 429 Too Many Requests, HTTP 500, HTTP 503 Service Unavailable, o IOException di rete).
     *
     * ATTENZIONE: questo metodo usa Thread.sleep e blocca il thread corrente.
     * Deve essere richiamato esclusivamente da un thread in background, mai dal thread della UI.
     */
    private fun <T> executeWithRetry(
        maxAttempts: Int = 4,
        initialDelayMs: Long = 1000,
        action: () -> T
    ): T {
        var delay = initialDelayMs
        var lastException: Exception? = null

        for (attempt in 1..maxAttempts) {
            try {
                return action()
            } catch (e: GoogleJsonResponseException) {
                lastException = e
                val code = e.statusCode
                // Riprova solo per rate limit o errori temporanei del server
                if (code == 429 || code == 500 || code == 503) {
                    if (attempt == maxAttempts) throw e
                    logger.warning(
                        "Errore temporaneo API (HTTP $code) durante una chiamata API " +
                                "(tentativo $attempt/$maxAttempts). Nuovo tentativo tra ${delay}ms."
                    )
                    try {
                        Thread.sleep(delay)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw e
                    }
                    delay *= 2
                } else {
                    throw e
                }
            } catch (e: IOException) {
                lastException = e
                val msg = e.message ?: ""
                val isAuthError = msg.contains("invalid_grant", ignoreCase = true) ||
                        msg.contains("401", ignoreCase = true) ||
                        msg.contains("Unauthorized", ignoreCase = true) ||
                        msg.contains("unauthorized_client", ignoreCase = true) ||
                        msg.contains("access_denied", ignoreCase = true)

                if (isAuthError) {
                    val friendlyMsg = if (msg.contains("Invalid email or User ID", ignoreCase = true)) {
                        "Autenticazione Google fallita: l'account amministratore '${adminEmail ?: ""}' non esiste o non appartiene al dominio Google Workspace autorizzato. Verificare l'indirizzo inserito."
                    } else if (msg.contains("401", ignoreCase = true) || msg.contains("Unauthorized", ignoreCase = true)) {
                        "Autenticazione token Google respinta (401 Unauthorized): verificare che l'API richiesta sia abilitata nella Google Cloud Console e che l'ambito OAuth sia autorizzato nella delega DWD."
                    } else {
                        "Autenticazione Google fallita ($msg): verificare che il Service Account sia stato autorizzato per la delega DWD nella Google Admin Console per l'amministratore '${adminEmail ?: ""}'."
                    }
                    throw IOException(friendlyMsg, e)
                }

                if (attempt == maxAttempts) throw e
                logger.warning(
                    "Errore di rete durante una chiamata API " +
                            "(tentativo $attempt/$maxAttempts): ${e.message}. Nuovo tentativo tra ${delay}ms."
                )
                try {
                    Thread.sleep(delay)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw e
                }
                delay *= 2
            }
        }
        throw lastException ?: IOException("Tentativi esauriti durante la chiamata API.")
    }
}