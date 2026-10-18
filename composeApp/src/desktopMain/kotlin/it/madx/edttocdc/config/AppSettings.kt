package it.madx.edttocdc.config

import kotlinx.serialization.Serializable

/**
 * Rappresenta la configurazione dell'applicazione EDT to CDC Sync.
 *
 * @property edtXmlUrl URL HTTP/HTTPS da cui scaricare il file XML generato da EDT.
 * @property googleCredentialsPath Percorso locale al file JSON delle credenziali Google Workspace.
 * @property schoolDomain Dominio istituzionale della scuola (es. "scuola.edu.it").
 * @property adminEmail Indirizzo email dell'amministratore per la delega a livello di dominio (DWD).
 * @property dryRunDefault Se true, avvia le sincronizzazioni in modalità simulazione per impostazione predefinita.
 */
@Serializable
data class AppSettings(
    val edtXmlUrl: String = "",
    val googleCredentialsPath: String = "",
    val schoolDomain: String = "",
    val adminEmail: String = "",
    val dryRunDefault: Boolean = false
) {
    /**
     * Verifica la presenza delle impostazioni obbligatorie.
     * Restituisce una lista di messaggi di errore; se vuota, la configurazione è valida.
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        if (edtXmlUrl.isBlank()) {
            errors.add("L'URL del file XML EDT non è specificato.")
        } else if (!edtXmlUrl.startsWith("http://", ignoreCase = true) &&
            !edtXmlUrl.startsWith("https://", ignoreCase = true)
        ) {
            errors.add("L'URL EDT deve iniziare con http:// o https://.")
        }

        if (googleCredentialsPath.isBlank()) {
            errors.add("Il percorso del file di credenziali Google non è specificato.")
        }

        if (schoolDomain.isNotBlank()) {
            if (!schoolDomain.contains(".") || schoolDomain.startsWith("@")) {
                errors.add("Il formato del dominio della scuola non è valido (es. istituto.edu.it).")
            }
        }

        if (adminEmail.isNotBlank()) {
            val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
            if (!emailRegex.matches(adminEmail.trim())) {
                errors.add("L'email dell'amministratore delegato non ha un formato valido.")
            }
        }

        return errors
    }
}
