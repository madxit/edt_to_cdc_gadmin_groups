package it.madx.edttocdc.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import it.madx.edttocdc.config.AppSettings
import it.madx.edttocdc.config.SettingsManager
import it.madx.edttocdc.edt.ClassGroupMapper
import it.madx.edttocdc.edt.EdtXmlDownloader
import it.madx.edttocdc.google.ExportFormat
import it.madx.edttocdc.google.ExportType
import it.madx.edttocdc.google.GoogleWorkspaceClient
import it.madx.edttocdc.google.GoogleWorkspaceExporter
import it.madx.edttocdc.model.LogEntry
import it.madx.edttocdc.model.LogLevel
import it.madx.edttocdc.model.SyncSummary
import it.madx.edttocdc.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainViewModel(private val scope: CoroutineScope) {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    var settings by mutableStateOf(AppSettings())
        private set

    var isRunning by mutableStateOf(false)
        private set

    var dryRun by mutableStateOf(false)

    var logGroupsDetails by mutableStateOf(false)

    var progressStage by mutableStateOf("Pronto")
        private set

    var progressCurrent by mutableStateOf(0)
        private set

    var progressTotal by mutableStateOf(0)
        private set

    val logs = mutableStateListOf<LogEntry>()

    var showEdtHelp by mutableStateOf(false)
    var showGoogleHelp by mutableStateOf(false)
    var showAbout by mutableStateOf(false)
    var activeSummary by mutableStateOf<SyncSummary?>(null)

    init {
        val loaded = SettingsManager.loadSettings()
        settings = loaded
        dryRun = loaded.dryRunDefault
        addLog(LogLevel.INFO, "Applicazione avviata. Impostazioni caricate con successo.")
    }

    fun updateSettings(newSettings: AppSettings) {
        settings = newSettings
        SettingsManager.saveSettings(newSettings)
    }

    fun addLog(level: LogLevel, message: String) {
        val timestamp = LocalTime.now().format(timeFormatter)
        logs.add(LogEntry(timestamp, level, message))
    }

    fun testEdtConnection() {
        if (isRunning) return
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isRunning = true
                progressStage = "Verifica URL EDT..."
                addLog(LogLevel.INFO, "Avvio verifica connessione EDT: ${settings.edtXmlUrl}")
            }

            val result = EdtXmlDownloader.testEdtConnection(settings.edtXmlUrl)

            withContext(Dispatchers.Main) {
                isRunning = false
                progressStage = "Pronto"
                if (result.isSuccess) {
                    val info = result.getOrThrow()
                    val detectedDomain = ClassGroupMapper.detectDominantDomain(info.parsedClasses)
                    if (detectedDomain != null) {
                        updateSettings(settings.copy(schoolDomain = detectedDomain))
                        addLog(LogLevel.SUCCESS, "Dominio scuola ricavato dal file EDT: $detectedDomain")
                    }

                    addLog(
                        LogLevel.SUCCESS,
                        "Verifica EDT riuscita: XML scaricato (${info.sizeBytes} byte) ed estratte ${info.detectedClassesCount} classi."
                    )
                    if (logGroupsDetails) {
                        val domain = settings.schoolDomain.ifBlank { detectedDomain ?: "dominio.scuola" }
                        val classEmailMap = ClassGroupMapper.buildClassEmailMap(info.parsedClasses)
                        val groupEmailMap = ClassGroupMapper.buildGroupEmailMap(classEmailMap, domain)
                        for ((groupEmail, emails) in groupEmailMap) {
                            addLog(LogLevel.INFO, "Gruppo: $groupEmail")
                            if (emails.isEmpty()) {
                                addLog(LogLevel.WARNING, "    (nessun docente associato)")
                            } else {
                                for (email in emails.sorted()) {
                                    addLog(LogLevel.INFO, "    $email")
                                }
                            }
                        }
                    }
                } else {
                    addLog(
                        LogLevel.ERROR,
                        "Verifica EDT fallita: ${result.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    fun testGoogleConnection() {
        if (isRunning) return
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isRunning = true
                progressStage = "Verifica connessione Google Workspace..."
                addLog(LogLevel.INFO, "Avvio verifica credenziali Google Workspace...")
            }

            val credResult = SettingsManager.getCredentialsFile(settings.googleCredentialsPath)
            if (credResult.isFailure) {
                withContext(Dispatchers.Main) {
                    isRunning = false
                    progressStage = "Pronto"
                    addLog(LogLevel.ERROR, "Credenziali non valide: ${credResult.exceptionOrNull()?.message}")
                }
                return@launch
            }

            val effectiveDomain = settings.schoolDomain.ifBlank {
                settings.adminEmail.substringAfter('@', "").ifBlank { null }
            }
            if (effectiveDomain.isNullOrBlank()) {
                withContext(Dispatchers.Main) {
                    isRunning = false
                    progressStage = "Pronto"
                    addLog(
                        LogLevel.ERROR,
                        "Dominio scuola non disponibile: verificare prima il file XML EDT o inserire l'email dell'amministratore."
                    )
                }
                return@launch
            }

            val credentialsFile = credResult.getOrThrow()
            val result = runCatching {
                val client = GoogleWorkspaceClient(
                    credentialsFile = credentialsFile,
                    domain = effectiveDomain,
                    adminEmail = settings.adminEmail.ifBlank { null }
                )
                client.testConnection().getOrThrow()
            }

            withContext(Dispatchers.Main) {
                isRunning = false
                progressStage = "Pronto"
                if (result.isSuccess) {
                    val info = result.getOrThrow()
                    addLog(
                        LogLevel.SUCCESS,
                        "Connessione Google Workspace riuscita: account ${info.authenticatedEmail}, dominio ${info.domain}."
                    )
                } else {
                    addLog(
                        LogLevel.ERROR,
                        "Connessione Google Workspace fallita: ${result.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    fun startSync(runDryRun: Boolean = false) {
        if (isRunning) return

        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isRunning = true
                progressStage = "Fase 1/3: Verifica sorgente EDT..."
                progressCurrent = 0
                progressTotal = 100
                addLog(LogLevel.INFO, "=== Avvio procedura di sincronizzazione ===")
                addLog(LogLevel.INFO, "Fase 1/3: Verifica sorgente dati EDT...")
            }

            // --- VERIFICA 1: Sorgente dati EDT ---
            val edtResult = EdtXmlDownloader.testEdtConnection(settings.edtXmlUrl)
            if (edtResult.isFailure) {
                withContext(Dispatchers.Main) {
                    isRunning = false
                    progressStage = "Errore verifica EDT"
                    addLog(
                        LogLevel.ERROR,
                        "Interruzione sincronizzazione: verifica EDT fallita - ${edtResult.exceptionOrNull()?.message}"
                    )
                }
                return@launch
            }

            val edtInfo = edtResult.getOrThrow()
            val detectedDomain = ClassGroupMapper.detectDominantDomain(edtInfo.parsedClasses)
            val updatedSettings = if (detectedDomain != null && settings.schoolDomain.isBlank()) {
                val s = settings.copy(schoolDomain = detectedDomain)
                withContext(Dispatchers.Main) {
                    updateSettings(s)
                    addLog(LogLevel.SUCCESS, "Dominio scuola ricavato dal file EDT: $detectedDomain")
                }
                s
            } else {
                settings
            }

            withContext(Dispatchers.Main) {
                addLog(
                    LogLevel.SUCCESS,
                    "Fase 1 completata: XML EDT valido, trovate ${edtInfo.detectedClassesCount} classi."
                )
                progressStage = "Fase 2/3: Verifica connessione Google Workspace..."
                progressCurrent = 15
                addLog(LogLevel.INFO, "Fase 2/3: Verifica connessione Google Workspace...")
            }

            // --- VERIFICA 2: Google Workspace ---
            val credResult = SettingsManager.getCredentialsFile(updatedSettings.googleCredentialsPath)
            if (credResult.isFailure) {
                withContext(Dispatchers.Main) {
                    isRunning = false
                    progressStage = "Errore credenziali"
                    addLog(
                        LogLevel.ERROR,
                        "Interruzione sincronizzazione: file credenziali non valido - ${credResult.exceptionOrNull()?.message}"
                    )
                }
                return@launch
            }

            val effectiveDomain = updatedSettings.schoolDomain.ifBlank {
                detectedDomain ?: updatedSettings.adminEmail.substringAfter('@', "").ifBlank { null }
            }
            if (effectiveDomain.isNullOrBlank()) {
                withContext(Dispatchers.Main) {
                    isRunning = false
                    progressStage = "Errore dominio"
                    addLog(
                        LogLevel.ERROR,
                        "Interruzione sincronizzazione: impossibile determinare il dominio della scuola."
                    )
                }
                return@launch
            }

            val credentialsFile = credResult.getOrThrow()
            val googleConnResult = runCatching {
                val client = GoogleWorkspaceClient(
                    credentialsFile = credentialsFile,
                    domain = effectiveDomain,
                    adminEmail = updatedSettings.adminEmail.ifBlank { null }
                )
                client.testConnection().getOrThrow()
            }

            if (googleConnResult.isFailure) {
                withContext(Dispatchers.Main) {
                    isRunning = false
                    progressStage = "Errore Google Workspace"
                    addLog(
                        LogLevel.ERROR,
                        "Interruzione sincronizzazione: verifica Google Workspace fallita - ${googleConnResult.exceptionOrNull()?.message}"
                    )
                }
                return@launch
            }

            val gInfo = googleConnResult.getOrThrow()
            val finalSettings = if (updatedSettings.schoolDomain.isBlank()) {
                val s = updatedSettings.copy(schoolDomain = effectiveDomain)
                withContext(Dispatchers.Main) { updateSettings(s) }
                s
            } else {
                updatedSettings
            }

            withContext(Dispatchers.Main) {
                addLog(
                    LogLevel.SUCCESS,
                    "Fase 2 completata: connessione Google Workspace riuscita (${gInfo.authenticatedEmail})."
                )
                progressStage = "Fase 3/3: Sincronizzazione gruppi in corso..."
                progressCurrent = 30
                addLog(LogLevel.INFO, "Fase 3/3: Avvio sincronizzazione gruppi CdC...")
            }

            // --- FASE 3: Sincronizzazione reale ---
            val summary = SyncEngine.runSync(
                settings = finalSettings,
                dryRun = runDryRun,
                logGroupsDetails = logGroupsDetails,
                onLog = { entry ->
                    scope.launch(Dispatchers.Main) {
                        logs.add(entry)
                    }
                },
                onProgress = { stage, current, total ->
                    scope.launch(Dispatchers.Main) {
                        progressStage = stage
                        progressCurrent = current
                        progressTotal = total
                    }
                }
            )

            withContext(Dispatchers.Main) {
                isRunning = false
                progressStage = "Completato"
                activeSummary = summary
            }
        }
    }

    fun exportData(type: ExportType, format: ExportFormat, targetFile: File) {
        if (isRunning) return

        val credResult = SettingsManager.getCredentialsFile(settings.googleCredentialsPath)
        if (credResult.isFailure) {
            addLog(
                LogLevel.ERROR,
                "Esportazione impossibile: file credenziali Google non valido - ${credResult.exceptionOrNull()?.message}"
            )
            return
        }

        val effectiveDomain = settings.schoolDomain.ifBlank {
            settings.adminEmail.substringAfter('@', "").ifBlank { null }
        }
        if (effectiveDomain.isNullOrBlank()) {
            addLog(
                LogLevel.ERROR,
                "Esportazione impossibile: specificare il dominio della scuola o l'email dell'amministratore (DWD)."
            )
            return
        }

        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isRunning = true
                progressStage = "Avvio esportazione ${type.title} (${format.name})..."
                progressCurrent = 0
                progressTotal = 100
                addLog(LogLevel.INFO, "Avvio esportazione '${type.title}' in formato ${format.name}...")
            }

            try {
                val credentialsFile = credResult.getOrThrow()
                val exporter = GoogleWorkspaceExporter(
                    credentialsFile = credentialsFile,
                    domain = effectiveDomain,
                    adminEmail = settings.adminEmail.ifBlank { null }
                )

                when (type) {
                    ExportType.CLASSE_EMAIL_CDC -> {
                        withContext(Dispatchers.Main) {
                            progressStage = "Recupero gruppi CdC da Google Workspace..."
                        }
                        val items = exporter.fetchClassGroups()
                        withContext(Dispatchers.Main) {
                            progressStage = "Scrittura file in corso..."
                        }
                        when (format) {
                            ExportFormat.CSV -> exporter.exportClassGroupsToCsv(targetFile, items)
                            ExportFormat.EXCEL -> exporter.exportClassGroupsToExcel(targetFile, items)
                        }
                        withContext(Dispatchers.Main) {
                            addLog(
                                LogLevel.SUCCESS,
                                "Esportazione '${type.title}' completata con successo: ${items.size} classi/CdC salvate in ${targetFile.name}."
                            )
                        }
                    }
                    ExportType.CLASSE_EMAIL_CDC_EMAIL_PARTECIPANTI -> {
                        withContext(Dispatchers.Main) {
                            progressStage = "Recupero gruppi e partecipanti da Google Workspace..."
                        }
                        val items = exporter.fetchClassGroupMembers { current, total, groupEmail ->
                            scope.launch(Dispatchers.Main) {
                                progressStage = "Lettura partecipanti gruppo $current/$total ($groupEmail)..."
                                progressCurrent = current
                                progressTotal = total
                            }
                        }
                        withContext(Dispatchers.Main) {
                            progressStage = "Scrittura file in corso..."
                        }
                        when (format) {
                            ExportFormat.CSV -> exporter.exportClassGroupMembersToCsv(targetFile, items)
                            ExportFormat.EXCEL -> exporter.exportClassGroupMembersToExcel(targetFile, items)
                        }
                        withContext(Dispatchers.Main) {
                            addLog(
                                LogLevel.SUCCESS,
                                "Esportazione '${type.title}' completata con successo: ${items.size} righe partecipanti salvate in ${targetFile.name}."
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addLog(
                        LogLevel.ERROR,
                        "Errore durante l'esportazione '${type.title}': ${e.message}"
                    )
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isRunning = false
                    progressStage = "Pronto"
                }
            }
        }
    }

    fun copyLogsToClipboard() {
        val text = logs.joinToString("\n") { "[${it.timestamp}] [${it.level}] ${it.message}" }
        val selection = StringSelection(text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
        addLog(LogLevel.INFO, "Log copiati negli appunti.")
    }

    fun saveLogsToFile(targetFile: File) {
        try {
            val text = logs.joinToString("\n") { "[${it.timestamp}] [${it.level}] ${it.message}" }
            targetFile.writeText(text, Charsets.UTF_8)
            addLog(LogLevel.SUCCESS, "Log salvati nel file: ${targetFile.absolutePath}")
        } catch (e: Exception) {
            addLog(LogLevel.ERROR, "Errore durante il salvataggio del log: ${e.message}")
        }
    }

    fun clearLogs() {
        logs.clear()
        addLog(LogLevel.INFO, "Console dei log cancellata.")
    }
}
