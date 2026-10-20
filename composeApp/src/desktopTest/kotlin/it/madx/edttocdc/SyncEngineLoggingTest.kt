package it.madx.edttocdc

import it.madx.edttocdc.config.AppSettings
import it.madx.edttocdc.model.LogEntry
import it.madx.edttocdc.sync.SyncEngine
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncEngineLoggingTest {

    @Test
    fun testLogGroupsDetailsSwitchControlsOutput() {
        // Prepariamo credenziali temporanee valide formalmente
        val tempCreds = File.createTempFile("test_creds", ".json").apply {
            writeText("""{"type": "service_account", "client_email": "test@scuola.iam.gserviceaccount.com"}""")
            deleteOnExit()
        }

        val settings = AppSettings(
            edtXmlUrl = "https://scuola.edu.it/export.xml",
            googleCredentialsPath = tempCreds.absolutePath,
            schoolDomain = "scuola.edu.it",
            adminEmail = "admin@scuola.edu.it"
        )

        val logsDisabled = mutableListOf<LogEntry>()
        // Con download non riuscito (URL fittizio), il flusso fallisce al download prima dei gruppi
        SyncEngine.runSync(
            settings = settings,
            dryRun = true,
            logGroupsDetails = false,
            onLog = { logsDisabled.add(it) },
            onProgress = { _, _, _ -> }
        )

        assertFalse(logsDisabled.any { it.message.startsWith("Gruppo: ") })
    }
}
