package it.madx.edttocdc.sync

import it.madx.edttocdc.config.AppSettings
import it.madx.edttocdc.config.SettingsManager
import it.madx.edttocdc.edt.ClassGroupMapper
import it.madx.edttocdc.edt.EdtXmlDownloader
import it.madx.edttocdc.edt.EdtXmlParser
import it.madx.edttocdc.google.GoogleWorkspaceClient
import it.madx.edttocdc.model.LogEntry
import it.madx.edttocdc.model.LogLevel
import it.madx.edttocdc.model.SyncSummary
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Motore principale di orchestrazione della sincronizzazione tra EDT e Google Workspace.
 *
 * Coordina l'intero ciclo di vita:
 * 1. Validazione delle impostazioni locali
 * 2. Download e validazione dell'XML EDT
 * 3. Analisi e costruzione della mappa delle classi e delle email
 * 4. Controlli di sicurezza e validazione degli indirizzi di gruppo
 * 5. Connessione ad Admin SDK Directory API
 * 6. Verifica/Creazione dei gruppi cdc_{classe}@dominio
 * 7. Sincronizzazione idempotente dei docenti iscritti
 * 8. Generazione del riepilogo finale
 */
object SyncEngine {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    private fun log(
        level: LogLevel,
        message: String,
        onLog: (LogEntry) -> Unit
    ) {
        val timestamp = LocalTime.now().format(timeFormatter)
        onLog(LogEntry(timestamp, level, message))
    }

    /**
     * Esegue l'intero flusso di sincronizzazione (o simulazione dry-run).
     *
     * @param settings Configurazione dell'applicazione.
     * @param dryRun Se true, calcola le modifiche e interroga Google senza scrivere su gruppi o membri.
     * @param onLog Callback per la ricezione in tempo reale dei messaggi di log.
     * @param onProgress Callback per l'aggiornamento della barra o stato di avanzamento.
     * @return Oggetto SyncSummary con le statistiche conclusive e l'esito.
     */
    fun runSync(
        settings: AppSettings,
        dryRun: Boolean = false,
        logGroupsDetails: Boolean = false,
        onLog: (LogEntry) -> Unit,
        onProgress: (stage: String, current: Int, total: Int) -> Unit
    ): SyncSummary {
        val runMode = if (dryRun) "SIMULAZIONE (DRY RUN)" else "SINCRONIZZAZIONE REALE"
        log(if (dryRun) LogLevel.DRY_RUN else LogLevel.INFO, "Avvio del processo in modalità $runMode.", onLog)

        // FASE 1: Convalida delle impostazioni
        onProgress("Convalida impostazioni", 0, 5)
        val validationErrors = SettingsManager.validateSettings(settings)
        if (validationErrors.isNotEmpty()) {
            for (err in validationErrors) {
                log(LogLevel.ERROR, "Errore di configurazione: $err", onLog)
            }
            return SyncSummary(
                totalClasses = 0,
                existingGroups = 0,
                createdGroups = 0,
                addedTeachersCount = 0,
                removedTeachersCount = 0,
                successfullySyncedGroups = 0,
                failedGroups = 0,
                anomalies = validationErrors,
                isDryRun = dryRun,
                success = false
            )
        }

        val credentialsFileResult = SettingsManager.getCredentialsFile(settings.googleCredentialsPath)
        val credentialsFile = credentialsFileResult.getOrElse {
            log(LogLevel.ERROR, "Credenziali non valide: ${it.message}", onLog)
            return SyncSummary(0, 0, 0, 0, 0, 0, 0, listOf(it.message ?: "Errore credenziali"), dryRun, false)
        }

        // FASE 2: Download dell'XML da EDT
        onProgress("Download file XML da EDT", 1, 5)
        log(LogLevel.INFO, "Download del file XML da: ${settings.edtXmlUrl}", onLog)

        val xmlDownloadResult = EdtXmlDownloader.downloadEdtXml(settings.edtXmlUrl)
        if (xmlDownloadResult.isFailure) {
            val err = xmlDownloadResult.exceptionOrNull()?.message ?: "Errore sconosciuto durante il download EDT"
            log(LogLevel.ERROR, "Download fallito: $err", onLog)
            return SyncSummary(0, 0, 0, 0, 0, 0, 0, listOf(err), dryRun, false)
        }
        val xmlContent = xmlDownloadResult.getOrThrow()
        log(LogLevel.SUCCESS, "File XML scaricato con successo (${xmlContent.toByteArray(Charsets.UTF_8).size} bytes).", onLog)

        // FASE 3: Parsing sicuro e costruzione della mappa classi -> docenti
        onProgress("Analisi struttura XML", 2, 5)
        log(LogLevel.INFO, "Analisi del contenuto XML...", onLog)

        val parsedClasses = try {
            EdtXmlParser.parseEdtXml(xmlContent)
        } catch (e: Exception) {
            log(LogLevel.ERROR, "Errore durante l'analisi dell'XML: ${e.message}", onLog)
            return SyncSummary(0, 0, 0, 0, 0, 0, 0, listOf("Parsing XML fallito: ${e.message}"), dryRun, false)
        }

        if (parsedClasses.isEmpty()) {
            val msg = "Il file XML non contiene alcuna classe associata a docenti."
            log(LogLevel.ERROR, msg, onLog)
            return SyncSummary(0, 0, 0, 0, 0, 0, 0, listOf(msg), dryRun, false)
        }

        val classEmailMap = ClassGroupMapper.buildClassEmailMap(parsedClasses)
        val groupEmailMap = ClassGroupMapper.buildGroupEmailMap(classEmailMap, settings.schoolDomain)
        val distinctEmails = classEmailMap.values.flatten().toSet()

        log(LogLevel.SUCCESS, "Individuate ${classEmailMap.size} classi distinte.", onLog)
        log(LogLevel.INFO, "Individuati ${distinctEmails.size} indirizzi email istituzionali unici.", onLog)

        if (logGroupsDetails) {
            for ((groupEmail, emails) in groupEmailMap) {
                log(LogLevel.INFO, "Gruppo: $groupEmail", onLog)
                if (emails.isEmpty()) {
                    log(LogLevel.WARNING, "    (nessun docente associato)", onLog)
                } else {
                    for (email in emails.sorted()) {
                        log(LogLevel.INFO, "    $email", onLog)
                    }
                }
            }
        }

        // Convalida formale degli indirizzi generati
        val anomalies = ClassGroupMapper.validateGroupEmailMap(groupEmailMap, settings.schoolDomain).toMutableList()
        for (anomaly in anomalies) {
            log(LogLevel.WARNING, anomaly, onLog)
        }

        // FASE 4: Inizializzazione del Client Google Workspace e test di connessione
        onProgress("Connessione a Google Workspace", 3, 5)
        log(LogLevel.INFO, "Autenticazione con Google Workspace (dominio: ${settings.schoolDomain})...", onLog)

        val googleClient = try {
            GoogleWorkspaceClient(
                credentialsFile = credentialsFile,
                domain = settings.schoolDomain,
                adminEmail = settings.adminEmail.ifBlank { null }
            )
        } catch (e: Exception) {
            log(LogLevel.ERROR, "Inizializzazione client Google fallita: ${e.message}", onLog)
            return SyncSummary(classEmailMap.size, 0, 0, 0, 0, 0, 0, listOf("Inizializzazione Google fallita: ${e.message}"), dryRun, false)
        }

        val connectionTest = googleClient.testConnection()
        if (connectionTest.isFailure) {
            val err = connectionTest.exceptionOrNull()?.message ?: "Errore di connessione a Google Directory API"
            log(LogLevel.ERROR, "Autenticazione Google Workspace fallita: $err", onLog)
            return SyncSummary(classEmailMap.size, 0, 0, 0, 0, 0, 0, listOf("Connessione Google fallita: $err"), dryRun, false)
        }
        log(LogLevel.SUCCESS, "Autenticazione Google Workspace riuscita.", onLog)

        // FASE 5: Sincronizzazione dei gruppi e dei relativi membri
        onProgress("Sincronizzazione gruppi", 4, 5)

        var existingGroupsCount = 0
        var createdGroupsCount = 0
        var totalAddedTeachers = 0
        var totalRemovedTeachers = 0
        var successGroupsCount = 0
        var failedGroupsCount = 0

        val totalGroups = groupEmailMap.size
        var currentIndex = 0

        for ((groupEmail, desiredMembers) in groupEmailMap) {
            currentIndex++
            onProgress("Sincronizzazione $groupEmail ($currentIndex/$totalGroups)", currentIndex, totalGroups)
            log(LogLevel.INFO, "Verifica del gruppo $groupEmail...", onLog)

            try {
                // Sola lettura: verifica se il gruppo esiste già
                val existing = googleClient.getGroup(groupEmail)
                if (existing != null) {
                    existingGroupsCount++
                    log(LogLevel.INFO, "Gruppo esistente: $groupEmail", onLog)
                } else {
                    createdGroupsCount++
                    val prefix = if (dryRun) "[SIMULAZIONE] Creazione prevista gruppo:" else "Creazione nuovo gruppo:"
                    log(if (dryRun) LogLevel.DRY_RUN else LogLevel.SUCCESS, "$prefix $groupEmail", onLog)
                }

                // Sincronizza membri tramite il metodo dedicato in GoogleWorkspaceClient
                val result = googleClient.syncGroup(groupEmail, desiredMembers, dryRun = dryRun)

                if (result.success) {
                    successGroupsCount++
                    val addedCount = result.addedMembers.size
                    val removedCount = result.removedMembers.size
                    totalAddedTeachers += addedCount
                    totalRemovedTeachers += removedCount

                    if (addedCount == 0 && removedCount == 0) {
                        log(LogLevel.INFO, "Membri già allineati (${desiredMembers.size} docenti).", onLog)
                    } else {
                        if (addedCount > 0) {
                            val prefix = if (dryRun) "[SIMULAZIONE] Da aggiungere" else "Aggiunti"
                            log(if (dryRun) LogLevel.DRY_RUN else LogLevel.SUCCESS, "$prefix $addedCount docenti: ${result.addedMembers.joinToString(", ")}", onLog)
                        }
                        if (removedCount > 0) {
                            val prefix = if (dryRun) "[SIMULAZIONE] Da rimuovere" else "Rimossi"
                            log(if (dryRun) LogLevel.DRY_RUN else LogLevel.WARNING, "$prefix $removedCount docenti: ${result.removedMembers.joinToString(", ")}", onLog)
                        }
                    }
                    log(LogLevel.SUCCESS, "Classe ${result.className} sincronizzata con successo.", onLog)
                } else {
                    failedGroupsCount++
                    for (err in result.errors) {
                        log(LogLevel.ERROR, "Errore nella sincronizzazione di $groupEmail: $err", onLog)
                        anomalies.add("Errore $groupEmail: $err")
                    }
                }
            } catch (e: Exception) {
                failedGroupsCount++
                val err = e.message ?: "Errore imprevisto"
                log(LogLevel.ERROR, "Eccezione durante l'elaborazione di $groupEmail: $err", onLog)
                anomalies.add("Eccezione $groupEmail: $err")
            }
        }

        // FASE 6: Riepilogo conclusivo
        onProgress("Completato", totalGroups, totalGroups)
        val finalStatus = if (failedGroupsCount == 0) "SUCCESSO" else "COMPLETATA CON ERRORI"
        log(
            if (failedGroupsCount == 0) LogLevel.SUCCESS else LogLevel.WARNING,
            "Sincronizzazione terminata ($finalStatus): $totalGroups classi elaborate, " +
            "$createdGroupsCount gruppi creati, $totalAddedTeachers docenti aggiunti, $totalRemovedTeachers docenti rimossi.",
            onLog
        )

        return SyncSummary(
            totalClasses = classEmailMap.size,
            existingGroups = existingGroupsCount,
            createdGroups = createdGroupsCount,
            addedTeachersCount = totalAddedTeachers,
            removedTeachersCount = totalRemovedTeachers,
            successfullySyncedGroups = successGroupsCount,
            failedGroups = failedGroupsCount,
            anomalies = anomalies,
            isDryRun = dryRun,
            success = failedGroupsCount == 0
        )
    }
}
