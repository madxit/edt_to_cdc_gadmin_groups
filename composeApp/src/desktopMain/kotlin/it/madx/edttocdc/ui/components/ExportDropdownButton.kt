package it.madx.edttocdc.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.madx.edttocdc.google.ExportFormat
import it.madx.edttocdc.google.ExportType
import it.madx.edttocdc.ui.MainViewModel
import it.madx.edttocdc.ui.theme.PrimaryBlue
import it.madx.edttocdc.ui.theme.SecondaryTeal
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Menu a discesa multilivello integrato per l'esportazione dei dati Google Workspace.
 *
 * Presenta una struttura gerarchica coerente all'interno di un unico pannello elevato:
 * - Livello 1: Selezione della tipologia di esportazione (2 colonne o 3 colonne).
 * - Livello 2: Espansione contestuale del formato desiderato (CSV o Excel) al passaggio del mouse o al clic.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ExportDropdownButton(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var expandedType by remember { mutableStateOf<ExportType?>(ExportType.CLASSE_EMAIL_CDC) }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = {
                menuExpanded = !menuExpanded
            },
            enabled = !viewModel.isRunning
        ) {
            Icon(
                Icons.Default.FileDownload,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("Esporta", fontSize = 12.sp)
            Spacer(Modifier.width(2.dp))
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = {
                menuExpanded = false
            },
            modifier = Modifier.width(340.dp)
        ) {
            // Intestazione del menu multilivello
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Output,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = PrimaryBlue
                )
                Text(
                    "Esportazione Dati Google Workspace",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))

            // ================================================================
            // LIVELLO 1 - OPZIONE A: classe_email_cdc (2 colonne)
            // ================================================================
            val isAExpanded = expandedType == ExportType.CLASSE_EMAIL_CDC
            val arrowRotationA by animateFloatAsState(targetValue = if (isAExpanded) 90f else 0f)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onPointerEvent(PointerEventType.Enter) {
                        expandedType = ExportType.CLASSE_EMAIL_CDC
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            expandedType = if (isAExpanded) null else ExportType.CLASSE_EMAIL_CDC
                        }
                        .background(
                            if (isAExpanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surface
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.TableChart,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = PrimaryBlue
                        )
                        Column {
                            Text(
                                "classe_email_cdc",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                "2 colonne: classe, mail_cdc",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(arrowRotationA),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // LIVELLO 2 - Formati per Opzione A
                AnimatedVisibility(
                    visible = isAExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 28.dp, end = 12.dp, top = 4.dp, bottom = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Scegli formato di esportazione:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                        )

                        FormatSubMenuItem(
                            icon = Icons.Default.Description,
                            iconTint = PrimaryBlue,
                            title = "Esporta in CSV (.csv)",
                            subtitle = "Formato testo compatibile Excel / Calc",
                            onClick = {
                                menuExpanded = false
                                triggerExportDialog(viewModel, ExportType.CLASSE_EMAIL_CDC, ExportFormat.CSV)
                            }
                        )

                        FormatSubMenuItem(
                            icon = Icons.Default.GridOn,
                            iconTint = SecondaryTeal,
                            title = "Esporta in Excel (.xlsx)",
                            subtitle = "Cartella di lavoro Microsoft Excel",
                            onClick = {
                                menuExpanded = false
                                triggerExportDialog(viewModel, ExportType.CLASSE_EMAIL_CDC, ExportFormat.EXCEL)
                            }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ================================================================
            // LIVELLO 1 - OPZIONE B: classe_email_cdc_email_partecipanti (3 colonne)
            // ================================================================
            val isBExpanded = expandedType == ExportType.CLASSE_EMAIL_CDC_EMAIL_PARTECIPANTI
            val arrowRotationB by animateFloatAsState(targetValue = if (isBExpanded) 90f else 0f)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onPointerEvent(PointerEventType.Enter) {
                        expandedType = ExportType.CLASSE_EMAIL_CDC_EMAIL_PARTECIPANTI
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            expandedType = if (isBExpanded) null else ExportType.CLASSE_EMAIL_CDC_EMAIL_PARTECIPANTI
                        }
                        .background(
                            if (isBExpanded) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surface
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Groups,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = SecondaryTeal
                        )
                        Column {
                            Text(
                                "classe_email_cdc_email_partecipanti",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                "3 colonne: classe, mail_cdc, membro",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(arrowRotationB),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // LIVELLO 2 - Formati per Opzione B
                AnimatedVisibility(
                    visible = isBExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 28.dp, end = 12.dp, top = 4.dp, bottom = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Scegli formato di esportazione:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
                        )

                        FormatSubMenuItem(
                            icon = Icons.Default.Description,
                            iconTint = PrimaryBlue,
                            title = "Esporta in CSV (.csv)",
                            subtitle = "Formato testo compatibile Excel / Calc",
                            onClick = {
                                menuExpanded = false
                                triggerExportDialog(
                                    viewModel,
                                    ExportType.CLASSE_EMAIL_CDC_EMAIL_PARTECIPANTI,
                                    ExportFormat.CSV
                                )
                            }
                        )

                        FormatSubMenuItem(
                            icon = Icons.Default.GridOn,
                            iconTint = SecondaryTeal,
                            title = "Esporta in Excel (.xlsx)",
                            subtitle = "Cartella di lavoro Microsoft Excel",
                            onClick = {
                                menuExpanded = false
                                triggerExportDialog(
                                    viewModel,
                                    ExportType.CLASSE_EMAIL_CDC_EMAIL_PARTECIPANTI,
                                    ExportFormat.EXCEL
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatSubMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = iconTint
        )
        Column {
            Text(
                title,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun triggerExportDialog(
    viewModel: MainViewModel,
    type: ExportType,
    format: ExportFormat
) {
    val defaultFilename = "${type.defaultFilenamePrefix}.${format.extension}"
    val dialog = FileDialog(null as Frame?, "Salva esportazione ${type.title}", FileDialog.SAVE)
    dialog.file = defaultFilename
    dialog.isVisible = true

    val dir = dialog.directory
    val file = dialog.file
    if (dir != null && file != null) {
        val targetFile = if (file.endsWith(".${format.extension}", ignoreCase = true)) {
            File(dir, file)
        } else {
            File(dir, "$file.${format.extension}")
        }
        viewModel.exportData(type, format, targetFile)
    }
}
