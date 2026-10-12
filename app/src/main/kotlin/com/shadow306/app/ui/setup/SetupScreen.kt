package com.shadow306.app.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shadow306.app.R
import com.shadow306.app.core.modeldownload.ModelDownloadService
import com.shadow306.app.core.util.Format
import com.shadow306.app.di.ServiceLocator
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(
    onDone: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { ServiceLocator.provideModelRepository(context) }
    val prefs = remember { ServiceLocator.providePrefs(context) }
    val sideloadScanner = remember { ServiceLocator.provideSideloadScanner(context) }
    val state by repo.state.collectAsState()
    val scope = rememberCoroutineScope()

    var sideloadFound by remember { mutableStateOf(false) }
    var sideloadImporting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repo.loadBundledManifest()
        repo.refreshVerification()
        val packs = sideloadScanner.scan()
        sideloadFound = packs.isNotEmpty()
    }

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) {
            prefs.setSetupDone(true)
        }
    }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.setup_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.setup_size_label, Format.bytes(state.totalBytes)), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.setup_free_space, Format.bytes(com.shadow306.app.core.util.FileIo.freeBytes(com.shadow306.app.core.modeldownload.ModelPaths.modelsDir(context)))), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (state.error != null) {
                        Text(state.error.toString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    if (state.currentModelName != null) {
                        Text(stringResource(R.string.setup_downloading_body, state.currentModelName!!, Format.speed(state.speedBps)), style = MaterialTheme.typography.bodySmall)
                        if (state.etaMillis > 0) {
                            Text(stringResource(R.string.setup_eta, Format.duration(state.etaMillis)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    LinearProgressIndicator(
                        progress = { state.percent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                    )
                    Text("${state.percent}% • ${Format.bytes(state.downloadedBytes)} / ${Format.bytes(state.totalBytes)}", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (sideloadFound) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.setup_sideload_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.setup_sideload_body), style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = {
                                scope.launch {
                                    sideloadImporting = true
                                    val packs = sideloadScanner.scan()
                                    packs.firstOrNull()?.let { pack ->
                                        sideloadScanner.importPack(pack)
                                        repo.refreshVerification()
                                    }
                                    sideloadImporting = false
                                }
                            },
                            enabled = !sideloadImporting
                        ) {
                            Text(if (sideloadImporting) "..." else stringResource(R.string.setup_sideload_action))
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            if (state.isComplete) {
                Text(stringResource(R.string.setup_done_body), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.setup_enter_studio))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.setup_skipping))
                    }
                    Button(
                        onClick = {
                            ModelDownloadService.start(context)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (state.downloadedBytes > 0) stringResource(R.string.setup_resume) else stringResource(R.string.setup_start))
                    }
                }
                if (state.isPaused) {
                    TextButton(onClick = { ModelDownloadService.resume(context) }) {
                        Text(stringResource(R.string.setup_resume))
                    }
                }
            }
        }
    }
}
