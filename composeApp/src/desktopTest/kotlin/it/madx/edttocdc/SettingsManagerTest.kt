package it.madx.edttocdc

import it.madx.edttocdc.config.AppSettings
import it.madx.edttocdc.config.SettingsManager
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsManagerTest {

    @Test
    fun testValidateSettingsErrors() {
        // Impostazioni completamente vuote (mancano URL e credenziali)
        val emptySettings = AppSettings()
        val errors = emptySettings.validate()
        assertTrue(errors.size >= 2)

        // URL non HTTP/HTTPS
        val invalidUrlSettings = AppSettings(
            edtXmlUrl = "ftp://scuola.it/test.xml",
            googleCredentialsPath = "/fake/creds.json",
            schoolDomain = "scuola.edu.it",
            adminEmail = "admin@scuola.edu.it"
        )
        val urlErrors = invalidUrlSettings.validate()
        assertTrue(urlErrors.any { it.contains("http:// o https://") })

        // Dominio non valido
        val invalidDomainSettings = AppSettings(
            edtXmlUrl = "https://scuola.it/test.xml",
            googleCredentialsPath = "/fake/creds.json",
            schoolDomain = "non_un_dominio",
            adminEmail = "admin@scuola.edu.it"
        )
        val domainErrors = invalidDomainSettings.validate()
        assertTrue(domainErrors.any { it.contains("dominio") })

        // Impostazioni valide
        val validSettings = AppSettings(
            edtXmlUrl = "https://scuola.it/test.xml",
            googleCredentialsPath = "/fake/creds.json",
            schoolDomain = "scuola.edu.it",
            adminEmail = "admin@scuola.edu.it"
        )
        assertTrue(validSettings.validate().isEmpty())
    }

    @Test
    fun testGetCredentialsFileValidation() {
        // Percorso vuoto
        val emptyResult = SettingsManager.getCredentialsFile("")
        assertTrue(emptyResult.isFailure)

        // File inesistente
        val nonExistentResult = SettingsManager.getCredentialsFile("file_che_sicuramente_non_esiste_12345.json")
        assertTrue(nonExistentResult.isFailure)

        // File con estensione diversa da .json
        val tempTxt = File.createTempFile("test_creds", ".txt")
        tempTxt.deleteOnExit()
        val txtResult = SettingsManager.getCredentialsFile(tempTxt.absolutePath)
        assertTrue(txtResult.isFailure)

        // File temporaneo JSON valido
        val tempJson = File.createTempFile("test_creds", ".json")
        tempJson.deleteOnExit()
        val jsonResult = SettingsManager.getCredentialsFile(tempJson.absolutePath)
        assertTrue(jsonResult.isSuccess)
    }

    @Test
    fun testSaveAndLoadSettings() {
        val testSettings = AppSettings(
            edtXmlUrl = "https://test.scuola.edu.it/export.xml",
            googleCredentialsPath = "/tmp/my_creds.json",
            schoolDomain = "istituto.edu.it",
            adminEmail = "preside@istituto.edu.it",
            dryRunDefault = true
        )

        val saveResult = SettingsManager.saveSettings(testSettings)
        assertTrue(saveResult.isSuccess)

        val loaded = SettingsManager.loadSettings()
        assertEquals(testSettings.edtXmlUrl, loaded.edtXmlUrl)
        assertEquals(testSettings.googleCredentialsPath, loaded.googleCredentialsPath)
        assertEquals(testSettings.schoolDomain, loaded.schoolDomain)
        assertEquals(testSettings.adminEmail, loaded.adminEmail)
        assertEquals(testSettings.dryRunDefault, loaded.dryRunDefault)
    }
}
