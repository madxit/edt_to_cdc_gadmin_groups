package it.madx.edttocdc.edt

import it.madx.edttocdc.model.ClassTeacherData
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parser sicuro e modulare per l'analisi del file XML esportato da EDT (Index Éducation).
 *
 * Supporta la specifica ufficiale Index Éducation (ExportIndexPartenaire V3.3) con:
 * - Nodi `<Professeurs>` / `<Professeur Ident="..." EMail="..." />`
 * - Nodi `<Classes>` / `<Classe Ident="..." Nom="..." />`
 * - Nodi `<PartiesDeClasses>` e `<Groupes>` (per classi sdoppiate e gruppi opzionali)
 * - Nodi `<Cours>` / `<Cours>` con associazioni a Docenti e Classi
 * - Nodi coordinatore `<ProfesseurPrincipal Ident="..." />`
 *
 * Implementa le direttive OWASP per la prevenzione di vulnerabilità XXE (disabilitazione DTD,
 * entità esterne ed espansione di schemi remoti).
 */
object EdtXmlParser {

    private fun createSecureDocumentBuilder(): DocumentBuilderFactory {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.isValidating = false

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
     * Analizza l'elemento radice cercando la struttura relazionale standard EDT (Index Éducation)
     * o strutture gerarchiche dirette.
     */
    fun extractClassesAndTeachers(root: Element): List<ClassTeacherData> {
        val relationalResult = parseRelationalFormat(root)
        if (relationalResult.isNotEmpty()) {
            return relationalResult
        }

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
     * Formato relazionale standard EDT (Index Éducation / ExportIndexPartenaire):
     * - `<Professeurs>` / `<Docenti>`
     * - `<Classes>`
     * - `<PartiesDeClasses>` e `<Groupes>`
     * - `<Cours>`
     */
    private fun parseRelationalFormat(root: Element): List<ClassTeacherData> {
        // 1. Mappa ID docente -> Email
        val teacherEmailById = mutableMapOf<String, String>()
        val teacherElements = findElementsByTagNames(
            root,
            listOf("PROFESSEUR", "PROFESSEURS", "DOCENTE", "DOCENTI", "TEACHER", "ENSEIGNANT")
        )

        for (el in teacherElements) {
            val id = getAttributeOrChild(el, listOf("Ident", "ident", "Id", "id", "ID", "code", "Code")) ?: continue
            val email = getAttributeOrChild(el, listOf("EMail", "email", "Email", "EMAIL", "Courriel", "courriel", "Mail", "mail"))
            if (!email.isNullOrBlank()) {
                val normalized = ClassGroupMapper.normalizeEmail(email)
                if (ClassGroupMapper.validateEmail(normalized)) {
                    teacherEmailById[id] = normalized
                }
            }
        }

        // 2. Mappa ID classe -> Nome classe
        val classNameById = mutableMapOf<String, String>()
        val classIdByPartieDeClasse = mutableMapOf<String, String>()
        val principalTeachersByClass = mutableMapOf<String, MutableSet<String>>()

        val classElements = findElementsByTagNames(
            root,
            listOf("CLASSE", "CLASSES", "CLASS")
        )

        for (el in classElements) {
            val id = getAttributeOrChild(el, listOf("Ident", "ident", "Id", "id", "ID", "code", "Code")) ?: continue
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

            // Associazioni PartiesDeClasses interne al nodo Classe
            val parties = findElementsByTagNames(el, listOf("PARTIEDECLASSE", "PARTIE_DE_CLASSE"))
            for (p in parties) {
                val pId = getAttributeOrChild(p, listOf("Ident", "ident", "Id", "id"))
                if (!pId.isNullOrBlank()) {
                    classIdByPartieDeClasse[pId] = id
                }
            }

            // Eventuale ProfesseurPrincipal (Coordinatore di classe)
            val principals = findElementsByTagNames(el, listOf("PROFESSEURPRINCIPAL", "COORDINATORE"))
            for (pr in principals) {
                val prId = getAttributeOrChild(pr, listOf("Ident", "ident", "Id", "id"))
                if (!prId.isNullOrBlank()) {
                    principalTeachersByClass.getOrPut(id) { mutableSetOf() }.add(prId)
                }
            }
        }

        if (classNameById.isEmpty() || teacherEmailById.isEmpty()) {
            return emptyList()
        }

        // 3. Mappa Groupes -> Class IDs
        val classIdsByGroupe = mutableMapOf<String, MutableSet<String>>()
        val groupeElements = findElementsByTagNames(root, listOf("GROUPE", "GROUPES", "GROUP"))
        for (gr in groupeElements) {
            val gId = getAttributeOrChild(gr, listOf("Ident", "ident", "Id", "id")) ?: continue
            val mappedClasses = classIdsByGroupe.getOrPut(gId) { mutableSetOf() }

            // Tramite PartieDeClasse
            val parties = findElementsByTagNames(gr, listOf("PARTIEDECLASSE", "PARTIE_DE_CLASSE"))
            for (p in parties) {
                val pId = getAttributeOrChild(p, listOf("Ident", "ident", "Id", "id"))
                val cId = classIdByPartieDeClasse[pId]
                if (cId != null) mappedClasses.add(cId)
            }

            // Tramite Classe diretta
            val clNodes = findElementsByTagNames(gr, listOf("CLASSE", "CLASS"))
            for (cl in clNodes) {
                if (cl != gr) {
                    val cId = getAttributeOrChild(cl, listOf("Ident", "ident", "Id", "id"))
                    if (!cId.isNullOrBlank()) mappedClasses.add(cId)
                }
            }
        }

        // 4. Inizializzazione mappa Classe -> Insieme email
        val classToEmails = mutableMapOf<String, MutableSet<String>>()
        for ((cId, className) in classNameById) {
            val emailSet = classToEmails.getOrPut(className) { mutableSetOf() }
            // Aggiungi eventuale coordinatore di classe
            val principals = principalTeachersByClass[cId] ?: emptySet()
            for (pId in principals) {
                val email = teacherEmailById[pId]
                if (email != null) emailSet.add(email)
            }
        }

        // 5. Associazioni Cours / Lezioni
        val rawRelationElements = findElementsByTagNames(
            root,
            listOf("COUR", "COURS", "SERVICE", "SERVICES", "LEZIONE", "LEZIONI", "ATTIVITA", "SEANCE", "LESSON")
        )

        // Filtra unicamente gli elementi foglia di lezione (escludendo i contenitori padri)
        val lessonItems = rawRelationElements.filter { el ->
            val childLessons = findElementsByTagNames(
                el,
                listOf("COUR", "COURS", "SERVICE", "SERVICES", "LEZIONE", "SEANCE", "LESSON")
            ).filter { it != el }
            childLessons.isEmpty()
        }

        for (lesson in lessonItems) {
            // Estrazione classi della lezione
            val classIds = mutableSetOf<String>()
            val directClasses = findReferencedIds(
                lesson,
                listOf("CLASSE", "CLASS"),
                listOf("IdClasse", "ClasseId", "id_classe", "classe", "Classe")
            )
            classIds.addAll(directClasses)

            // Estrazione da eventuali gruppi associati alla lezione
            val groupIds = findReferencedIds(
                lesson,
                listOf("GROUPE", "GROUP"),
                listOf("IdGroupe", "GroupeId", "groupe", "Groupe")
            )
            for (gId in groupIds) {
                val cIds = classIdsByGroupe[gId]
                if (cIds != null) classIds.addAll(cIds)
            }

            // Estrazione da eventuali parti di classi associate alla lezione
            val partieIds = findReferencedIds(
                lesson,
                listOf("PARTIEDECLASSE", "PARTIE_DE_CLASSE"),
                listOf("IdPartie", "PartieId")
            )
            for (pId in partieIds) {
                val cId = classIdByPartieDeClasse[pId]
                if (cId != null) classIds.add(cId)
            }

            // Estrazione docenti della lezione
            val teacherIds = findReferencedIds(
                lesson,
                listOf("PROFESSEUR", "DOCENTE", "DOCENTI", "TEACHER"),
                listOf("IdDocente", "DocenteId", "id_docente", "docente", "Docente", "professeur", "Professeur")
            )

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

        // Se le lezioni non hanno prodotto associazioni, controlla riferimenti diretti nelle classi
        if (classToEmails.values.all { it.isEmpty() }) {
            for (classEl in classElements) {
                val classId = getAttributeOrChild(classEl, listOf("Ident", "Id", "id", "ID")) ?: continue
                val className = classNameById[classId] ?: continue
                val teacherIds = findReferencedIds(classEl, listOf("PROFESSEUR", "DOCENTE"), listOf("IdDocente", "DocenteId"))
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
     * Formato gerarchico semplice: nodi `<CLASSE>` contenenti direttamente nodi docenti.
     */
    private fun parseHierarchicalFormat(root: Element): List<ClassTeacherData> {
        val result = mutableListOf<ClassTeacherData>()
        val classNodes = findElementsByTagNames(root, listOf("CLASSE", "CLASS"))

        for (classEl in classNodes) {
            val rawName = getAttributeOrChild(classEl, listOf("Nom", "nom", "Nome", "nome", "Name", "name", "Ident", "Id", "id")) ?: continue
            val className = ClassGroupMapper.normalizeClassName(rawName)
            if (className.isBlank()) continue

            val emails = mutableSetOf<String>()
            val teacherNodes = findElementsByTagNames(classEl, listOf("PROFESSEUR", "DOCENTE", "TEACHER", "INSEGNANTE"))
            for (t in teacherNodes) {
                val email = getAttributeOrChild(t, listOf("EMail", "email", "Email", "EMAIL", "Courriel", "courriel", "Mail", "mail"))
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

        for (attr in attributeNames) {
            val v = getAttributeCaseInsensitive(container, attr)
            if (!v.isNullOrBlank()) ids.add(v.trim())
        }

        for (tag in tagNames) {
            val children = findElementsByTagNames(container, listOf(tag))
            for (el in children) {
                if (el != container) {
                    val id = getAttributeOrChild(el, listOf("Ident", "ident", "Id", "id", "ID", "code", "Code"))
                    if (!id.isNullOrBlank()) {
                        ids.add(id.trim())
                    }
                }
            }
        }

        return ids
    }

    private fun findElementsByTagNames(root: Element, tags: List<String>): List<Element> {
        val list = mutableListOf<Element>()
        val upperTags = tags.map { it.uppercase() }.toSet()

        val rootName = (root.localName ?: root.tagName).uppercase()
        if (upperTags.contains(rootName)) {
            list.add(root)
        }

        val allElements = root.getElementsByTagName("*")
        for (i in 0 until allElements.length) {
            val item = allElements.item(i)
            if (item is Element) {
                val local = (item.localName ?: item.tagName).uppercase()
                if (upperTags.contains(local)) {
                    list.add(item)
                }
            }
        }
        return list
    }

    private fun getAttributeOrChild(el: Element, names: List<String>): String? {
        for (name in names) {
            val v = getAttributeCaseInsensitive(el, name)
            if (!v.isNullOrBlank()) return v
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

    private fun getAttributeCaseInsensitive(el: Element, name: String): String? {
        if (el.hasAttribute(name)) {
            val v = el.getAttribute(name).trim()
            if (v.isNotEmpty()) return v
        }
        val attrs = el.attributes ?: return null
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            val attrName = attr.localName ?: attr.nodeName
            if (attrName.equals(name, ignoreCase = true)) {
                val v = attr.nodeValue?.trim() ?: ""
                if (v.isNotEmpty()) return v
            }
        }
        return null
    }
}
