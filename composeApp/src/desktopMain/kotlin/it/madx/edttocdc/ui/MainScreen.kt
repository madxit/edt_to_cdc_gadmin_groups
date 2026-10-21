package it.madx.edttocdc.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.madx.edttocdc.model.LogEntry
import it.madx.edttocdc.model.LogLevel
import it.madx.edttocdc.ui.components.AboutDialog
import it.madx.edttocdc.ui.components.EdtHelpDialog
import it.madx.edttocdc.ui.components.ExportDropdownButton
import it.madx.edttocdc.ui.components.GoogleHelpDialog
import it.madx.edttocdc.ui.components.SummaryDialog
import it.madx.edttocdc.ui.theme.*
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val listState = rememberLazyListState()

    // Scorrimento automatico verso l'ultima riga dei log
    LaunchedEffect(viewModel.logs.size) {
        if (viewModel.logs.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.logs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.SyncAlt,
                            contentDescription = "Logo",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                "EDT to Google Workspace CdC Sync",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                "Sincronizzazione automatica mailing list Consigli di Classe",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    ExportDropdownButton(
                        viewModel = viewModel,
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    OutlinedButton(
                        onClick = { viewModel.showEdtHelp = true },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Aiuto EDT", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { viewModel.showGoogleHelp = true },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Aiuto Google", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { viewModel.showAbout = true },
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("About", fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // COLONNA SINISTRA: Pannelli di configurazione ed esecuzione
            Column(
                modifier = Modifier
                    .weight(0.52f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. SCHEDA CONFIGURAZIONE EDT
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = PrimaryBlue)
                                Text("1. Sorgente Dati EDT (XML)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            if (viewModel.isEdtVerified) {
                                Surface(
                                    color = SuccessGreen.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                                        Text("Verificato", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = viewModel.settings.edtXmlUrl,
                            onValueChange = { viewModel.updateSettings(viewModel.settings.copy(edtXmlUrl = it)) },
                            label = { Text("URL del file XML EDT (HTTP / HTTPS)") },
                            placeholder = { Text("https://scuola.edu.it/edt_export.xml") },
                            singleLine = true,
                            enabled = !viewModel.isRunning,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = { viewModel.testEdtConnection() },
                                enabled = !viewModel.isRunning && viewModel.settings.edtXmlUrl.isNotBlank()
                            ) {
                                Icon(
                                    if (viewModel.isEdtVerified) Icons.Default.CheckCircle else Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (viewModel.isEdtVerified) "Verificato (Riesegui)" else "Verifica XML")
                            }
                        }
                    }
                }

                // 2. SCHEDA CONFIGURAZIONE GOOGLE WORKSPACE
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = if (!viewModel.isEdtVerified) {
                        CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        )
                    } else {
                        CardDefaults.elevatedCardColors()
                    }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    if (viewModel.isEdtVerified) Icons.Default.AdminPanelSettings else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (viewModel.isEdtVerified) SecondaryTeal else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "2. Destinazione Google Workspace",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (viewModel.isEdtVerified) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (viewModel.isGoogleVerified) {
                                Surface(
                                    color = SuccessGreen.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                                        Text("Verificato", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                            } else if (!viewModel.isEdtVerified) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        "In attesa del punto 1",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        if (!viewModel.isEdtVerified) {
                            Text(
                                "🔒 Completa prima la verifica del file EDT (Punto 1) per sbloccare questo passaggio.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        // Selezione file credenziali JSON
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = if (viewModel.settings.googleCredentialsPath.isNotBlank()) {
                                    File(viewModel.settings.googleCredentialsPath).name
                                } else {
                                    ""
                                },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("File credenziali JSON") },
                                placeholder = { Text("Nessun file selezionato") },
                                modifier = Modifier.weight(1f),
                                enabled = !viewModel.isRunning && viewModel.isEdtVerified
                            )

                            Button(
                                onClick = {
                                    val dialog = FileDialog(null as Frame?, "Seleziona credenziali JSON", FileDialog.LOAD)
                                    dialog.isVisible = true
                                    val dir = dialog.directory
                                    val file = dialog.file
                                    if (dir != null && file != null) {
                                        val fullPath = File(dir, file).absolutePath
                                        viewModel.updateSettings(viewModel.settings.copy(googleCredentialsPath = fullPath))
                                    }
                                },
                                enabled = !viewModel.isRunning && viewModel.isEdtVerified
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Sfoglia...")
                            }
                        }

                        OutlinedTextField(
                            value = viewModel.settings.adminEmail,
                            onValueChange = { input ->
                                val trimmed = input.trim()
                                val inferredDomain = if (trimmed.contains("@")) trimmed.substringAfter('@') else viewModel.settings.schoolDomain
                                viewModel.updateSettings(
                                    viewModel.settings.copy(
                                        adminEmail = trimmed,
                                        schoolDomain = if (viewModel.settings.schoolDomain.isBlank() && inferredDomain.isNotBlank()) inferredDomain else viewModel.settings.schoolDomain
                                    )
                                )
                            },
                            label = { Text("Email Amministratore Google Workspace (DWD)") },
                            placeholder = {
                                val detected = viewModel.settings.schoolDomain
                                if (detected.isNotBlank()) Text("admin@$detected") else Text("admin@scuola.edu.it")
                            },
                            singleLine = true,
                            enabled = !viewModel.isRunning && viewModel.isEdtVerified,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = { viewModel.testGoogleConnection() },
                                enabled = !viewModel.isRunning &&
                                    viewModel.isEdtVerified &&
                                    viewModel.settings.googleCredentialsPath.isNotBlank() &&
                                    viewModel.settings.adminEmail.isNotBlank()
                            ) {
                                Icon(
                                    if (viewModel.isGoogleVerified) Icons.Default.CheckCircle else Icons.Default.CloudSync,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (viewModel.isGoogleVerified) "Verificato (Riesegui)" else "Verifica Connessione")
                            }
                        }
                    }
                }

                // 3. ESECUZIONE SINCRONIZZAZIONE
                val canStartSync = !viewModel.isRunning && viewModel.isEdtVerified && viewModel.isGoogleVerified

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = if (!canStartSync) {
                        CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                        )
                    } else {
                        CardDefaults.elevatedCardColors()
                    }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    if (canStartSync) Icons.Default.PlayCircle else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (canStartSync) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "3. Esecuzione Sincronizzazione",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (canStartSync) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (!canStartSync) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        "In attesa verifiche",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            when {
                                !viewModel.isEdtVerified -> "🔒 Completa con successo la verifica del file EDT (Punto 1) per abilitare la procedura."
                                !viewModel.isGoogleVerified -> "🔒 Completa con successo la verifica delle credenziali Google Workspace (Punto 2) per abilitare la procedura."
                                else -> "Tutte le verifiche preliminari sono state superate. Premere il pulsante per avviare la sincronizzazione e l'aggiornamento reale dei gruppi CdC."
                            },
                            fontSize = 12.sp,
                            color = if (canStartSync) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                        )

                        AnimatedVisibility(visible = viewModel.isRunning) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(viewModel.progressStage, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    if (viewModel.progressTotal > 0) {
                                        Text("${viewModel.progressCurrent}/${viewModel.progressTotal}", fontSize = 13.sp)
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = {
                                        if (viewModel.progressTotal > 0) {
                                            viewModel.progressCurrent.toFloat() / viewModel.progressTotal.toFloat()
                                        } else {
                                            0f
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                                )
                            }
                        }

                        Button(
                            onClick = { viewModel.startSync(runDryRun = false) },
                            enabled = canStartSync,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryBlue
                            ),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            if (viewModel.isRunning) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Sincronizzazione in corso...")
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Avvia Sincronizzazione", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // COLONNA DESTRA: Console e Cronologia dei Log
            ElevatedCard(
                modifier = Modifier
                    .weight(0.48f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Intestazione log con azioni
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                            Text("Registro Operazioni (Log)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Switch per abilitare o disabilitare il log dettagliato dei gruppi (default off)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "Log gruppi",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Switch(
                                    checked = viewModel.logGroupsDetails,
                                    onCheckedChange = { viewModel.logGroupsDetails = it },
                                    modifier = Modifier.scale(0.8f)
                                )
                            }

                            Spacer(Modifier.width(2.dp))

                            IconButton(
                                onClick = { viewModel.copyLogsToClipboard() },
                                enabled = viewModel.logs.isNotEmpty()
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copia log", modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = {
                                    val dialog = FileDialog(null as Frame?, "Salva file log", FileDialog.SAVE)
                                    dialog.file = "sync_log_${System.currentTimeMillis()}.txt"
                                    dialog.isVisible = true
                                    val dir = dialog.directory
                                    val file = dialog.file
                                    if (dir != null && file != null) {
                                        viewModel.saveLogsToFile(File(dir, file))
                                    }
                                },
                                enabled = viewModel.logs.isNotEmpty()
                            ) {
                                Icon(Icons.Default.SaveAlt, contentDescription = "Salva log su file", modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = { viewModel.clearLogs() },
                                enabled = viewModel.logs.isNotEmpty() && !viewModel.isRunning
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Pulisci", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // Area di testo dei log non modificabile con auto-scroll e scorrimento laterale
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F1115))
                            .border(1.dp, Color(0xFF232730), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        if (viewModel.logs.isEmpty()) {
                            Text(
                                "Nessun evento registrato. Avvia una verifica o sincronizzazione.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            val horizontalScrollState = rememberScrollState()
                            Box(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(end = 12.dp, bottom = 12.dp)
                                        .horizontalScroll(horizontalScrollState),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(viewModel.logs) { log ->
                                        LogLineItem(log)
                                    }
                                }

                                val scrollbarStyle = defaultScrollbarStyle().copy(
                                    unhoverColor = Color(0xFF64748B),
                                    hoverColor = Color(0xFF60A5FA),
                                    shape = RoundedCornerShape(4.dp),
                                    thickness = 8.dp
                                )

                                HorizontalScrollbar(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .padding(end = 12.dp)
                                        .background(Color(0xFF1E2430), RoundedCornerShape(4.dp)),
                                    adapter = rememberScrollbarAdapter(horizontalScrollState),
                                    style = scrollbarStyle
                                )

                                VerticalScrollbar(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .fillMaxHeight()
                                        .padding(bottom = 12.dp)
                                        .background(Color(0xFF1E2430), RoundedCornerShape(4.dp)),
                                    adapter = rememberScrollbarAdapter(listState),
                                    style = scrollbarStyle
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog modali
    if (viewModel.showEdtHelp) {
        EdtHelpDialog(onDismiss = { viewModel.showEdtHelp = false })
    }

    if (viewModel.showGoogleHelp) {
        GoogleHelpDialog(onDismiss = { viewModel.showGoogleHelp = false })
    }

    if (viewModel.showAbout) {
        AboutDialog(onDismiss = { viewModel.showAbout = false })
    }

    viewModel.activeSummary?.let { summary ->
        SummaryDialog(summary = summary, onDismiss = { viewModel.activeSummary = null })
    }
}

@Composable
private fun LogLineItem(log: LogEntry) {
    val levelColor = when (log.level) {
        LogLevel.INFO -> Color(0xFF60A5FA)
        LogLevel.SUCCESS -> Color(0xFF34D399)
        LogLevel.WARNING -> Color(0xFFFBBF24)
        LogLevel.ERROR -> Color(0xFFF87171)
        LogLevel.DRY_RUN -> Color(0xFFA78BFA)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "[${log.timestamp}]",
            color = Color(0xFF6B7280),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            softWrap = false
        )
        Text(
            text = log.message,
            color = levelColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            softWrap = false
        )
    }
}
