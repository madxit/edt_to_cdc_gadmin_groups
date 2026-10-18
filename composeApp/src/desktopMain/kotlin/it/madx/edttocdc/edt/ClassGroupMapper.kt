package it.madx.edttocdc.edt

import it.madx.edttocdc.model.ClassTeacherData
import java.util.regex.Pattern

/**
 * Modulo responsabile della normalizzazione dei nomi di classe, della validazione degli indirizzi email
 * e della trasformazione in indirizzi dei gruppi Google Workspace nel formato cdc_{classe}@dominioscuola.xyz.
 */
object ClassGroupMapper {

    // Regex per la validazione sintattica degli indirizzi email istituzionali (RFC 5322 semplificata)
    private val EMAIL_REGEX = Pattern.compile(
        "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    )

    // Regex per verificare che il nome della classe sia sicuro per l'uso nel local-part di un'email Google
    private val CLASS_NAME_SAFE_REGEX = Pattern.compile("^[a-zA-Z0-9_-]+$")

    /**
     * Normalizza il nome della classe:
     * - Rimuove spazi superflui interni ed esterni (es. " 1 A " -> "1A")
     * - Converte caratteri speciali incompatibili (es. accentate, barre)
     * - Converte in minuscolo per uniformità nell'indirizzo email
     */
    fun normalizeClassName(rawName: String): String {
        val trimmed = rawName.trim().replace("\\s+".toRegex(), "")
        return trimmed
            .replace("à", "a")
            .replace("è", "e")
            .replace("é", "e")
            .replace("ì", "i")
            .replace("ò", "o")
            .replace("ù", "u")
            .replace("/", "_")
            .replace("-", "_")
            .trim('_')
            .lowercase()
    }

    /**
     * Normalizza un indirizzo email: rimuove spazi bianchi esterni e converte in minuscolo.
     */
    fun normalizeEmail(email: String): String {
        return email.trim().lowercase()
    }

    /**
     * Valida la correttezza formale di un indirizzo email.
     */
    fun validateEmail(email: String): Boolean {
        val normalized = normalizeEmail(email)
        return normalized.isNotEmpty() && EMAIL_REGEX.matcher(normalized).matches()
    }

    /**
     * Costruisce la mappa Classe -> Insieme ordinato di email docenti deduplicate.
     */
    fun buildClassEmailMap(classTeacherData: List<ClassTeacherData>): Map<String, Set<String>> {
        val map = sortedMapOf<String, MutableSet<String>>()

        for (item in classTeacherData) {
            val normalizedClass = normalizeClassName(item.className)
            if (normalizedClass.isBlank()) continue

            val set = map.getOrPut(normalizedClass) { mutableSetOf() }
            for (rawEmail in item.teacherEmails) {
                val email = normalizeEmail(rawEmail)
                if (validateEmail(email)) {
                    set.add(email)
                }
            }
        }

        return map.mapValues { it.value.toSet() }
    }

    /**
     * Converte la mappa delle classi nella mappa finale dei gruppi Google Workspace:
     * Indirizzo del gruppo: cdc_{nome_classe}@{dominio}
     */
    fun buildGroupEmailMap(
        classEmailMap: Map<String, Set<String>>,
        domain: String
    ): Map<String, Set<String>> {
        val cleanDomain = domain.trim().trimStart('@').lowercase()
        val groupMap = sortedMapOf<String, Set<String>>()

        for ((className, emails) in classEmailMap) {
            val groupEmail = "cdc_${className}@${cleanDomain}"
            groupMap[groupEmail] = emails
        }

        return groupMap
    }

    /**
     * Verifica che l'indirizzo del gruppo rispetti tassativamente il pattern autorizzato:
     * cdc_{nome_classe}@{dominio}
     */
    fun validateGroupEmail(groupEmail: String, expectedDomain: String): Boolean {
        val cleanDomain = expectedDomain.trim().trimStart('@').lowercase()
        val normalizedGroup = normalizeEmail(groupEmail)

        val parts = normalizedGroup.split("@")
        if (parts.size != 2) return false

        val localPart = parts[0]
        val domainPart = parts[1]

        if (domainPart != cleanDomain) return false
        if (!localPart.startsWith("cdc_")) return false

        val classNamePart = localPart.removePrefix("cdc_")
        if (classNamePart.isBlank()) return false

        return CLASS_NAME_SAFE_REGEX.matcher(classNamePart).matches()
    }

    /**
     * Effettua una scansione dell'intera mappa dei gruppi prima della sincronizzazione,
     * segnalando qualsiasi anomalia (gruppi con formato invalido, classi senza docenti, ecc.).
     */
    fun validateGroupEmailMap(
        groupEmailMap: Map<String, Set<String>>,
        expectedDomain: String
    ): List<String> {
        val anomalies = mutableListOf<String>()

        if (groupEmailMap.isEmpty()) {
            anomalies.add("Nessun gruppo generato: l'elenco delle classi è vuoto.")
            return anomalies
        }

        for ((groupEmail, members) in groupEmailMap) {
            if (!validateGroupEmail(groupEmail, expectedDomain)) {
                anomalies.add("L'indirizzo del gruppo '$groupEmail' non rispetta il formato autorizzato cdc_{classe}@$expectedDomain")
            }
            if (members.isEmpty()) {
                anomalies.add("Avviso: il gruppo '$groupEmail' non ha alcun docente associato nell'XML.")
            }
            for (member in members) {
                if (!validateEmail(member)) {
                    anomalies.add("Indirizzo docente non valido nel gruppo '$groupEmail': $member")
                }
            }
        }

        return anomalies
    }
}
