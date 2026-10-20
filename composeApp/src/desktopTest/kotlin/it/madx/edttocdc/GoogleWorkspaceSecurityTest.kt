package it.madx.edttocdc

import it.madx.edttocdc.google.GoogleWorkspaceClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoogleWorkspaceSecurityTest {

    private val client = GoogleWorkspaceClient(
        credentialsFile = File("fake_credentials.json"),
        domain = "scuola.edu.it"
    )

    @Test
    fun testValidateManagedGroupAllowsOnlyCdcPattern() {
        // Formato autorizzato: non deve lanciare eccezioni
        client.validateManagedGroup("cdc_1a@scuola.edu.it")
        client.validateManagedGroup("cdc_5bs_inf@scuola.edu.it")
    }

    @Test
    fun testValidateManagedGroupRejectsGlobalAndArbitraryGroups() {
        // Tentativo di operare su gruppi globali o sensibili deve lanciare SecurityException
        assertThrows<SecurityException> {
            client.validateManagedGroup("tuttascuola@scuola.edu.it")
        }

        assertThrows<SecurityException> {
            client.validateManagedGroup("docenti@scuola.edu.it")
        }

        assertThrows<SecurityException> {
            client.validateManagedGroup("dirigenza@scuola.edu.it")
        }

        assertThrows<SecurityException> {
            client.validateManagedGroup("admin@scuola.edu.it")
        }

        assertThrows<SecurityException> {
            client.validateManagedGroup("cdc_1a@altradominio.com")
        }
    }

    @Test
    fun testNoGroupDeletionMethodExists() {
        // Verifica riflessiva che nessun metodo di cancellazione gruppi sia esposto nella classe
        val methods = GoogleWorkspaceClient::class.java.declaredMethods
        val deleteGroupMethods = methods.filter {
            it.name.contains("deleteGroup", ignoreCase = true) ||
            it.name.contains("removeGroup", ignoreCase = true)
        }

        assertTrue(
            deleteGroupMethods.isEmpty(),
            "Non deve esistere alcun metodo per cancellare gruppi Google Workspace: trovati $deleteGroupMethods"
        )
    }

    @Test
    fun testSecurityMarkersPresentInSourceFile() {
        // Verifica che i marcatori di revisione sicurezza siano presenti nel file sorgente
        val sourceFile = File("composeApp/src/desktopMain/kotlin/it/madx/edttocdc/google/GoogleWorkspaceClient.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            assertTrue(content.contains("SECURITY REVIEW MAP"), "Deve contenere l'intestazione SECURITY REVIEW MAP")
            assertTrue(content.contains("REVISIONE SICUREZZA"), "Deve contenere i marcatori REVISIONE SICUREZZA")
            assertTrue(content.contains("Cancellazione di gruppi:"), "Deve specificare lo stato di cancellazione gruppi")
        }
    }

    @Test
    fun testConfigureRestrictedGroupSettingsValidatesGroup() {
        assertThrows<SecurityException> {
            client.configureRestrictedGroupSettings("tuttascuola@scuola.edu.it", dryRun = true).getOrThrow()
        }
        assertThrows<SecurityException> {
            client.configureRestrictedGroupSettings("cdc_1a@altradominio.com", dryRun = true).getOrThrow()
        }
    }

    @Test
    fun testConfigureRestrictedGroupSettingsDryRun() {
        val result = client.configureRestrictedGroupSettings("cdc_1a@scuola.edu.it", dryRun = true)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrDefault(false))
    }

    @Test
    fun testEnsureGroupExistsSecurityBoundary() {
        assertThrows<SecurityException> {
            client.ensureGroupExists("tuttascuola@scuola.edu.it", "tuttascuola", dryRun = true).getOrThrow()
        }
        assertThrows<SecurityException> {
            client.ensureGroupExists("cdc_1a@altradominio.com", "1a", dryRun = true).getOrThrow()
        }
    }

    @Test
    fun testCdcGroupAccessPolicyDefaults() {
        // Verifica che i criteri predefiniti corrispondano a una mailing list interna:
        // - conversazioni web e dettagli riservati a gestori
        // - invio post e visualizzazione membri aperti a tutta l'organizzazione
        org.junit.jupiter.api.Assertions.assertEquals("ALL_MANAGERS_CAN_VIEW", it.madx.edttocdc.google.CdcGroupAccessPolicy.whoCanViewGroup)
        org.junit.jupiter.api.Assertions.assertEquals("ALL_IN_DOMAIN_CAN_VIEW", it.madx.edttocdc.google.CdcGroupAccessPolicy.whoCanViewMembership)
        org.junit.jupiter.api.Assertions.assertEquals("ALL_IN_DOMAIN_CAN_POST", it.madx.edttocdc.google.CdcGroupAccessPolicy.whoCanPostMessage)
        org.junit.jupiter.api.Assertions.assertEquals("ALL_MANAGERS_CAN_CONTACT", it.madx.edttocdc.google.CdcGroupAccessPolicy.whoCanContactOwner)
        org.junit.jupiter.api.Assertions.assertEquals("INVITED_CAN_JOIN", it.madx.edttocdc.google.CdcGroupAccessPolicy.whoCanJoin)
        org.junit.jupiter.api.Assertions.assertEquals("false", it.madx.edttocdc.google.CdcGroupAccessPolicy.allowExternalMembers)
    }

    @Test
    fun testEnsureGroupAccessSettingsSecurityBoundary() {
        assertThrows<SecurityException> {
            client.ensureGroupAccessSettings("tuttascuola@scuola.edu.it", dryRun = true).getOrThrow()
        }
        val result = client.ensureGroupAccessSettings("cdc_1a@scuola.edu.it", dryRun = true)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrDefault(false))
    }
}
