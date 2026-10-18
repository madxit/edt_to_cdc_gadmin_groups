package it.madx.edttocdc.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import it.madx.edttocdc.config.AppSettings
import it.madx.edttocdc.config.SettingsManager
import it.madx.edttocdc.edt.EdtXmlDownloader
import it.madx.edttocdc.google.GoogleWorkspaceClient
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

    var progressStage by mutableStateOf("Pronto")
        private set

    var progressCurrent by mutableStateOf(0)
        private set

    var progressTotal by mutableStateOf(0)
        private set

    val logs = mutableStateListOf<LogEntry>()

    var showEdtHelp by mutableStateOf(false)
    var showGoogleHelp by mutableStateOf(false)
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
                    addLog(
                        LogLevel.SUCCESS,
                        "Verifica EDT riuscita: XML scaricato (${info.sizeBytes} byte) ed estratte ${info.detectedClassesCount} classi."
                    )
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

            val credentialsFile = credResult.getOrThrow()
            val result = runCatching {
                val client = GoogleWorkspaceClient(
                    credentialsFile = credentialsFile,
                    domain = settings.schoolDomain,
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

    fun startSync(runDryRun: Boolean) {
        if (isRunning) return

        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isRunning = true
                progressStage = "Avvio sincronizzazione..."
                progressCurrent = 0
                progressTotal = 100
            }

            val summary = SyncEngine.runSync(
                settings = settings,
                dryRun = runDryRun,
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
