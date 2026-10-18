package it.madx.edttocdc.model

import kotlinx.serialization.Serializable

/**
 * Rappresenta i dati grezzi estratti dal file XML di EDT per una classe.
 *
 * @property className Nome o identificatore della classe (es. "1A", "3BS").
 * @property teacherEmails Insieme degli indirizzi email istituzionali associati ai docenti della classe.
 */
@Serializable
data class ClassTeacherData(
    val className: String,
    val teacherEmails: Set<String>
)

/**
 * Differenza calcolata tra i membri attuali di un gruppo e quelli desiderati.
 *
 * @property toAdd Membri da aggiungere al gruppo Google Workspace.
 * @property toRemove Membri da rimuovere dal gruppo Google Workspace.
 * @property unchanged Membri già presenti che rimangono invariati.
 */
data class MembershipDiff(
    val toAdd: Set<String>,
    val toRemove: Set<String>,
    val unchanged: Set<String>
) {
    val hasChanges: Boolean get() = toAdd.isNotEmpty() || toRemove.isNotEmpty()
}

/**
 * Risultato della sincronizzazione per un singolo gruppo Consigli di Classe.
 *
 * @property groupEmail Indirizzo email del gruppo (es. cdc_1a@scuola.edu.it).
 * @property className Nome normalizzato della classe.
 * @property created Indica se il gruppo è stato creato durante questa operazione.
 * @property addedMembers Membri aggiunti al gruppo.
 * @property removedMembers Membri rimossi dal gruppo.
 * @property errors Eventuali errori riscontrati durante la sincronizzazione.
 * @property success Esito dell'operazione sul gruppo.
 */
data class GroupSyncResult(
    val groupEmail: String,
    val className: String,
    val created: Boolean,
    val addedMembers: List<String> = emptyList(),
    val removedMembers: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val success: Boolean = errors.isEmpty()
)

/**
 * Riepilogo finale complessivo del processo di sincronizzazione.
 *
 * @property totalClasses Numero di classi individuate nell'XML EDT.
 * @property existingGroups Numero di gruppi già esistenti in Google Workspace.
 * @property createdGroups Numero di gruppi creati.
 * @property addedTeachersCount Numero totale di docenti aggiunti ai gruppi.
 * @property removedTeachersCount Numero totale di docenti rimossi dai gruppi.
 * @property successfullySyncedGroups Numero di gruppi sincronizzati con successo.
 * @property failedGroups Numero di gruppi con errori o sincronizzazione incompleta.
 * @property anomalies Eventuali anomalie riscontrate durante la validazione dell'XML o dei gruppi.
 * @property isDryRun Indica se l'operazione è stata eseguita in modalità simulazione.
 * @property success Esito globale dell'operazione.
 */
data class SyncSummary(
    val totalClasses: Int,
    val existingGroups: Int,
    val createdGroups: Int,
    val addedTeachersCount: Int,
    val removedTeachersCount: Int,
    val successfullySyncedGroups: Int,
    val failedGroups: Int,
    val anomalies: List<String> = emptyList(),
    val isDryRun: Boolean = false,
    val success: Boolean = failedGroups == 0 && anomalies.isEmpty()
)

/**
 * Informazioni di connessione Google Workspace.
 *
 * @property authenticatedEmail Email dell'account o service account autenticato.
 * @property domain Dominio gestito.
 * @property directoryAccessConferred Conferma che l'accesso alle Directory API è attivo.
 */
data class GoogleConnectionInfo(
    val authenticatedEmail: String,
    val domain: String,
    val directoryAccessConferred: Boolean
)

/**
 * Livello di severità dei log.
 */
enum class LogLevel {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
    DRY_RUN
}

/**
 * Singola riga di log con marcatura oraria e livello.
 */
data class LogEntry(
    val timestamp: String,
    val level: LogLevel,
    val message: String
)
