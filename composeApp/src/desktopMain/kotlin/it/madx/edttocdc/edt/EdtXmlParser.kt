package it.madx.edttocdc.edt

import it.madx.edttocdc.model.ClassTeacherData
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parser sicuro e modulare per l'analisi del file XML esportato da EDT (Index Éducation).
 *
 * Implementa rigorose protezioni contro attacchi XXE (XML External Entity), disabilitando
 * l'inclusione di DTD, entità esterne e processamento di schemi remoti.
 *
 * Supporta sia il formato relazionale standard di EDT (con elenchi separati di docenti, classi e
 * associazioni/corsi) sia formati gerarchici diretti (classi contenenti direttamente i propri docenti).
 */
object EdtXmlParser {

    /**
     * Crea un DocumentBuilder configurato secondo le linee guida OWASP per la prevenzione di XXE.
     */
    private fun createSecureDocumentBuilder(): DocumentBuilderFactory {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.isValidating = false

        // Disabilita DTD e feature pericolose per la sicurezza
        runCatching { factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        factory.isXIncludeAware = false
        factory.isExpandEntityReferences = false

        return factory
    }

    /**
     * Analizza una stringa XML ed estrae le classi con i rispettivi insiemi di email dei docenti.
     *
     * @param xmlContent Il testo XML scaricato da EDT.
     * @return Elenco delle associazioni classe -> email docenti.
     * @throws IllegalArgumentException Se l'XML è vuoto, malformato o non contiene informazioni attese.
     */
    fun parseEdtXml(xmlContent: String): List<ClassTeacherData> {
        val trimmed = xmlContent.trim()
        if (trimmed.isBlank()) {
            throw IllegalArgumentException("Il contenuto del file XML EDT è vuoto.")
        }

        val document = try {
            val factory = createSecureDocumentBuilder()
            val builder = factory.newDocumentBuilder()
            builder.parse(InputSource(StringReader(trimmed)))
        } catch (e: Exception) {
            throw IllegalArgumentException("Impossibile analizzare il file XML: sintassi non valida (${e.message})", e)
        }

        val root = document.documentElement
            ?: throw IllegalArgumentException("Il documento XML non contiene un elemento radice.")

        return extractClassesAndTeachers(root)
    }

    /**
     * Analizza l'elemento radice cercando prima la struttura relazionale standard di EDT,
     * e successivamente provando la struttura gerarchica diretta.
     */
    fun extractClassesAndTeachers(root: Element): List<ClassTeacherData> {
        // Tentativo 1: Formato relazionale EDT (Tabelle separate di Docenti, Classi e Corsi/Attività)
        val relationalResult = parseRelationalFormat(root)
        if (relationalResult.isNotEmpty()) {
            return relationalResult
        }

        // Tentativo 2: Formato gerarchico (I tag Classe contengono direttamente i Docenti)
        val hierarchicalResult = parseHierarchicalFormat(root)
        if (hierarchicalResult.isNotEmpty()) {
            return hierarchicalResult
        }

        throw IllegalArgumentException(
            "Il file XML non contiene una struttura riconoscibile di classi e docenti EDT. " +
            "Verificare che l'esportazione EDT includa l'elenco delle classi e le associazioni con i docenti."
        )
    }

    /**
     * Formato relazionale standard EDT:
     * - `<DOCENTI>` o `<PROFESSEURS>` con id e email/courriel
     * - `<CLASSES>` con id e nome/nom
     * - `<COURS>` o `<SERVICES>` o `<LEZIONI>` che associano gli id
     */
    private fun parseRelationalFormat(root: Element): List<ClassTeacherData> {
        // 1. Mappa ID docente -> Email
        val teacherEmailById = mutableMapOf<String, String>()
        val teacherElements = findElementsByTagNames(
            root,
            listOf("DOCENTE", "DOCENTI", "PROFESSEUR", "PROFESSEURS", "TEACHER", "ENSEIGNANT")
        )

        for (el in teacherElements) {
            val id = getAttributeOrChild(el, listOf("Id", "id", "ID", "code", "Code")) ?: continue
            val email = getAttributeOrChild(el, listOf("Email", "email", "EMAIL", "Courriel", "courriel", "Mail", "mail"))
            if (!email.isNullOrBlank()) {
                val normalized = ClassGroupMapper.normalizeEmail(email)
                if (ClassGroupMapper.validateEmail(normalized)) {
                    teacherEmailById[id] = normalized
                }
            }
        }

        // 2. Mappa ID classe -> Nome classe
        val classNameById = mutableMapOf<String, String>()
        val classElements = findElementsByTagNames(
            root,
            listOf("CLASSE", "CLASSES", "CLASS")
        )

        for (el in classElements) {
            val id = getAttributeOrChild(el, listOf("Id", "id", "ID", "code", "Code")) ?: continue
            val explicitName = getAttributeOrChild(el, listOf("Nom", "nom", "Nome", "nome", "Name", "name", "Libelle", "libelle"))
            if (!explicitName.isNullOrBlank()) {
                val normalizedName = ClassGroupMapper.normalizeClassName(explicitName)
                if (normalizedName.isNotBlank()) {
                    classNameById[id] = normalizedName
                }
            } else if (!classNameById.containsKey(id)) {
                val normalizedName = ClassGroupMapper.normalizeClassName(id)
                if (normalizedName.isNotBlank()) {
                    classNameById[id] = normalizedName
                }
            }
        }

        if (teacherEmailById.isEmpty() || classNameById.isEmpty()) {
            return emptyList()
        }

        // 3. Associazioni (Corsi, Servizi, Lezioni, o relazioni dirette nei nodi classe/corso)
        val classToEmails = mutableMapOf<String, MutableSet<String>>()
        for (className in classNameById.values) {
            classToEmails[className] = mutableSetOf()
        }

        // Cerca gli elementi di associazione singoli (es. COUR, SERVICE, LEZIONE)
        // ed esclude i contenitori (COURS, SERVICES, LEZIONI) per non aggregare tutti i corsi insieme
        val rawRelationElements = findElementsByTagNames(
            root,
            listOf("COUR", "COURS", "SERVICE", "SERVICES", "LEZIONE", "LEZIONI", "ATTIVITA", "SEANCE", "LESSON")
        )

        val relationElements = rawRelationElements.filter { el ->
            val tag = el.tagName.uppercase()
            // Se è un contenitore che include sotto-elementi COUR o SERVICE, non elaborarlo come singola relazione
            val hasChildItems = el.getElementsByTagName("COUR").length > 0 ||
                               el.getElementsByTagName("SERVICE").length > 0 ||
                               el.getElementsByTagName("LEZIONE").length > 0
            !hasChildItems && tag != "COURS" && tag != "SERVICES" && tag != "LEZIONI"
        }

        for (rel in relationElements) {
            val classIds = findReferencedIds(rel, listOf("CLASSE", "CLASSES", "CLASS"), listOf("IdClasse", "ClasseId", "id_classe", "classe", "Classe"))
            val teacherIds = findReferencedIds(rel, listOf("DOCENTE", "DOCENTI", "PROFESSEUR", "TEACHER"), listOf("IdDocente", "DocenteId", "id_docente", "docente", "Docente", "professeur", "Professeur"))

            for (cId in classIds) {
                val className = classNameById[cId] ?: continue
                val emailSet = classToEmails.getOrPut(className) { mutableSetOf() }
                for (tId in teacherIds) {
                    val email = teacherEmailById[tId]
                    if (email != null) {
                        emailSet.add(email)
                    }
                }
            }
        }

        // Se non abbiamo trovato nodi di corsi separati, verifichiamo se le classi contengono riferimenti a docenti
        if (classToEmails.values.all { it.isEmpty() }) {
            for (classEl in classElements) {
                val classId = getAttributeOrChild(classEl, listOf("Id", "id", "ID")) ?: continue
                val className = classNameById[classId] ?: continue
                val teacherIds = findReferencedIds(classEl, listOf("DOCENTE", "PROFESSEUR"), listOf("IdDocente", "DocenteId"))
                for (tId in teacherIds) {
                    val email = teacherEmailById[tId]
                    if (email != null) {
                        classToEmails.getOrPut(className) { mutableSetOf() }.add(email)
                    }
                }
            }
        }

        return classToEmails.map { (className, emails) ->
            ClassTeacherData(className, emails)
        }.sortedBy { it.className }
    }

    /**
     * Formato gerarchico: nodi `<CLASSE>` che contengono nodi docenti direttamente.
     */
    private fun parseHierarchicalFormat(root: Element): List<ClassTeacherData> {
        val result = mutableListOf<ClassTeacherData>()
        val classNodes = findElementsByTagNames(root, listOf("CLASSE", "CLASS"))

        for (classEl in classNodes) {
            val rawName = getAttributeOrChild(classEl, listOf("Nom", "nom", "Nome", "nome", "Name", "name", "Id", "id")) ?: continue
            val className = ClassGroupMapper.normalizeClassName(rawName)
            if (className.isBlank()) continue

            val emails = mutableSetOf<String>()
            val teacherNodes = findElementsByTagNames(classEl, listOf("DOCENTE", "PROFESSEUR", "TEACHER", "INSEGNANTE"))
            for (t in teacherNodes) {
                val email = getAttributeOrChild(t, listOf("Email", "email", "EMAIL", "Courriel", "courriel", "Mail", "mail"))
                    ?: t.textContent?.trim()
                if (!email.isNullOrBlank()) {
                    val normalized = ClassGroupMapper.normalizeEmail(email)
                    if (ClassGroupMapper.validateEmail(normalized)) {
                        emails.add(normalized)
                    }
                }
            }

            if (emails.isNotEmpty() || classNodes.size == 1) {
                result.add(ClassTeacherData(className, emails))
            }
        }

        return result.sortedBy { it.className }
    }

    private fun findReferencedIds(container: Element, tagNames: List<String>, attributeNames: List<String>): Set<String> {
        val ids = mutableSetOf<String>()

        // 1. Controlla attributi dell'elemento stesso
        for (attr in attributeNames) {
            val v = container.getAttribute(attr)
            if (!v.isNullOrBlank()) ids.add(v.trim())
        }

        // 2. Controlla sotto-elementi
        for (tag in tagNames) {
            val children = container.getElementsByTagName(tag)
            for (i in 0 until children.length) {
                val el = children.item(i) as? Element ?: continue
                val id = getAttributeOrChild(el, listOf("Id", "id", "ID", "code", "Code"))
                if (!id.isNullOrBlank()) {
                    ids.add(id.trim())
                }
            }
        }

        return ids
    }

    private fun findElementsByTagNames(root: Element, tags: List<String>): List<Element> {
        val list = mutableListOf<Element>()
        for (tag in tags) {
            // Cerca sia con nome esatto che case-insensitive
            val nodes = root.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val item = nodes.item(i)
                if (item is Element) {
                    list.add(item)
                }
            }
            val nodesLower = root.getElementsByTagName(tag.lowercase())
            for (i in 0 until nodesLower.length) {
                val item = nodesLower.item(i)
                if (item is Element && !list.contains(item)) {
                    list.add(item)
                }
            }
        }
        return list
    }

    private fun getAttributeOrChild(el: Element, names: List<String>): String? {
        for (name in names) {
            if (el.hasAttribute(name)) {
                val v = el.getAttribute(name).trim()
                if (v.isNotEmpty()) return v
            }
        }
        for (name in names) {
            val children = el.getElementsByTagName(name)
            if (children.length > 0) {
                val txt = children.item(0).textContent?.trim()
                if (!txt.isNullOrEmpty()) return txt
            }
        }
        return null
    }
}
