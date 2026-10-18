package it.madx.edttocdc

import it.madx.edttocdc.edt.EdtXmlParser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EdtXmlParserTest {

    @Test
    fun testParseRelationalEdtXml() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <EDT xmlns="http://www.index-education.com/edt">
              <DOCENTI>
                <DOCENTE Id="D1" Cognome="Rossi" Nome="Mario" Email="mario.rossi@scuola.edu.it" />
                <DOCENTE Id="D2" Cognome="Bianchi" Nome="Luigi" Email="luigi.bianchi@scuola.edu.it" />
                <DOCENTE Id="D3" Cognome="Verdi" Nome="Anna" Email="anna.verdi@scuola.edu.it" />
              </DOCENTI>
              <CLASSES>
                <CLASSE Id="C1" Nom="1A" />
                <CLASSE Id="C2" Nom="2B" />
              </CLASSES>
              <COURS>
                <COUR Id="K1">
                  <CLASSE Id="C1" />
                  <DOCENTE Id="D1" />
                </COUR>
                <COUR Id="K2">
                  <CLASSE Id="C1" />
                  <DOCENTE Id="D2" />
                </COUR>
                <COUR Id="K3">
                  <CLASSE Id="C2" />
                  <DOCENTE Id="D2" />
                </COUR>
                <COUR Id="K4">
                  <CLASSE Id="C2" />
                  <DOCENTE Id="D3" />
                </COUR>
              </COURS>
            </EDT>
        """.trimIndent()

        val result = EdtXmlParser.parseEdtXml(xml)

        assertEquals(2, result.size)
        val class1A = result.first { it.className == "1a" }
        assertEquals(setOf("mario.rossi@scuola.edu.it", "luigi.bianchi@scuola.edu.it"), class1A.teacherEmails)

        val class2B = result.first { it.className == "2b" }
        assertEquals(setOf("luigi.bianchi@scuola.edu.it", "anna.verdi@scuola.edu.it"), class2B.teacherEmails)
    }

    @Test
    fun testParseHierarchicalEdtXml() {
        val xml = """
            <CONSIGLI_DI_CLASSE>
              <CLASSE Nome="3INF">
                <DOCENTI>
                  <DOCENTE Nome="Prof A" Email="prof.a@scuola.edu.it"/>
                  <DOCENTE Nome="Prof B" Email="prof.b@scuola.edu.it"/>
                </DOCENTI>
              </CLASSE>
              <CLASSE Nome="4INF">
                <DOCENTI>
                  <DOCENTE Nome="Prof A" Email="prof.a@scuola.edu.it"/>
                </DOCENTI>
              </CLASSE>
            </CONSIGLI_DI_CLASSE>
        """.trimIndent()

        val result = EdtXmlParser.parseEdtXml(xml)
        assertEquals(2, result.size)

        val class3 = result.first { it.className == "3inf" }
        assertEquals(setOf("prof.a@scuola.edu.it", "prof.b@scuola.edu.it"), class3.teacherEmails)
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
            <EDT>
              <DOCENTI>
                <DOCENTE Id="D1" Email="doc@scuola.edu.it"/>
              </DOCENTI>
            </EDT>
        """.trimIndent()

        assertThrows<IllegalArgumentException> {
            EdtXmlParser.parseEdtXml(xml)
        }
    }

    @Test
    fun testXxeInjectionBlocked() {
        // Tentativo di inclusione di DTD ed entità esterne
        val xxePayload = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE test [
              <!ENTITY xxe SYSTEM "file:///etc/passwd">
            ]>
            <EDT>
              <DOCENTI>
                <DOCENTE Id="D1" Email="&xxe;"/>
              </DOCENTI>
              <CLASSES>
                <CLASSE Id="C1" Nom="1A"/>
              </CLASSES>
            </EDT>
        """.trimIndent()

        // Deve essere rifiutato a monte dalla configurazione sicura del parser (Disallow DTD)
        assertThrows<IllegalArgumentException> {
            EdtXmlParser.parseEdtXml(xxePayload)
        }
    }
}
