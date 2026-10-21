package it.madx.edttocdc.google

import com.google.api.services.directory.Directory
import it.madx.edttocdc.edt.ClassGroupMapper
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.logging.Logger

/**
 * Formati di file supportati per l'esportazione dei dati.
 */
enum class ExportFormat(val extension: String, val label: String) {
    CSV("csv", "File CSV (.csv)"),
    EXCEL("xlsx", "File Excel (.xlsx)")
}

/**
 * Tipologie di esportazione disponibili per i dati dei Consigli di Classe.
 */
enum class ExportType(val title: String, val defaultFilenamePrefix: String) {
    CLASSE_EMAIL_CDC("classe_email_cdc", "classe_email_cdc"),
    CLASSE_EMAIL_CDC_EMAIL_PARTECIPANTI("classe_email_cdc_email_partecipanti", "classe_email_cdc_email_partecipanti")
}

/**
 * Rappresenta una riga di esportazione a due colonne: classe e indirizzo email della mailing list.
 */
data class ClassGroupExportItem(
    val classe: String,
    val mailCdc: String
)

/**
 * Rappresenta una riga di esportazione a tre colonne: classe, indirizzo del CdC e email del membro partecipante.
 */
data class ClassGroupMemberExportItem(
    val classe: String,
    val mailCdc: String,
    val membro: String
)

/**
 * Servizio in SOLA LETTURA dedicato all'interrogazione e all'esportazione dei gruppi e dei partecipanti
 * dei Consigli di Classe da Google Workspace in formato CSV o Excel (.xlsx).
 *
 * Tutte le operazioni di questa classe sono prive di effetti collaterali (nessuna scrittura/creazione su Google).
 */
class GoogleWorkspaceExporter(
    val credentialsFile: File,
    val domain: String,
    val adminEmail: String? = null
) {
    private companion object {
        val logger: Logger = Logger.getLogger(GoogleWorkspaceExporter::class.java.name)
    }

    private val normalizedDomain = domain.trim().trimStart('@').lowercase()

    private val directoryService: Directory by lazy {
        GoogleWorkspaceAuthHelper.createDirectoryService(credentialsFile, adminEmail)
    }

    /**
     * Interroga Google Workspace per recuperare tutti i gruppi mailing list attivi
     * corrispondenti al pattern protetto cdc_{classe}@{dominio}.
     *
     * @return Elenco ordinato alfabeticamente per nome classe.
     */
    fun fetchClassGroups(): List<ClassGroupExportItem> {
        val result = mutableListOf<ClassGroupExportItem>()
        var pageToken: String? = null

        do {
            val req = directoryService.groups().list()
                .setDomain(normalizedDomain)
                .setMaxResults(200)

            if (!pageToken.isNullOrBlank()) {
                req.pageToken = pageToken
            }

            val resp = req.execute()
            val groups = resp.groups ?: emptyList()

            for (g in groups) {
                val email = g.email?.trim()?.lowercase() ?: continue
                if (ClassGroupMapper.validateGroupEmail(email, normalizedDomain)) {
                    val className = email.substringBefore('@').removePrefix("cdc_").uppercase()
                    result.add(ClassGroupExportItem(classe = className, mailCdc = email))
                }
            }
            pageToken = resp.nextPageToken
        } while (!pageToken.isNullOrBlank())

        return result.sortedBy { it.classe }
    }

    /**
     * Recupera tutti i gruppi cdc_{classe}@{dominio} e, per ciascuno, l'elenco completo
     * degli indirizzi email dei membri iscritti.
     *
     * @param onProgress Callback opzionale per aggiornare lo stato di avanzamento durante l'interrogazione.
     * @return Elenco ordinato per classe e per indirizzo email del membro.
     */
    fun fetchClassGroupMembers(
        onProgress: ((current: Int, total: Int, groupEmail: String) -> Unit)? = null
    ): List<ClassGroupMemberExportItem> {
        val groups = fetchClassGroups()
        val result = mutableListOf<ClassGroupMemberExportItem>()
        val total = groups.size

        for ((index, grp) in groups.withIndex()) {
            onProgress?.invoke(index + 1, total, grp.mailCdc)
            var pageToken: String? = null
            var hasMembers = false

            do {
                val req = directoryService.members().list(grp.mailCdc)
                    .setMaxResults(200)

                if (!pageToken.isNullOrBlank()) {
                    req.pageToken = pageToken
                }

                val resp = req.execute()
                val members = resp.members ?: emptyList()

                for (m in members) {
                    val memberEmail = m.email?.trim()?.lowercase() ?: continue
                    hasMembers = true
                    result.add(
                        ClassGroupMemberExportItem(
                            classe = grp.classe,
                            mailCdc = grp.mailCdc,
                            membro = memberEmail
                        )
                    )
                }
                pageToken = resp.nextPageToken
            } while (!pageToken.isNullOrBlank())

            if (!hasMembers) {
                // Se il gruppo esiste ma non ha docenti iscritti, mostra comunque la classe con membro vuoto
                result.add(
                    ClassGroupMemberExportItem(
                        classe = grp.classe,
                        mailCdc = grp.mailCdc,
                        membro = ""
                    )
                )
            }
        }

        return result.sortedWith(compareBy({ it.classe }, { it.membro }))
    }

    /**
     * Esporta i dati a due colonne (classe, mail_cdc) in formato CSV con encoding UTF-8 BOM
     * compatibile direttamente con Excel.
     */
    fun exportClassGroupsToCsv(targetFile: File, items: List<ClassGroupExportItem>) {
        FileOutputStream(targetFile).use { fos ->
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                // Byte Order Mark (BOM) per garantire la corretta visualizzazione degli accenti in Microsoft Excel
                writer.write("\uFEFF")
                writer.write("classe,mail_cdc\r\n")
                for (item in items) {
                    writer.write("${escapeCsv(item.classe)},${escapeCsv(item.mailCdc)}\r\n")
                }
            }
        }
        logger.info("Esportati con successo ${items.size} gruppi in CSV: ${targetFile.absolutePath}")
    }

    /**
     * Esporta i dati a tre colonne (classe, mail_cdc, membro) in formato CSV con encoding UTF-8 BOM.
     */
    fun exportClassGroupMembersToCsv(targetFile: File, items: List<ClassGroupMemberExportItem>) {
        FileOutputStream(targetFile).use { fos ->
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                writer.write("\uFEFF")
                writer.write("classe,mail_cdc,membro\r\n")
                for (item in items) {
                    writer.write("${escapeCsv(item.classe)},${escapeCsv(item.mailCdc)},${escapeCsv(item.membro)}\r\n")
                }
            }
        }
        logger.info("Esportati con successo ${items.size} partecipanti in CSV: ${targetFile.absolutePath}")
    }

    /**
     * Esporta i dati a due colonne (classe, mail_cdc) in formato cartella di lavoro Excel (.xlsx).
     */
    fun exportClassGroupsToExcel(targetFile: File, items: List<ClassGroupExportItem>) {
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Classi e CdC")

            // Stile per l'intestazione
            val headerFont = workbook.createFont().apply {
                bold = true
                color = IndexedColors.WHITE.index
            }
            val headerStyle = workbook.createCellStyle().apply {
                setFont(headerFont)
                fillForegroundColor = IndexedColors.ROYAL_BLUE.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
            }

            // Riga intestazione
            val headerRow = sheet.createRow(0)
            val headers = arrayOf("classe", "mail_cdc")
            for ((colIndex, headerTitle) in headers.withIndex()) {
                val cell = headerRow.createCell(colIndex)
                cell.setCellValue(headerTitle)
                cell.cellStyle = headerStyle
            }

            // Righe dati
            for ((rowIndex, item) in items.withIndex()) {
                val row = sheet.createRow(rowIndex + 1)
                row.createCell(0).setCellValue(item.classe)
                row.createCell(1).setCellValue(item.mailCdc)
            }

            // Ridimensionamento automatico delle colonne
            sheet.autoSizeColumn(0)
            sheet.autoSizeColumn(1)

            FileOutputStream(targetFile).use { fos ->
                workbook.write(fos)
            }
        }
        logger.info("Esportati con successo ${items.size} gruppi in Excel: ${targetFile.absolutePath}")
    }

    /**
     * Esporta i dati a tre colonne (classe, mail_cdc, membro) in formato cartella di lavoro Excel (.xlsx).
     */
    fun exportClassGroupMembersToExcel(targetFile: File, items: List<ClassGroupMemberExportItem>) {
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("CdC e Partecipanti")

            val headerFont = workbook.createFont().apply {
                bold = true
                color = IndexedColors.WHITE.index
            }
            val headerStyle = workbook.createCellStyle().apply {
                setFont(headerFont)
                fillForegroundColor = IndexedColors.ROYAL_BLUE.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
            }

            val headerRow = sheet.createRow(0)
            val headers = arrayOf("classe", "mail_cdc", "membro")
            for ((colIndex, headerTitle) in headers.withIndex()) {
                val cell = headerRow.createCell(colIndex)
                cell.setCellValue(headerTitle)
                cell.cellStyle = headerStyle
            }

            for ((rowIndex, item) in items.withIndex()) {
                val row = sheet.createRow(rowIndex + 1)
                row.createCell(0).setCellValue(item.classe)
                row.createCell(1).setCellValue(item.mailCdc)
                row.createCell(2).setCellValue(item.membro)
            }

            sheet.autoSizeColumn(0)
            sheet.autoSizeColumn(1)
            sheet.autoSizeColumn(2)

            FileOutputStream(targetFile).use { fos ->
                workbook.write(fos)
            }
        }
        logger.info("Esportati con successo ${items.size} partecipanti in Excel: ${targetFile.absolutePath}")
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            "\"$escaped\""
        } else {
            escaped
        }
    }
}
