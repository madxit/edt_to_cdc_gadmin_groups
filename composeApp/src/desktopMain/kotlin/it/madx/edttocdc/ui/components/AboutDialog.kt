package it.madx.edttocdc.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.madx.edttocdc.BuildConfig
import it.madx.edttocdc.ui.theme.PrimaryBlue
import java.awt.Desktop
import java.net.URI

/**
 * Finestra di dialogo "About" (Informazioni sull'applicazione).
 *
 * Mostra l'icona dell'applicazione in evidenza e una tabella centrata a due colonne (Key, Value)
 * con intestazioni in grassetto e senza bordi visibili, come richiesto.
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Chiudi")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                // Icona grossa dell'applicazione centrata
                val iconPainter = runCatching { painterResource("icons/icon.png") }.getOrNull()
                if (iconPainter != null) {
                    Image(
                        painter = iconPainter,
                        contentDescription = "Logo Applicazione",
                        modifier = Modifier
                            .size(100.dp)
                            .shadow(6.dp, RoundedCornerShape(22.dp))
                            .clip(RoundedCornerShape(22.dp))
                    )
                }

                // Tabella a due colonne (Key, Value) senza bordi visibili e centrata
                val tableRows = listOf(
                    "Nome" to BuildConfig.APP_NAME,
                    "Versione" to "v${BuildConfig.APP_VERSION}",
                    "Sviluppatore" to "Daniele Maddaluno",
                    "Codice Sorgente" to "https://github.com/madxit/edt_to_cdc_gadmin_groups"
                )

                Column(
                    modifier = Modifier.widthIn(max = 440.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Intestazione con due colonne in grassetto: Key, Value
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Key",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(0.36f)
                        )
                        Text(
                            text = "Value",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(0.64f)
                        )
                    }

                    // Righe dati senza bordi visibili
                    for ((key, value) in tableRows) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = key,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(0.36f)
                            )

                            if (value.startsWith("http")) {
                                Row(
                                    modifier = Modifier
                                        .weight(0.64f)
                                        .clickable {
                                            runCatching {
                                                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                                                    Desktop.getDesktop().browse(URI(value))
                                                }
                                            }
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = value,
                                        fontSize = 13.sp,
                                        color = PrimaryBlue,
                                        textDecoration = TextDecoration.Underline
                                    )
                                    Icon(
                                        Icons.Default.OpenInBrowser,
                                        contentDescription = "Apri link",
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = value,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (key == "Versione") FontWeight.Medium else FontWeight.Normal,
                                    modifier = Modifier.weight(0.64f)
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}
