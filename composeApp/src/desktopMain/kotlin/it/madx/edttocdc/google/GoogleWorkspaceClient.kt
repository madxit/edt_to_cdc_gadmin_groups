package it.madx.edttocdc.google

// ============================================================================
// SECURITY REVIEW MAP
// Metodi di sola lettura:
//   - testConnection
//   - getGroup
//   - listGroupMembers
// Punti di ingresso che possono scrivere su Google Workspace:
//   - ensureGroupExists (creazione gruppo mancante)
//   - replaceGroupMembers (aggiunta/rimozione iscritti al gruppo)
//   - syncGroup (orchestrazione sincronizzazione singolo gruppo)
// Cancellazione di gruppi:
//   - NON IMPLEMENTATA (nessun metodo o API di eliminazione gruppi presente)
//
// Cercare "REVISIONE SICUREZZA" per trovare i blocchi sensibili di scrittura.
// ============================================================================

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.directory.Directory
import com.google.api.services.directory.DirectoryScopes
import com.google.api.services.directory.model.Group
import com.google.api.services.directory.model.Member
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

/**
 * Client dedicato e isolato per tutte le interazioni con Google Workspace / Admin SDK Directory API.
 *
 * Tutte le chiamate alle API Google risiedono esclusivamente in questa classe.
 * Le operazioni di scrittura sono ridotte al minimo indispensabile e protette da controlli
 * tassativi sul formato dell'indirizzo del gruppo e sul rispetto della modalità simulazione (dry-run).
 */
class GoogleWorkspaceClient(
    val credentialsFile: File,
    val domain: String,
    val adminEmail: String? = null
) {

    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val normalizedDomain = domain.trim().trimStart('@').lowercase()

    private val directoryService: Directory by lazy {
        initDirectoryService()
    }

    private fun initDirectoryService(): Directory {
        if (!credentialsFile.exists() || !credentialsFile.canRead()) {
            throw IOException("File di credenziali Google non accessibile: ${credentialsFile.name}")
        }

        val scopes = listOf(
            DirectoryScopes.ADMIN_DIRECTORY_GROUP,
            DirectoryScopes.ADMIN_DIRECTORY_GROUP_MEMBER
        )

        val stream = FileInputStream(credentialsFile)
        var credentials = GoogleCredentials.fromStream(stream)

        if (credentials.createScopedRequired()) {
            credentials = credentials.createScoped(scopes)
        }

        // Se è specificata un'email amministrativa e si utilizzano credenziali di un Service Account,
        // configura la Domain-Wide Delegation (impersonazione dell'amministratore del dominio)
        if (!adminEmail.isNullOrBlank() && credentials is ServiceAccountCredentials) {
            credentials = credentials.createDelegated(adminEmail.trim())
        }

        val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        val requestInitializer = HttpCredentialsAdapter(credentials)

        return Directory.Builder(httpTransport, jsonFactory, requestInitializer)
            .setApplicationName("EdtToCdcSync/1.0")
            .build()
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

            GoogleConnectionInfo(
                authenticatedEmail = adminEmail?.ifBlank { null } ?: "Service Account (${credentialsFile.name})",
                domain = normalizedDomain,
                directoryAccessConferred = true
            )
        }
    }

    /**
     * Recupera un gruppo Google Workspace se esistente.
     * Operazione di sola lettura.
     *
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
     *
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

        return members
    }

    // ========================================================================
    // SEZIONE METODI DI SCRITTURA (MODIFICA GOOGLE WORKSPACE)
    // ========================================================================

    // ============================================================================
    // !!! REVISIONE SICUREZZA: OPERAZIONE DI SCRITTURA SU GOOGLE WORKSPACE !!!
    // Metodo: ensureGroupExists
    // Questo metodo può creare un gruppo mancante in Google Workspace.
    // Verificare con particolare attenzione:
    // - che l'indirizzo appartenga tassativamente al perimetro cdc_{classe}@dominio;
    // - che validateManagedGroup(groupEmail) blocchi qualsiasi altro indirizzo;
    // - che se dry_run == true NESSUNA chiamata di creazione venga eseguita;
    // - che non esista alcuna operazione di cancellazione o sovrascrittura del gruppo.
    // ============================================================================
    fun ensureGroupExists(
        groupEmail: String,
        className: String,
        description: String = "Mailing list Consiglio di Classe $className",
        dryRun: Boolean = false
    ): Result<Boolean> {
        return runCatching {
            val normalizedEmail = ClassGroupMapper.normalizeEmail(groupEmail)
            // CONTROLLO DI SICUREZZA INTERNO OBBLIGATORIO:
            validateManagedGroup(normalizedEmail)

            val existing = getGroup(normalizedEmail)
            if (existing != null) {
                // Il gruppo esiste già: nessuna creazione necessaria
                false
            } else {
                if (dryRun) {
                    // Modalità simulazione: segnala la creazione prevista senza effettuare chiamate API di scrittura
                    true
                } else {
                    val newGroup = Group()
                        .setEmail(normalizedEmail)
                        .setName("Consiglio di Classe $className")
                        .setDescription(description)

                    executeWithRetry {
                        directoryService.groups().insert(newGroup).execute()
                    }
                    true
                }
            }
        }
    }

    // ============================================================================
    // !!! REVISIONE SICUREZZA: OPERAZIONE DI SCRITTURA SU GOOGLE WORKSPACE !!!
    // Metodo: replaceGroupMembers
    // Questo metodo aggiunge docenti mancanti e rimuove docenti non più presenti
    // nella classe.
    // Verificare con particolare attenzione:
    // - che il gruppo sia rigorosamente nel formato cdc_{classe}@dominio;
    // - che rimuova ESCLUSIVAMENTE iscritti non previsti e MAI il gruppo stesso;
    // - che tutte le email dei nuovi docenti siano validate prima dell'aggiunta;
    // - che se dry_run == true NON venga effettuata alcuna chiamata API di scrittura;
    // - che la sincronizzazione sia idempotente.
    // ============================================================================
    fun replaceGroupMembers(
        groupEmail: String,
        desiredMembers: Set<String>,
        dryRun: Boolean = false
    ): Result<MembershipDiff> {
        return runCatching {
            val normalizedEmail = ClassGroupMapper.normalizeEmail(groupEmail)
            // CONTROLLO DI SICUREZZA INTERNO OBBLIGATORIO:
            validateManagedGroup(normalizedEmail)

            // Valida le email desiderate
            for (m in desiredMembers) {
                if (!ClassGroupMapper.validateEmail(m)) {
                    throw IllegalArgumentException("Email membro non valida nel gruppo $normalizedEmail: $m")
                }
            }

            val currentMembers = listGroupMembers(normalizedEmail).toSet()
            val diff = calculateMembershipChanges(currentMembers, desiredMembers)

            if (!dryRun) {
                // 1. Rimozione membri non più associati
                for (toRemove in diff.toRemove) {
                    executeWithRetry {
                        directoryService.members().delete(normalizedEmail, toRemove).execute()
                    }
                }

                // 2. Aggiunta membri mancanti
                for (toAdd in diff.toAdd) {
                    val newMember = Member()
                        .setEmail(toAdd)
                        .setRole("MEMBER")

                    executeWithRetry {
                        directoryService.members().insert(normalizedEmail, newMember).execute()
                    }
                }
            }

            diff
        }
    }

    // ============================================================================
    // !!! REVISIONE SICUREZZA: OPERAZIONE DI SCRITTURA SU GOOGLE WORKSPACE !!!
    // Metodo: syncGroup
    // Metodo coordinatore di alto livello per la sincronizzazione di un singolo gruppo.
    // Coordina ensureGroupExists e replaceGroupMembers riutilizzando le funzioni sopra.
    // Verificare con particolare attenzione:
    // - che rispetti rigorosamente dry_run;
    // - che restituisca un risultato strutturato GroupSyncResult con l'esito verificabile.
    // ============================================================================
    fun syncGroup(
        groupEmail: String,
        desiredMembers: Set<String>,
        dryRun: Boolean = false
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

            val membersResult = replaceGroupMembers(normalizedEmail, desiredMembers, dryRun = dryRun)
            if (membersResult.isFailure) {
                val err = membersResult.exceptionOrNull()?.message ?: "Errore aggiornamento membri"
                errors.add(err)
            } else {
                val diff = membersResult.getOrThrow()
                addedMembers = diff.toAdd.toList()
                removedMembers = diff.toRemove.toList()
            }
        } catch (e: Exception) {
            errors.add(e.message ?: "Errore imprevisto durante la sincronizzazione del gruppo")
        }

        return GroupSyncResult(
            groupEmail = normalizedEmail,
            className = className,
            created = created,
            addedMembers = addedMembers,
            removedMembers = removedMembers,
            errors = errors,
            success = errors.isEmpty()
        )
    }

    // ========================================================================
    // FUNZIONI DI SICUREZZA E CALCOLO DELLE DIFFERENZE
    // ========================================================================

    /**
     * CONTROLLO DI SICUREZZA CRITICO:
     * Verifica internamente che l'indirizzo del gruppo rientri tassativamente nel perimetro:
     * cdc_{nome_classe}@{dominio}
     *
     * Qualsiasi tentativo di accedere o modificare gruppi esterni a questo perimetro
     * lancia una SecurityException immediata, bloccando l'operazione prima delle chiamate API.
     */
    fun validateManagedGroup(groupEmail: String) {
        val normalized = ClassGroupMapper.normalizeEmail(groupEmail)
        if (!ClassGroupMapper.validateGroupEmail(normalized, normalizedDomain)) {
            throw SecurityException(
                "VIOLAZIONE DI SICUREZZA: Il gruppo '$groupEmail' non è autorizzato. " +
                "L'applicazione può gestire esclusivamente mailing list nel formato cdc_{classe}@$normalizedDomain"
            )
        }
    }

    /**
     * Calcola la differenza tra i membri attuali e quelli desiderati:
     * - toAdd: membri in desired ma non in current (case-insensitive)
     * - toRemove: membri in current ma non in desired
     * - unchanged: membri presenti in entrambi
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
        return local.removePrefix("cdc_")
    }

    /**
     * Esegue una chiamata API con retry automatico ed esponenziale in caso di errori temporanei
     * (HTTP 429 Too Many Requests, HTTP 503 Service Unavailable, o IOException di rete).
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
                if (attempt == maxAttempts) throw e
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
