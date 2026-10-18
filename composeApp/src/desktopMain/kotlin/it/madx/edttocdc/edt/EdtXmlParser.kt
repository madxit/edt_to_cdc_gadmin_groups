package it.madx.edttocdc.edt

import it.madx.edttocdc.model.ClassTeacherData
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parser per i file XML esportati da EDT (Index Éducation - ExportIndexPartenaire).
 *
 * Mappa fedelmente la struttura relazionale di EDT:
 * 1. Docenti: `<Professeurs>` -> `<Professeur Ident="..." EMail="..."/>`
 * 2. Classi: `<Classes>` -> `<Classe Ident="..." Nom="...">`
 *    - Eventuale coordinatore: `<ProfesseurPrincipal Ident="..."/>`
 *    - Eventuali partizioni: `<PartieDeClasse Ident="..."/>`
 * 3. Gruppi: `<Groupes>` -> `<Groupe Ident="...">`
 *    - Riferimento partizioni: `<PartieDeClasse Ident="..."/>`
 * 4. Lezioni: `<Cours>` -> `<Cours>`
 *    - Docenti: `<Professeur Ident="..."/>`
 *    - Classi / Gruppi: `<Classe Ident="..."/>`, `<Groupe Ident="..."/>`, `<PartieDeClasse Ident="..."/>`
 *
 * Include protezione XXE (XML External Entity) secondo le raccomandazioni OWASP.
 */
object EdtXmlParser {

    private fun createSecureDocumentBuilder(): DocumentBuilderFactory {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
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
     * Analizza una stringa XML ed estrae le classi con l'elenco delle email dei docenti associati.
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
     * Estrae le classi e i rispettivi docenti a partire dall'elemento radice XML.
     */
    fun extractClassesAndTeachers(root: Element): List<ClassTeacherData> {
        // 1. Mappa Docenti: ID -> Email
        val teacherEmailById = mutableMapOf<String, String>()
        val profList = root.getElementsByTagName("Professeur")
        for (i in 0 until profList.length) {
            val prof = profList.item(i) as? Element ?: continue
            val id = prof.getAttribute("Ident")
            val email = prof.getAttribute("EMail")
            if (id.isNotBlank() && email.isNotBlank()) {
                val normalized = ClassGroupMapper.normalizeEmail(email)
                if (ClassGroupMapper.validateEmail(normalized)) {
                    teacherEmailById[id] = normalized
                }
            }
        }

        // 2. Mappa Classi: ID -> Nome Classe, PartieDeClasse ID -> Class ID, inizializzazione insiemi email
        val classNameById = mutableMapOf<String, String>()
        val classIdByPartie = mutableMapOf<String, String>()
        val classToEmails = mutableMapOf<String, MutableSet<String>>()

        val classList = root.getElementsByTagName("Classe")
        for (i in 0 until classList.length) {
            val classEl = classList.item(i) as? Element ?: continue
            val id = classEl.getAttribute("Ident")
            val rawName = classEl.getAttribute("Nom")

            // Solo le definizioni di classe hanno l'attributo Nom (all'interno di <Cours> hanno solo Ident)
            if (id.isNotBlank() && rawName.isNotBlank()) {
                val className = ClassGroupMapper.normalizeClassName(rawName)
                if (className.isNotBlank()) {
                    classNameById[id] = className
                    val emails = classToEmails.getOrPut(className) { mutableSetOf() }

                    // Coordinatore di classe (<ProfesseurPrincipal Ident="..."/>)
                    for (principal in classEl.childElements("ProfesseurPrincipal")) {
                        val pId = principal.getAttribute("Ident")
                        teacherEmailById[pId]?.let { emails.add(it) }
                    }

                    // Partizioni associate (<PartieDeClasse Ident="..."/>)
                    for (partie in classEl.childElements("PartieDeClasse")) {
                        val pId = partie.getAttribute("Ident")
                        if (pId.isNotBlank()) {
                            classIdByPartie[pId] = id
                        }
                    }
                }
            }
        }

        if (classNameById.isEmpty()) {
            throw IllegalArgumentException(
                "Il file XML non contiene classi EDT valide. Verificare la presenza del tag <Classes> e dei relativi elementi <Classe>."
            )
        }

        // 3. Mappa Gruppi: ID Gruppo -> Set di ID Classe (tramite PartieDeClasse)
        val classIdsByGroupe = mutableMapOf<String, MutableSet<String>>()
        val groupList = root.getElementsByTagName("Groupe")
        for (i in 0 until groupList.length) {
            val grp = groupList.item(i) as? Element ?: continue
            val gId = grp.getAttribute("Ident")
            if (gId.isBlank()) continue

            val targetClasses = classIdsByGroupe.getOrPut(gId) { mutableSetOf() }
            for (partie in grp.childElements("PartieDeClasse")) {
                val pId = partie.getAttribute("Ident")
                classIdByPartie[pId]?.let { targetClasses.add(it) }
            }
        }

        // 4. Mappatura Lezioni (<Cours>): associa docenti alle classi coinvolte
        val coursList = root.getElementsByTagName("Cours")
        for (i in 0 until coursList.length) {
            val lesson = coursList.item(i) as? Element ?: continue

            // Esclude il contenitore principale <Cours> (processa solo nodi lezione foglia)
            if (lesson.hasChildElement("Cours")) continue

            // Docenti della lezione (<Professeur Ident="..."/>)
            val teacherEmails = lesson.childElements("Professeur")
                .mapNotNull { teacherEmailById[it.getAttribute("Ident")] }
            if (teacherEmails.isEmpty()) continue

            // Classi associate alla lezione
            val targetClassIds = mutableSetOf<String>()

            // Associazione diretta classe (<Classe Ident="..."/>)
            for (cl in lesson.childElements("Classe")) {
                val cId = cl.getAttribute("Ident")
                if (cId.isNotBlank()) targetClassIds.add(cId)
            }

            // Associazione tramite gruppo (<Groupe Ident="..."/>)
            for (gr in lesson.childElements("Groupe")) {
                val gId = gr.getAttribute("Ident")
                classIdsByGroupe[gId]?.let { targetClassIds.addAll(it) }
            }

            // Associazione diretta partizione (<PartieDeClasse Ident="..."/>)
            for (pt in lesson.childElements("PartieDeClasse")) {
                val pId = pt.getAttribute("Ident")
                classIdByPartie[pId]?.let { targetClassIds.add(it) }
            }

            // Aggiunge le email alle classi corrispondenti
            for (cId in targetClassIds) {
                val className = classNameById[cId] ?: continue
                classToEmails[className]?.addAll(teacherEmails)
            }
        }

        return classToEmails.map { (className, emails) ->
            ClassTeacherData(className, emails)
        }.sortedBy { it.className }
    }

    private fun Element.childElements(tagName: String): List<Element> {
        val result = mutableListOf<Element>()
        val children = childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && (node.localName ?: node.tagName) == tagName) {
                result.add(node)
            }
        }
        return result
    }

    private fun Element.hasChildElement(tagName: String): Boolean {
        val children = childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && (node.localName ?: node.tagName) == tagName) {
                return true
            }
        }
        return false
    }
}
