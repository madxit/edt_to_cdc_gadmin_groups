package it.madx.edttocdc

import it.madx.edttocdc.edt.EdtXmlParser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EdtXmlParserTest {

    @Test
    fun testParseAnonymizedEdtSampleRelationalXmlFile() {
        val stream = javaClass.classLoader.getResourceAsStream("samples/edt_sample_relational.xml")
            ?: File("composeApp/src/desktopMain/resources/samples/edt_sample_relational.xml").takeIf { it.exists() }?.inputStream()
            ?: File("src/desktopMain/resources/samples/edt_sample_relational.xml").inputStream()

        val xmlContent = stream.bufferedReader().use { it.readText() }
        val result = EdtXmlParser.parseEdtXml(xmlContent)

        // Verifichiamo che tutte le 5 classi siano estratte
        assertEquals(5, result.size)
        val classNames = result.map { it.className }.toSet()
        assertEquals(setOf("1a", "1b", "2a", "2b", "3a"), classNames)

        // Classe 1A: include i docenti delle materie più il coordinatore (mario.rossi)
        val class1A = result.first { it.className == "1a" }
        assertTrue(class1A.teacherEmails.contains("mario.rossi@scuola.edu.it"))
        assertTrue(class1A.teacherEmails.contains("luigi.bianchi@scuola.edu.it"))
        assertTrue(class1A.teacherEmails.contains("anna.verdi@scuola.edu.it"))
        assertTrue(class1A.teacherEmails.contains("giulia.ferrari@scuola.edu.it"))
        assertTrue(class1A.teacherEmails.contains("antonio.esposito@scuola.edu.it"))
        assertTrue(class1A.teacherEmails.contains("marco.romano@scuola.edu.it"))

        // Classe 1B: include anche il docente della partizione/gruppo GRP_1B_REL (chiara.marino)
        val class1B = result.first { it.className == "1b" }
        assertTrue(class1B.teacherEmails.contains("chiara.marino@scuola.edu.it"))
        assertTrue(class1B.teacherEmails.contains("anna.verdi@scuola.edu.it"))
        assertTrue(class1B.teacherEmails.contains("elena.colombo@scuola.edu.it"))
        assertTrue(class1B.teacherEmails.contains("giulia.ferrari@scuola.edu.it"))
        assertTrue(class1B.teacherEmails.contains("paolo.ricci@scuola.edu.it"))

        // Classe 2B: co-docenza (paolo.ricci e francesca.mancini sulla stessa materia/lezione)
        val class2B = result.first { it.className == "2b" }
        assertTrue(class2B.teacherEmails.contains("paolo.ricci@scuola.edu.it"))
        assertTrue(class2B.teacherEmails.contains("francesca.mancini@scuola.edu.it"))
        assertTrue(class2B.teacherEmails.contains("giorgio.rizzo@scuola.edu.it"))
        assertTrue(class2B.teacherEmails.contains("valeria.moretti@scuola.edu.it"))
    }

    @Test
    fun testParseRelationalEdtXmlSnippet() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Export xmlns="http://www.index-education.com/exportindexpartenaireV3.3" Version="3.3">
              <Professeurs>
                <Professeur Ident="P1" Nom="Rossi" Prenom="Mario" EMail="mario.rossi@scuola.edu.it" />
                <Professeur Ident="P2" Nom="Bianchi" Prenom="Luigi" EMail="luigi.bianchi@scuola.edu.it" />
                <Professeur Ident="P3" Nom="Verdi" Prenom="Anna" EMail="anna.verdi@scuola.edu.it" />
              </Professeurs>
              <Classes>
                <Classe Ident="C1" Nom="1A" />
                <Classe Ident="C2" Nom="2B" />
              </Classes>
              <Cours>
                <Cours Jour="1">
                  <Classe Ident="C1" />
                  <Professeur Ident="P1" />
                </Cours>
                <Cours Jour="1">
                  <Classe Ident="C1" />
                  <Professeur Ident="P2" />
                </Cours>
                <Cours Jour="2">
                  <Classe Ident="C2" />
                  <Professeur Ident="P2" />
                </Cours>
                <Cours Jour="2">
                  <Classe Ident="C2" />
                  <Professeur Ident="P3" />
                </Cours>
              </Cours>
            </Export>
        """.trimIndent()

        val result = EdtXmlParser.parseEdtXml(xml)

        assertEquals(2, result.size)
        val class1A = result.first { it.className == "1a" }
        assertEquals(setOf("mario.rossi@scuola.edu.it", "luigi.bianchi@scuola.edu.it"), class1A.teacherEmails)

        val class2B = result.first { it.className == "2b" }
        assertEquals(setOf("luigi.bianchi@scuola.edu.it", "anna.verdi@scuola.edu.it"), class2B.teacherEmails)
    }

    @Test
    fun testEmptyXmlThrowsException() {
        assertThrows<IllegalArgumentException> {
            EdtXmlParser.parseEdtXml("")
        }
    }

    @Test
    fun testMalformedXmlThrowsException() {
        assertThrows<IllegalArgumentException> {
            EdtXmlParser.parseEdtXml("<NON_CHIUSO>")
        }
    }

    @Test
    fun testXmlWithoutClassesThrowsException() {
        val xml = """
            <Export>
              <Professeurs>
                <Professeur Ident="P1" EMail="doc@scuola.edu.it"/>
              </Professeurs>
            </Export>
        """.trimIndent()

        assertThrows<IllegalArgumentException> {
            EdtXmlParser.parseEdtXml(xml)
        }
    }

    @Test
    fun testXxeInjectionBlocked() {
        val xxePayload = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE test [
              <!ENTITY xxe SYSTEM "file:///etc/passwd">
            ]>
            <Export>
              <Professeurs>
                <Professeur Ident="P1" EMail="&xxe;"/>
              </Professeurs>
              <Classes>
                <Classe Ident="C1" Nom="1A"/>
              </Classes>
            </Export>
        """.trimIndent()

        assertThrows<IllegalArgumentException> {
            EdtXmlParser.parseEdtXml(xxePayload)
        }
    }
}
