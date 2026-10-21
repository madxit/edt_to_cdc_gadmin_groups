package it.madx.edttocdc.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
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
 * Pulsante per la TopAppBar con menu a cascata (cascading dropdown) per l'esportazione dei gruppi CdC
 * e dei relativi membri in formato CSV o Excel (.xlsx).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ExportDropdownButton(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var mainMenuExpanded by remember { mutableStateOf(false) }
    var activeSubMenu by remember { mutableStateOf<ExportType?>(null) }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = {
                mainMenuExpanded = !mainMenuExpanded
                activeSubMenu = null
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
            expanded = mainMenuExpanded,
            onDismissRequest = {
                mainMenuExpanded = false
                activeSubMenu = null
            }
        ) {
            // ================================================================
            // OPZIONE 1: export classe_email_cdc (2 colonne)
            // ================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onPointerEvent(PointerEventType.Enter) {
                        activeSubMenu = ExportType.CLASSE_EMAIL_CDC
                    }
            ) {
                DropdownMenuItem(
                    text = {
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
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.TableChart,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = PrimaryBlue
                        )
                    },
                    trailingIcon = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    onClick = {
                        activeSubMenu = if (activeSubMenu == ExportType.CLASSE_EMAIL_CDC) null else ExportType.CLASSE_EMAIL_CDC
                    }
                )

                // Submenu secondo livello (CSV / Excel)
                DropdownMenu(
                    expanded = activeSubMenu == ExportType.CLASSE_EMAIL_CDC,
                    onDismissRequest = { activeSubMenu = null }
                ) {
                    DropdownMenuItem(
                        text = { Text("Esporta in CSV (.csv)", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        onClick = {
                            mainMenuExpanded = false
                            activeSubMenu = null
                            triggerExportDialog(viewModel, ExportType.CLASSE_EMAIL_CDC, ExportFormat.CSV)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Esporta in Excel (.xlsx)", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.GridOn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = SecondaryTeal
                            )
                        },
                        onClick = {
                            mainMenuExpanded = false
                            activeSubMenu = null
                            triggerExportDialog(viewModel, ExportType.CLASSE_EMAIL_CDC, ExportFormat.EXCEL)
                        }
                    )
                }
            }

            HorizontalDivider()

            // ================================================================
            // OPZIONE 2: export classe_email_cdc_email_partecipanti (3 colonne)
            // ================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onPointerEvent(PointerEventType.Enter) {
                        activeSubMenu = ExportType.CLASSE_EMAIL_CDC_EMAIL_PARTECIPANTI
                    }
            ) {
                DropdownMenuItem(
                    text = {
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
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Groups,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = SecondaryTeal
                        )
                    },
                    trailingIcon = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    onClick = {
                        activeSubMenu = if (activeSubMenu == ExportType.CLASSE_EMAIL_CDC_EMAIL_PARTECIPANTI) null else ExportType.CLASSE_EMAIL_CDC_EMAIL_PARTECIPANTI
                    }
                )

                // Submenu secondo livello (CSV / Excel)
                DropdownMenu(
                    expanded = activeSubMenu == ExportType.CLASSE_EMAIL_CDC_EMAIL_PARTECIPANTI,
                    onDismissRequest = { activeSubMenu = null }
                ) {
                    DropdownMenuItem(
                        text = { Text("Esporta in CSV (.csv)", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        onClick = {
                            mainMenuExpanded = false
                            activeSubMenu = null
                            triggerExportDialog(viewModel, ExportType.CLASSE_EMAIL_CDC_EMAIL_PARTECIPANTI, ExportFormat.CSV)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Esporta in Excel (.xlsx)", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.GridOn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = SecondaryTeal
                            )
                        },
                        onClick = {
                            mainMenuExpanded = false
                            activeSubMenu = null
                            triggerExportDialog(viewModel, ExportType.CLASSE_EMAIL_CDC_EMAIL_PARTECIPANTI, ExportFormat.EXCEL)
                        }
                    )
                }
            }
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
