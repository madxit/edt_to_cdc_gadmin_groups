package it.madx.edttocdc

import it.madx.edttocdc.google.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoogleWorkspaceExporterTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun testExportClassGroupsToCsv() {
        val exporter = GoogleWorkspaceExporter(
            credentialsFile = File("fake_credentials.json"),
            domain = "scuola.edu.it"
        )

        val items = listOf(
            ClassGroupExportItem(classe = "1A", mailCdc = "cdc_1a@scuola.edu.it"),
            ClassGroupExportItem(classe = "2B", mailCdc = "cdc_2b@scuola.edu.it")
        )

        val csvFile = File(tempDir, "class_groups.csv")
        exporter.exportClassGroupsToCsv(csvFile, items)

        assertTrue(csvFile.exists())
        val text = csvFile.readText(Charsets.UTF_8)
        assertTrue(text.startsWith("\uFEFF"), "Il file CSV deve includere il BOM UTF-8 per la compatibilità con Excel")

        val lines = text.removePrefix("\uFEFF").lines().filter { it.isNotBlank() }
        assertEquals(3, lines.size)
        assertEquals("classe,mail_cdc", lines[0])
        assertEquals("1A,cdc_1a@scuola.edu.it", lines[1])
        assertEquals("2B,cdc_2b@scuola.edu.it", lines[2])
    }

    @Test
    fun testExportClassGroupMembersToCsv() {
        val exporter = GoogleWorkspaceExporter(
            credentialsFile = File("fake_credentials.json"),
            domain = "scuola.edu.it"
        )

        val items = listOf(
            ClassGroupMemberExportItem(classe = "1A", mailCdc = "cdc_1a@scuola.edu.it", membro = "mario.rossi@scuola.edu.it"),
            ClassGroupMemberExportItem(classe = "1A", mailCdc = "cdc_1a@scuola.edu.it", membro = "luigi.bianchi@scuola.edu.it"),
            ClassGroupMemberExportItem(classe = "2B", mailCdc = "cdc_2b@scuola.edu.it", membro = "")
        )

        val csvFile = File(tempDir, "class_group_members.csv")
        exporter.exportClassGroupMembersToCsv(csvFile, items)

        assertTrue(csvFile.exists())
        val text = csvFile.readText(Charsets.UTF_8)
        assertTrue(text.startsWith("\uFEFF"))

        val lines = text.removePrefix("\uFEFF").lines().filter { it.isNotBlank() }
        assertEquals(4, lines.size)
        assertEquals("classe,mail_cdc,membro", lines[0])
        assertEquals("1A,cdc_1a@scuola.edu.it,mario.rossi@scuola.edu.it", lines[1])
        assertEquals("1A,cdc_1a@scuola.edu.it,luigi.bianchi@scuola.edu.it", lines[2])
        assertEquals("2B,cdc_2b@scuola.edu.it,", lines[3])
    }

    @Test
    fun testExportClassGroupsToExcel() {
        val exporter = GoogleWorkspaceExporter(
            credentialsFile = File("fake_credentials.json"),
            domain = "scuola.edu.it"
        )

        val items = listOf(
            ClassGroupExportItem(classe = "1A", mailCdc = "cdc_1a@scuola.edu.it"),
            ClassGroupExportItem(classe = "3C", mailCdc = "cdc_3c@scuola.edu.it")
        )

        val xlsxFile = File(tempDir, "class_groups.xlsx")
        exporter.exportClassGroupsToExcel(xlsxFile, items)

        assertTrue(xlsxFile.exists())
        assertTrue(xlsxFile.length() > 0)

        // Lettura e validazione con Apache POI
        FileInputStream(xlsxFile).use { fis ->
            val workbook = XSSFWorkbook(fis)
            assertEquals(1, workbook.numberOfSheets)
            val sheet = workbook.getSheet("Classi e CdC")
            assertTrue(sheet != null)

            // Header
            val headerRow = sheet.getRow(0)
            assertEquals("classe", headerRow.getCell(0).stringCellValue)
            assertEquals("mail_cdc", headerRow.getCell(1).stringCellValue)

            // Rows
            val row1 = sheet.getRow(1)
            assertEquals("1A", row1.getCell(0).stringCellValue)
            assertEquals("cdc_1a@scuola.edu.it", row1.getCell(1).stringCellValue)

            val row2 = sheet.getRow(2)
            assertEquals("3C", row2.getCell(0).stringCellValue)
            assertEquals("cdc_3c@scuola.edu.it", row2.getCell(1).stringCellValue)
        }
    }

    @Test
    fun testExportClassGroupMembersToExcel() {
        val exporter = GoogleWorkspaceExporter(
            credentialsFile = File("fake_credentials.json"),
            domain = "scuola.edu.it"
        )

        val items = listOf(
            ClassGroupMemberExportItem(classe = "1A", mailCdc = "cdc_1a@scuola.edu.it", membro = "mario.rossi@scuola.edu.it"),
            ClassGroupMemberExportItem(classe = "1A", mailCdc = "cdc_1a@scuola.edu.it", membro = "giulia.verdi@scuola.edu.it")
        )

        val xlsxFile = File(tempDir, "class_group_members.xlsx")
        exporter.exportClassGroupMembersToExcel(xlsxFile, items)

        assertTrue(xlsxFile.exists())
        assertTrue(xlsxFile.length() > 0)

        FileInputStream(xlsxFile).use { fis ->
            val workbook = XSSFWorkbook(fis)
            val sheet = workbook.getSheet("CdC e Partecipanti")
            assertTrue(sheet != null)

            val headerRow = sheet.getRow(0)
            assertEquals("classe", headerRow.getCell(0).stringCellValue)
            assertEquals("mail_cdc", headerRow.getCell(1).stringCellValue)
            assertEquals("membro", headerRow.getCell(2).stringCellValue)

            val row1 = sheet.getRow(1)
            assertEquals("1A", row1.getCell(0).stringCellValue)
            assertEquals("cdc_1a@scuola.edu.it", row1.getCell(1).stringCellValue)
            assertEquals("mario.rossi@scuola.edu.it", row1.getCell(2).stringCellValue)

            val row2 = sheet.getRow(2)
            assertEquals("1A", row2.getCell(0).stringCellValue)
            assertEquals("cdc_1a@scuola.edu.it", row2.getCell(1).stringCellValue)
            assertEquals("giulia.verdi@scuola.edu.it", row2.getCell(2).stringCellValue)
        }
    }

    @Test
    fun testExporterIsStrictlyReadOnly() {
        // Verifica riflessiva che GoogleWorkspaceExporter non dichiari metodi di scrittura/modifica
        val methods = GoogleWorkspaceExporter::class.java.declaredMethods
        val writeMethods = methods.filter {
            it.name.contains("insert", ignoreCase = true) ||
            it.name.contains("delete", ignoreCase = true) ||
            it.name.contains("update", ignoreCase = true) ||
            it.name.contains("patch", ignoreCase = true) ||
            it.name.contains("create", ignoreCase = true)
        }

        assertTrue(
            writeMethods.isEmpty(),
            "GoogleWorkspaceExporter deve essere in sola lettura: trovati metodi non ammessi $writeMethods"
        )
    }

    @Test
    fun testAuthHelperValidatesMissingCredentials() {
        assertThrows<IOException> {
            GoogleWorkspaceAuthHelper.createGoogleCredentials(
                credentialsFile = File("non_existent_file.json"),
                adminEmail = "admin@scuola.edu.it",
                scopes = listOf("scope1")
            )
        }
    }
}
