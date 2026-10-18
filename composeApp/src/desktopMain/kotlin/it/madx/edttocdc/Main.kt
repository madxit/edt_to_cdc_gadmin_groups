package it.madx.edttocdc

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import it.madx.edttocdc.ui.MainScreen
import it.madx.edttocdc.ui.MainViewModel
import it.madx.edttocdc.ui.theme.EdtSyncTheme

fun main() = application {
    val windowState = remember { WindowState(size = DpSize(1100.dp, 750.dp)) }

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "EDT to Google Workspace CdC Sync v${BuildConfig.APP_VERSION}",
        icon = runCatching { painterResource("icons/icon.png") }.getOrNull()
    ) {
        val scope = rememberCoroutineScope()
        val viewModel = remember { MainViewModel(scope) }

        EdtSyncTheme {
            MainScreen(viewModel = viewModel)
        }
    }
}
