package it.madx.edttocdc.google

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.directory.Directory
import com.google.api.services.directory.DirectoryScopes
import com.google.api.services.groupssettings.Groupssettings
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * Gestore centralizzato e condiviso per l'autenticazione Google Workspace e la creazione
 * dei client dei servizi Admin SDK Directory API e Groups Settings API.
 *
 * Evita la duplicazione del codice di connessione e timeout tra [GoogleWorkspaceClient]
 * e i servizi ausiliari in sola lettura come [GoogleWorkspaceExporter].
 */
object GoogleWorkspaceAuthHelper {

    const val CONNECT_TIMEOUT_MS = 30_000
    const val READ_TIMEOUT_MS = 60_000

    private val jsonFactory = GsonFactory.getDefaultInstance()

    /**
     * Carica e prepara le credenziali Google dal file fornito (Service Account o OAuth).
     * Se si tratta di un Service Account, configura tassativamente la delega a livello di dominio (DWD)
     * impersonando l'amministratore specificato.
     */
    fun createGoogleCredentials(
        credentialsFile: File,
        adminEmail: String?,
        scopes: List<String>
    ): GoogleCredentials {
        if (!credentialsFile.exists() || !credentialsFile.canRead()) {
            throw IOException("File di credenziali Google non accessibile: ${credentialsFile.name}")
        }

        var credentials = FileInputStream(credentialsFile).use { stream ->
            GoogleCredentials.fromStream(stream)
        }

        if (credentials.createScopedRequired()) {
            credentials = credentials.createScoped(scopes)
        }

        // Se si utilizzano credenziali di un Service Account, è obbligatoria
        // la Domain-Wide Delegation (impersonazione dell'amministratore del dominio)
        if (credentials is ServiceAccountCredentials) {
            if (adminEmail.isNullOrBlank()) {
                throw IllegalArgumentException(
                    "Le credenziali appartengono al Service Account Google Cloud '${credentials.clientEmail}'. " +
                            "Per accedere alle Google API è necessario specificare l'email di un amministratore Google Workspace abilitato alla delega DWD."
                )
            }
            credentials = credentials.createDelegated(adminEmail.trim())
        }

        return credentials
    }

    /**
     * Inizializza il servizio Google Admin SDK Directory API con gli ambiti specificati
     * e i timeout HTTP di protezione configurati.
     */
    fun createDirectoryService(
        credentialsFile: File,
        adminEmail: String?,
        scopes: List<String> = listOf(
            DirectoryScopes.ADMIN_DIRECTORY_GROUP,
            DirectoryScopes.ADMIN_DIRECTORY_GROUP_MEMBER
        ),
        applicationName: String = "EdtToCdcSync/1.0"
    ): Directory {
        val credentials = createGoogleCredentials(credentialsFile, adminEmail, scopes)
        val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        val credentialsAdapter = HttpCredentialsAdapter(credentials)

        // Timeout HTTP obbligatori per evitare richieste appese indefinitamente
        val requestInitializer = HttpRequestInitializer { request ->
            credentialsAdapter.initialize(request)
            request.connectTimeout = CONNECT_TIMEOUT_MS
            request.readTimeout = READ_TIMEOUT_MS
        }

        return Directory.Builder(httpTransport, jsonFactory, requestInitializer)
            .setApplicationName(applicationName)
            .build()
    }

    /**
     * Inizializza il servizio Google Groups Settings API con gli ambiti e i timeout configurati.
     */
    fun createGroupsSettingsService(
        credentialsFile: File,
        adminEmail: String?,
        applicationName: String = "EdtToCdcSync/1.0"
    ): Groupssettings {
        val scopes = listOf("https://www.googleapis.com/auth/apps.groups.settings")
        val credentials = createGoogleCredentials(credentialsFile, adminEmail, scopes)
        val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        val credentialsAdapter = HttpCredentialsAdapter(credentials)

        val requestInitializer = HttpRequestInitializer { request ->
            credentialsAdapter.initialize(request)
            request.connectTimeout = CONNECT_TIMEOUT_MS
            request.readTimeout = READ_TIMEOUT_MS
        }

        return Groupssettings.Builder(httpTransport, jsonFactory, requestInitializer)
            .setApplicationName(applicationName)
            .build()
    }
}
