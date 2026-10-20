package it.madx.edttocdc.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EdtHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Guida e Configurazione EDT", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Come ottenere e verificare l'URL del file XML da EDT (Index Éducation):",
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    "1. Configurazione su EDT:\n" +
                    "   • Aprire il client EDT con i privilegi di pianificazione/amministrazione.\n" +
                    "   • Dal menu di esportazione o pubblicazione web, selezionare l'esportazione dati in formato XML (orari, docenti e classi).\n" +
                    "   • Pubblicare il file su un server HTTP o HTTPS accessibile (es. server web interno della scuola o area protetta del sito istituzionale).\n" +
                    "   • È fortemente raccomandato l'uso di HTTPS."
                )

                Text(
                    "2. Informazioni richieste nel file XML:\n" +
                    "   • Docenti: ogni docente deve avere associato il proprio indirizzo email istituzionale (es. nome.cognome@scuola.edu.it).\n" +
                    "   • Classi: devono essere presenti i codici/nomi delle classi (es. 1A, 2B, 3INF).\n" +
                    "   • Associazioni: i corsi, lezioni o servizi devono collegare chiaramente ciascun docente alle rispettive classi."
                )

                Text(
                    "3. Verifica della validità:\n" +
                    "   • È possibile utilizzare il pulsante 'Verifica XML' nella schermata principale per testare la raggiungibilità dell'URL e la conformità della struttura.\n" +
                    "   • Il parser dell'applicazione supporta sia il formato relazionale standard EDT che formati gerarchici diretti."
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Chiudi")
            }
        }
    )
}

@Composable
fun GoogleHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Guida e Configurazione Google Workspace", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Configurazione passo per passo di Google Cloud e Admin SDK:",
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    "1. Accesso a Google Cloud Console:\n" +
                    "   • Accedere a https://console.cloud.google.com con l'account amministratore della scuola.\n" +
                    "   • Creare o selezionare un progetto dedicato (es. 'MailingList-CdC')."
                )

                Text(
                    "2. Abilitazione delle API nella Google Cloud Console:\n" +
                    "   • Nel menu 'API e servizi' > 'Libreria', cercare e abilitare entrambe le seguenti API:\n" +
                    "     1) 'Admin SDK API' (gestione di gruppi e membri);\n" +
                    "     2) 'Groups Settings API' (impostazione permessi di accesso 'Restricted')."
                )

                Text(
                    "3. Creazione del Service Account e download della chiave:\n" +
                    "   • Andare su IAM e amministrazione > Account di servizio.\n" +
                    "   • Fare clic su 'Crea account di servizio' (es. nome 'cdc-sync-sa').\n" +
                    "   • Completare la creazione senza concedere ruoli di progetto globali non necessari.\n" +
                    "   • Selezionare l'account di servizio creato, andare nella scheda 'Chiavi' > 'Aggiungi chiave' > 'Crea nuova chiave' in formato JSON.\n" +
                    "   • Salvare il file JSON sul proprio computer e selezionarlo in questa applicazione."
                )

                Text(
                    "4. Configurazione della Delega a livello di dominio (Domain-Wide Delegation):\n" +
                    "   • Nella pagina del Service Account (o aprendo il file JSON scaricato al campo 'client_id'), copiare l'ID client numerico univoco.\n" +
                    "   • Aprire la Console di amministrazione Google Workspace (https://admin.google.com).\n" +
                    "   • Andare in: Sicurezza > Controllo degli accessi e dei dati > Controlli API > Gestisci delega a livello di dominio.\n" +
                    "   • Fare clic su 'Aggiungi nuovo':\n" +
                    "     - Nel campo 'ID client': incollare il Client ID numerico.\n" +
                    "     - Nel campo 'Ambiti OAuth' (comma-delimited), incollare la stringa seguente con gli ambiti separati da virgola (Directory API e Groups Settings per impostare l'accesso 'Restricted'):\n\n" +
                    "       https://www.googleapis.com/auth/admin.directory.group, https://www.googleapis.com/auth/admin.directory.group.member, https://www.googleapis.com/auth/apps.groups.settings\n\n" +
                    "     - Fare clic su 'Autorizza'."
                )

                Text(
                    "5. Impostazioni nell'applicazione:\n" +
                    "   • File credenziali JSON: selezionare il file scaricato da Google Cloud.\n" +
                    "   • Email Amministratore (DWD): inserire l'email dell'amministratore reale della scuola abilitato (es. admin@scuola.edu.it).\n" +
                    "   • Dominio scuola: viene ricavato automaticamente dal file EDT (non serve inserirlo manualmente).\n" +
                    "   • Utilizzare il pulsante 'Verifica Connessione' per testare l'accesso."
                )

                Text(
                    "VINCOLO DI SICUREZZA:\n" +
                    "L'applicazione opera ESCLUSIVAMENTE sui gruppi conformi al formato cdc_{classe}@dominio. " +
                    "Nessun gruppo esterno può essere modificato e l'applicazione NON contiene alcuna funzione per cancellare gruppi Google Workspace.",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Chiudi")
            }
        }
    )
}
