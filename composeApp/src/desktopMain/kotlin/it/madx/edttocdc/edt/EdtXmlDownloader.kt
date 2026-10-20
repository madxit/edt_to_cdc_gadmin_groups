package it.madx.edttocdc.edt

import it.madx.edttocdc.model.ClassTeacherData
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Esito del test di connessione all'URL EDT.
 */
data class EdtConnectionTestResult(
    val statusCode: Int,
    val sizeBytes: Long,
    val contentType: String?,
    val isXmlValid: Boolean,
    val detectedClassesCount: Int,
    val parsedClasses: List<ClassTeacherData> = emptyList()
)

/**
 * Modulo responsabile del download e della verifica preliminare del file XML generato da EDT.
 */
object EdtXmlDownloader {

    private const val MAX_XML_SIZE_BYTES = 25 * 1024 * 1024 // 25 MB limite di sicurezza

    private val httpClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build()
    }

    /**
     * Scarica l'XML dall'URL configurato, applicando controlli di sicurezza su protocollo, timeout e dimensione.
     */
    fun downloadEdtXml(url: String, timeoutSeconds: Int = 15): Result<String> {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            return Result.failure(IllegalArgumentException("L'URL di EDT non può essere vuoto."))
        }

        val uri = try {
            val parsed = URI.create(trimmedUrl)
            val scheme = parsed.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") {
                return Result.failure(IllegalArgumentException("L'URL deve utilizzare il protocollo HTTP o HTTPS."))
            }
            parsed
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("URL EDT non valido: ${e.message}"))
        }

        return runCatching {
            val request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(timeoutSeconds.toLong()))
                .header("User-Agent", "EdtToCdcSync/1.0 (Desktop; Educational)")
                .header("Accept", "application/xml, text/xml, */*")
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
            val statusCode = response.statusCode()

            if (statusCode !in 200..299) {
                throw IllegalStateException("Errore HTTP durante il download dell'XML EDT: codice $statusCode (${response.uri()})")
            }

            val body = response.body() ?: ""
            if (body.isBlank()) {
                throw IllegalStateException("Il server ha restituito una risposta vuota.")
            }

            if (body.toByteArray(Charsets.UTF_8).size > MAX_XML_SIZE_BYTES) {
                throw IllegalStateException("Il file XML scaricato supera il limite massimo consentito di 25 MB.")
            }

            val contentType = response.headers().firstValue("Content-Type").orElse("")
            validateXmlResponse(statusCode, body, contentType).getOrThrow()

            body
        }
    }

    /**
     * Verifica la coerenza della risposta HTTP e la presenza della struttura minima di un documento XML.
     */
    fun validateXmlResponse(statusCode: Int, body: String, contentType: String?): Result<Unit> {
        return runCatching {
            if (statusCode !in 200..299) {
                throw IllegalStateException("Codice HTTP non valido: $statusCode")
            }
            val trimmedBody = body.trim()
            if (!trimmedBody.startsWith("<?xml", ignoreCase = true) && !trimmedBody.startsWith("<")) {
                throw IllegalStateException("La risposta scaricata non sembra essere un documento XML valido.")
            }
        }
    }

    /**
     * Verifica la raggiungibilità dell'URL EDT ed esegue un test di parsing rapido.
     */
    fun testEdtConnection(url: String): Result<EdtConnectionTestResult> {
        return runCatching {
            val xml = downloadEdtXml(url).getOrThrow()
            val parsed = EdtXmlParser.parseEdtXml(xml)
            EdtConnectionTestResult(
                statusCode = 200,
                sizeBytes = xml.toByteArray(Charsets.UTF_8).size.toLong(),
                contentType = "text/xml",
                isXmlValid = true,
                detectedClassesCount = parsed.size,
                parsedClasses = parsed
            )
        }
    }
}
