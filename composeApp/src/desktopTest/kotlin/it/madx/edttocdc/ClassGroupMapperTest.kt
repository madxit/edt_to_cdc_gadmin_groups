package it.madx.edttocdc

import it.madx.edttocdc.edt.ClassGroupMapper
import it.madx.edttocdc.model.ClassTeacherData
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClassGroupMapperTest {

    @Test
    fun testNormalizeClassName() {
        assertEquals("1a", ClassGroupMapper.normalizeClassName("1A"))
        assertEquals("1a", ClassGroupMapper.normalizeClassName("  1  A  "))
        assertEquals("3bs", ClassGroupMapper.normalizeClassName("3 BS"))
        assertEquals("5a_inf", ClassGroupMapper.normalizeClassName("5A/INF"))
        assertEquals("2c", ClassGroupMapper.normalizeClassName("2C-"))
    }

    @Test
    fun testNormalizeAndValidateEmail() {
        assertEquals("docente@scuola.edu.it", ClassGroupMapper.normalizeEmail("  Docente@Scuola.EDU.it  "))

        assertTrue(ClassGroupMapper.validateEmail("mario.rossi@scuola.edu.it"))
        assertTrue(ClassGroupMapper.validateEmail("docente+test@sub.scuola.org"))

        assertFalse(ClassGroupMapper.validateEmail(""))
        assertFalse(ClassGroupMapper.validateEmail("non_una_email"))
        assertFalse(ClassGroupMapper.validateEmail("@mancante.it"))
        assertFalse(ClassGroupMapper.validateEmail("docente@"))
    }

    @Test
    fun testBuildClassEmailMapDeduplication() {
        val input = listOf(
            ClassTeacherData("1A", setOf("mario@scuola.it", " MARIO@scuola.it ", "luigi@scuola.it")),
            ClassTeacherData("1A", setOf("mario@scuola.it", "anna@scuola.it")),
            ClassTeacherData("2B", setOf("luigi@scuola.it"))
        )

        val map = ClassGroupMapper.buildClassEmailMap(input)

        assertEquals(2, map.size)
        assertEquals(setOf("mario@scuola.it", "luigi@scuola.it", "anna@scuola.it"), map["1a"])
        assertEquals(setOf("luigi@scuola.it"), map["2b"])
    }

    @Test
    fun testBuildGroupEmailMap() {
        val classMap = mapOf(
            "1a" to setOf("mario@scuola.it"),
            "2b" to setOf("luigi@scuola.it")
        )

        val groupMap = ClassGroupMapper.buildGroupEmailMap(classMap, "scuola.edu.it")

        assertEquals(2, groupMap.size)
        assertTrue(groupMap.containsKey("cdc_1a@scuola.edu.it"))
        assertTrue(groupMap.containsKey("cdc_2b@scuola.edu.it"))
    }

    @Test
    fun testValidateGroupEmailSecurity() {
        val domain = "scuola.edu.it"

        // Formati validi autorizzati
        assertTrue(ClassGroupMapper.validateGroupEmail("cdc_1a@scuola.edu.it", domain))
        assertTrue(ClassGroupMapper.validateGroupEmail("cdc_3bs@scuola.edu.it", domain))
        assertTrue(ClassGroupMapper.validateGroupEmail("cdc_5a_inf@scuola.edu.it", domain))

        // Formati vietati o non conformi (MUST BE REJECTED)
        assertFalse(ClassGroupMapper.validateGroupEmail("tuttascuola@scuola.edu.it", domain))
        assertFalse(ClassGroupMapper.validateGroupEmail("dirigenza@scuola.edu.it", domain))
        assertFalse(ClassGroupMapper.validateGroupEmail("docenti@scuola.edu.it", domain))
        assertFalse(ClassGroupMapper.validateGroupEmail("cdc_1a@altradominio.com", domain))
        assertFalse(ClassGroupMapper.validateGroupEmail("cdc_@scuola.edu.it", domain))
        assertFalse(ClassGroupMapper.validateGroupEmail("cdc_1a+pericoloso@scuola.edu.it", domain))
    }

    @Test
    fun testValidateGroupEmailMapAnomalies() {
        val domain = "scuola.edu.it"

        val validMap = mapOf(
            "cdc_1a@scuola.edu.it" to setOf("docente@scuola.edu.it")
        )
        assertTrue(ClassGroupMapper.validateGroupEmailMap(validMap, domain).isEmpty())

        val invalidMap = mapOf(
            "gruppo_arbitrario@scuola.edu.it" to setOf("docente@scuola.edu.it"),
            "cdc_1b@scuola.edu.it" to emptySet()
        )
        val anomalies = ClassGroupMapper.validateGroupEmailMap(invalidMap, domain)
        assertEquals(2, anomalies.size)
    }

    @Test
    fun testDetectDominantDomain() {
        val classes = listOf(
            ClassTeacherData("1A", setOf("mario@liceo.edu.it", "luigi@liceo.edu.it")),
            ClassTeacherData("2B", setOf("anna@liceo.edu.it", "esterno@gmail.com")),
            ClassTeacherData("3C", setOf("giulia@liceo.edu.it"))
        )
        val detected = ClassGroupMapper.detectDominantDomain(classes)
        assertEquals("liceo.edu.it", detected)
    }
}
