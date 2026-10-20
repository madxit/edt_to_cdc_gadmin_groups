package it.madx.edttocdc.config

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Gestore per la persistenza e validazione delle impostazioni locali dell'applicazione.
 *
 * Salva i parametri non sensibili in una cartella standard nella home dell'utente
 * (~/.edttocdc/settings.json), compatibile sia con macOS che con Windows.
 * Non memorizza password né chiavi private: memorizza unicamente il percorso del file di credenziali.
 */
object SettingsManager {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val configDir: File by lazy {
        val userHome = System.getProperty("user.home") ?: "."
        val dir = File(userHome, ".edttocdc")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    private val configFile: File by lazy {
        File(configDir, "settings.json")
    }

    /**
     * Carica le impostazioni salvate su disco.
     * Se il file non esiste o è corrotto, restituisce le impostazioni predefinite.
     */
    fun loadSettings(): AppSettings {
        return try {
            if (configFile.exists() && configFile.canRead()) {
                val content = configFile.readText(Charsets.UTF_8)
                json.decodeFromString<AppSettings>(content)
            } else {
                AppSettings()
            }
        } catch (e: Exception) {
            AppSettings()
        }
    }

    /**
     * Salva le impostazioni fornite nel file JSON locale.
     */
    fun saveSettings(settings: AppSettings): Result<Unit> {
        return runCatching {
            val content = json.encodeToString(settings)
            configFile.writeText(content, Charsets.UTF_8)
        }
    }

    /**
     * Verifica la presenza e la correttezza del file di credenziali Google.
     * Non espone segreti o chiavi private nei messaggi di errore.
     */
    fun getCredentialsFile(path: String): Result<File> {
        if (path.isBlank()) {
            return Result.failure(IllegalArgumentException("Nessun file di credenziali specificato."))
        }
        val file = File(path.trim())
        if (!file.exists()) {
            return Result.failure(java.io.FileNotFoundException("Il file di credenziali selezionato non esiste: ${file.name}"))
        }
        if (!file.isFile) {
            return Result.failure(IllegalArgumentException("Il percorso selezionato non corrisponde a un file valido: ${file.name}"))
        }
        if (!file.canRead()) {
            return Result.failure(SecurityException("Permessi insufficienti per leggere il file: ${file.name}"))
        }
        if (!file.name.endsWith(".json", ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("Il file delle credenziali deve avere estensione .json"))
        }
        return Result.success(file)
    }

    /**
     * Convalida le impostazioni e lo stato del file di credenziali.
     */
    fun validateSettings(settings: AppSettings): List<String> {
        val errors = settings.validate().toMutableList()
        if (settings.googleCredentialsPath.isNotBlank()) {
            val credResult = getCredentialsFile(settings.googleCredentialsPath)
            if (credResult.isFailure) {
                errors.add(credResult.exceptionOrNull()?.message ?: "File di credenziali non valido.")
            }
        }
        return errors
    }
}
