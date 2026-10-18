package it.madx.edttocdc.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.madx.edttocdc.model.SyncSummary
import it.madx.edttocdc.ui.theme.DryRunPurple
import it.madx.edttocdc.ui.theme.ErrorRed
import it.madx.edttocdc.ui.theme.SuccessGreen

@Composable
fun SummaryDialog(
    summary: SyncSummary,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (summary.success) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Successo",
                        tint = SuccessGreen,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Attenzione",
                        tint = ErrorRed,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = if (summary.isDryRun) "Riepilogo Simulazione (Dry Run)" else "Riepilogo Sincronizzazione",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (summary.isDryRun) {
                    Surface(
                        color = DryRunPurple.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "MODALITÀ SIMULAZIONE: Nessuna modifica reale è stata apportata su Google Workspace.",
                            color = DryRunPurple,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                StatRow("Classi individuate nell'XML:", "${summary.totalClasses}")
                StatRow("Gruppi già esistenti:", "${summary.existingGroups}")
                StatRow("Gruppi creati (o previsti):", "${summary.createdGroups}")
                StatRow("Docenti aggiunti (o previsti):", "${summary.addedTeachersCount}")
                StatRow("Membri rimossi (o previsti):", "${summary.removedTeachersCount}")
                StatRow("Gruppi sincronizzati con successo:", "${summary.successfullySyncedGroups}")
                StatRow("Gruppi con errori:", "${summary.failedGroups}", isError = summary.failedGroups > 0)

                if (summary.anomalies.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        "Anomalie o note rilevate (${summary.anomalies.size}):",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (anomaly in summary.anomalies.take(15)) {
                            Text("• $anomaly", fontSize = 12.sp)
                        }
                        if (summary.anomalies.size > 15) {
                            Text("... e altre ${summary.anomalies.size - 15} segnalazioni nel log.", fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Chiudi")
            }
        }
    )
}

@Composable
private fun StatRow(label: String, value: String, isError: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            color = if (isError) ErrorRed else MaterialTheme.colorScheme.onSurface
        )
    }
}
