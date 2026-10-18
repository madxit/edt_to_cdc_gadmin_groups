package it.madx.edttocdc

import it.madx.edttocdc.google.GoogleWorkspaceClient
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MembershipDiffTest {

    // Helper client per i test sulle logiche pure di diff
    private val dummyClient = GoogleWorkspaceClient(
        credentialsFile = File("fake_credentials.json"),
        domain = "scuola.edu.it"
    )

    @Test
    fun testCalculateMembershipChanges() {
        val current = listOf("mario@scuola.it", "luigi@scuola.it", "anna@scuola.it")
        val desired = listOf("mario@scuola.it", "luigi@scuola.it", "giulia@scuola.it")

        val diff = dummyClient.calculateMembershipChanges(current, desired)

        assertEquals(setOf("giulia@scuola.it"), diff.toAdd)
        assertEquals(setOf("anna@scuola.it"), diff.toRemove)
        assertEquals(setOf("mario@scuola.it", "luigi@scuola.it"), diff.unchanged)
        assertTrue(diff.hasChanges)
    }

    @Test
    fun testCaseInsensitiveMembershipComparison() {
        val current = listOf("Mario.Rossi@Scuola.IT")
        val desired = listOf("mario.rossi@scuola.it")

        val diff = dummyClient.calculateMembershipChanges(current, desired)

        assertTrue(diff.toAdd.isEmpty())
        assertTrue(diff.toRemove.isEmpty())
        assertEquals(setOf("mario.rossi@scuola.it"), diff.unchanged)
        assertFalse(diff.hasChanges)
    }

    @Test
    fun testIdempotency() {
        val current = listOf("doc1@scuola.it", "doc2@scuola.it")
        val desired = listOf("doc1@scuola.it", "doc2@scuola.it")

        val diff = dummyClient.calculateMembershipChanges(current, desired)

        assertTrue(diff.toAdd.isEmpty())
        assertTrue(diff.toRemove.isEmpty())
        assertFalse(diff.hasChanges)
    }

    @Test
    fun testAllNewMembers() {
        val current = emptyList<String>()
        val desired = listOf("doc1@scuola.it", "doc2@scuola.it")

        val diff = dummyClient.calculateMembershipChanges(current, desired)

        assertEquals(setOf("doc1@scuola.it", "doc2@scuola.it"), diff.toAdd)
        assertTrue(diff.toRemove.isEmpty())
    }

    @Test
    fun testAllRemovedMembers() {
        val current = listOf("vecchio@scuola.it")
        val desired = emptyList<String>()

        val diff = dummyClient.calculateMembershipChanges(current, desired)

        assertTrue(diff.toAdd.isEmpty())
        assertEquals(setOf("vecchio@scuola.it"), diff.toRemove)
    }
}
